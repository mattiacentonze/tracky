package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.BleLogEvent

interface BleLogSink {
    suspend fun log(event: BleLogEvent)
}
