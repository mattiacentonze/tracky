package com.aloneagle.tracky.data.repository

import com.aloneagle.tracky.data.local.entity.BleLogEventEntity
import com.aloneagle.tracky.data.local.entity.KnownTrackerEntity
import com.aloneagle.tracky.data.local.entity.TrackerObservationEntity
import com.aloneagle.tracky.domain.model.BatteryState
import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.GattCharacteristicProperty
import com.aloneagle.tracky.domain.model.GattCharacteristicSummary
import com.aloneagle.tracky.domain.model.GattServiceSummary
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.LocationSnapshot
import com.aloneagle.tracky.domain.model.ProximityEstimate
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.model.TrackerCapability
import com.aloneagle.tracky.domain.model.TrackerConnectionState
import com.aloneagle.tracky.domain.model.TrackerObservation
import com.aloneagle.tracky.domain.model.TrackerPresence
import com.aloneagle.tracky.domain.model.TrackerProtocolType

internal fun KnownTrackerEntity.toDomain(): KnownTracker {
    val services = discoveredServices
        .filter { it.isNotBlank() }
        .map(::deserializeGattServiceSummary)
    return KnownTracker(
        id = id,
        deviceAddress = deviceAddress,
        nickname = nickname,
        advertisedName = advertisedName,
        resolvedName = resolvedName,
        protocolType = TrackerProtocolType.valueOf(protocolType),
        capabilities = capabilities.mapTo(linkedSetOf()) { TrackerCapability.valueOf(it) },
        batteryState = BatteryState(
            percentage = batteryPercent,
            status = BatteryState.Status.valueOf(batteryStatus),
        ),
        presence = lastSeenAt.toCurrentPresence(),
        connectionState = TrackerConnectionState.Disconnected,
        proximity = smoothedRssi?.let { smoothed ->
            val level = when {
                smoothed >= -55 -> ProximityEstimate.Level.Immediate
                smoothed >= -67 -> ProximityEstimate.Level.Near
                smoothed >= -75 -> ProximityEstimate.Level.Warm
                smoothed >= -88 -> ProximityEstimate.Level.Far
                else -> ProximityEstimate.Level.Lost
            }
            ProximityEstimate(
                level = level,
                // A persisted RSSI snapshot has no live confidence/trend context. Finder waits
                // for fresh observations before presenting an approximate metre value.
                estimatedDistanceMeters = null,
                signalPercent = (((smoothed + 100.0) / 55.0) * 100).toInt().coerceIn(0, 100),
                trend = ProximityEstimate.Trend.Stable,
                descriptor = when (level) {
                    ProximityEstimate.Level.Immediate -> "Stored very-close signal · needs live readings"
                    ProximityEstimate.Level.Near -> "Stored nearby signal · needs live readings"
                    ProximityEstimate.Level.Warm -> "Stored mid-range signal · needs live readings"
                    ProximityEstimate.Level.Far -> "Stored distant signal · needs live readings"
                    ProximityEstimate.Level.Lost -> "Signal not currently reliable"
                },
            )
        },
        lastSeenAt = lastSeenAt,
        lastRssi = lastRssi,
        smoothedRssi = smoothedRssi,
        lastLocation = if (lastLatitude != null && lastLongitude != null && lastLocationAt != null) {
            LocationSnapshot(
                latitude = lastLatitude,
                longitude = lastLongitude,
                accuracyMeters = lastAccuracyMeters,
                observedAt = lastLocationAt,
            )
        } else {
            null
        },
        monitorEnabled = monitorEnabled,
        discoveredServices = services,
        manufacturerDataHex = manufacturerDataHex,
        isNutCandidate = isNutCandidate,
        lastSessionType = lastSessionType?.let(ScanSessionType::valueOf),
        lastOutOfRangeAlertAt = lastOutOfRangeAlertAt,
    )
}

private fun Long?.toCurrentPresence(): TrackerPresence {
    val age = this?.let { System.currentTimeMillis() - it } ?: return TrackerPresence.NotRecent
    return when {
        age < 30_000L -> TrackerPresence.Nearby
        age < 5 * 60_000L -> TrackerPresence.RecentlySeen
        else -> TrackerPresence.NotRecent
    }
}

internal fun TrackerObservationEntity.toDomain(): TrackerObservation = TrackerObservation(
    id = id,
    trackerId = trackerId,
    seenAt = seenAt,
    sessionType = ScanSessionType.valueOf(sessionType),
    rssi = rssi,
    smoothedRssi = smoothedRssi,
    advertisedName = advertisedName,
    resolvedName = resolvedName,
    proximityEstimate = proximityLevel?.let {
        ProximityEstimate(
            level = ProximityEstimate.Level.valueOf(it),
            estimatedDistanceMeters = estimatedDistanceMeters,
            signalPercent = signalPercent ?: 0,
            trend = proximityTrend?.let(ProximityEstimate.Trend::valueOf) ?: ProximityEstimate.Trend.Stable,
            descriptor = proximityDescriptor.orEmpty(),
        )
    },
    locationSnapshot = if (latitude != null && longitude != null && locationObservedAt != null) {
        LocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            observedAt = locationObservedAt,
        )
    } else {
        null
    },
    connected = connected,
    serviceUuids = serviceUuids,
    batteryPercentage = batteryPercent,
)

internal fun BleLogEventEntity.toDomain(): BleLogEvent = BleLogEvent(
    id = id,
    sessionId = sessionId,
    trackerId = trackerId,
    timestamp = timestamp,
    category = BleLogEvent.Category.valueOf(category),
    action = action,
    message = message,
    deviceAddress = deviceAddress,
    serviceUuid = serviceUuid,
    characteristicUuid = characteristicUuid,
    payloadHex = payloadHex,
    resultCode = resultCode,
    rssi = rssi,
)

internal fun serializeGattServiceSummary(service: GattServiceSummary): String = buildString {
    append(service.serviceUuid)
    service.characteristics.forEach { characteristic ->
        append(';')
        append(characteristic.characteristicUuid)
        if (characteristic.properties.isNotEmpty() || characteristic.descriptorUuids.isNotEmpty()) {
            append('#')
            append(characteristic.properties.joinToString(separator = ",") { property -> property.name })
            if (characteristic.descriptorUuids.isNotEmpty()) {
                append('#')
                append(characteristic.descriptorUuids.joinToString(separator = ","))
            }
        }
    }
}

internal fun deserializeGattServiceSummary(serialized: String): GattServiceSummary {
    val parts = serialized.split(";")
    val serviceUuid = parts.firstOrNull().orEmpty()
    val characteristics = parts.drop(1).mapNotNull { characteristicPart ->
        if (characteristicPart.isBlank()) {
            null
        } else {
            val sections = characteristicPart.split("#")
            val characteristicUuid = sections.firstOrNull().orEmpty()
            if (characteristicUuid.isBlank()) {
                null
            } else {
                GattCharacteristicSummary(
                    characteristicUuid = characteristicUuid,
                    properties = sections.getOrNull(1)
                        ?.split(",")
                        ?.mapNotNull(::parseGattCharacteristicProperty)
                        ?.toSet()
                        .orEmpty(),
                    descriptorUuids = sections.getOrNull(2)
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        .orEmpty(),
                )
            }
        }
    }
    return GattServiceSummary(
        serviceUuid = serviceUuid,
        characteristics = characteristics,
    )
}

private fun parseGattCharacteristicProperty(value: String): GattCharacteristicProperty? = when (value) {
    GattCharacteristicProperty.Read.name -> GattCharacteristicProperty.Read
    GattCharacteristicProperty.Write.name -> GattCharacteristicProperty.Write
    GattCharacteristicProperty.WriteNoResponse.name -> GattCharacteristicProperty.WriteNoResponse
    GattCharacteristicProperty.Notify.name -> GattCharacteristicProperty.Notify
    GattCharacteristicProperty.Indicate.name -> GattCharacteristicProperty.Indicate
    else -> null
}
