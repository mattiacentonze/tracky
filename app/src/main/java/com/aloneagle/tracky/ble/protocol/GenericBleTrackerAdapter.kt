package com.aloneagle.tracky.ble.protocol

import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.GattReadRequest
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.model.GattWriteRequest
import com.aloneagle.tracky.domain.model.TrackerCapability
import com.aloneagle.tracky.domain.model.TrackerProtocolType
import com.aloneagle.tracky.domain.service.TrackerProtocolAdapter
import javax.inject.Inject

class GenericBleTrackerAdapter @Inject constructor() : TrackerProtocolAdapter {
    override val protocolType: TrackerProtocolType = TrackerProtocolType.GenericBle

    override fun matches(scanResult: BleScanResult, services: List<GattServiceSummary>): Boolean = true

    override fun inferCapabilities(
        scanResult: BleScanResult,
        services: List<GattServiceSummary>,
    ): Set<TrackerCapability> {
        val hasBattery = services.any { it.serviceUuid.equals(BATTERY_SERVICE_UUID, ignoreCase = true) }
        return buildSet {
            add(TrackerCapability.ServiceDiscovery)
            add(TrackerCapability.NameResolution)
            if (hasBattery) {
                add(TrackerCapability.BatteryRead)
            }
            add(TrackerCapability.RingUnsupported)
        }
    }

    override fun batteryReadRequest(services: List<GattServiceSummary>): GattReadRequest? {
        val batteryService = services.firstOrNull {
            it.serviceUuid.equals(BATTERY_SERVICE_UUID, ignoreCase = true)
        } ?: return null
        val batteryCharacteristic = batteryService.characteristics.firstOrNull { characteristic ->
            characteristic.characteristicUuid.equals(BATTERY_LEVEL_UUID, ignoreCase = true)
        } ?: return null
        return GattReadRequest(
            serviceUuid = batteryService.serviceUuid,
            characteristicUuid = batteryCharacteristic.characteristicUuid,
        )
    }

    override fun ringWriteRequest(services: List<GattServiceSummary>): GattWriteRequest? = null

    companion object {
        private const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
        private const val BATTERY_LEVEL_UUID = "00002a19-0000-1000-8000-00805f9b34fb"
    }
}
