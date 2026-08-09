package com.aloneagle.tracky.ble.protocol

import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.GattReadRequest
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.model.GattWriteRequest
import com.aloneagle.tracky.domain.model.TrackerCapability
import com.aloneagle.tracky.domain.model.TrackerProtocolType
import com.aloneagle.tracky.domain.service.TrackerProtocolAdapter
import javax.inject.Inject

class NutFindthingAdapter @Inject constructor() : TrackerProtocolAdapter {
    override val protocolType: TrackerProtocolType = TrackerProtocolType.NutFindthing

    override fun matches(scanResult: BleScanResult, services: List<GattServiceSummary>): Boolean {
        val advertisedName = scanResult.advertisedName.orEmpty().lowercase()
        val resolvedName = scanResult.resolvedName.orEmpty().lowercase()
        return advertisedName.contains("nut") ||
            advertisedName.contains("findthing") ||
            resolvedName.contains("nut") ||
            resolvedName.contains("findthing")
    }

    override fun inferCapabilities(
        scanResult: BleScanResult,
        services: List<GattServiceSummary>,
    ): Set<TrackerCapability> = buildSet {
        add(TrackerCapability.ServiceDiscovery)
        add(TrackerCapability.NameResolution)
        add(TrackerCapability.RingUnconfirmed)
        if (services.any { it.serviceUuid.equals(BATTERY_SERVICE_UUID, ignoreCase = true) }) {
            add(TrackerCapability.BatteryRead)
        }
    }

    override fun batteryReadRequest(services: List<GattServiceSummary>): GattReadRequest? {
        val generic = GenericBleTrackerAdapter()
        return generic.batteryReadRequest(services)
    }

    override fun ringWriteRequest(services: List<GattServiceSummary>): GattWriteRequest? = null

    override fun notes(scanResult: BleScanResult, services: List<GattServiceSummary>): List<String> = listOf(
        "Nut candidate inferred from advertised or resolved name.",
        "Ring path remains unconfirmed until live GATT validation proves a writeable characteristic and device response.",
        "Use tracker diagnostics to inspect writable characteristics and run an explicit raw GATT write probe.",
    )

    companion object {
        private const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
    }
}
