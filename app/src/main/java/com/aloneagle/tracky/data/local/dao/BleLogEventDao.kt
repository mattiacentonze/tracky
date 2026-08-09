package com.aloneagle.tracky.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aloneagle.tracky.data.local.entity.BleLogEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BleLogEventDao {
    @Insert
    suspend fun insert(entity: BleLogEventEntity)

    @Query("SELECT * FROM ble_log_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BleLogEventEntity>>

    @Query(
        """
        SELECT * FROM ble_log_events
        WHERE trackerId = :trackerId
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun observeForTracker(trackerId: String, limit: Int): Flow<List<BleLogEventEntity>>

    @Query("SELECT * FROM ble_log_events ORDER BY timestamp ASC")
    suspend fun getAll(): List<BleLogEventEntity>

    @Query("DELETE FROM ble_log_events WHERE id NOT IN (SELECT id FROM ble_log_events ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun prune(limit: Int)
}
