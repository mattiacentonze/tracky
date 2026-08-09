package com.aloneagle.tracky

import com.aloneagle.tracky.ble.protocol.GenericBleTrackerAdapter
import com.aloneagle.tracky.ble.protocol.NutFindthingAdapter
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.GattCharacteristicSummary
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.model.TrackerCapability
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProtocolAdaptersTest {
    @Test
    fun genericAdapter_exposesBatteryReadWhenStandardServicePresent() {
        val adapter = GenericBleTrackerAdapter()
        val request = adapter.batteryReadRequest(
            services = listOf(
                GattServiceSummary(
                    serviceUuid = "0000180f-0000-1000-8000-00805f9b34fb",
                    characteristics = listOf(
                        GattCharacteristicSummary("00002a19-0000-1000-8000-00805f9b34fb"),
                    ),
                ),
            ),
        )

        assertThat(request).isNotNull()
        assertThat(request?.characteristicUuid).contains("2a19")
    }

    @Test
    fun nutAdapter_marksRingAsUnconfirmedForNutNamedTracker() {
        val adapter = NutFindthingAdapter()
        val scanResult = BleScanResult(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            advertisedName = "Nut Findthing",
            resolvedName = null,
            manufacturerDataHex = null,
            serviceUuids = emptyList(),
            rssi = -64,
            seenAt = 1L,
            connectable = true,
        )

        val capabilities = adapter.inferCapabilities(scanResult)

        assertThat(adapter.matches(scanResult)).isTrue()
        assertThat(capabilities).contains(TrackerCapability.RingUnconfirmed)
        assertThat(adapter.ringWriteRequest(emptyList())).isNull()
    }
}
