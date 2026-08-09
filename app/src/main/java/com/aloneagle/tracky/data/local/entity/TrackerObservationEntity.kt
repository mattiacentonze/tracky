package com.aloneagle.tracky.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracker_observations",
    indices = [
        Index(value = ["trackerId", "seenAt"]),
    ],
)
data class TrackerObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackerId: String,
    val seenAt: Long,
    val sessionType: String,
    val rssi: Int,
    val smoothedRssi: Double?,
    val advertisedName: String?,
    val resolvedName: String?,
    val proximityLevel: String?,
    val estimatedDistanceMeters: Double?,
    val signalPercent: Int?,
    val proximityTrend: String?,
    val proximityDescriptor: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val locationObservedAt: Long?,
    val connected: Boolean,
    val serviceUuids: List<String>,
    val batteryPercent: Int?,
)
