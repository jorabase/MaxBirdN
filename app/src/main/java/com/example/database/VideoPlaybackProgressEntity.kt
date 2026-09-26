package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_playback_progress")
data class VideoPlaybackProgressEntity(
    @PrimaryKey
    val videoKey: String,
    val lessonId: String? = null,
    val title: String = "",
    val subjectName: String? = null,
    val courseId: String? = null,
    val lastPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val percentage: Float = 0f,
    val isCompleted: Boolean = false,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)
