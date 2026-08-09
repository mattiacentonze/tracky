package com.aloneagle.tracky

import com.aloneagle.tracky.service.shouldStopIdleService
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackerMonitorServiceIdleTest {
    @Test
    fun idleLatestStart_canStop() {
        assertThat(
            shouldStopIdleService(
                expectedStartId = 7,
                latestStartId = 7,
                activeTrackerIds = emptySet(),
                activeSearchTrackerId = null,
            ),
        ).isTrue()
    }

    @Test
    fun newerStartOrActiveWork_preventsStop() {
        assertThat(
            shouldStopIdleService(7, 8, emptySet(), null),
        ).isFalse()
        assertThat(
            shouldStopIdleService(7, 7, setOf("tracker"), null),
        ).isFalse()
        assertThat(
            shouldStopIdleService(7, 7, emptySet(), "search"),
        ).isFalse()
    }
}
