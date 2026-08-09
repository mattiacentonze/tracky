package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.TrackerObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TrackerMonitorCoordinator {
    val activeTrackerIds: StateFlow<Set<String>>
    val activeSearchTrackerId: StateFlow<String?>
    fun observeRealtimeObservation(trackerId: String): StateFlow<TrackerObservation?>
    fun scanNearby(sessionId: String): Flow<BleScanResult>
    suspend fun startSearch(trackerId: String)
    suspend fun stopSearch(trackerId: String)
    suspend fun syncMonitoredTrackers()
    suspend fun stopAll()
}
