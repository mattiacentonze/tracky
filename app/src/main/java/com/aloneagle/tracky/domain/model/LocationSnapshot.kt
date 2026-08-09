package com.aloneagle.tracky.domain.model

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val observedAt: Long,
)
