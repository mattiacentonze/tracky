package com.aloneagle.tracky.util

import com.aloneagle.tracky.domain.model.BatteryState
import com.aloneagle.tracky.domain.model.LocationSnapshot
import com.aloneagle.tracky.domain.model.TrackerConnectionState
import com.aloneagle.tracky.domain.model.TrackerPresence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

fun formatLastSeen(timestamp: Long?): String {
    if (timestamp == null) return "Never seen"
    val age = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now())
    return when {
        age.seconds.absoluteValue < 60 -> "Just now"
        age.toMinutes() < 60 -> "${age.toMinutes()} min ago"
        age.toHours() < 24 -> "${age.toHours()} hr ago"
        else -> DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
    }
}

fun formatLocation(snapshot: LocationSnapshot?): String {
    if (snapshot == null) return "No phone location saved"
    val accuracy = snapshot.accuracyMeters?.let { " ±${it.toInt()}m" }.orEmpty()
    return "%.5f, %.5f%s".format(snapshot.latitude, snapshot.longitude, accuracy)
}

fun formatBattery(batteryState: BatteryState): String = when (batteryState.status) {
    BatteryState.Status.Available -> "${batteryState.percentage ?: "--"}%"
    BatteryState.Status.Unknown -> "Unknown"
    BatteryState.Status.Unsupported -> "Unsupported"
}

fun formatPresence(presence: TrackerPresence): String = when (presence) {
    TrackerPresence.Nearby -> "Nearby"
    TrackerPresence.RecentlySeen -> "Recently seen"
    TrackerPresence.NotRecent -> "Not recent"
}

fun formatConnection(connectionState: TrackerConnectionState): String = when (connectionState) {
    TrackerConnectionState.Disconnected -> "Disconnected"
    TrackerConnectionState.Connecting -> "Connecting"
    TrackerConnectionState.Connected -> "Connected"
    TrackerConnectionState.DiscoveringServices -> "Discovering services"
    TrackerConnectionState.Ready -> "Ready"
    TrackerConnectionState.Failed -> "Failed"
}
