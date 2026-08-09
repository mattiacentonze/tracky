package com.aloneagle.tracky.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ble_log_events",
    indices = [
        Index(value = ["trackerId", "timestamp"]),
        Index(value = ["sessionId", "timestamp"]),
    ],
)
data class BleLogEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val trackerId: String?,
    val timestamp: Long,
    val category: String,
    val action: String,
    val message: String,
    val deviceAddress: String?,
    val serviceUuid: String?,
    val characteristicUuid: String?,
    val payloadHex: String?,
    val resultCode: Int?,
    val rssi: Int?,
)
