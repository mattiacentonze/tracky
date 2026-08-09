package com.aloneagle.tracky.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aloneagle.tracky.data.local.dao.AutomationRuleDao
import com.aloneagle.tracky.data.local.dao.BleLogEventDao
import com.aloneagle.tracky.data.local.dao.KnownTrackerDao
import com.aloneagle.tracky.data.local.dao.TrackerObservationDao
import com.aloneagle.tracky.data.local.entity.BleLogEventEntity
import com.aloneagle.tracky.data.local.entity.AutomationRuleEntity
import com.aloneagle.tracky.data.local.entity.KnownTrackerEntity
import com.aloneagle.tracky.data.local.entity.TrackerObservationEntity

@Database(
    entities = [
        KnownTrackerEntity::class,
        TrackerObservationEntity::class,
        BleLogEventEntity::class,
        AutomationRuleEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(TrackyConverters::class)
abstract class TrackyDatabase : RoomDatabase() {
    abstract fun knownTrackerDao(): KnownTrackerDao
    abstract fun trackerObservationDao(): TrackerObservationDao
    abstract fun bleLogEventDao(): BleLogEventDao
    abstract fun automationRuleDao(): AutomationRuleDao
}
