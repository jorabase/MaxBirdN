package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoPlaybackProgressDao {

    @Query("SELECT * FROM video_playback_progress WHERE videoKey = :key LIMIT 1")
    suspend fun getProgress(key: String): VideoPlaybackProgressEntity?

    @Query("SELECT * FROM video_playback_progress WHERE videoKey = :key LIMIT 1")
    fun getProgressFlow(key: String): Flow<VideoPlaybackProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(entity: VideoPlaybackProgressEntity)

    @Query("DELETE FROM video_playback_progress WHERE videoKey = :key")
    suspend fun deleteProgress(key: String)

    @Query("SELECT * FROM video_playback_progress ORDER BY lastWatchedTimestamp DESC")
    fun getAllProgress(): Flow<List<VideoPlaybackProgressEntity>>
}
