package com.aloneagle.tracky

import com.aloneagle.tracky.domain.model.ProximityAutomationAction
import com.aloneagle.tracky.domain.model.ProximityAutomationRule
import com.aloneagle.tracky.domain.model.ProximityObservation
import com.aloneagle.tracky.domain.model.ProximityRuleState
import com.aloneagle.tracky.domain.model.ProximityTransition
import com.aloneagle.tracky.domain.model.ProximityZone
import com.aloneagle.tracky.domain.service.ProximityRuleEvaluator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProximityRuleEvaluatorTest {
    private val evaluator = ProximityRuleEvaluator()
    private val notification = ProximityAutomationAction.ShowNotification(
        title = "Keys nearby",
        message = "Your keys entered the selected radius.",
    )

    @Test
    fun initialObservations_establishBaselineWithoutTriggering() {
        val rule = rule(minimumSamples = 2, minimumDwellMillis = 1_000L)
        var state = ProximityRuleState()

        val first = evaluate(rule, state, time = 100L, distance = 2.0)
        state = first.state
        val tooSoon = evaluate(rule, state, time = 600L, distance = 2.1)
        state = tooSoon.state
        val baseline = evaluate(rule, state, time = 1_100L, distance = 2.2)

        assertThat(first.state.candidateSamples).isEqualTo(1)
        assertThat(tooSoon.state.stableZone).isEqualTo(ProximityZone.Unknown)
        assertThat(baseline.state.stableZone).isEqualTo(ProximityZone.Inside)
        assertThat(baseline.confirmedTransition).isNull()
        assertThat(baseline.event).isNull()
    }

    @Test
    fun enterRule_triggersOnceAfterSamplesAndDwell() {
        val rule = rule(minimumSamples = 2, minimumDwellMillis = 500L)
        var state = baseline(rule, distance = 8.0)

        val firstInside = evaluate(rule, state, time = 1_000L, distance = 4.0)
        state = firstInside.state
        val entered = evaluate(rule, state, time = 1_500L, distance = 3.5)
        state = entered.state
        val stillInside = evaluate(rule, state, time = 2_000L, distance = 2.0)

        assertThat(firstInside.event).isNull()
        assertThat(entered.confirmedTransition).isEqualTo(ProximityTransition.Enter)
        assertThat(entered.event).isNotNull()
        assertThat(entered.event?.actions).containsExactly(notification)
        assertThat(stillInside.event).isNull()
        assertThat(stillInside.state.stableZone).isEqualTo(ProximityZone.Inside)
    }

    @Test
    fun hysteresis_preventsBoundaryNoiseFromProducingLeaveEvent() {
        val rule = rule(
            transition = ProximityTransition.Leave,
            hysteresisMeters = 1.0,
            minimumSamples = 2,
            minimumDwellMillis = 200L,
        )
        var state = baseline(rule, distance = 4.0)

        // The leave boundary is 6 m. Samples in the 5..6 m dead band retain Inside.
        listOf(5.2, 5.9, 5.4, 5.99).forEachIndexed { index, distance ->
            val result = evaluate(rule, state, time = 1_000L + index * 100L, distance = distance)
            assertThat(result.event).isNull()
            state = result.state
        }

        val outsideCandidate = evaluate(rule, state, time = 1_500L, distance = 6.2)
        state = outsideCandidate.state
        val recoveredInsideBand = evaluate(rule, state, time = 1_600L, distance = 5.5)

        assertThat(outsideCandidate.state.candidateZone).isEqualTo(ProximityZone.Outside)
        assertThat(recoveredInsideBand.state.candidateZone).isNull()
        assertThat(recoveredInsideBand.state.stableZone).isEqualTo(ProximityZone.Inside)
        assertThat(recoveredInsideBand.event).isNull()
    }

    @Test
    fun transition_requiresBothMinimumSamplesAndDwell() {
        val rule = rule(minimumSamples = 3, minimumDwellMillis = 1_000L)
        var state = baseline(rule, distance = 8.0)

        state = evaluate(rule, state, time = 1_000L, distance = 4.0).state
        val dwellMetButSamplesMissing = evaluate(rule, state, time = 2_000L, distance = 4.0)
        state = dwellMetButSamplesMissing.state
        val allConditionsMet = evaluate(rule, state, time = 2_100L, distance = 4.0)

        assertThat(dwellMetButSamplesMissing.event).isNull()
        assertThat(dwellMetButSamplesMissing.state.stableZone).isEqualTo(ProximityZone.Outside)
        assertThat(allConditionsMet.event).isNotNull()
        assertThat(allConditionsMet.state.stableZone).isEqualTo(ProximityZone.Inside)
    }

    @Test
    fun cooldown_suppressesRepeatedCrossingButStillUpdatesZone() {
        val rule = rule(
            minimumSamples = 1,
            minimumDwellMillis = 0L,
            cooldownMillis = 10_000L,
        )
        var state = baseline(rule, distance = 8.0)

        val firstEnter = evaluate(rule, state, time = 1_000L, distance = 4.0)
        state = firstEnter.state
        state = evaluate(rule, state, time = 2_000L, distance = 7.0).state
        val enterDuringCooldown = evaluate(rule, state, time = 3_000L, distance = 4.0)
        state = enterDuringCooldown.state
        state = evaluate(rule, state, time = 10_500L, distance = 7.0).state
        val enterAfterCooldown = evaluate(rule, state, time = 11_000L, distance = 4.0)

        assertThat(firstEnter.event).isNotNull()
        assertThat(enterDuringCooldown.event).isNull()
        assertThat(enterDuringCooldown.triggerSuppressedByCooldown).isTrue()
        assertThat(enterDuringCooldown.state.stableZone).isEqualTo(ProximityZone.Inside)
        assertThat(enterAfterCooldown.event).isNotNull()
    }

    @Test
    fun missingScanWindows_canConfirmLeave() {
        val rule = rule(
            transition = ProximityTransition.Leave,
            minimumSamples = 2,
            minimumDwellMillis = 1_000L,
        )
        var state = baseline(rule, distance = 2.0)

        state = evaluate(rule, state, time = 1_000L, distance = null).state
        val left = evaluate(rule, state, time = 2_000L, distance = null)

        assertThat(left.event?.transition).isEqualTo(ProximityTransition.Leave)
        assertThat(left.event?.distanceMeters).isNull()
        assertThat(left.state.stableZone).isEqualTo(ProximityZone.Outside)
    }

    @Test
    fun staleObservation_isIgnoredWithoutMutatingState() {
        val rule = rule(minimumSamples = 1, minimumDwellMillis = 0L)
        val state = baseline(rule, distance = 8.0).copy(lastObservationAtMillis = 5_000L)

        val result = evaluate(rule, state, time = 4_999L, distance = 2.0)

        assertThat(result.observationAccepted).isFalse()
        assertThat(result.state).isEqualTo(state)
        assertThat(result.event).isNull()
    }

    @Test
    fun eventCarriesEverySupportedActionForPlatformDispatch() {
        val actions = listOf(
            notification,
            ProximityAutomationAction.PlaySound("subtle_chime"),
            ProximityAutomationAction.Vibrate(listOf(0L, 100L, 50L, 200L)),
            ProximityAutomationAction.OpenWifiPanel,
            ProximityAutomationAction.WhatsAppDraft("I am home", "+391234567890"),
            ProximityAutomationAction.TelegramDraft("I am home", "tracky_test"),
        )
        val rule = rule(
            actions = actions,
            minimumSamples = 1,
            minimumDwellMillis = 0L,
        )
        val outside = baseline(rule, distance = 8.0)

        val result = evaluate(rule, outside, time = 1_000L, distance = 2.0)

        assertThat(result.event?.actions).containsExactlyElementsIn(actions).inOrder()
    }

    private fun baseline(
        rule: ProximityAutomationRule,
        distance: Double?,
    ): ProximityRuleState = evaluate(
        rule = rule.copy(minimumSamples = 1, minimumDwellMillis = 0L),
        state = ProximityRuleState(),
        time = 0L,
        distance = distance,
    ).state

    private fun evaluate(
        rule: ProximityAutomationRule,
        state: ProximityRuleState,
        time: Long,
        distance: Double?,
    ) = evaluator.evaluate(
        rule = rule,
        previousState = state,
        observation = ProximityObservation(
            observedAtMillis = time,
            distanceMeters = distance,
        ),
    )

    private fun rule(
        transition: ProximityTransition = ProximityTransition.Enter,
        hysteresisMeters: Double = 1.0,
        minimumSamples: Int = 3,
        minimumDwellMillis: Long = 2_000L,
        cooldownMillis: Long = 60_000L,
        actions: List<ProximityAutomationAction> = listOf(notification),
    ) = ProximityAutomationRule(
        id = "keys-enter-home",
        trackerId = "tracker-1",
        transition = transition,
        radiusMeters = 5.0,
        hysteresisMeters = hysteresisMeters,
        minimumSamples = minimumSamples,
        minimumDwellMillis = minimumDwellMillis,
        cooldownMillis = cooldownMillis,
        actions = actions,
    )
}
