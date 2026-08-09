package com.aloneagle.tracky.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aloneagle.tracky.data.local.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE trackerId = :trackerId AND enabled = 1")
    suspend fun getEnabledForTracker(trackerId: String): List<AutomationRuleEntity>

    @Query("SELECT * FROM automation_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AutomationRuleEntity?

    @Upsert
    suspend fun upsert(rule: AutomationRuleEntity)

    @Query("UPDATE automation_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        UPDATE automation_rules SET
            stableZone = :stableZone,
            candidateZone = :candidateZone,
            candidateSinceMillis = :candidateSinceMillis,
            candidateSamples = :candidateSamples,
            lastTriggeredAtMillis = :lastTriggeredAtMillis,
            lastObservationAtMillis = :lastObservationAtMillis
        WHERE id = :id
        """,
    )
    suspend fun updateState(
        id: String,
        stableZone: String,
        candidateZone: String?,
        candidateSinceMillis: Long?,
        candidateSamples: Int,
        lastTriggeredAtMillis: Long?,
        lastObservationAtMillis: Long?,
    )
}
