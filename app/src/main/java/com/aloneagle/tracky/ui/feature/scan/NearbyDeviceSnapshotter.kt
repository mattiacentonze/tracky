package com.aloneagle.tracky.ui.feature.scan

import com.aloneagle.tracky.domain.model.BleScanResult
import java.util.Locale

/**
 * Collects radio callbacks continuously while exposing a stable, periodically
 * refreshed snapshot to the UI. Freshness uses a monotonic receipt time so a
 * wall-clock adjustment cannot make an old device look nearby again.
 */
internal class NearbyDeviceSnapshotter(
    private val freshnessMillis: Long = LIVE_DEVICE_FRESHNESS_MILLIS,
) {
    private data class Entry(
        val result: BleScanResult,
        val receivedAtElapsedMillis: Long,
    )

    private val latestByAddress = linkedMapOf<String, Entry>()

    @Synchronized
    fun record(result: BleScanResult, receivedAtElapsedMillis: Long) {
        if (result.deviceAddress.isBlank()) return
        val address = result.deviceAddress.uppercase(Locale.ROOT)
        val previous = latestByAddress[address]
        if (previous != null && receivedAtElapsedMillis < previous.receivedAtElapsedMillis) return

        latestByAddress[address] = Entry(
            result = result.copy(
                // Advertising packets from the same device do not always repeat
                // Local Name. Retain a recent nonblank name to prevent section
                // jumping without retaining the device beyond its freshness TTL.
                advertisedName = result.advertisedName.nonBlank()
                    ?: previous?.result?.advertisedName.nonBlank(),
                resolvedName = result.resolvedName.nonBlank()
                    ?: previous?.result?.resolvedName.nonBlank(),
            ),
            receivedAtElapsedMillis = receivedAtElapsedMillis,
        )
    }

    @Synchronized
    fun snapshot(nowElapsedMillis: Long): Map<String, BleScanResult> {
        latestByAddress.entries.removeAll { (_, entry) ->
            val age = nowElapsedMillis - entry.receivedAtElapsedMillis
            age < 0L || age >= freshnessMillis
        }
        return latestByAddress.mapValues { (_, entry) -> entry.result }
    }

    @Synchronized
    fun clear() {
        latestByAddress.clear()
    }
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

internal const val LIVE_LIST_REFRESH_MILLIS = 5_000L
internal const val LIVE_DEVICE_FRESHNESS_MILLIS = 10_000L
