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
    fun `groups only captured paired then named and unnamed devices`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(
                scan(PAIRED_ADDRESS, "Advertised buds", -64),
                scan(NAMED_ADDRESS, "Keyboard", -41),
                scan(UNNAMED_ADDRESS, null, -35),
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
    fun `omits paired and saved devices without a current scan result`() {
        val sections = buildScanSections(
            discoveredDevices = emptyList(),
            knownTrackers = listOf(knownTracker(PAIRED_ADDRESS, nickname = "Backpack tag")),
            pairedDevices = listOf(PairedBluetoothDevice(PAIRED_ADDRESS, "Earbuds")),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.totalDeviceCount).isEqualTo(0)
    }

    @Test
    fun `deduplicates captured saved and paired metadata by address ignoring case`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(scan(PAIRED_ADDRESS, "Broadcast name", -50)),
            knownTrackers = listOf(knownTracker(PAIRED_ADDRESS.lowercase(), nickname = "Travel buds")),
            pairedDevices = listOf(PairedBluetoothDevice(PAIRED_ADDRESS, "Android alias")),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.totalDeviceCount).isEqualTo(1)
        assertThat(sections.yourDevices.single().displayName).isEqualTo("Travel buds")
        assertThat(sections.yourDevices.single().isPaired).isTrue()
        assertThat(sections.yourDevices.single().isSaved).isTrue()
    }

    @Test
    fun `saved but unpaired captured device is categorized by its effective name`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(scan(NAMED_ADDRESS, null, -58)),
            knownTrackers = listOf(knownTracker(NAMED_ADDRESS, nickname = "Bike tag")),
            pairedDevices = emptyList(),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.yourDevices).isEmpty()
        assertThat(sections.namedNearby.single().displayName).isEqualTo("Bike tag")
        assertThat(sections.namedNearby.single().isSaved).isTrue()
    }

    @Test
    fun `distance sort uses strongest signal inside fixed sections`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(
                scan(PAIRED_ADDRESS, "Weak", -82),
                scan(SECOND_PAIRED_ADDRESS, "Strong", -43),
                scan(NAMED_ADDRESS, "Named weak", -90),
                scan(SECOND_NAMED_ADDRESS, "Named strong", -50),
            ),
            knownTrackers = emptyList(),
            pairedDevices = listOf(
                PairedBluetoothDevice(PAIRED_ADDRESS, "Weak"),
                PairedBluetoothDevice(SECOND_PAIRED_ADDRESS, "Strong"),
            ),
            sortOption = DeviceSortOption.Distance,
        )

        assertThat(sections.yourDevices.map { it.deviceAddress })
            .containsExactly(SECOND_PAIRED_ADDRESS, PAIRED_ADDRESS)
            .inOrder()
        assertThat(sections.namedNearby.map { it.deviceAddress })
            .containsExactly(SECOND_NAMED_ADDRESS, NAMED_ADDRESS)
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
    fun `blank name remains unnamed and duplicate names keep distinct addresses`() {
        val sections = buildScanSections(
            discoveredDevices = listOf(
                scan(UNNAMED_ADDRESS, "   ", -52),
                scan(NAMED_ADDRESS, "Sensor", -60),
                scan(SECOND_NAMED_ADDRESS, "Sensor", -61),
            ),
            knownTrackers = emptyList(),
            pairedDevices = emptyList(),
            sortOption = DeviceSortOption.Name,
        )

        assertThat(sections.unnamedNearby.single().deviceAddress).isEqualTo(UNNAMED_ADDRESS)
        assertThat(sections.namedNearby.map { it.deviceAddress })
            .containsExactly(NAMED_ADDRESS, SECOND_NAMED_ADDRESS)
            .inOrder()
    }

    private fun scan(address: String, name: String?, rssi: Int, seenAt: Long = 1_000L) = BleScanResult(
        deviceAddress = address,
        advertisedName = name,
        resolvedName = null,
        manufacturerDataHex = null,
        serviceUuids = emptyList(),
        rssi = rssi,
        seenAt = seenAt,
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
        const val SECOND_PAIRED_ADDRESS = "AA:BB:CC:DD:EE:02"
        const val NAMED_ADDRESS = "AA:BB:CC:DD:EE:03"
        const val SECOND_NAMED_ADDRESS = "AA:BB:CC:DD:EE:04"
        const val UNNAMED_ADDRESS = "AA:BB:CC:DD:EE:05"
    }
}
