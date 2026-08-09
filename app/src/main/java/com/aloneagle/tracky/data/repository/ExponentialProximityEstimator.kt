package com.aloneagle.tracky.data.repository

import com.aloneagle.tracky.domain.model.ProximityEstimate
import com.aloneagle.tracky.domain.service.ProximityEstimator
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

class ExponentialProximityEstimator @Inject constructor() : ProximityEstimator {
    override fun smooth(previous: Double?, currentRssi: Int): Double {
        val safePrevious = previous
            ?.takeIf(Double::isFinite)
            ?.coerceIn(MIN_RSSI, MAX_RSSI)
        val raw = currentRssi
            .takeIf { it < 0 }
            ?.toDouble()
            ?.coerceIn(MIN_RSSI, MAX_RSSI)

        if (raw == null) return safePrevious ?: MIN_RSSI
        if (safePrevious == null) return raw

        val rawDelta = raw - safePrevious
        val boundedDelta = rawDelta.coerceIn(-MAX_SAMPLE_DELTA_DB, MAX_SAMPLE_DELTA_DB)
        val alpha = if (abs(rawDelta) >= OUTLIER_DELTA_DB) OUTLIER_ALPHA else BASE_ALPHA

        return (safePrevious + alpha * boundedDelta).coerceIn(MIN_RSSI, MAX_RSSI)
    }

    override fun estimate(previousSmoothed: Double?, smoothedRssi: Double): ProximityEstimate {
        val current = smoothedRssi
            .takeIf(Double::isFinite)
            ?.coerceIn(MIN_RSSI, MAX_RSSI)
            ?: MIN_RSSI
        val previous = previousSmoothed
            ?.takeIf(Double::isFinite)
            ?.coerceIn(MIN_RSSI, MAX_RSSI)
        val confidence = confidence(previous, current)
        val level = resolveLevel(previous, current, confidence)
        val representativeRssi = if (confidence == Confidence.Low && previous != null) previous else current
        val distance = approximateDistance(representativeRssi, level, confidence)
        val signalPercent = (((representativeRssi + 100.0) / 55.0) * 100).roundToInt().coerceIn(0, 100)
        val trend = when {
            previous == null || confidence == Confidence.Low -> ProximityEstimate.Trend.Stable
            current - previous > TREND_THRESHOLD_DB -> ProximityEstimate.Trend.Improving
            previous - current > TREND_THRESHOLD_DB -> ProximityEstimate.Trend.Weakening
            else -> ProximityEstimate.Trend.Stable
        }
        val rangeDescription = when (level) {
            ProximityEstimate.Level.Immediate -> "Very close signal range"
            ProximityEstimate.Level.Near -> "Nearby signal range"
            ProximityEstimate.Level.Warm -> "Mid-range signal"
            ProximityEstimate.Level.Far -> "Distant signal"
            ProximityEstimate.Level.Lost -> "Signal not currently reliable"
        }
        val confidenceDescription = when (confidence) {
            Confidence.High -> "steady"
            Confidence.Medium -> "changing"
            Confidence.Low -> "needs more readings"
        }
        return ProximityEstimate(
            level = level,
            estimatedDistanceMeters = distance,
            signalPercent = signalPercent,
            trend = trend,
            descriptor = "$rangeDescription · $confidenceDescription",
        )
    }

    private fun resolveLevel(
        previous: Double?,
        current: Double,
        confidence: Confidence,
    ): ProximityEstimate.Level {
        val currentLevel = levelFor(current)
        val previousLevel = previous?.let(::levelFor) ?: return currentLevel

        if (confidence == Confidence.Low) return previousLevel
        if (currentLevel == previousLevel) return currentLevel

        val boundary = boundaryBetween(previousLevel, currentLevel) ?: return currentLevel
        return if (abs(current - boundary) <= LEVEL_HYSTERESIS_DB) previousLevel else currentLevel
    }

    private fun levelFor(rssi: Double): ProximityEstimate.Level = when {
        rssi >= IMMEDIATE_RSSI -> ProximityEstimate.Level.Immediate
        rssi >= NEAR_RSSI -> ProximityEstimate.Level.Near
        rssi >= WARM_RSSI -> ProximityEstimate.Level.Warm
        rssi >= FAR_RSSI -> ProximityEstimate.Level.Far
        else -> ProximityEstimate.Level.Lost
    }

    private fun boundaryBetween(
        first: ProximityEstimate.Level,
        second: ProximityEstimate.Level,
    ): Double? {
        val pair = setOf(first, second)
        return when (pair) {
            setOf(ProximityEstimate.Level.Immediate, ProximityEstimate.Level.Near) -> IMMEDIATE_RSSI
            setOf(ProximityEstimate.Level.Near, ProximityEstimate.Level.Warm) -> NEAR_RSSI
            setOf(ProximityEstimate.Level.Warm, ProximityEstimate.Level.Far) -> WARM_RSSI
            setOf(ProximityEstimate.Level.Far, ProximityEstimate.Level.Lost) -> FAR_RSSI
            else -> null
        }
    }

    private fun confidence(previous: Double?, current: Double): Confidence {
        if (previous == null) return Confidence.Low
        return when {
            abs(current - previous) <= HIGH_CONFIDENCE_DELTA_DB -> Confidence.High
            abs(current - previous) <= LOW_CONFIDENCE_DELTA_DB -> Confidence.Medium
            else -> Confidence.Low
        }
    }

    private fun approximateDistance(
        rssi: Double,
        level: ProximityEstimate.Level,
        confidence: Confidence,
    ): Double? {
        if (level == ProximityEstimate.Level.Lost || confidence == Confidence.Low) return null

        val rawDistance = 10.0
            .pow((TX_POWER - rssi) / (10 * SIGNAL_LOSS_FACTOR))
            .coerceIn(MIN_DISTANCE_METERS, MAX_DISTANCE_METERS)
        val step = when (level) {
            ProximityEstimate.Level.Immediate -> 0.25
            ProximityEstimate.Level.Near -> 0.5
            ProximityEstimate.Level.Warm -> 1.0
            ProximityEstimate.Level.Far -> 2.0
            ProximityEstimate.Level.Lost -> return null
        }
        return (round(rawDistance / step) * step)
            .coerceIn(MIN_DISTANCE_METERS, MAX_DISTANCE_METERS)
    }

    private enum class Confidence {
        High,
        Medium,
        Low,
    }

    private companion object {
        const val BASE_ALPHA = 0.25
        const val OUTLIER_ALPHA = 0.08
        const val MIN_RSSI = -105.0
        const val MAX_RSSI = -20.0
        const val MAX_SAMPLE_DELTA_DB = 12.0
        const val OUTLIER_DELTA_DB = 20.0

        const val IMMEDIATE_RSSI = -55.0
        const val NEAR_RSSI = -67.0
        const val WARM_RSSI = -75.0
        const val FAR_RSSI = -88.0
        const val LEVEL_HYSTERESIS_DB = 1.5

        const val HIGH_CONFIDENCE_DELTA_DB = 1.5
        const val LOW_CONFIDENCE_DELTA_DB = 3.5
        const val TREND_THRESHOLD_DB = 2.25

        const val TX_POWER = -59.0
        const val SIGNAL_LOSS_FACTOR = 2.1
        const val MIN_DISTANCE_METERS = 0.3
        const val MAX_DISTANCE_METERS = 40.0
    }
}
