package com.aloneagle.tracky.domain.model

data class TrackerObservation(
    val id: Long = 0L,
    val trackerId: String,
    val seenAt: Long,
    val sessionType: ScanSessionType,
    val rssi: Int,
    val smoothedRssi: Double?,
    val advertisedName: String?,
    val resolvedName: String?,
    val proximityEstimate: ProximityEstimate?,
    val locationSnapshot: LocationSnapshot?,
    val connected: Boolean,
    val serviceUuids: List<String>,
    val batteryPercentage: Int?,
)
