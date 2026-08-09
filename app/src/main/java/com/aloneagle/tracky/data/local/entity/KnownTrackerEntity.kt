package com.aloneagle.tracky.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_trackers")
data class KnownTrackerEntity(
    @PrimaryKey val id: String,
    val deviceAddress: String,
    val nickname: String?,
    val advertisedName: String?,
    val resolvedName: String?,
    val protocolType: String,
    val capabilities: List<String>,
    val batteryPercent: Int?,
    val batteryStatus: String,
    val batteryUpdatedAt: Long?,
    val lastSeenAt: Long?,
    val lastRssi: Int?,
    val smoothedRssi: Double?,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastAccuracyMeters: Float?,
    val lastLocationAt: Long?,
    val presenceState: String,
    val connectionState: String,
    val monitorEnabled: Boolean,
    val discoveredServices: List<String>,
    val manufacturerDataHex: String?,
    val isNutCandidate: Boolean,
    val lastSessionType: String?,
    val lastOutOfRangeAlertAt: Long?,
)
