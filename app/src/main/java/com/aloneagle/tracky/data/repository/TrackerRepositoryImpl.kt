package com.aloneagle.tracky.data.repository

import com.aloneagle.tracky.ble.protocol.ProtocolRegistry
import com.aloneagle.tracky.data.local.dao.BleLogEventDao
import com.aloneagle.tracky.data.local.dao.KnownTrackerDao
import com.aloneagle.tracky.data.local.dao.TrackerObservationDao
import com.aloneagle.tracky.data.local.entity.KnownTrackerEntity
import com.aloneagle.tracky.data.local.entity.TrackerObservationEntity
import com.aloneagle.tracky.di.IoDispatcher
import com.aloneagle.tracky.domain.model.BatteryState
import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.GattCharacteristicSummary
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.PairedBluetoothDevice
import com.aloneagle.tracky.domain.model.RingResult
import com.aloneagle.tracky.domain.model.TrackerCapability
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.model.TrackerConnectionState
import com.aloneagle.tracky.domain.model.TrackerPresence
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.service.BleConnectionManager
import com.aloneagle.tracky.domain.service.BleLogSink
import com.aloneagle.tracky.domain.service.LocationSnapshotProvider
import com.aloneagle.tracky.domain.service.ProximityEstimator
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class TrackerRepositoryImpl @Inject constructor(
    private val knownTrackerDao: KnownTrackerDao,
    private val observationDao: TrackerObservationDao,
    private val bleLogEventDao: BleLogEventDao,
    private val proximityEstimator: ProximityEstimator,
    private val protocolRegistry: ProtocolRegistry,
    private val bleConnectionManager: BleConnectionManager,
    private val locationSnapshotProvider: LocationSnapshotProvider,
    private val bleLogSink: BleLogSink,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TrackerRepository {
    private val lastPersistedObservationAt = ConcurrentHashMap<String, Long>()

    override fun observeKnownTrackers(): Flow<List<KnownTracker>> = knownTrackerDao.observeAll()
        .map { entities -> entities.map(KnownTrackerEntity::toDomain) }

    override fun observeTracker(id: String): Flow<KnownTracker?> = knownTrackerDao.observeById(id)
        .map { entity -> entity?.toDomain() }

    override fun observeRecentObservations(id: String, limit: Int) = observationDao.observeByTrackerId(id, limit)
        .map { rows -> rows.map { it.toDomain() } }

    override fun observeBleLogs(trackerId: String?, limit: Int) =
        if (trackerId == null) {
            bleLogEventDao.observeRecent(limit)
        } else {
            bleLogEventDao.observeForTracker(trackerId, limit)
        }.map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertFromScan(scanResult: BleScanResult, sessionType: ScanSessionType): KnownTracker =
        withContext(ioDispatcher) {
            val existing = knownTrackerDao.getById(scanResult.deviceAddress)
            val smoothedRssi = proximityEstimator.smooth(existing?.smoothedRssi, scanResult.rssi)
            val estimate = proximityEstimator.estimate(existing?.smoothedRssi, smoothedRssi)
            // Last-seen phone location is captured only while the foreground Devices UI owns
            // the manual scan. Background monitoring must not request while-in-use location.
            val location = if (sessionType == ScanSessionType.Manual) {
                locationSnapshotProvider.currentSnapshot()
            } else {
                null
            }
            val adapter = protocolRegistry.resolve(scanResult)
            val entity = KnownTrackerEntity(
                id = scanResult.deviceAddress,
                deviceAddress = scanResult.deviceAddress,
                nickname = existing?.nickname,
                advertisedName = scanResult.advertisedName ?: existing?.advertisedName,
                resolvedName = scanResult.resolvedName ?: existing?.resolvedName,
                protocolType = adapter.protocolType.name,
                capabilities = adapter.inferCapabilities(scanResult).map { it.name },
                batteryPercent = existing?.batteryPercent,
                batteryStatus = existing?.batteryStatus ?: BatteryState.Status.Unknown.name,
                batteryUpdatedAt = existing?.batteryUpdatedAt,
                lastSeenAt = scanResult.seenAt,
                lastRssi = scanResult.rssi,
                smoothedRssi = smoothedRssi,
                lastLatitude = location?.latitude ?: existing?.lastLatitude,
                lastLongitude = location?.longitude ?: existing?.lastLongitude,
                lastAccuracyMeters = location?.accuracyMeters ?: existing?.lastAccuracyMeters,
                lastLocationAt = location?.observedAt ?: existing?.lastLocationAt,
                presenceState = resolvePresence(scanResult.seenAt).name,
                connectionState = TrackerConnectionState.Disconnected.name,
                monitorEnabled = existing?.monitorEnabled ?: false,
                discoveredServices = existing?.discoveredServices.orEmpty(),
                manufacturerDataHex = scanResult.manufacturerDataHex ?: existing?.manufacturerDataHex,
                isNutCandidate = adapter.protocolType.name == "NutFindthing",
                lastSessionType = sessionType.name,
                lastOutOfRangeAlertAt = existing?.lastOutOfRangeAlertAt,
            )
            knownTrackerDao.upsert(entity)
            val previousPersistedAt = lastPersistedObservationAt[scanResult.deviceAddress]
            val shouldPersistObservation = previousPersistedAt == null ||
                scanResult.seenAt - previousPersistedAt >= OBSERVATION_PERSIST_INTERVAL_MILLIS
            if (shouldPersistObservation) {
                lastPersistedObservationAt[scanResult.deviceAddress] = scanResult.seenAt
                observationDao.insert(TrackerObservationEntity(
                    trackerId = scanResult.deviceAddress,
                    seenAt = scanResult.seenAt,
                    sessionType = sessionType.name,
                    rssi = scanResult.rssi,
                    smoothedRssi = smoothedRssi,
                    advertisedName = scanResult.advertisedName,
                    resolvedName = scanResult.resolvedName,
                    proximityLevel = estimate.level.name,
                    estimatedDistanceMeters = estimate.estimatedDistanceMeters,
                    signalPercent = estimate.signalPercent,
                    proximityTrend = estimate.trend.name,
                    proximityDescriptor = estimate.descriptor,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracyMeters = location?.accuracyMeters,
                    locationObservedAt = location?.observedAt,
                    connected = false,
                    serviceUuids = scanResult.serviceUuids,
                    batteryPercent = existing?.batteryPercent,
                ))
                bleLogSink.log(BleLogEvent(
                    sessionId = "repo-${scanResult.seenAt}",
                    trackerId = scanResult.deviceAddress,
                    timestamp = scanResult.seenAt,
                    category = BleLogEvent.Category.Repository,
                    action = "scan_persisted",
                    message = "Persisted scan result for ${scanResult.deviceAddress}",
                    deviceAddress = scanResult.deviceAddress,
                    rssi = scanResult.rssi,
                ))
                adapter.notes(scanResult).forEach { note ->
                    bleLogSink.log(BleLogEvent(
                        sessionId = "repo-${scanResult.seenAt}",
                        trackerId = scanResult.deviceAddress,
                        timestamp = scanResult.seenAt,
                        category = BleLogEvent.Category.Protocol,
                        action = "adapter_note",
                        message = note,
                        deviceAddress = scanResult.deviceAddress,
                    ))
                }
            }
            // Preserve the estimate computed from this exact observation. The Room entity
            // stores only the smoothed RSSI, so reconstructing it here would otherwise lose
            // distance and trend before the live finder receives the value.
            entity.toDomain().copy(proximity = estimate)
        }

    override suspend fun upsertPairedDevice(device: PairedBluetoothDevice): KnownTracker = withContext(ioDispatcher) {
        val existing = knownTrackerDao.getById(device.deviceAddress)
        val entity = existing?.copy(
            resolvedName = device.systemName ?: existing.resolvedName,
        ) ?: KnownTrackerEntity(
            id = device.deviceAddress,
            deviceAddress = device.deviceAddress,
            nickname = null,
            advertisedName = null,
            resolvedName = device.systemName,
            protocolType = com.aloneagle.tracky.domain.model.TrackerProtocolType.GenericBle.name,
            capabilities = emptyList(),
            batteryPercent = null,
            batteryStatus = BatteryState.Status.Unknown.name,
            batteryUpdatedAt = null,
            lastSeenAt = null,
            lastRssi = null,
            smoothedRssi = null,
            lastLatitude = null,
            lastLongitude = null,
            lastAccuracyMeters = null,
            lastLocationAt = null,
            presenceState = TrackerPresence.NotRecent.name,
            connectionState = TrackerConnectionState.Disconnected.name,
            monitorEnabled = false,
            discoveredServices = emptyList(),
            manufacturerDataHex = null,
            isNutCandidate = false,
            lastSessionType = null,
            lastOutOfRangeAlertAt = null,
        )
        knownTrackerDao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun renameTracker(id: String, nickname: String) = withContext(ioDispatcher) {
        knownTrackerDao.updateNickname(id, nickname)
    }

    override suspend fun setMonitorEnabled(id: String, enabled: Boolean) = withContext(ioDispatcher) {
        knownTrackerDao.updateMonitorEnabled(id, enabled)
    }

    override suspend fun refreshTracker(id: String, sessionId: String): Result<KnownTracker> = withContext(ioDispatcher) {
        val tracker = knownTrackerDao.getById(id) ?: return@withContext Result.failure(
            IllegalArgumentException("Unknown tracker: $id"),
        )
        val connection = bleConnectionManager.connect(tracker.deviceAddress, sessionId) ?: run {
            bleLogSink.log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = id,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Connection,
                    action = "connect_unavailable",
                    message = "Could not connect to ${tracker.deviceAddress}.",
                    deviceAddress = tracker.deviceAddress,
                ),
            )
            return@withContext Result.failure(IllegalStateException("Could not connect to ${tracker.deviceAddress}"))
        }
        try {
            val services = connection.discoverServices().getOrElse { throwable ->
                bleLogSink.log(
                    BleLogEvent(
                        sessionId = sessionId,
                        trackerId = id,
                        timestamp = System.currentTimeMillis(),
                        category = BleLogEvent.Category.Discovery,
                        action = "discover_failed",
                        message = throwable.message ?: "Service discovery failed.",
                        deviceAddress = tracker.deviceAddress,
                    ),
                )
                return@withContext Result.failure(throwable)
            }
            val scanResult = BleScanResult(
                deviceAddress = tracker.deviceAddress,
                advertisedName = tracker.advertisedName,
                resolvedName = tracker.resolvedName,
                manufacturerDataHex = tracker.manufacturerDataHex,
                serviceUuids = services.map { it.serviceUuid },
                rssi = tracker.lastRssi ?: -100,
                seenAt = System.currentTimeMillis(),
                connectable = true,
            )
            val adapter = protocolRegistry.resolve(scanResult, services)
            val batteryRequest = adapter.batteryReadRequest(services)
            val batteryPercent = batteryRequest?.let { request ->
                connection.readCharacteristic(request.serviceUuid, request.characteristicUuid)
                    .getOrNull()
                    ?.firstOrNull()
                    ?.toInt()
            }
            adapter.notes(scanResult, services).forEach { note ->
                bleLogSink.log(
                    BleLogEvent(
                        sessionId = sessionId,
                        trackerId = id,
                        timestamp = System.currentTimeMillis(),
                        category = BleLogEvent.Category.Protocol,
                        action = "adapter_note",
                        message = note,
                        deviceAddress = tracker.deviceAddress,
                    ),
                )
            }
            knownTrackerDao.upsert(
                tracker.copy(
                    protocolType = adapter.protocolType.name,
                    capabilities = adapter.inferCapabilities(scanResult, services).map { it.name },
                    batteryPercent = batteryPercent ?: tracker.batteryPercent,
                    batteryStatus = when {
                        batteryPercent != null -> BatteryState.Status.Available.name
                        tracker.batteryPercent != null -> tracker.batteryStatus
                        else -> BatteryState.Status.Unsupported.name
                    },
                    batteryUpdatedAt = if (batteryPercent != null) System.currentTimeMillis() else tracker.batteryUpdatedAt,
                    discoveredServices = services.map(::serializeGattServiceSummary),
                    connectionState = TrackerConnectionState.Disconnected.name,
                ),
            )
            val refreshed = (knownTrackerDao.getById(id) ?: tracker).toDomain()
            bleLogSink.log(
                BleLogEvent(
                    sessionId = sessionId,
                    trackerId = id,
                    timestamp = System.currentTimeMillis(),
                    category = BleLogEvent.Category.Repository,
                    action = "refresh_complete",
                    message = "Refresh completed with ${services.size} discovered service(s).",
                    deviceAddress = tracker.deviceAddress,
                ),
            )
            Result.success(refreshed)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun ringTracker(id: String, sessionId: String): RingResult = withContext(ioDispatcher) {
        val tracker = knownTrackerDao.getById(id) ?: return@withContext RingResult(
            status = RingResult.Status.Failed,
            message = "Unknown tracker",
        )
        val refreshResult = refreshTracker(id, sessionId)
        val refreshed = refreshResult.getOrNull() ?: return@withContext RingResult(
            status = RingResult.Status.Failed,
            message = refreshResult.exceptionOrNull()?.message ?: "Could not refresh tracker",
        )
        val adapter = protocolRegistry.resolve(
            scanResult = BleScanResult(
                deviceAddress = refreshed.deviceAddress,
                advertisedName = refreshed.advertisedName,
                resolvedName = refreshed.resolvedName,
                manufacturerDataHex = refreshed.manufacturerDataHex,
                serviceUuids = refreshed.discoveredServices.map { it.serviceUuid },
                rssi = refreshed.lastRssi ?: -100,
                seenAt = System.currentTimeMillis(),
                connectable = true,
            ),
            services = refreshed.discoveredServices,
        )
        val request = adapter.ringWriteRequest(refreshed.discoveredServices)
        when {
            request != null -> {
                val connection = bleConnectionManager.connect(refreshed.deviceAddress, "$sessionId-write")
                    ?: return@withContext RingResult(
                        status = RingResult.Status.Failed,
                        message = "Could not reconnect for ring write.",
                    )
                try {
                    connection.discoverServices()
                    val writeResult = connection.writeCharacteristic(
                        request.serviceUuid,
                        request.characteristicUuid,
                        request.payload,
                        request.writeType,
                    )
                    if (writeResult.isSuccess) {
                        bleLogSink.log(
                            BleLogEvent(
                                sessionId = sessionId,
                                trackerId = id,
                                timestamp = System.currentTimeMillis(),
                                category = BleLogEvent.Category.Write,
                                action = "ring_write_ack",
                                message = "Ring write acknowledged by GATT.",
                                deviceAddress = refreshed.deviceAddress,
                                serviceUuid = request.serviceUuid,
                                characteristicUuid = request.characteristicUuid,
                                payloadHex = request.payload.joinToString(separator = "") { byte -> "%02x".format(byte) },
                            ),
                        )
                        RingResult(
                            status = RingResult.Status.Success,
                            message = "Ring write was acknowledged.",
                        )
                    } else {
                        RingResult(
                            status = RingResult.Status.Failed,
                            message = writeResult.exceptionOrNull()?.message ?: "Ring write failed.",
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }

            refreshed.capabilities.contains(TrackerCapability.RingUnconfirmed) -> RingResult(
                status = RingResult.Status.Unconfirmed,
                message = "Nut ring path is still unconfirmed. Open Diagnostics for this tracker to inspect writable characteristics and run a raw GATT write probe.",
            )

            else -> RingResult(
                status = RingResult.Status.Unsupported,
                message = "This tracker does not expose a confirmed ring command.",
            )
        }
    }

    override suspend fun debugWriteCharacteristic(
        id: String,
        serviceUuid: String,
        characteristicUuid: String,
        payloadHex: String,
        sessionId: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        val tracker = knownTrackerDao.getById(id) ?: return@withContext Result.failure(
            IllegalArgumentException("Unknown tracker: $id"),
        )
        val service = tracker.toDomain().discoveredServices.firstOrNull {
            it.serviceUuid.equals(serviceUuid, ignoreCase = true)
        } ?: return@withContext Result.failure(
            IllegalArgumentException("Service $serviceUuid not found in stored discovery data"),
        )
        val characteristic = service.characteristics.firstOrNull {
            it.characteristicUuid.equals(characteristicUuid, ignoreCase = true)
        } ?: return@withContext Result.failure(
            IllegalArgumentException("Characteristic $characteristicUuid not found in stored discovery data"),
        )
        val payload = parseHexPayload(payloadHex).getOrElse { throwable ->
            return@withContext Result.failure(throwable)
        }
        if (!characteristic.supportsWrite) {
            return@withContext Result.failure(
                IllegalArgumentException("Characteristic $characteristicUuid is not marked writable in the last discovery snapshot"),
            )
        }
        val connection = bleConnectionManager.connect(tracker.deviceAddress, sessionId) ?: return@withContext Result.failure(
            IllegalStateException("Could not connect to ${tracker.deviceAddress}"),
        )
        try {
            connection.discoverServices().getOrElse { throwable ->
                return@withContext Result.failure(throwable)
            }
            val writeResult = connection.writeCharacteristic(
                serviceUuid = serviceUuid,
                characteristicUuid = characteristicUuid,
                payload = payload,
            )
            if (writeResult.isSuccess) {
                bleLogSink.log(
                    BleLogEvent(
                        sessionId = sessionId,
                        trackerId = id,
                        timestamp = System.currentTimeMillis(),
                        category = BleLogEvent.Category.Repository,
                        action = "debug_write_ack",
                        message = "Raw GATT write acknowledged by GATT.",
                        deviceAddress = tracker.deviceAddress,
                        serviceUuid = serviceUuid,
                        characteristicUuid = characteristicUuid,
                        payloadHex = payload.joinToString(separator = "") { byte -> "%02x".format(byte) },
                    ),
                )
            }
            writeResult
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun markOutOfRangeAlertSent(id: String, timestamp: Long) = withContext(ioDispatcher) {
        knownTrackerDao.updateOutOfRangeAlertAt(id, timestamp)
    }

    override suspend fun pruneLogs(limit: Int) = withContext(ioDispatcher) {
        bleLogEventDao.prune(limit)
        observationDao.prune(limit * 2)
    }

    private fun resolvePresence(lastSeenAt: Long): TrackerPresence {
        val age = System.currentTimeMillis() - lastSeenAt
        return when {
            age < 30_000L -> TrackerPresence.Nearby
            age < 5 * 60_000L -> TrackerPresence.RecentlySeen
            else -> TrackerPresence.NotRecent
        }
    }

    private fun parseHexPayload(payloadHex: String): Result<ByteArray> {
        val normalized = payloadHex
            .replace("0x", "", ignoreCase = true)
            .replace("\\s".toRegex(), "")
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Payload hex cannot be blank"))
        }
        if (normalized.length % 2 != 0) {
            return Result.failure(IllegalArgumentException("Payload hex must contain an even number of characters"))
        }
        return runCatching {
            ByteArray(normalized.length / 2) { index ->
                normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}

private const val OBSERVATION_PERSIST_INTERVAL_MILLIS = 2_000L
