package com.aloneagle.tracky.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.aloneagle.tracky.notifications.TrackyNotifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackerMonitorService : android.app.Service() {
    @Inject lateinit var coordinator: DefaultTrackerMonitorCoordinator
    @Inject lateinit var notifications: TrackyNotifications

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var idleStopJob: Job? = null
    private var hasHandledCommand = false
    private var latestStartId: Int? = null

    override fun onCreate() {
        super.onCreate()
        if (!hasBleRuntimePermissions(this)) {
            stopSelf()
            return
        }
        startForeground(
            TrackyNotifications.FOREGROUND_NOTIFICATION_ID,
            notifications.buildForegroundNotification(
                title = "Tracky idle",
                message = "Foreground monitor is ready.",
            ),
        )
        notificationJob = serviceScope.launch {
            combine(
                coordinator.activeTrackerIds,
                coordinator.activeSearchTrackerId,
            ) { activeTrackers, activeSearch ->
                Triple(
                    activeTrackers,
                    activeSearch,
                    when {
                        activeSearch != null -> "Finding the selected device"
                        activeTrackers.isNotEmpty() -> "Monitoring ${activeTrackers.size} tracker(s)"
                        else -> "Tracky idle"
                    },
                )
            }.collect { (activeTrackers, activeSearch, message) ->
                val title = if (activeSearch != null) "Tracky search" else "Tracky monitor"
                if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this@TrackerMonitorService,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        androidx.core.app.NotificationManagerCompat.from(this@TrackerMonitorService)
                            .notify(
                                TrackyNotifications.FOREGROUND_NOTIFICATION_ID,
                                notifications.buildForegroundNotification(title, message),
                            )
                    } catch (_: SecurityException) {
                        // Permission may be revoked between the check and notification update.
                    }
                }
                val isIdle = activeTrackers.isEmpty() && activeSearch == null
                if (isIdle) {
                    scheduleIdleStop()
                } else {
                    cancelIdleStop()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasBleRuntimePermissions(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        hasHandledCommand = true
        latestStartId = startId
        cancelIdleStop()
        when (intent?.action) {
            ACTION_START_SEARCH -> {
                val trackerId = intent.getStringExtra(EXTRA_TRACKER_ID).orEmpty()
                serviceScope.launch { coordinator.startSearch(trackerId) }
            }

            ACTION_STOP_SEARCH -> {
                val trackerId = intent.getStringExtra(EXTRA_TRACKER_ID).orEmpty()
                serviceScope.launch { coordinator.stopSearch(trackerId) }
            }

            ACTION_SYNC_MONITOR, null -> serviceScope.launch {
                coordinator.syncMonitoredTrackers()
                // StateFlow does not re-emit when an already-empty state stays empty.
                // Reconcile after sync so an idle sticky/sync start still stops.
                scheduleIdleStop(startId)
            }
        }
        return START_STICKY
    }

    private fun scheduleIdleStop(expectedStartId: Int? = latestStartId) {
        if (!hasHandledCommand || expectedStartId == null || expectedStartId != latestStartId) return
        if (coordinator.activeTrackerIds.value.isNotEmpty() || coordinator.activeSearchTrackerId.value != null) {
            cancelIdleStop()
            return
        }
        if (idleStopJob?.isActive == true) return
        idleStopJob = serviceScope.launch {
            delay(2_000L)
            if (
                shouldStopIdleService(
                    expectedStartId = expectedStartId,
                    latestStartId = latestStartId,
                    activeTrackerIds = coordinator.activeTrackerIds.value,
                    activeSearchTrackerId = coordinator.activeSearchTrackerId.value,
                )
            ) {
                stopSelfResult(expectedStartId)
            }
        }
    }

    private fun cancelIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = null
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        idleStopJob?.cancel()
        serviceScope.launch { coordinator.stopAll() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_TRACKER_ID = "extra_tracker_id"
        private const val ACTION_START_SEARCH = "com.aloneagle.tracky.action.START_SEARCH"
        private const val ACTION_STOP_SEARCH = "com.aloneagle.tracky.action.STOP_SEARCH"
        private const val ACTION_SYNC_MONITOR = "com.aloneagle.tracky.action.SYNC_MONITOR"

        fun startSearch(context: Context, trackerId: String): Boolean {
            if (!hasBleRuntimePermissions(context)) return false
            return runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TrackerMonitorService::class.java).apply {
                        action = ACTION_START_SEARCH
                        putExtra(EXTRA_TRACKER_ID, trackerId)
                    },
                )
                true
            }.getOrDefault(false)
        }

        fun stopSearch(context: Context, trackerId: String) {
            if (!hasBleRuntimePermissions(context)) return
            runCatching {
                context.startService(
                    Intent(context, TrackerMonitorService::class.java).apply {
                        action = ACTION_STOP_SEARCH
                        putExtra(EXTRA_TRACKER_ID, trackerId)
                    },
                )
            }
        }

        fun syncMonitoring(context: Context): Boolean {
            if (!hasBleRuntimePermissions(context)) return false
            return runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TrackerMonitorService::class.java).apply {
                        action = ACTION_SYNC_MONITOR
                    },
                )
                true
            }.getOrDefault(false)
        }

        private fun hasBleRuntimePermissions(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
    }
}

internal fun shouldStopIdleService(
    expectedStartId: Int,
    latestStartId: Int?,
    activeTrackerIds: Set<String>,
    activeSearchTrackerId: String?,
): Boolean =
    expectedStartId == latestStartId &&
        activeTrackerIds.isEmpty() &&
        activeSearchTrackerId == null
