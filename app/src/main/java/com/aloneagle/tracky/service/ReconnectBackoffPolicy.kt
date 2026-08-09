package com.aloneagle.tracky.service

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReconnectBackoffPolicy @Inject constructor() {
    fun nextDelayMillis(attemptCount: Int): Long = when (attemptCount) {
        0 -> 5_000L
        1 -> 15_000L
        2 -> 60_000L
        else -> 5 * 60_000L
    }
}
