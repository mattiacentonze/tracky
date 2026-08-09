package com.aloneagle.tracky.automation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aloneagle.tracky.R
import com.aloneagle.tracky.domain.model.ProximityAutomationAction
import com.aloneagle.tracky.domain.model.ProximityAutomationEvent
import com.aloneagle.tracky.domain.model.ProximityTransition
import com.aloneagle.tracky.domain.service.AutomationActionExecutor
import com.aloneagle.tracky.domain.service.AutomationDispatchResult
import com.aloneagle.tracky.notifications.TrackyNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAutomationActionExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifications: TrackyNotifications,
) : AutomationActionExecutor {
    override suspend fun execute(event: ProximityAutomationEvent, trackerName: String): AutomationDispatchResult {
        var deliveredActions = 0
        val failures = mutableListOf<String>()
        event.actions.forEach { action ->
            val result = when (action) {
                is ProximityAutomationAction.ShowNotification -> notify(
                    title = action.title,
                    message = action.message,
                    event = event,
                )
                is ProximityAutomationAction.PlaySound -> runCatching {
                    checkNotNull(RingtoneManager.getRingtone(
                        context,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    )) { "No notification sound is available." }.play()
                }
                is ProximityAutomationAction.Vibrate -> runCatching {
                    val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
                    check(vibrator.hasVibrator()) { "This device has no vibrator." }
                    vibrator.vibrate(VibrationEffect.createWaveform(action.patternMillis.toLongArray(), -1))
                }
                ProximityAutomationAction.OpenWifiPanel -> notify(
                    title = "$trackerName ${event.transition.label}",
                    message = "Tap to open Wi-Fi controls.",
                    event = event,
                    actionIntent = Intent(Settings.Panel.ACTION_WIFI),
                )
                is ProximityAutomationAction.WhatsAppDraft -> notify(
                    title = "$trackerName ${event.transition.label}",
                    message = "Tap to review the WhatsApp draft. You decide whether to send it.",
                    event = event,
                    actionIntent = whatsappIntent(action),
                )
                is ProximityAutomationAction.TelegramDraft -> notify(
                    title = "$trackerName ${event.transition.label}",
                    message = "Tap to review the Telegram draft. You decide whether to send it.",
                    event = event,
                    actionIntent = telegramIntent(action),
                )
            }
            result.onSuccess { deliveredActions++ }
                .onFailure { throwable ->
                    failures += throwable.message ?: "${action.javaClass.simpleName} failed."
                }
        }
        return AutomationDispatchResult(deliveredActions, failures)
    }

    private fun notify(
        title: String,
        message: String,
        event: ProximityAutomationEvent,
        actionIntent: Intent? = null,
    ): Result<Unit> {
        notifications.ensureChannels()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.failure(IllegalStateException("Notifications are disabled for Tracky."))
        }
        if (actionIntent != null && actionIntent.resolveActivity(context.packageManager) == null) {
            return Result.failure(IllegalStateException("No compatible app is available for this action."))
        }
        val pendingIntent = actionIntent?.let { intent ->
            PendingIntent.getActivity(
                context,
                event.ruleId.hashCode(),
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, TrackyNotifications.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracky)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(event.ruleId.hashCode(), notification)
        }
    }

    private fun whatsappIntent(action: ProximityAutomationAction.WhatsAppDraft): Intent {
        val recipient = action.recipientPhoneNumber?.filter(Char::isDigit)
        return if (recipient.isNullOrBlank()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, action.message)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$recipient?text=${Uri.encode(action.message)}"))
        }
    }

    private fun telegramIntent(action: ProximityAutomationAction.TelegramDraft): Intent {
        val shareUrl = "https://t.me/share/url?url=&text=${Uri.encode(action.message)}"
        return Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl))
    }
}

private val ProximityTransition.label: String
    get() = if (this == ProximityTransition.Enter) "is nearby" else "moved out of range"
