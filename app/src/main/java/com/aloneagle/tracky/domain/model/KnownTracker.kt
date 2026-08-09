package com.aloneagle.tracky.domain.model

data class KnownTracker(
    val id: String,
    val deviceAddress: String,
    val nickname: String?,
    val advertisedName: String?,
    val resolvedName: String?,
    val protocolType: TrackerProtocolType,
    val capabilities: Set<TrackerCapability>,
    val batteryState: BatteryState,
    val presence: TrackerPresence,
    val connectionState: TrackerConnectionState,
    val proximity: ProximityEstimate?,
    val lastSeenAt: Long?,
    val lastRssi: Int?,
    val smoothedRssi: Double?,
    val lastLocation: LocationSnapshot?,
    val monitorEnabled: Boolean,
    val discoveredServices: List<GattServiceSummary>,
    val manufacturerDataHex: String?,
    val isNutCandidate: Boolean,
    val lastSessionType: ScanSessionType?,
    val lastOutOfRangeAlertAt: Long?,
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() }
            ?: resolvedName?.takeIf { it.isNotBlank() }
            ?: advertisedName?.takeIf { it.isNotBlank() }
            ?: deviceAddress
}
