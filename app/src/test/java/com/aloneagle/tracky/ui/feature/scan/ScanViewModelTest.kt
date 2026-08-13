package com.aloneagle.tracky.ui.feature.scan

import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.PairedBluetoothDevice
import com.aloneagle.tracky.domain.model.RingResult
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.model.TrackerObservation
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.service.BluetoothDeviceCatalog
import com.aloneagle.tracky.domain.service.BluetoothRadioState
import com.aloneagle.tracky.domain.service.TrackerMonitorCoordinator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    @Test
    fun `one scanner stays active and publishes the live list every five seconds`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val coordinator = FakeMonitorCoordinator()
            val catalog = FakeBluetoothDeviceCatalog(BluetoothRadioState.Enabled)
            val viewModel = ScanViewModel(coordinator, FakeTrackerRepository(), catalog)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            viewModel.startScanning()
            runCurrent()
            assertThat(coordinator.startCount).isEqualTo(1)
            assertThat(coordinator.activeCount).isEqualTo(1)

            viewModel.startScanning()
            runCurrent()
            assertThat(coordinator.startCount).isEqualTo(1)

            coordinator.results.emit(scan())
            runCurrent()
            assertThat(viewModel.uiState.value.sections.totalDeviceCount).isEqualTo(0)

            advanceTimeBy(LIVE_LIST_REFRESH_MILLIS - 1L)
            runCurrent()
            assertThat(viewModel.uiState.value.sections.totalDeviceCount).isEqualTo(0)

            advanceTimeBy(1L)
            runCurrent()
            assertThat(viewModel.uiState.value.sections.totalDeviceCount).isEqualTo(1)

            viewModel.refreshVisibleDevices()
            runCurrent()
            assertThat(coordinator.startCount).isEqualTo(1)

            viewModel.stopScanning()
            runCurrent()
            assertThat(coordinator.activeCount).isEqualTo(0)
            assertThat(viewModel.uiState.value.sections.totalDeviceCount).isEqualTo(0)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `disabled Bluetooth never starts the scanner`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val coordinator = FakeMonitorCoordinator()
            val viewModel = ScanViewModel(
                coordinator,
                FakeTrackerRepository(),
                FakeBluetoothDeviceCatalog(BluetoothRadioState.Disabled),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            viewModel.startScanning()
            runCurrent()

            assertThat(coordinator.startCount).isEqualTo(0)
            assertThat(viewModel.uiState.value.sections.totalDeviceCount).isEqualTo(0)
            assertThat(viewModel.uiState.value.radioState).isEqualTo(BluetoothRadioState.Disabled)
            assertThat(viewModel.uiState.value.isScanning).isFalse()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun scan() = BleScanResult(
        deviceAddress = ADDRESS,
        advertisedName = "Keyboard",
        resolvedName = null,
        manufacturerDataHex = null,
        serviceUuids = emptyList(),
        rssi = -52,
        seenAt = System.currentTimeMillis(),
        connectable = true,
    )

    private class FakeBluetoothDeviceCatalog(
        var state: BluetoothRadioState,
    ) : BluetoothDeviceCatalog {
        override fun currentRadioState(): BluetoothRadioState = state
        override fun pairedDevices(): List<PairedBluetoothDevice> = emptyList()
    }

    private class FakeMonitorCoordinator : TrackerMonitorCoordinator {
        val results = MutableSharedFlow<BleScanResult>(extraBufferCapacity = 16)
        var startCount = 0
        var activeCount = 0

        override val activeTrackerIds = MutableStateFlow(emptySet<String>())
        override val activeSearchTrackerId = MutableStateFlow<String?>(null)

        override fun observeRealtimeObservation(trackerId: String): StateFlow<TrackerObservation?> =
            MutableStateFlow(null)

        override fun scanNearby(sessionId: String): Flow<BleScanResult> = flow {
            startCount++
            activeCount++
            try {
                results.collect { result -> emit(result) }
            } finally {
                activeCount--
            }
        }

        override suspend fun startSearch(trackerId: String) = Unit
        override suspend fun stopSearch(trackerId: String) = Unit
        override suspend fun syncMonitoredTrackers() = Unit
        override suspend fun stopAll() = Unit
    }

    private class FakeTrackerRepository : TrackerRepository {
        private val knownTrackers = MutableStateFlow(emptyList<KnownTracker>())

        override fun observeKnownTrackers(): Flow<List<KnownTracker>> = knownTrackers
        override fun observeTracker(id: String): Flow<KnownTracker?> = emptyFlow()
        override fun observeRecentObservations(id: String, limit: Int): Flow<List<TrackerObservation>> = emptyFlow()
        override fun observeBleLogs(trackerId: String?, limit: Int): Flow<List<BleLogEvent>> = emptyFlow()
        override suspend fun upsertFromScan(scanResult: BleScanResult, sessionType: ScanSessionType): KnownTracker =
            error("Not used")
        override suspend fun upsertPairedDevice(device: PairedBluetoothDevice): KnownTracker = error("Not used")
        override suspend fun renameTracker(id: String, nickname: String) = Unit
        override suspend fun setMonitorEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun refreshTracker(id: String, sessionId: String): Result<KnownTracker> = error("Not used")
        override suspend fun ringTracker(id: String, sessionId: String): RingResult = error("Not used")
        override suspend fun debugWriteCharacteristic(
            id: String,
            serviceUuid: String,
            characteristicUuid: String,
            payloadHex: String,
            sessionId: String,
        ): Result<Unit> = error("Not used")
        override suspend fun markOutOfRangeAlertSent(id: String, timestamp: Long) = Unit
        override suspend fun pruneLogs(limit: Int) = Unit
    }

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:01"
    }
}
