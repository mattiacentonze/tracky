package com.aloneagle.tracky.domain.model

data class GattReadRequest(
    val serviceUuid: String,
    val characteristicUuid: String,
)

data class GattWriteRequest(
    val serviceUuid: String,
    val characteristicUuid: String,
    val payload: ByteArray,
    val writeType: Int? = null,
)
