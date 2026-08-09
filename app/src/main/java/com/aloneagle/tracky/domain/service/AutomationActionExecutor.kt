package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.ProximityAutomationEvent

data class AutomationDispatchResult(
    val deliveredActions: Int,
    val failures: List<String>,
) {
    val isSuccess: Boolean get() = deliveredActions > 0 && failures.isEmpty()

    val summary: String
        get() = if (isSuccess) {
            "$deliveredActions action(s) dispatched."
        } else {
            failures.joinToString(separator = " ").ifBlank { "No action was dispatched." }
        }
}

interface AutomationActionExecutor {
    suspend fun execute(event: ProximityAutomationEvent, trackerName: String): AutomationDispatchResult
}
