package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.model.TrackerConnectionState
import kotlinx.coroutines.flow.StateFlow

interface BleConnectionManager {
    suspend fun connect(deviceAddress: String, sessionId: String): ConnectionSession?

    interface ConnectionSession {
        val deviceAddress: String
        val connectionState: StateFlow<TrackerConnectionState>
        suspend fun discoverServices(): Result<List<GattServiceSummary>>
        suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): Result<ByteArray>
        suspend fun writeCharacteristic(
            serviceUuid: String,
            characteristicUuid: String,
            payload: ByteArray,
            writeType: Int? = null,
        ): Result<Unit>

        suspend fun disconnect()
    }
}
