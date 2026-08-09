package com.aloneagle.tracky.domain.model

data class GattServiceSummary(
    val serviceUuid: String,
    val characteristics: List<GattCharacteristicSummary>,
) {
    val characteristicUuids: List<String>
        get() = characteristics.map(GattCharacteristicSummary::characteristicUuid)
}

data class GattCharacteristicSummary(
    val characteristicUuid: String,
    val properties: Set<GattCharacteristicProperty> = emptySet(),
    val descriptorUuids: List<String> = emptyList(),
) {
    val supportsWrite: Boolean
        get() = GattCharacteristicProperty.Write in properties ||
            GattCharacteristicProperty.WriteNoResponse in properties
}

enum class GattCharacteristicProperty {
    Read,
    Write,
    WriteNoResponse,
    Notify,
    Indicate,
}
