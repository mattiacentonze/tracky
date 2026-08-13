package com.aloneagle.tracky.ui.feature.scan

import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.PairedBluetoothDevice
import java.util.Locale

enum class DeviceSortOption {
    Distance,
    Name,
}

data class ScanCandidateUi(
    val deviceAddress: String,
    val result: BleScanResult,
    val savedTracker: KnownTracker?,
    val pairedDevice: PairedBluetoothDevice?,
) {
    val isPaired: Boolean get() = pairedDevice != null
    val isSaved: Boolean get() = savedTracker != null

    val displayName: String
        get() = savedTracker?.nickname.nonBlank()
            ?: pairedDevice?.systemName.nonBlank()
            ?: savedTracker?.resolvedName.nonBlank()
            ?: result.resolvedName.nonBlank()
            ?: savedTracker?.advertisedName.nonBlank()
            ?: result.advertisedName.nonBlank()
            ?: "Unknown device"

    val hasHumanReadableName: Boolean
        get() = savedTracker?.nickname.nonBlank() != null ||
            pairedDevice?.systemName.nonBlank() != null ||
            savedTracker?.resolvedName.nonBlank() != null ||
            result.resolvedName.nonBlank() != null ||
            savedTracker?.advertisedName.nonBlank() != null ||
            result.advertisedName.nonBlank() != null

    val stableNameKey: String
        get() = if (hasHumanReadableName) displayName.lowercase(Locale.ROOT) else deviceAddress
}

data class ScanSections(
    val yourDevices: List<ScanCandidateUi> = emptyList(),
    val namedNearby: List<ScanCandidateUi> = emptyList(),
    val unnamedNearby: List<ScanCandidateUi> = emptyList(),
) {
    val totalDeviceCount: Int
        get() = yourDevices.size + namedNearby.size + unnamedNearby.size
}

internal fun buildScanSections(
    discoveredDevices: Collection<BleScanResult>,
    knownTrackers: List<KnownTracker>,
    pairedDevices: List<PairedBluetoothDevice>,
    sortOption: DeviceSortOption,
): ScanSections {
    val discoveredByAddress = discoveredDevices.associateBy { result -> result.deviceAddress.normalizedAddress() }
    val knownByAddress = knownTrackers.associateBy { tracker -> tracker.deviceAddress.normalizedAddress() }
    val pairedByAddress = pairedDevices.associateBy { device -> device.deviceAddress.normalizedAddress() }
    val comparator = candidateComparator(sortOption)
    val capturedDevices = discoveredByAddress.map { (address, result) ->
        ScanCandidateUi(
            deviceAddress = result.deviceAddress,
            result = result,
            savedTracker = knownByAddress[address],
            pairedDevice = pairedByAddress[address],
        )
    }
    return ScanSections(
        // Paired and saved records enrich a live scan result; they never create an
        // offline row. This keeps the Devices screen a truthful view of broadcasts
        // received during the current scan session.
        yourDevices = capturedDevices.filter(ScanCandidateUi::isPaired).sortedWith(comparator),
        namedNearby = capturedDevices
            .filterNot(ScanCandidateUi::isPaired)
            .filter(ScanCandidateUi::hasHumanReadableName)
            .sortedWith(comparator),
        unnamedNearby = capturedDevices
            .filterNot(ScanCandidateUi::isPaired)
            .filterNot(ScanCandidateUi::hasHumanReadableName)
            .sortedWith(comparator),
    )
}

private fun candidateComparator(sortOption: DeviceSortOption): Comparator<ScanCandidateUi> =
    when (sortOption) {
        DeviceSortOption.Distance -> compareByDescending<ScanCandidateUi> { candidate -> candidate.result.rssi }
            .thenBy { candidate -> candidate.stableNameKey }
            .thenBy { candidate -> candidate.deviceAddress }

        DeviceSortOption.Name -> compareBy<ScanCandidateUi> { candidate -> candidate.stableNameKey }
            .thenByDescending { candidate -> candidate.result.rssi }
            .thenBy { candidate -> candidate.deviceAddress }
    }

private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String.normalizedAddress(): String = uppercase(Locale.ROOT)
