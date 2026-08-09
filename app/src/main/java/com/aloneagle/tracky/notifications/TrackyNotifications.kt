package com.aloneagle.tracky.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aloneagle.tracky.MainActivity
import com.aloneagle.tracky.R
import com.aloneagle.tracky.domain.model.KnownTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackyNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    "Tracky monitor",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Foreground scan and monitor state."
                },
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Tracky alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Tracker disappearance and range alerts."
                },
            ),
        )
    }

    fun buildForegroundNotification(title: String, message: String) = NotificationCompat.Builder(
        context,
        MONITOR_CHANNEL_ID,
    ).setSmallIcon(R.drawable.ic_stat_tracky)
        .setContentTitle(title)
        .setContentText(message)
        .setContentIntent(mainActivityPendingIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun showOutOfRangeAlert(tracker: KnownTracker) {
        NotificationManagerCompat.from(context).notify(
            tracker.id.hashCode(),
            NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_tracky)
                .setContentTitle("${tracker.displayName} out of range")
                .setContentText("Tracky has not seen this tracker recently.")
                .setContentIntent(mainActivityPendingIntent())
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "tracky.monitor"
        const val ALERT_CHANNEL_ID = "tracky.alerts"
        const val FOREGROUND_NOTIFICATION_ID = 1010
    }
}
