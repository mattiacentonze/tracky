package com.aloneagle.tracky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aloneagle.tracky.domain.model.ProximityEstimate
import com.aloneagle.tracky.ui.theme.SignalAmber
import com.aloneagle.tracky.ui.theme.SignalBlue
import com.aloneagle.tracky.ui.theme.SignalGreen
import com.aloneagle.tracky.ui.theme.SignalRed

@Composable
fun SignalMeter(
    signalPercent: Int,
    modifier: Modifier = Modifier,
) {
    val activeBars = when {
        signalPercent >= 85 -> 5
        signalPercent >= 70 -> 4
        signalPercent >= 50 -> 3
        signalPercent >= 30 -> 2
        signalPercent > 0 -> 1
        else -> 0
    }
    val activeColor = when {
        signalPercent >= 70 -> SignalGreen
        signalPercent >= 45 -> SignalAmber
        signalPercent > 0 -> SignalRed
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height((12 + (index * 6)).dp)
                    .background(
                        color = if (index < activeBars) activeColor else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
            )
        }
    }
}

@Composable
fun ProximityPulse(level: ProximityEstimate.Level, modifier: Modifier = Modifier) {
    val color = when (level) {
        ProximityEstimate.Level.Immediate -> SignalGreen
        ProximityEstimate.Level.Near -> SignalGreen
        ProximityEstimate.Level.Warm -> SignalAmber
        ProximityEstimate.Level.Far -> SignalRed
        ProximityEstimate.Level.Lost -> SignalBlue
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(24.dp),
    )
}
