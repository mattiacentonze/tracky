package com.aloneagle.tracky.logging

import com.aloneagle.tracky.data.local.dao.BleLogEventDao
import com.aloneagle.tracky.data.local.entity.BleLogEventEntity
import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.service.BleLogSink
import javax.inject.Inject

class RoomBleLogSink @Inject constructor(
    private val bleLogEventDao: BleLogEventDao,
) : BleLogSink {
    override suspend fun log(event: BleLogEvent) {
        bleLogEventDao.insert(
            BleLogEventEntity(
                sessionId = event.sessionId,
                trackerId = event.trackerId,
                timestamp = event.timestamp,
                category = event.category.name,
                action = event.action,
                message = event.message,
                deviceAddress = event.deviceAddress,
                serviceUuid = event.serviceUuid,
                characteristicUuid = event.characteristicUuid,
                payloadHex = event.payloadHex,
                resultCode = event.resultCode,
                rssi = event.rssi,
            ),
        )
    }
}
