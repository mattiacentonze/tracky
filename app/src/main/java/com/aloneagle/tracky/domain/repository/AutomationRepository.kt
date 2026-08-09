package com.aloneagle.tracky.domain.repository

import com.aloneagle.tracky.domain.model.ProximityAutomationRule
import com.aloneagle.tracky.domain.model.ProximityRuleState
import kotlinx.coroutines.flow.Flow

data class StoredAutomationRule(
    val rule: ProximityAutomationRule,
    val state: ProximityRuleState,
)

interface AutomationRepository {
    fun observeRules(): Flow<List<StoredAutomationRule>>
    suspend fun getEnabledForTracker(trackerId: String): List<StoredAutomationRule>
    suspend fun save(rule: ProximityAutomationRule, state: ProximityRuleState = ProximityRuleState())
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)
    suspend fun updateState(id: String, state: ProximityRuleState)
}
