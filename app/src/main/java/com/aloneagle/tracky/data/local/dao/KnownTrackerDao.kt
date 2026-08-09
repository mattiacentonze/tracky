package com.aloneagle.tracky.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aloneagle.tracky.data.local.entity.KnownTrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownTrackerDao {
    @Query("SELECT * FROM known_trackers ORDER BY COALESCE(lastSeenAt, 0) DESC, deviceAddress ASC")
    fun observeAll(): Flow<List<KnownTrackerEntity>>

    @Query("SELECT * FROM known_trackers WHERE id = :id")
    fun observeById(id: String): Flow<KnownTrackerEntity?>

    @Query("SELECT * FROM known_trackers WHERE id = :id")
    suspend fun getById(id: String): KnownTrackerEntity?

    @Query("SELECT * FROM known_trackers WHERE monitorEnabled = 1 ORDER BY COALESCE(lastSeenAt, 0) DESC")
    suspend fun getMonitoredTrackers(): List<KnownTrackerEntity>

    @Upsert
    suspend fun upsert(entity: KnownTrackerEntity)

    @Query("UPDATE known_trackers SET nickname = :nickname WHERE id = :id")
    suspend fun updateNickname(id: String, nickname: String)

    @Query("UPDATE known_trackers SET monitorEnabled = :enabled WHERE id = :id")
    suspend fun updateMonitorEnabled(id: String, enabled: Boolean)

    @Query("UPDATE known_trackers SET lastOutOfRangeAlertAt = :timestamp WHERE id = :id")
    suspend fun updateOutOfRangeAlertAt(id: String, timestamp: Long)
}
