package com.aloneagle.tracky

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.aloneagle.tracky.data.local.TrackyDatabase
import com.aloneagle.tracky.data.local.entity.AutomationRuleEntity
import com.aloneagle.tracky.data.local.entity.KnownTrackerEntity
import java.io.Closeable
import kotlinx.coroutines.runBlocking

object TestDatabaseSeeder {
    fun clearAndSeed(
        sampleTrackerName: String = "Office Tag",
        includeAutomation: Boolean = false,
    ): Closeable {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TrackyDatabase::class.java, "tracky.db")
            .allowMainThreadQueries()
            .build()
        database.clearAllTables()
        runBlocking {
            database.knownTrackerDao().upsert(
                KnownTrackerEntity(
                    id = "AA:BB:CC:DD:EE:FF",
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    nickname = sampleTrackerName,
                    advertisedName = "Nut Findthing",
                    resolvedName = sampleTrackerName,
                    protocolType = "NutFindthing",
                    capabilities = listOf("ServiceDiscovery", "RingUnconfirmed"),
                    batteryPercent = 77,
                    batteryStatus = "Available",
                    batteryUpdatedAt = 1L,
                    lastSeenAt = System.currentTimeMillis(),
                    lastRssi = -63,
                    smoothedRssi = -61.2,
                    lastLatitude = 45.4642,
                    lastLongitude = 9.19,
                    lastAccuracyMeters = 18f,
                    lastLocationAt = System.currentTimeMillis(),
                    presenceState = "Nearby",
                    connectionState = "Disconnected",
                    monitorEnabled = false,
                    discoveredServices = listOf("0000180f-0000-1000-8000-00805f9b34fb;00002a19-0000-1000-8000-00805f9b34fb"),
                    manufacturerDataHex = "1234:aabb",
                    isNutCandidate = true,
                    lastSessionType = "Manual",
                    lastOutOfRangeAlertAt = null,
                ),
            )
            if (includeAutomation) {
                database.automationRuleDao().upsert(
                    AutomationRuleEntity(
                        id = "test-leave-rule",
                        trackerId = "AA:BB:CC:DD:EE:FF",
                        transition = "Leave",
                        radiusMeters = 8.0,
                        hysteresisMeters = 1.2,
                        minimumSamples = 3,
                        minimumDwellMillis = 2_500L,
                        cooldownMillis = 60_000L,
                        actionType = "notification",
                        actionPayload = "Office tag left the selected range.",
                        actionRecipient = null,
                        enabled = true,
                        stableZone = "Unknown",
                        candidateZone = null,
                        candidateSinceMillis = null,
                        candidateSamples = 0,
                        lastTriggeredAtMillis = null,
                        lastObservationAtMillis = null,
                        createdAtMillis = 1L,
                    ),
                )
            }
        }
        return Closeable { database.close() }
    }

    fun clearDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TrackyDatabase::class.java, "tracky.db")
            .allowMainThreadQueries()
            .build()
        database.clearAllTables()
        database.close()
    }
}
