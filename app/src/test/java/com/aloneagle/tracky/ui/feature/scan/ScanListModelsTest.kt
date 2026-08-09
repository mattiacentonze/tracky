package com.aloneagle.tracky.ui.feature.scan

import com.aloneagle.tracky.domain.model.BatteryState
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.PairedBluetoothDevice
import com.aloneagle.tracky.domain.model.TrackerConnectionState
import com.aloneagle.tracky.domain.model.TrackerPresence
import com.aloneagle.tracky.domain.model.TrackerProtocolType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanListModelsTest {
    @Test
    fun `groups paired first then named and unnamed devices`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(
                scan(address = PAIRED_ADDRESS, name = "Advertised buds", rssi = -64),
                scan(address = NAMED_ADDRESS, name = "Keyboard", rssi = -41),
                scan(address = UNNAMED_ADDRESS, name = null, rssi = -35),
            ),
            knownTrackers = emptyList(),
            pairedDevices = listOf(PairedBluetoothDevice(PAIRED_ADDRESS, "Mattia's earbuds")),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.yourDevices.map { it.deviceAddress }).containsExactly(PAIRED_ADDRESS)
        assertThat(sections.namedNearby.map { it.deviceAddress }).containsExactly(NAMED_ADDRESS)
        assertThat(sections.unnamedNearby.map { it.deviceAddress }).containsExactly(UNNAMED_ADDRESS)
        assertThat(sections.yourDevices.single().displayName).isEqualTo("Mattia's earbuds")
    }

    @Test
    fun `deduplicates paired saved and live entries by address ignoring case`() {
        val tracker = knownTracker(
            address = PAIRED_ADDRESS.lowercase(),
            nickname = "Travel buds",
            resolvedName = "Old name",
        )
        val sections = buildScanSections(
            discoveredDevices = listOf(scan(PAIRED_ADDRESS, "Broadcast name", -50)),
            knownTrackers = listOf(tracker),
            pairedDevices = listOf(PairedBluetoothDevice(PAIRED_ADDRESS, "Android alias")),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.totalDeviceCount).isEqualTo(1)
        assertThat(sections.yourDevices.single().displayName).isEqualTo("Travel buds")
        assertThat(sections.yourDevices.single().isLive).isTrue()
        assertThat(sections.yourDevices.single().isPaired).isTrue()
        assertThat(sections.yourDevices.single().isSaved).isTrue()
    }

    @Test
    fun `distance sort puts strongest live signal first and unavailable signal last`() {
        val weak = PairedBluetoothDevice(PAIRED_ADDRESS, "Weak")
        val strong = PairedBluetoothDevice(NAMED_ADDRESS, "Strong")
        val unavailable = PairedBluetoothDevice(UNNAMED_ADDRESS, "Unavailable")
        val sections = buildScanSections(
            discoveredDevices = listOf(
                scan(PAIRED_ADDRESS, "Weak", -82),
                scan(NAMED_ADDRESS, "Strong", -43),
            ),
            knownTrackers = emptyList(),
            pairedDevices = listOf(weak, unavailable, strong),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.yourDevices.map { it.displayName })
            .containsExactly("Strong", "Weak", "Unavailable")
            .inOrder()
    }

    @Test
    fun `name sort applies only inside fixed sections`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(
                scan(PAIRED_ADDRESS, "Zulu", -40),
                scan(NAMED_ADDRESS, "beta", -90),
                scan(SECOND_NAMED_ADDRESS, "Alpha", -95),
                scan(UNNAMED_ADDRESS, null, -30),
            ),
            knownTrackers = emptyList(),
            pairedDevices = listOf(PairedBluetoothDevice(PAIRED_ADDRESS, "Zulu")),
            sortOption = DeviceSortOption.Name,
        )

        assertThat(sections.yourDevices.map { it.displayName }).containsExactly("Zulu")
        assertThat(sections.namedNearby.map { it.displayName })
            .containsExactly("Alpha", "beta")
            .inOrder()
        assertThat(sections.unnamedNearby.map { it.deviceAddress }).containsExactly(UNNAMED_ADDRESS)
    }

    @Test
    fun `saved device remains in first section without a current broadcast`() {
        val sections = buildScanSections(
            discoveredDevices = emptyList(),
            knownTrackers = listOf(knownTracker(PAIRED_ADDRESS, nickname = "Backpack tag")),
            pairedDevices = emptyList(),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.yourDevices.single().displayName).isEqualTo("Backpack tag")
        assertThat(sections.yourDevices.single().isLive).isFalse()
        assertThat(sections.namedNearby).isEmpty()
        assertThat(sections.unnamedNearby).isEmpty()
    }

    @Test
    fun `blank advertised name remains in unnamed section`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(scan(UNNAMED_ADDRESS, "   ", -52)),
            knownTrackers = emptyList(),
            pairedDevices = emptyList(),
            sortOption = DeviceSortOption.Name,
        )

        assertThat(sections.namedNearby).isEmpty()
        assertThat(sections.unnamedNearby.single().deviceAddress).isEqualTo(UNNAMED_ADDRESS)
    }

    private fun scan(address: String, name: String?, rssi: Int) = BleScanResult(
        deviceAddress = address,
        advertisedName = name,
        resolvedName = null,
        manufacturerDataHex = null,
        serviceUuids = emptyList(),
        rssi = rssi,
        seenAt = 1_000L,
        connectable = true,
    )

    private fun knownTracker(
        address: String,
        nickname: String? = null,
        resolvedName: String? = null,
    ) = KnownTracker(
        id = address,
        deviceAddress = address,
        nickname = nickname,
        advertisedName = null,
        resolvedName = resolvedName,
        protocolType = TrackerProtocolType.GenericBle,
        capabilities = emptySet(),
        batteryState = BatteryState(null, BatteryState.Status.Unknown),
        presence = TrackerPresence.NotRecent,
        connectionState = TrackerConnectionState.Disconnected,
        proximity = null,
        lastSeenAt = null,
        lastRssi = null,
        smoothedRssi = null,
        lastLocation = null,
        monitorEnabled = false,
        discoveredServices = emptyList(),
        manufacturerDataHex = null,
        isNutCandidate = false,
        lastSessionType = null,
        lastOutOfRangeAlertAt = null,
    )

    private companion object {
        const val PAIRED_ADDRESS = "AA:BB:CC:DD:EE:01"
        const val NAMED_ADDRESS = "AA:BB:CC:DD:EE:02"
        const val SECOND_NAMED_ADDRESS = "AA:BB:CC:DD:EE:03"
        const val UNNAMED_ADDRESS = "AA:BB:CC:DD:EE:04"
    }
}
