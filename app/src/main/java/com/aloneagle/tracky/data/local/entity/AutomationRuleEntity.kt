package com.aloneagle.tracky.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_rules",
    indices = [Index(value = ["trackerId"])],
)
data class AutomationRuleEntity(
    @PrimaryKey val id: String,
    val trackerId: String,
    val transition: String,
    val radiusMeters: Double,
    val hysteresisMeters: Double,
    val minimumSamples: Int,
    val minimumDwellMillis: Long,
    val cooldownMillis: Long,
    val actionType: String,
    val actionPayload: String?,
    val actionRecipient: String?,
    val enabled: Boolean,
    val stableZone: String,
    val candidateZone: String?,
    val candidateSinceMillis: Long?,
    val candidateSamples: Int,
    val lastTriggeredAtMillis: Long?,
    val lastObservationAtMillis: Long?,
    val createdAtMillis: Long,
)
