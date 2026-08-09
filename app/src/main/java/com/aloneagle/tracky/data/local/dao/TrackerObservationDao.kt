package com.aloneagle.tracky.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aloneagle.tracky.data.local.entity.TrackerObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerObservationDao {
    @Insert
    suspend fun insert(entity: TrackerObservationEntity)

    @Query(
        """
        SELECT * FROM tracker_observations
        WHERE trackerId = :trackerId
        ORDER BY seenAt DESC
        LIMIT :limit
        """,
    )
    fun observeByTrackerId(trackerId: String, limit: Int): Flow<List<TrackerObservationEntity>>

    @Query("DELETE FROM tracker_observations WHERE id NOT IN (SELECT id FROM tracker_observations ORDER BY seenAt DESC LIMIT :limit)")
    suspend fun prune(limit: Int)
}
