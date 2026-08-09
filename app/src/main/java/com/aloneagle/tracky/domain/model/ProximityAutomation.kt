package com.aloneagle.tracky.domain.model

/**
 * The side of a proximity boundary that has been confirmed by the evaluator.
 *
 * [Unknown] is intentionally a real state: the first observations establish a baseline and must
 * not be mistaken for an enter/leave event when monitoring starts.
 */
enum class ProximityZone {
    Unknown,
    Inside,
    Outside,
}

enum class ProximityTransition {
    Enter,
    Leave,
}

/** Platform-neutral descriptions of work an Android adapter can perform after a rule fires. */
sealed interface ProximityAutomationAction {
    data class ShowNotification(
        val title: String,
        val message: String,
    ) : ProximityAutomationAction {
        init {
            require(title.isNotBlank()) { "Notification title must not be blank." }
            require(message.isNotBlank()) { "Notification message must not be blank." }
        }
    }

    data class PlaySound(
        /** A stable application-owned key; null selects the user's default automation sound. */
        val soundKey: String? = null,
    ) : ProximityAutomationAction {
        init {
            require(soundKey == null || soundKey.isNotBlank()) { "Sound key must not be blank." }
        }
    }

    data class Vibrate(
        /** Alternating wait/vibrate durations, matching Android's vibration pattern convention. */
        val patternMillis: List<Long> = listOf(0L, 300L),
    ) : ProximityAutomationAction {
        init {
            require(patternMillis.isNotEmpty()) { "Vibration pattern must not be empty." }
            require(patternMillis.all { duration -> duration >= 0L }) {
                "Vibration durations must not be negative."
            }
            require(patternMillis.any { duration -> duration > 0L }) {
                "Vibration pattern must contain a non-zero duration."
            }
        }
    }

    /** Opens the system Wi-Fi panel; current Android versions do not allow silent Wi-Fi toggles. */
    data object OpenWifiPanel : ProximityAutomationAction

    /** Opens a pre-filled draft. Sending remains an explicit user action. */
    data class WhatsAppDraft(
        val message: String,
        val recipientPhoneNumber: String? = null,
    ) : ProximityAutomationAction {
        init {
            require(message.isNotBlank()) { "WhatsApp draft must not be blank." }
            require(recipientPhoneNumber == null || recipientPhoneNumber.isNotBlank()) {
                "WhatsApp recipient must not be blank."
            }
        }
    }

    /** Opens a pre-filled draft. Sending remains an explicit user action. */
    data class TelegramDraft(
        val message: String,
        val recipientUsername: String? = null,
    ) : ProximityAutomationAction {
        init {
            require(message.isNotBlank()) { "Telegram draft must not be blank." }
            require(recipientUsername == null || recipientUsername.isNotBlank()) {
                "Telegram recipient must not be blank."
            }
        }
    }
}

/**
 * A rule evaluated against distance estimates for one tracker.
 *
 * Enter is confirmed at [radiusMeters] or closer. Leave is confirmed at
 * `radiusMeters + hysteresisMeters` or farther. The gap prevents noisy BLE estimates near the
 * boundary from repeatedly changing zones.
 */
data class ProximityAutomationRule(
    val id: String,
    val trackerId: String,
    val transition: ProximityTransition,
    val radiusMeters: Double,
    val hysteresisMeters: Double = 1.0,
    val minimumSamples: Int = 3,
    val minimumDwellMillis: Long = 2_000L,
    val cooldownMillis: Long = 60_000L,
    val actions: List<ProximityAutomationAction>,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Rule id must not be blank." }
        require(trackerId.isNotBlank()) { "Tracker id must not be blank." }
        require(radiusMeters.isFinite() && radiusMeters > 0.0) {
            "Radius must be finite and greater than zero."
        }
        require(hysteresisMeters.isFinite() && hysteresisMeters >= 0.0) {
            "Hysteresis must be finite and non-negative."
        }
        require(minimumSamples > 0) { "Minimum samples must be greater than zero." }
        require(minimumDwellMillis >= 0L) { "Minimum dwell must be non-negative." }
        require(cooldownMillis >= 0L) { "Cooldown must be non-negative." }
        require(actions.isNotEmpty()) { "A rule must contain at least one action." }
    }
}

/**
 * One evaluator input. A null distance means a completed scan window in which the tracker was not
 * observed; it counts as an outside sample. It must not be emitted for every missing scan packet.
 */
data class ProximityObservation(
    val observedAtMillis: Long,
    val distanceMeters: Double?,
) {
    init {
        require(observedAtMillis >= 0L) { "Observation time must be non-negative." }
        require(distanceMeters == null || (distanceMeters.isFinite() && distanceMeters >= 0.0)) {
            "Distance must be null or a finite, non-negative value."
        }
    }
}

/** Persist this value per rule between evaluations (and across process restarts if required). */
data class ProximityRuleState(
    val stableZone: ProximityZone = ProximityZone.Unknown,
    val candidateZone: ProximityZone? = null,
    val candidateSinceMillis: Long? = null,
    val candidateSamples: Int = 0,
    val lastTriggeredAtMillis: Long? = null,
    val lastObservationAtMillis: Long? = null,
) {
    init {
        require(candidateSamples >= 0) { "Candidate sample count must be non-negative." }
        require((candidateZone == null) == (candidateSinceMillis == null)) {
            "Candidate zone and start time must both be present or both be absent."
        }
        require(candidateZone != ProximityZone.Unknown) { "Unknown cannot be a candidate zone." }
        require(candidateZone != null || candidateSamples == 0) {
            "Candidate samples require a candidate zone."
        }
        require(candidateZone == null || candidateSamples > 0) {
            "A candidate zone requires at least one sample."
        }
        require(candidateSinceMillis == null || candidateSinceMillis >= 0L) {
            "Candidate start time must be non-negative."
        }
        require(lastTriggeredAtMillis == null || lastTriggeredAtMillis >= 0L) {
            "Last trigger time must be non-negative."
        }
        require(lastObservationAtMillis == null || lastObservationAtMillis >= 0L) {
            "Last observation time must be non-negative."
        }
    }
}

data class ProximityAutomationEvent(
    val ruleId: String,
    val trackerId: String,
    val transition: ProximityTransition,
    val observedAtMillis: Long,
    val distanceMeters: Double?,
    val actions: List<ProximityAutomationAction>,
)

data class ProximityEvaluation(
    val state: ProximityRuleState,
    /** Null while establishing the initial baseline or when no confirmed boundary was crossed. */
    val confirmedTransition: ProximityTransition? = null,
    val event: ProximityAutomationEvent? = null,
    val observationAccepted: Boolean = true,
    val triggerSuppressedByCooldown: Boolean = false,
)
