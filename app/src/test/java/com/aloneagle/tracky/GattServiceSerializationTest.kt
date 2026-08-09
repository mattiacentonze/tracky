package com.aloneagle.tracky

import com.aloneagle.tracky.data.repository.deserializeGattServiceSummary
import com.aloneagle.tracky.data.repository.serializeGattServiceSummary
import com.aloneagle.tracky.domain.model.GattCharacteristicProperty
import com.aloneagle.tracky.domain.model.GattCharacteristicSummary
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GattServiceSerializationTest {
    @Test
    fun roundTrip_preservesCharacteristicPropertiesAndDescriptors() {
        val service = GattServiceSummary(
            serviceUuid = "service-uuid",
            characteristics = listOf(
                GattCharacteristicSummary(
                    characteristicUuid = "char-uuid",
                    properties = setOf(
                        GattCharacteristicProperty.Write,
                        GattCharacteristicProperty.Notify,
                    ),
                    descriptorUuids = listOf("desc-1", "desc-2"),
                ),
            ),
        )

        val restored = deserializeGattServiceSummary(serializeGattServiceSummary(service))

        assertThat(restored).isEqualTo(service)
    }

    @Test
    fun deserialize_supportsLegacyUuidOnlyFormat() {
        val restored = deserializeGattServiceSummary("service-uuid;char-1;char-2")

        assertThat(restored.serviceUuid).isEqualTo("service-uuid")
        assertThat(restored.characteristicUuids).containsExactly("char-1", "char-2").inOrder()
        assertThat(restored.characteristics.all { it.properties.isEmpty() }).isTrue()
    }
}
