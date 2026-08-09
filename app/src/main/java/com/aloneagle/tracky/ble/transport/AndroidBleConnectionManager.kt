package com.aloneagle.tracky.ble.transport

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.aloneagle.tracky.di.IoDispatcher
import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.GattCharacteristicProperty
import com.aloneagle.tracky.domain.model.GattCharacteristicSummary
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.model.TrackerConnectionState
import com.aloneagle.tracky.domain.service.BleConnectionManager
import com.aloneagle.tracky.domain.service.BleLogSink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AndroidBleConnectionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bleLogSink: BleLogSink,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BleConnectionManager {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    override suspend fun connect(deviceAddress: String, sessionId: String): BleConnectionManager.ConnectionSession? {
        if (!hasConnectPermission()) {
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = deviceAddress,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Connection,
                    action = "permission_missing",
                    message = "BLUETOOTH_CONNECT permission is missing.",
                    deviceAddress = deviceAddress,
                ),
            )
            return null
        }
        val adapter = bluetoothManager?.adapter ?: return null
        val device = runCatching { adapter.getRemoteDevice(deviceAddress) }.getOrNull() ?: return null
        return withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val session = ConnectionSessionImpl(
                    context = context,
                    device = device,
                    sessionId = sessionId,
                    bleLogSink = bleLogSink,
                    ioDispatcher = ioDispatcher,
                )
                continuation.invokeOnCancellation { session.disconnectSafely() }
                session.open(continuation::resume)
            }
        }
    }

    private fun hasConnectPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED

    private fun log(event: BleLogEvent) {
        CoroutineScope(SupervisorJob() + ioDispatcher).launch { bleLogSink.log(event) }
    }

    private class ConnectionSessionImpl(
        private val context: Context,
        private val device: BluetoothDevice,
        private val sessionId: String,
        private val bleLogSink: BleLogSink,
        private val ioDispatcher: CoroutineDispatcher,
    ) : BleConnectionManager.ConnectionSession {
        override val deviceAddress: String = device.address.orEmpty()
        override val connectionState = kotlinx.coroutines.flow.MutableStateFlow(TrackerConnectionState.Connecting)

        private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        private val operationMutex = Mutex()
        private var bluetoothGatt: BluetoothGatt? = null
        private var pendingDiscovery: CompletableDeferred<Result<List<GattServiceSummary>>>? = null
        private var pendingRead: CompletableDeferred<Result<ByteArray>>? = null
        private var pendingWrite: CompletableDeferred<Result<Unit>>? = null
        private var isClosed = false

        fun open(onReady: (BleConnectionManager.ConnectionSession?) -> Unit) {
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = deviceAddress,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Connection,
                    action = "connect_start",
                    message = "Connecting to $deviceAddress",
                    deviceAddress = deviceAddress,
                ),
            )
            readyContinuation = onReady
            val connectionAttempt: Result<BluetoothGatt?> = if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Result.failure(missingConnectPermission("start a GATT connection"))
            } else {
                try {
                    Result.success(device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE))
                } catch (exception: SecurityException) {
                    Result.failure(exception)
                } catch (exception: RuntimeException) {
                    Result.failure(exception)
                }
            }
            bluetoothGatt = connectionAttempt.onFailure { throwable ->
                connectionState.value = TrackerConnectionState.Failed
                log(
                    BleLogEvent(
                        sessionId = sessionId,
                        trackerId = deviceAddress,
                        timestamp = System.currentTimeMillis(),
                        category = BleLogEvent.Category.Connection,
                        action = "connect_exception",
                        message = throwable.message ?: "Failed to start GATT connection.",
                        deviceAddress = deviceAddress,
                    ),
                )
            }.getOrNull()

            if (bluetoothGatt == null) {
                readyContinuation?.invoke(null)
                readyContinuation = null
            }
        }

        private var readyContinuation: ((BleConnectionManager.ConnectionSession?) -> Unit)? = null

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> {
                        connectionState.value = TrackerConnectionState.Failed
                        readyContinuation?.invoke(null)
                        readyContinuation = null
                        failPending(IllegalStateException("GATT connection failed with status $status"))
                        disconnectSafely()
                    }

                    newState == BluetoothGatt.STATE_CONNECTED -> {
                        connectionState.value = TrackerConnectionState.Connected
                        log(
                            BleLogEvent(
                                sessionId = sessionId,
                                trackerId = deviceAddress,
                                timestamp = System.currentTimeMillis(),
                                category = BleLogEvent.Category.Connection,
                                action = "connected",
                                message = "Connected to tracker.",
                                deviceAddress = deviceAddress,
                            ),
                        )
                        readyContinuation?.invoke(this@ConnectionSessionImpl)
                        readyContinuation = null
                    }

                    newState == BluetoothGatt.STATE_DISCONNECTED -> {
                        connectionState.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                            TrackerConnectionState.Disconnected
                        } else {
                            TrackerConnectionState.Failed
                        }
                        log(
                            BleLogEvent(
                                sessionId = sessionId,
                                trackerId = deviceAddress,
                                timestamp = System.currentTimeMillis(),
                                category = BleLogEvent.Category.Connection,
                                action = "disconnected",
                                message = "Disconnected from tracker with status $status.",
                                deviceAddress = deviceAddress,
                                resultCode = status,
                            ),
                        )
                        readyContinuation?.invoke(null)
                        readyContinuation = null
                        failPending(IllegalStateException("Disconnected with status $status"))
                        disconnectSafely()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = gatt.services.map { service -> service.toServiceSummary() }
                    connectionState.value = TrackerConnectionState.Ready
                    log(
                        BleLogEvent(
                            sessionId = sessionId,
                            trackerId = deviceAddress,
                            timestamp = System.currentTimeMillis(),
                            category = BleLogEvent.Category.Discovery,
                            action = "services_discovered",
                            message = "Discovered ${services.size} service(s).",
                            deviceAddress = deviceAddress,
                        ),
                    )
                    services.forEach { service ->
                        log(
                            BleLogEvent(
                                sessionId = sessionId,
                                trackerId = deviceAddress,
                                timestamp = System.currentTimeMillis(),
                                category = BleLogEvent.Category.Discovery,
                                action = "service_inventory",
                                message = "Service ${service.serviceUuid} with ${service.characteristicUuids.size} characteristic(s).",
                                deviceAddress = deviceAddress,
                                serviceUuid = service.serviceUuid,
                                payloadHex = service.characteristics.joinToString(separator = ",") { characteristic ->
                                    buildString {
                                        append(characteristic.characteristicUuid)
                                        if (characteristic.properties.isNotEmpty()) {
                                            append("[")
                                            append(characteristic.properties.joinToString("|"))
                                            append("]")
                                        }
                                    }
                                },
                            ),
                        )
                    }
                    Result.success(services)
                } else {
                    connectionState.value = TrackerConnectionState.Failed
                    Result.failure(IllegalStateException("Service discovery failed: $status"))
                }
                pendingDiscovery?.complete(result)
                pendingDiscovery = null
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                completeRead(characteristic, characteristic.value, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                completeRead(characteristic, value, status)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                completeWrite(characteristic, status)
            }

            @Deprecated("Deprecated in Java")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
            }
        }

        override suspend fun discoverServices(): Result<List<GattServiceSummary>> = operationMutex.withLock {
            val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("No active connection"))
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.failure(missingConnectPermission("discover GATT services"))
            }
            connectionState.value = TrackerConnectionState.DiscoveringServices
            val deferred = CompletableDeferred<Result<List<GattServiceSummary>>>()
            pendingDiscovery = deferred
            val started = try {
                gatt.discoverServices()
            } catch (exception: SecurityException) {
                pendingDiscovery = null
                return Result.failure(exception)
            } catch (exception: RuntimeException) {
                pendingDiscovery = null
                return Result.failure(exception)
            }
            if (!started) {
                pendingDiscovery = null
                return Result.failure(IllegalStateException("Failed to start service discovery"))
            }
            withTimeoutOrNull(OPERATION_TIMEOUT_MILLIS) { deferred.await() }
                ?: run {
                    pendingDiscovery = null
                    Result.failure(IllegalStateException("Service discovery timed out"))
                }
        }

        override suspend fun readCharacteristic(
            serviceUuid: String,
            characteristicUuid: String,
        ): Result<ByteArray> = operationMutex.withLock {
            val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("No active connection"))
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.failure(missingConnectPermission("read a GATT characteristic"))
            }
            val characteristic = gatt.findCharacteristic(serviceUuid, characteristicUuid)
                ?: return Result.failure(IllegalArgumentException("Characteristic $characteristicUuid not found"))
            val deferred = CompletableDeferred<Result<ByteArray>>()
            pendingRead = deferred
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = deviceAddress,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Read,
                    action = "read_start",
                    message = "Reading characteristic $characteristicUuid",
                    deviceAddress = deviceAddress,
                    serviceUuid = serviceUuid,
                    characteristicUuid = characteristicUuid,
                ),
            )
            val started = try {
                gatt.readCharacteristic(characteristic)
            } catch (exception: SecurityException) {
                pendingRead = null
                return Result.failure(exception)
            } catch (exception: RuntimeException) {
                pendingRead = null
                return Result.failure(exception)
            }
            if (!started) {
                pendingRead = null
                return Result.failure(IllegalStateException("Failed to start characteristic read"))
            }
            withTimeoutOrNull(OPERATION_TIMEOUT_MILLIS) { deferred.await() }
                ?: run {
                    pendingRead = null
                    Result.failure(IllegalStateException("Characteristic read timed out"))
                }
        }

        override suspend fun writeCharacteristic(
            serviceUuid: String,
            characteristicUuid: String,
            payload: ByteArray,
            writeType: Int?,
        ): Result<Unit> = operationMutex.withLock {
            val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("No active connection"))
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.failure(missingConnectPermission("write a GATT characteristic"))
            }
            val characteristic = gatt.findCharacteristic(serviceUuid, characteristicUuid)
                ?: return Result.failure(IllegalArgumentException("Characteristic $characteristicUuid not found"))
            val effectiveWriteType = writeType ?: characteristic.preferredWriteType()
            val deferred = CompletableDeferred<Result<Unit>>()
            pendingWrite = deferred
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = deviceAddress,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Write,
                    action = "write_start",
                    message = "Writing characteristic $characteristicUuid",
                    deviceAddress = deviceAddress,
                    serviceUuid = serviceUuid,
                    characteristicUuid = characteristicUuid,
                    payloadHex = payload.toHexString(),
                    resultCode = effectiveWriteType,
                ),
            )
            val started = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        payload,
                        effectiveWriteType,
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = payload
                    characteristic.writeType = effectiveWriteType
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
            } catch (exception: SecurityException) {
                pendingWrite = null
                return Result.failure(exception)
            } catch (exception: RuntimeException) {
                pendingWrite = null
                return Result.failure(exception)
            }
            if (!started) {
                pendingWrite = null
                return Result.failure(IllegalStateException("Failed to start characteristic write"))
            }
            withTimeoutOrNull(OPERATION_TIMEOUT_MILLIS) { deferred.await() }
                ?: run {
                    pendingWrite = null
                    Result.failure(IllegalStateException("Characteristic write timed out"))
                }
        }

        override suspend fun disconnect() {
            disconnectSafely()
        }

        fun disconnectSafely() {
            if (isClosed) return
            isClosed = true
            val gatt = bluetoothGatt
            bluetoothGatt = null
            if (gatt == null) return
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                handleGattCleanupFailure(
                    operation = "disconnect",
                    throwable = missingConnectPermission("disconnect and close the GATT connection"),
                )
                return
            }
            try {
                gatt.disconnect()
            } catch (exception: SecurityException) {
                handleGattCleanupFailure("disconnect", exception)
            } catch (exception: RuntimeException) {
                handleGattCleanupFailure("disconnect", exception)
            }
            try {
                gatt.close()
            } catch (exception: SecurityException) {
                handleGattCleanupFailure("close", exception)
            } catch (exception: RuntimeException) {
                handleGattCleanupFailure("close", exception)
            }
        }

        private fun missingConnectPermission(operation: String): SecurityException = SecurityException(
            "BLUETOOTH_CONNECT permission is required to $operation.",
        )

        private fun handleGattCleanupFailure(operation: String, throwable: Throwable) {
            failPending(throwable)
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = deviceAddress,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Connection,
                    action = "${operation}_exception",
                    message = throwable.message ?: "Failed to $operation the GATT connection.",
                    deviceAddress = deviceAddress,
                ),
            )
        }

        private fun completeRead(characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                Result.success(value)
            } else {
                Result.failure(IllegalStateException("Read failed with status $status"))
            }
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = deviceAddress,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Read,
                    action = "read_complete",
                    message = "Read ${characteristic.uuid}",
                    deviceAddress = deviceAddress,
                    serviceUuid = characteristic.service.uuid.toString(),
                    characteristicUuid = characteristic.uuid.toString(),
                    resultCode = status,
                    payloadHex = value.toHexString(),
                ),
            )
            pendingRead?.complete(result)
            pendingRead = null
        }

        private fun completeWrite(characteristic: BluetoothGattCharacteristic, status: Int) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Write failed with status $status"))
            }
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = deviceAddress,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Write,
                    action = "write_complete",
                    message = "Write ${characteristic.uuid}",
                    deviceAddress = deviceAddress,
                    serviceUuid = characteristic.service.uuid.toString(),
                    characteristicUuid = characteristic.uuid.toString(),
                    resultCode = status,
                ),
            )
            pendingWrite?.complete(result)
            pendingWrite = null
        }

        private fun failPending(throwable: Throwable) {
            pendingDiscovery?.complete(Result.failure(throwable))
            pendingRead?.complete(Result.failure(throwable))
            pendingWrite?.complete(Result.failure(throwable))
            pendingDiscovery = null
            pendingRead = null
            pendingWrite = null
        }

        private fun BluetoothGatt.findCharacteristic(serviceUuid: String, characteristicUuid: String): BluetoothGattCharacteristic? =
            services.firstOrNull { it.uuid.toString().equals(serviceUuid, ignoreCase = true) }
                ?.characteristics
                ?.firstOrNull { it.uuid.toString().equals(characteristicUuid, ignoreCase = true) }

        private fun BluetoothGattService.toServiceSummary(): GattServiceSummary = GattServiceSummary(
            serviceUuid = uuid.toString(),
            characteristics = characteristics.map { characteristic ->
                GattCharacteristicSummary(
                    characteristicUuid = characteristic.uuid.toString(),
                    properties = characteristic.properties.toCharacteristicProperties(),
                    descriptorUuids = characteristic.descriptors.map { descriptor -> descriptor.uuid.toString() },
                )
            },
        )

        private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

        private fun BluetoothGattCharacteristic.preferredWriteType(): Int = when {
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        private fun Int.toCharacteristicProperties(): Set<GattCharacteristicProperty> = buildSet {
            if (this@toCharacteristicProperties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                add(GattCharacteristicProperty.Read)
            }
            if (this@toCharacteristicProperties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                add(GattCharacteristicProperty.Write)
            }
            if (this@toCharacteristicProperties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                add(GattCharacteristicProperty.WriteNoResponse)
            }
            if (this@toCharacteristicProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                add(GattCharacteristicProperty.Notify)
            }
            if (this@toCharacteristicProperties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                add(GattCharacteristicProperty.Indicate)
            }
        }

        private fun log(event: BleLogEvent) {
            scope.launch { bleLogSink.log(event) }
        }
    }
}

private const val CONNECT_TIMEOUT_MILLIS = 12_000L
private const val OPERATION_TIMEOUT_MILLIS = 10_000L
