package com.aloneagle.tracky.ble.protocol

import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.service.TrackerProtocolAdapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtocolRegistry @Inject constructor(
    private val adapters: Set<@JvmSuppressWildcards TrackerProtocolAdapter>,
) {
    fun resolve(
        scanResult: BleScanResult,
        services: List<GattServiceSummary> = emptyList(),
    ): TrackerProtocolAdapter = adapters.firstOrNull { it.matches(scanResult, services) }
        ?: adapters.first()
}
