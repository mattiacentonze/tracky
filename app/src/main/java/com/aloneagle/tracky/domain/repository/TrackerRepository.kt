package com.aloneagle.tracky.domain.repository

import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.RingResult
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.model.TrackerObservation
import kotlinx.coroutines.flow.Flow

interface TrackerRepository {
    fun observeKnownTrackers(): Flow<List<KnownTracker>>
    fun observeTracker(id: String): Flow<KnownTracker?>
    fun observeRecentObservations(id: String, limit: Int = 30): Flow<List<TrackerObservation>>
    fun observeBleLogs(trackerId: String? = null, limit: Int = 500): Flow<List<BleLogEvent>>
    suspend fun upsertFromScan(scanResult: BleScanResult, sessionType: ScanSessionType): KnownTracker
    suspend fun renameTracker(id: String, nickname: String)
    suspend fun setMonitorEnabled(id: String, enabled: Boolean)
    suspend fun refreshTracker(id: String, sessionId: String): Result<KnownTracker>
    suspend fun ringTracker(id: String, sessionId: String): RingResult
    suspend fun debugWriteCharacteristic(
        id: String,
        serviceUuid: String,
        characteristicUuid: String,
        payloadHex: String,
        sessionId: String,
    ): Result<Unit>
    suspend fun markOutOfRangeAlertSent(id: String, timestamp: Long)
    suspend fun pruneLogs(limit: Int = 4000)
}
