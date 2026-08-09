package com.aloneagle.tracky

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SanityTest {
    @Test
    fun sanity() {
        assertThat("Tracky").isEqualTo("Tracky")
    }
}
