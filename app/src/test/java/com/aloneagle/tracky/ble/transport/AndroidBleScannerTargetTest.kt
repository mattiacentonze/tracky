package com.aloneagle.tracky.ble.transport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidBleScannerTargetTest {
    @Test
    fun `unfiltered scan accepts every address`() {
        assertThat(matchesTargetAddress(emptySet(), "C2:11:22:33:44:55")).isTrue()
    }

    @Test
    fun `target matching accepts random address without case sensitivity`() {
        val targets = setOf("C2:11:22:33:44:55")

        assertThat(matchesTargetAddress(targets, "c2:11:22:33:44:55")).isTrue()
        assertThat(matchesTargetAddress(targets, "D3:11:22:33:44:55")).isFalse()
    }
}
