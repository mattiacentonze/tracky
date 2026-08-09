package com.aloneagle.tracky.ble.transport

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aloneagle.tracky.di.IoDispatcher
import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.service.BleLogSink
import com.aloneagle.tracky.domain.service.BleScanException
import com.aloneagle.tracky.domain.service.BleScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

@Singleton
class AndroidBleScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bleLogSink: BleLogSink,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BleScanner {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
    private val logScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun scan(
        sessionId: String,
        sessionType: ScanSessionType,
        targetAddresses: Set<String>,
        onScanHealthChanged: (Boolean) -> Unit,
    ): Flow<BleScanResult> = callbackFlow {
        if (!hasScanPermission()) {
            onScanHealthChanged(false)
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = null,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Scan,
                    action = "permission_missing",
                    message = "BLUETOOTH_SCAN permission is missing.",
                ),
            )
            close(BleScanException("BLUETOOTH_SCAN permission is missing."))
            return@callbackFlow
        }
        val adapter = bluetoothManager?.adapter
        val scanner = try {
            if (adapter?.isEnabled == true) adapter.bluetoothLeScanner else null
        } catch (securityException: SecurityException) {
            onScanHealthChanged(false)
            close(BleScanException("Unable to access the Bluetooth scanner.", securityException))
            return@callbackFlow
        }
        if (adapter == null || scanner == null) {
            onScanHealthChanged(false)
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = null,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Scan,
                    action = "scanner_unavailable",
                    message = "Bluetooth adapter or scanner is unavailable.",
                ),
            )
            close(BleScanException("Bluetooth adapter or scanner is unavailable."))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toDomainResult())
                log(
                    BleLogEvent(
                        sessionId = sessionId,
                        trackerId = result.device?.address,
                        timestamp = System.currentTimeMillis(),
                        category = BleLogEvent.Category.Scan,
                        action = "scan_result",
                        message = "Scan result received.",
                        deviceAddress = result.device?.address,
                        rssi = result.rssi,
                    ),
                )
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { scanResult -> onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult) }
            }

            override fun onScanFailed(errorCode: Int) {
                onScanHealthChanged(false)
                log(
                    BleLogEvent(
                        sessionId = sessionId,
                        trackerId = null,
                        timestamp = System.currentTimeMillis(),
                        category = BleLogEvent.Category.Scan,
                        action = "scan_failed",
                        message = "Scan failed with code $errorCode.",
                        resultCode = errorCode,
                    ),
                )
                close(BleScanException("BLE scan failed with code $errorCode."))
            }
        }

        val bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) return
                onScanHealthChanged(false)
                log(
                    BleLogEvent(
                        sessionId = sessionId,
                        trackerId = null,
                        timestamp = System.currentTimeMillis(),
                        category = BleLogEvent.Category.Scan,
                        action = "scanner_unavailable",
                        message = "Bluetooth was turned off while scanning.",
                    ),
                )
                close(BleScanException("Bluetooth was turned off while scanning."))
            }
        }
        var receiverRegistered = false

        try {
            ContextCompat.registerReceiver(
                context,
                bluetoothStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
            scanner.startScan(
                buildFilters(targetAddresses),
                buildSettings(sessionType),
                callback,
            )
            onScanHealthChanged(true)
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = null,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Scan,
                    action = "scan_started",
                    message = "Started ${sessionType.name.lowercase()} scan for ${targetAddresses.size} target(s).",
                ),
            )
        } catch (securityException: SecurityException) {
            onScanHealthChanged(false)
            if (receiverRegistered) {
                runCatching { context.unregisterReceiver(bluetoothStateReceiver) }
                receiverRegistered = false
            }
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = null,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Scan,
                    action = "scan_security_exception",
                    message = securityException.message ?: "SecurityException while starting scan.",
                ),
            )
            close(BleScanException("Unable to start the Bluetooth scan.", securityException))
            return@callbackFlow
        } catch (runtimeException: RuntimeException) {
            onScanHealthChanged(false)
            if (receiverRegistered) {
                runCatching { context.unregisterReceiver(bluetoothStateReceiver) }
                receiverRegistered = false
            }
            close(BleScanException("Unable to start the Bluetooth scan.", runtimeException))
            return@callbackFlow
        }

        awaitClose {
            onScanHealthChanged(false)
            runCatching { scanner.stopScan(callback) }
            if (receiverRegistered) {
                runCatching { context.unregisterReceiver(bluetoothStateReceiver) }
                receiverRegistered = false
            }
            log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = null,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Scan,
                    action = "scan_stopped",
                    message = "Stopped ${sessionType.name.lowercase()} scan.",
                ),
            )
        }
    }

    private fun buildFilters(targetAddresses: Set<String>): List<ScanFilter> = targetAddresses
        .filter { it.isNotBlank() }
        .map { address -> ScanFilter.Builder().setDeviceAddress(address).build() }

    private fun buildSettings(sessionType: ScanSessionType): ScanSettings = ScanSettings.Builder()
        .setScanMode(
            when (sessionType) {
                ScanSessionType.Manual,
                ScanSessionType.Search,
                -> ScanSettings.SCAN_MODE_LOW_LATENCY

                ScanSessionType.Monitor -> ScanSettings.SCAN_MODE_BALANCED
            },
        )
        .build()

    private fun ScanResult.toDomainResult(): BleScanResult {
        val serviceUuids = scanRecord?.serviceUuids?.map { uuid -> uuid.uuid.toString().lowercase() }.orEmpty()
        val manufacturerData = scanRecord?.manufacturerSpecificData
        val manufacturerHex = manufacturerData
            ?.takeIf { it.size() > 0 }
            ?.let { sparse ->
                buildString {
                    for (index in 0 until sparse.size()) {
                        val key = sparse.keyAt(index)
                        val value = sparse.valueAt(index)
                        append(key.toString(16).padStart(4, '0'))
                        append(':')
                        append(value.joinToString(separator = "") { byte -> "%02x".format(byte) })
                        if (index < sparse.size() - 1) {
                            append(',')
                        }
                    }
                }
            }
        return BleScanResult(
            deviceAddress = device.address.orEmpty(),
            advertisedName = scanRecord?.deviceName,
            resolvedName = device.name,
            manufacturerDataHex = manufacturerHex,
            serviceUuids = serviceUuids,
            rssi = rssi,
            seenAt = System.currentTimeMillis(),
            connectable = isConnectable,
        )
    }

    private fun log(event: BleLogEvent) {
        logScope.launch { bleLogSink.log(event) }
    }

    private fun hasScanPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED
}
