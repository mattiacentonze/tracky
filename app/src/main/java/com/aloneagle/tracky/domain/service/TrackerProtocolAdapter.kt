package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.GattReadRequest
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.model.GattWriteRequest
import com.aloneagle.tracky.domain.model.TrackerCapability
import com.aloneagle.tracky.domain.model.TrackerProtocolType

interface TrackerProtocolAdapter {
    val protocolType: TrackerProtocolType

    fun matches(
        scanResult: BleScanResult,
        services: List<GattServiceSummary> = emptyList(),
    ): Boolean

    fun inferCapabilities(
        scanResult: BleScanResult,
        services: List<GattServiceSummary> = emptyList(),
    ): Set<TrackerCapability>

    fun batteryReadRequest(services: List<GattServiceSummary>): GattReadRequest?

    fun ringWriteRequest(services: List<GattServiceSummary>): GattWriteRequest?

    fun notes(
        scanResult: BleScanResult,
        services: List<GattServiceSummary> = emptyList(),
    ): List<String> = emptyList()
}
