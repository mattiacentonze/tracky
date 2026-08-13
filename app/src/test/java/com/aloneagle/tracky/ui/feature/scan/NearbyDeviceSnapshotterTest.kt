package com.aloneagle.tracky.ui.feature.scan

import com.aloneagle.tracky.domain.model.BleScanResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NearbyDeviceSnapshotterTest {
    @Test
    fun `snapshot keeps a device before and removes it at the freshness boundary`() {
        val snapshotter = NearbyDeviceSnapshotter(freshnessMillis = 10_000L)
        snapshotter.record(scan(name = "Tag", seenAt = 1_000L), receivedAtElapsedMillis = 2_000L)

        assertThat(snapshotter.snapshot(7_000L)).containsKey(ADDRESS)
        assertThat(snapshotter.snapshot(11_999L)).containsKey(ADDRESS)
        assertThat(snapshotter.snapshot(12_000L)).isEmpty()
    }

    @Test
    fun `newer nameless advertisement keeps recent nonblank names`() {
        val snapshotter = NearbyDeviceSnapshotter()
        snapshotter.record(
            scan(name = "Bike tag", resolvedName = "Android alias", seenAt = 1_000L),
            receivedAtElapsedMillis = 1_000L,
        )
        snapshotter.record(
            scan(name = null, resolvedName = " ", seenAt = 2_000L, rssi = -44),
            receivedAtElapsedMillis = 2_000L,
        )

        val result = snapshotter.snapshot(2_000L).getValue(ADDRESS)
        assertThat(result.advertisedName).isEqualTo("Bike tag")
        assertThat(result.resolvedName).isEqualTo("Android alias")
        assertThat(result.rssi).isEqualTo(-44)
    }

    @Test
    fun `out of order result cannot replace the newest observation`() {
        val snapshotter = NearbyDeviceSnapshotter()
        snapshotter.record(scan(name = "New", seenAt = 2_000L, rssi = -40), 2_000L)
        snapshotter.record(scan(name = "Old", seenAt = 1_000L, rssi = -90), 1_500L)

        val result = snapshotter.snapshot(3_000L).getValue(ADDRESS)
        assertThat(result.advertisedName).isEqualTo("New")
        assertThat(result.rssi).isEqualTo(-40)
    }

    @Test
    fun `address matching is case insensitive and clear removes pending results`() {
        val snapshotter = NearbyDeviceSnapshotter()
        snapshotter.record(scan(address = ADDRESS.lowercase(), name = "First", seenAt = 1_000L), 1_000L)
        snapshotter.record(scan(address = ADDRESS, name = "Second", seenAt = 2_000L), 2_000L)

        assertThat(snapshotter.snapshot(2_000L)).hasSize(1)
        snapshotter.clear()
        assertThat(snapshotter.snapshot(2_000L)).isEmpty()
    }

    private fun scan(
        address: String = ADDRESS,
        name: String?,
        resolvedName: String? = null,
        seenAt: Long,
        rssi: Int = -60,
    ) = BleScanResult(
        deviceAddress = address,
        advertisedName = name,
        resolvedName = resolvedName,
        manufacturerDataHex = null,
        serviceUuids = emptyList(),
        rssi = rssi,
        seenAt = seenAt,
        connectable = true,
    )

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:01"
    }
}
