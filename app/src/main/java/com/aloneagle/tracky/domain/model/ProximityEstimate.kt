package com.aloneagle.tracky.domain.model

data class ProximityEstimate(
    val level: Level,
    val estimatedDistanceMeters: Double?,
    val signalPercent: Int,
    val trend: Trend,
    val descriptor: String,
) {
    enum class Level {
        Immediate,
        Near,
        Warm,
        Far,
        Lost,
    }

    enum class Trend {
        Improving,
        Stable,
        Weakening,
    }
}
