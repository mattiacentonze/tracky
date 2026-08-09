package com.aloneagle.tracky.service

import com.aloneagle.tracky.di.IoDispatcher
import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.ProximityObservation
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.model.TrackerObservation
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.repository.AutomationRepository
import com.aloneagle.tracky.domain.service.AutomationActionExecutor
import com.aloneagle.tracky.domain.service.BleLogSink
import com.aloneagle.tracky.domain.service.BleScanner
import com.aloneagle.tracky.domain.service.TrackerMonitorCoordinator
import com.aloneagle.tracky.domain.service.ProximityRuleEvaluator
import com.aloneagle.tracky.notifications.TrackyNotifications
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DefaultTrackerMonitorCoordinator @Inject constructor(
    private val trackerRepository: TrackerRepository,
    private val automationRepository: AutomationRepository,
    private val bleScanner: BleScanner,
    private val bleLogSink: BleLogSink,
    private val notifications: TrackyNotifications,
    private val automationActionExecutor: AutomationActionExecutor,
    private val ruleEvaluator: ProximityRuleEvaluator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TrackerMonitorCoordinator {
    override val activeTrackerIds: StateFlow<Set<String>> get() = _activeTrackerIds
    override val activeSearchTrackerId: StateFlow<String?> get() = _activeSearchTrackerId

    private val _activeTrackerIds = MutableStateFlow(emptySet<String>())
    private val _activeSearchTrackerId = MutableStateFlow<String?>(null)
    private val realtimeObservations = ConcurrentHashMap<String, MutableStateFlow<TrackerObservation?>>()
    private val trackerEvaluationMutexes = ConcurrentHashMap<String, Mutex>()
    private val failedDispatchAt = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private var searchJob: Job? = null
    private var monitorJob: Job? = null
    private var alertJob: Job? = null
    @Volatile private var manualScanActive = false
    @Volatile private var monitorScannerHealthySince: Long? = null
    private val scanRetryBackoff = ReconnectBackoffPolicy()

    override fun observeRealtimeObservation(trackerId: String): StateFlow<TrackerObservation?> =
        realtimeObservations.computeIfAbsent(trackerId) { MutableStateFlow<TrackerObservation?>(null) }

    override fun scanNearby(sessionId: String): Flow<BleScanResult> = flow {
        manualScanActive = true
        try {
            setMonitorScannerHealthy(false)
            monitorJob?.cancelAndJoin()
            monitorJob = null
            val savedTrackerIds = trackerRepository.observeKnownTrackers().first()
                .mapTo(hashSetOf()) { tracker -> tracker.id }
            bleScanner.scan(
                sessionId = sessionId,
                sessionType = ScanSessionType.Manual,
            ).collect { scanResult ->
                if (scanResult.deviceAddress in savedTrackerIds) {
                    val tracker = trackerRepository.upsertFromScan(scanResult, ScanSessionType.Manual)
                    publishObservation(
                        tracker = tracker,
                        rssi = scanResult.rssi,
                        seenAt = scanResult.seenAt,
                        sessionType = ScanSessionType.Manual,
                        serviceUuids = scanResult.serviceUuids,
                    )
                }
                emit(scanResult)
            }
        } finally {
            manualScanActive = false
            withContext(NonCancellable) { syncMonitoredTrackers() }
        }
    }

    override suspend fun startSearch(trackerId: String) {
        _activeSearchTrackerId.value = trackerId
        searchJob?.cancel()
        // Android only allows a small number of concurrent scans and duplicate scans
        // make signal trends less trustworthy. Search temporarily owns the scanner.
        setMonitorScannerHealthy(false)
        monitorJob?.cancelAndJoin()
        monitorJob = null
        // Monitoring is intentionally paused while Finder owns the scanner. Absence during
        // that pause is not evidence that another device left its configured range.
        alertJob?.cancel()
        alertJob = null
        searchJob = scope.launch {
            var failedAttempts = 0
            while (isActive && _activeSearchTrackerId.value == trackerId) {
                val sessionId = "search-${System.currentTimeMillis()}-$failedAttempts"
                try {
                    bleScanner.scan(
                        sessionId = sessionId,
                        sessionType = ScanSessionType.Search,
                        targetAddresses = setOf(trackerId),
                    ).collect { scanResult ->
                        failedAttempts = 0
                        val tracker = trackerRepository.upsertFromScan(scanResult, ScanSessionType.Search)
                        publishObservation(tracker, scanResult.rssi, scanResult.seenAt, ScanSessionType.Search, scanResult.serviceUuids)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Transport failures are logged by the scanner and retried below.
                }
                delay(scanRetryBackoff.nextDelayMillis(failedAttempts).coerceAtMost(60_000L))
                failedAttempts = (failedAttempts + 1).coerceAtMost(3)
            }
        }
    }

    override suspend fun stopSearch(trackerId: String) {
        if (_activeSearchTrackerId.value == trackerId) {
            _activeSearchTrackerId.value = null
        }
        searchJob?.cancel()
        searchJob = null
        syncMonitoredTrackers()
    }

    override suspend fun syncMonitoredTrackers() {
        val enabledRuleTrackerIds = automationRepository.observeRules().first()
            .filter { stored -> stored.rule.enabled }
            .mapTo(linkedSetOf()) { stored -> stored.rule.trackerId }
        val trackers = trackerRepository.observeKnownTrackers().first()
            .filter { tracker -> tracker.monitorEnabled || tracker.id in enabledRuleTrackerIds }
        val trackerIds = trackers.mapTo(linkedSetOf()) { tracker -> tracker.id }
        _activeTrackerIds.value = trackerIds
        monitorJob?.cancelAndJoin()
        monitorJob = null
        setMonitorScannerHealthy(false)
        if (trackerIds.isEmpty()) {
            alertJob?.cancel()
            alertJob = null
            return
        }
        if (_activeSearchTrackerId.value != null || manualScanActive) return
        if (alertJob?.isActive != true) {
            alertJob = scope.launch {
                while (true) {
                    evaluateOutOfRangeAlerts()
                    delay(15_000L)
                }
            }
        }
        monitorJob = scope.launch {
            var failedAttempts = 0
            while (isActive) {
                val sessionId = "monitor-${System.currentTimeMillis()}-$failedAttempts"
                setMonitorScannerHealthy(false)
                try {
                    bleScanner.scan(
                        sessionId = sessionId,
                        sessionType = ScanSessionType.Monitor,
                        targetAddresses = trackerIds,
                        onScanHealthChanged = ::setMonitorScannerHealthy,
                    ).collect { scanResult ->
                        failedAttempts = 0
                        val tracker = trackerRepository.upsertFromScan(scanResult, ScanSessionType.Monitor)
                        publishObservation(tracker, scanResult.rssi, scanResult.seenAt, ScanSessionType.Monitor, scanResult.serviceUuids)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // A failed or unavailable scanner is retried below with bounded backoff.
                } finally {
                    setMonitorScannerHealthy(false)
                }
                delay(scanRetryBackoff.nextDelayMillis(failedAttempts).coerceAtMost(60_000L))
                failedAttempts = (failedAttempts + 1).coerceAtMost(3)
            }
        }
    }

    override suspend fun stopAll() {
        setMonitorScannerHealthy(false)
        _activeSearchTrackerId.value = null
        _activeTrackerIds.value = emptySet()
        searchJob?.cancel()
        monitorJob?.cancel()
        alertJob?.cancel()
        searchJob = null
        monitorJob = null
        alertJob = null
    }

    private suspend fun publishObservation(
        tracker: KnownTracker,
        rssi: Int,
        seenAt: Long,
        sessionType: ScanSessionType,
        serviceUuids: List<String>,
    ) {
        val observation = TrackerObservation(
            trackerId = tracker.id,
            seenAt = seenAt,
            sessionType = sessionType,
            rssi = rssi,
            smoothedRssi = tracker.smoothedRssi,
            advertisedName = tracker.advertisedName,
            resolvedName = tracker.resolvedName,
            proximityEstimate = tracker.proximity,
            locationSnapshot = tracker.lastLocation,
            connected = tracker.connectionState == com.aloneagle.tracky.domain.model.TrackerConnectionState.Ready,
            serviceUuids = serviceUuids,
            batteryPercentage = tracker.batteryState.percentage,
        )
        realtimeObservations.computeIfAbsent(tracker.id) { MutableStateFlow<TrackerObservation?>(null) }.value = observation
        observation.proximityEstimate?.estimatedDistanceMeters?.let { distance ->
            evaluateAutomationRules(tracker, distance, seenAt)
        }
    }

    private suspend fun evaluateOutOfRangeAlerts() {
        val healthySince = monitorScannerHealthySince ?: return
        val trackers = trackerRepository.observeKnownTrackers().first()
        if (monitorScannerHealthySince != healthySince) return
        val now = System.currentTimeMillis()
        val activeIds = _activeTrackerIds.value
        val absentActiveTrackers = trackers.filter { tracker ->
            val lastSeenAt = tracker.lastSeenAt ?: return@filter false
            tracker.id in activeIds && now - maxOf(lastSeenAt, healthySince) > 20_000L
        }
        for (tracker in absentActiveTrackers) {
            if (monitorScannerHealthySince != healthySince) return
            // One null observation per completed monitor window represents absence. The
            // evaluator still requires dwell, multiple windows and hysteresis before firing.
            evaluateAutomationRules(tracker, distanceMeters = null, observedAt = now)
        }
        val outOfRangeTrackers = trackers.filter { tracker ->
            val lastSeenAt = tracker.lastSeenAt ?: return@filter false
            tracker.monitorEnabled &&
                now - maxOf(lastSeenAt, healthySince) > 90_000L &&
                (
                    tracker.lastOutOfRangeAlertAt == null ||
                        tracker.lastOutOfRangeAlertAt < lastSeenAt
                    )
        }
        for (tracker in outOfRangeTrackers) {
            if (monitorScannerHealthySince != healthySince) return
            if (automationRepository.getEnabledForTracker(tracker.id).isNotEmpty()) {
                continue
            }
            if (monitorScannerHealthySince != healthySince) return
            if (!notifications.showOutOfRangeAlert(tracker)) continue
            trackerRepository.markOutOfRangeAlertSent(tracker.id, now)
            bleLogSink.log(
                BleLogEvent(
                    sessionId = "alert-$now",
                    trackerId = tracker.id,
                    timestamp = now,
                    category = BleLogEvent.Category.Monitor,
                    action = "out_of_range_alert",
                    message = "Tracker considered out of range after 90 seconds without a sighting.",
                    deviceAddress = tracker.deviceAddress,
                ),
            )
        }
    }

    private fun setMonitorScannerHealthy(healthy: Boolean) {
        if (!healthy) {
            monitorScannerHealthySince = null
        } else if (monitorScannerHealthySince == null) {
            monitorScannerHealthySince = System.currentTimeMillis()
        }
    }

    private suspend fun evaluateAutomationRules(
        tracker: KnownTracker,
        distanceMeters: Double?,
        observedAt: Long,
    ) {
        trackerEvaluationMutexes.computeIfAbsent(tracker.id) { Mutex() }.withLock {
            automationRepository.getEnabledForTracker(tracker.id).forEach { stored ->
                val evaluation = ruleEvaluator.evaluate(
                    rule = stored.rule,
                    previousState = stored.state,
                    observation = ProximityObservation(
                        observedAtMillis = observedAt,
                        distanceMeters = distanceMeters,
                    ),
                )
                if (!evaluation.observationAccepted || evaluation.state == stored.state) return@forEach
                val event = evaluation.event
                if (event == null) {
                    automationRepository.updateState(stored.rule.id, evaluation.state)
                } else {
                    val previousFailureAt = failedDispatchAt[event.ruleId]
                    if (previousFailureAt != null &&
                        event.observedAtMillis - previousFailureAt < FAILED_DISPATCH_RETRY_MILLIS
                    ) {
                        return@forEach
                    }
                    val dispatch = automationActionExecutor.execute(event, tracker.displayName)
                    // Advance the confirmed zone and cooldown only after a successful dispatch.
                    // On failure the candidate remains retryable on subsequent observations.
                    if (dispatch.isSuccess) {
                        failedDispatchAt.remove(event.ruleId)
                        automationRepository.updateState(stored.rule.id, evaluation.state)
                    } else {
                        failedDispatchAt[event.ruleId] = event.observedAtMillis
                    }
                    bleLogSink.log(
                        BleLogEvent(
                            sessionId = "automation-${event.ruleId}-${event.observedAtMillis}",
                            trackerId = tracker.id,
                            timestamp = event.observedAtMillis,
                            category = BleLogEvent.Category.Monitor,
                            action = if (dispatch.isSuccess) "automation_dispatched" else "automation_dispatch_failed",
                            message = "Proximity ${event.transition.name.lowercase()} rule: ${dispatch.summary}",
                            deviceAddress = tracker.deviceAddress,
                        ),
                    )
                }
            }
        }
    }

}

private const val FAILED_DISPATCH_RETRY_MILLIS = 30_000L
