package com.aloneagle.tracky.domain.model

data class BleScanResult(
    val deviceAddress: String,
    val advertisedName: String?,
    val resolvedName: String?,
    val manufacturerDataHex: String?,
    val serviceUuids: List<String>,
    val rssi: Int,
    val seenAt: Long,
    val connectable: Boolean,
)
