package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.ProximityAutomationEvent
import com.aloneagle.tracky.domain.model.ProximityAutomationRule
import com.aloneagle.tracky.domain.model.ProximityEvaluation
import com.aloneagle.tracky.domain.model.ProximityObservation
import com.aloneagle.tracky.domain.model.ProximityRuleState
import com.aloneagle.tracky.domain.model.ProximityTransition
import com.aloneagle.tracky.domain.model.ProximityZone

/**
 * Pure, deterministic state machine for one proximity rule.
 *
 * It does not read a clock, start Android components, or retain mutable state. The caller supplies
 * the previous state and persists the returned state before dispatching [ProximityEvaluation.event].
 */
class ProximityRuleEvaluator {
    fun evaluate(
        rule: ProximityAutomationRule,
        previousState: ProximityRuleState,
        observation: ProximityObservation,
    ): ProximityEvaluation {
        val previousObservationAt = previousState.lastObservationAtMillis
        if (previousObservationAt != null && observation.observedAtMillis < previousObservationAt) {
            return ProximityEvaluation(
                state = previousState,
                observationAccepted = false,
            )
        }

        val observedZone = classify(
            rule = rule,
            stableZone = previousState.stableZone,
            distanceMeters = observation.distanceMeters,
        )

        if (observedZone == ProximityZone.Unknown || observedZone == previousState.stableZone) {
            return ProximityEvaluation(
                state = previousState.copy(
                    candidateZone = null,
                    candidateSinceMillis = null,
                    candidateSamples = 0,
                    lastObservationAtMillis = observation.observedAtMillis,
                ),
            )
        }

        val isContinuingCandidate = previousState.candidateZone == observedZone
        val candidateSince = if (isContinuingCandidate) {
            checkNotNull(previousState.candidateSinceMillis)
        } else {
            observation.observedAtMillis
        }
        val candidateSamples = if (isContinuingCandidate) {
            previousState.candidateSamples + 1
        } else {
            1
        }

        val candidateState = previousState.copy(
            candidateZone = observedZone,
            candidateSinceMillis = candidateSince,
            candidateSamples = candidateSamples,
            lastObservationAtMillis = observation.observedAtMillis,
        )
        val hasEnoughSamples = candidateSamples >= rule.minimumSamples
        val hasDwelledLongEnough =
            observation.observedAtMillis - candidateSince >= rule.minimumDwellMillis
        if (!hasEnoughSamples || !hasDwelledLongEnough) {
            return ProximityEvaluation(state = candidateState)
        }

        val confirmedTransition = transitionBetween(previousState.stableZone, observedZone)
        var confirmedState = candidateState.copy(
            stableZone = observedZone,
            candidateZone = null,
            candidateSinceMillis = null,
            candidateSamples = 0,
        )

        // Unknown -> Inside/Outside establishes a baseline. Only a later crossing may run actions.
        if (confirmedTransition == null || confirmedTransition != rule.transition || !rule.enabled) {
            return ProximityEvaluation(
                state = confirmedState,
                confirmedTransition = confirmedTransition,
            )
        }

        val lastTriggeredAt = previousState.lastTriggeredAtMillis
        val cooldownActive = lastTriggeredAt != null &&
            observation.observedAtMillis - lastTriggeredAt < rule.cooldownMillis
        if (cooldownActive) {
            return ProximityEvaluation(
                state = confirmedState,
                confirmedTransition = confirmedTransition,
                triggerSuppressedByCooldown = true,
            )
        }

        confirmedState = confirmedState.copy(lastTriggeredAtMillis = observation.observedAtMillis)
        return ProximityEvaluation(
            state = confirmedState,
            confirmedTransition = confirmedTransition,
            event = ProximityAutomationEvent(
                ruleId = rule.id,
                trackerId = rule.trackerId,
                transition = confirmedTransition,
                observedAtMillis = observation.observedAtMillis,
                distanceMeters = observation.distanceMeters,
                actions = rule.actions,
            ),
        )
    }

    private fun classify(
        rule: ProximityAutomationRule,
        stableZone: ProximityZone,
        distanceMeters: Double?,
    ): ProximityZone {
        if (distanceMeters == null) return ProximityZone.Outside

        return when (stableZone) {
            ProximityZone.Inside -> {
                if (distanceMeters >= rule.radiusMeters + rule.hysteresisMeters) {
                    ProximityZone.Outside
                } else {
                    ProximityZone.Inside
                }
            }

            ProximityZone.Outside -> {
                if (distanceMeters <= rule.radiusMeters) {
                    ProximityZone.Inside
                } else {
                    ProximityZone.Outside
                }
            }

            ProximityZone.Unknown -> when {
                distanceMeters <= rule.radiusMeters -> ProximityZone.Inside
                distanceMeters >= rule.radiusMeters + rule.hysteresisMeters -> ProximityZone.Outside
                else -> ProximityZone.Unknown
            }
        }
    }

    private fun transitionBetween(
        previous: ProximityZone,
        current: ProximityZone,
    ): ProximityTransition? = when {
        previous == ProximityZone.Outside && current == ProximityZone.Inside ->
            ProximityTransition.Enter

        previous == ProximityZone.Inside && current == ProximityZone.Outside ->
            ProximityTransition.Leave

        else -> null
    }
}
