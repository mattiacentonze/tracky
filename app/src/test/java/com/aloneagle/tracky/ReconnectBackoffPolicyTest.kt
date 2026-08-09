package com.aloneagle.tracky

import com.aloneagle.tracky.service.ReconnectBackoffPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReconnectBackoffPolicyTest {
    private val policy = ReconnectBackoffPolicy()

    @Test
    fun delays_matchExpectedSequence() {
        assertThat(policy.nextDelayMillis(0)).isEqualTo(5_000L)
        assertThat(policy.nextDelayMillis(1)).isEqualTo(15_000L)
        assertThat(policy.nextDelayMillis(2)).isEqualTo(60_000L)
        assertThat(policy.nextDelayMillis(3)).isEqualTo(300_000L)
        assertThat(policy.nextDelayMillis(7)).isEqualTo(300_000L)
    }
}
