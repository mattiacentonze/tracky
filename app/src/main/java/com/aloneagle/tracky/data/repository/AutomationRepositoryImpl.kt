package com.aloneagle.tracky.data.repository

import com.aloneagle.tracky.data.local.dao.AutomationRuleDao
import com.aloneagle.tracky.data.local.entity.AutomationRuleEntity
import com.aloneagle.tracky.di.IoDispatcher
import com.aloneagle.tracky.domain.model.ProximityAutomationAction
import com.aloneagle.tracky.domain.model.ProximityAutomationRule
import com.aloneagle.tracky.domain.model.ProximityRuleState
import com.aloneagle.tracky.domain.model.ProximityTransition
import com.aloneagle.tracky.domain.model.ProximityZone
import com.aloneagle.tracky.domain.repository.AutomationRepository
import com.aloneagle.tracky.domain.repository.StoredAutomationRule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class AutomationRepositoryImpl @Inject constructor(
    private val dao: AutomationRuleDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AutomationRepository {
    override fun observeRules(): Flow<List<StoredAutomationRule>> = dao.observeAll()
        .map { rules -> rules.map(AutomationRuleEntity::toDomain) }

    override suspend fun getEnabledForTracker(trackerId: String): List<StoredAutomationRule> = withContext(ioDispatcher) {
        dao.getEnabledForTracker(trackerId).map(AutomationRuleEntity::toDomain)
    }

    override suspend fun save(rule: ProximityAutomationRule, state: ProximityRuleState) = withContext(ioDispatcher) {
        dao.upsert(rule.toEntity(state))
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = withContext(ioDispatcher) {
        dao.setEnabled(id, enabled)
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        dao.delete(id)
    }

    override suspend fun updateState(id: String, state: ProximityRuleState) = withContext(ioDispatcher) {
        dao.updateState(
            id = id,
            stableZone = state.stableZone.name,
            candidateZone = state.candidateZone?.name,
            candidateSinceMillis = state.candidateSinceMillis,
            candidateSamples = state.candidateSamples,
            lastTriggeredAtMillis = state.lastTriggeredAtMillis,
            lastObservationAtMillis = state.lastObservationAtMillis,
        )
    }
}

private fun AutomationRuleEntity.toDomain(): StoredAutomationRule {
    val action = when (actionType) {
        "notification" -> ProximityAutomationAction.ShowNotification(
            title = "Tracky automation",
            message = actionPayload?.takeIf(String::isNotBlank) ?: "A proximity rule was triggered.",
        )
        "sound" -> ProximityAutomationAction.PlaySound(actionPayload)
        "vibration" -> ProximityAutomationAction.Vibrate()
        "wifi" -> ProximityAutomationAction.OpenWifiPanel
        "whatsapp" -> ProximityAutomationAction.WhatsAppDraft(
            message = actionPayload?.takeIf(String::isNotBlank) ?: "Tracky proximity alert",
            recipientPhoneNumber = actionRecipient,
        )
        "telegram" -> ProximityAutomationAction.TelegramDraft(
            message = actionPayload?.takeIf(String::isNotBlank) ?: "Tracky proximity alert",
            recipientUsername = actionRecipient,
        )
        else -> ProximityAutomationAction.ShowNotification(
            title = "Tracky automation",
            message = "A proximity rule was triggered.",
        )
    }
    return StoredAutomationRule(
        rule = ProximityAutomationRule(
            id = id,
            trackerId = trackerId,
            transition = ProximityTransition.valueOf(transition),
            radiusMeters = radiusMeters,
            hysteresisMeters = hysteresisMeters,
            minimumSamples = minimumSamples,
            minimumDwellMillis = minimumDwellMillis,
            cooldownMillis = cooldownMillis,
            actions = listOf(action),
            enabled = enabled,
        ),
        state = ProximityRuleState(
            stableZone = ProximityZone.valueOf(stableZone),
            candidateZone = candidateZone?.let(ProximityZone::valueOf),
            candidateSinceMillis = candidateSinceMillis,
            candidateSamples = candidateSamples,
            lastTriggeredAtMillis = lastTriggeredAtMillis,
            lastObservationAtMillis = lastObservationAtMillis,
        ),
    )
}

private fun ProximityAutomationRule.toEntity(state: ProximityRuleState): AutomationRuleEntity {
    val action = actions.first()
    val actionType: String
    val payload: String?
    val recipient: String?
    when (action) {
        is ProximityAutomationAction.ShowNotification -> {
            actionType = "notification"
            payload = action.message
            recipient = null
        }
        is ProximityAutomationAction.PlaySound -> {
            actionType = "sound"
            payload = action.soundKey
            recipient = null
        }
        is ProximityAutomationAction.Vibrate -> {
            actionType = "vibration"
            payload = null
            recipient = null
        }
        ProximityAutomationAction.OpenWifiPanel -> {
            actionType = "wifi"
            payload = null
            recipient = null
        }
        is ProximityAutomationAction.WhatsAppDraft -> {
            actionType = "whatsapp"
            payload = action.message
            recipient = action.recipientPhoneNumber
        }
        is ProximityAutomationAction.TelegramDraft -> {
            actionType = "telegram"
            payload = action.message
            recipient = action.recipientUsername
        }
    }
    return AutomationRuleEntity(
        id = id,
        trackerId = trackerId,
        transition = transition.name,
        radiusMeters = radiusMeters,
        hysteresisMeters = hysteresisMeters,
        minimumSamples = minimumSamples,
        minimumDwellMillis = minimumDwellMillis,
        cooldownMillis = cooldownMillis,
        actionType = actionType,
        actionPayload = payload,
        actionRecipient = recipient,
        enabled = enabled,
        stableZone = state.stableZone.name,
        candidateZone = state.candidateZone?.name,
        candidateSinceMillis = state.candidateSinceMillis,
        candidateSamples = state.candidateSamples,
        lastTriggeredAtMillis = state.lastTriggeredAtMillis,
        lastObservationAtMillis = state.lastObservationAtMillis,
        createdAtMillis = System.currentTimeMillis(),
    )
}
