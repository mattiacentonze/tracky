package com.aloneagle.tracky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aloneagle.tracky.domain.model.TrackerConnectionState
import com.aloneagle.tracky.domain.model.TrackerPresence
import com.aloneagle.tracky.ui.theme.SignalAmber
import com.aloneagle.tracky.ui.theme.SignalBlue
import com.aloneagle.tracky.ui.theme.SignalGreen
import com.aloneagle.tracky.ui.theme.SignalRed

@Composable
fun PresenceBadge(presence: TrackerPresence) {
    val (label, color) = when (presence) {
        TrackerPresence.Nearby -> "Nearby" to SignalGreen
        TrackerPresence.RecentlySeen -> "Recent" to SignalAmber
        TrackerPresence.NotRecent -> "Stale" to SignalRed
    }
    Badge(label = label, color = color)
}

@Composable
fun ConnectionBadge(connectionState: TrackerConnectionState) {
    val (label, color) = when (connectionState) {
        TrackerConnectionState.Connecting -> "Connecting" to SignalBlue
        TrackerConnectionState.Connected -> "Connected" to SignalBlue
        TrackerConnectionState.DiscoveringServices -> "Discovering" to SignalBlue
        TrackerConnectionState.Ready -> "Ready" to SignalGreen
        TrackerConnectionState.Disconnected -> "Offline" to MaterialTheme.colorScheme.surfaceVariant
        TrackerConnectionState.Failed -> "Failed" to SignalRed
    }
    Badge(label = label, color = color)
}

@Composable
private fun Badge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
