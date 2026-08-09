package com.aloneagle.tracky.domain.model

data class BleLogEvent(
    val id: Long = 0L,
    val sessionId: String,
    val trackerId: String?,
    val timestamp: Long,
    val category: Category,
    val action: String,
    val message: String,
    val deviceAddress: String? = null,
    val serviceUuid: String? = null,
    val characteristicUuid: String? = null,
    val payloadHex: String? = null,
    val resultCode: Int? = null,
    val rssi: Int? = null,
) {
    enum class Category {
        Scan,
        Connection,
        Discovery,
        Read,
        Write,
        Monitor,
        Repository,
        Protocol,
    }
}
