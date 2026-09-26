package com.example.player

import android.content.Context
import android.content.SharedPreferences
import com.example.database.AppDatabase
import com.example.database.VideoPlaybackProgressEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Robust Video Playback Resume & Progress Manager.
 * Persists watch progress across app restarts and course syllabus changes.
 * Uses a dual-layer strategy:
 * 1. Synchronous SharedPreferences for instant zero-latency seek upon opening.
 * 2. Room Database for structured persistence across sessions, courses, and devices.
 */
class VideoProgressManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shikho_video_progress_cache", Context.MODE_PRIVATE)
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ProgressInfo(
        val positionMs: Long,
        val durationMs: Long,
        val isCompleted: Boolean,
        val timestamp: Long
    ) {
        val percentage: Float
            get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        val isEligibleForResume: Boolean
            get() = positionMs > 5_000L && (durationMs <= 0 || positionMs < (durationMs - 10_000L)) && !isCompleted
    }

    /**
     * Generates a stable, unique key for a video regardless of whether it's streaming, offline, or renamed.
     */
    fun generateVideoKey(
        lessonId: String?,
        contentId: String? = null,
        remoteUrl: String? = null,
        title: String? = null
    ): String {
        val cleanLessonId = lessonId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (cleanLessonId != null) return "lesson_$cleanLessonId"

        val cleanContentId = contentId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (cleanContentId != null) return "content_$cleanContentId"

        val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        // Strip query params so temporary tokens or signatures don't break playback continuity
        val cleanUrl = remoteUrl?.trim()?.substringBefore("?")?.takeIf { it.isNotBlank() && it != "null" }

        if (cleanTitle != null && cleanUrl != null) {
            return "vid_" + md5("${cleanTitle}_${cleanUrl}")
        }
        if (cleanTitle != null) {
            return "title_" + md5(cleanTitle)
        }
        if (cleanUrl != null) {
            return "url_" + md5(cleanUrl)
        }
        val seed = "${title?.trim().orEmpty()}_${remoteUrl?.trim().orEmpty()}"
        return "hash_" + md5(seed)
    }

    /**
     * Synchronously returns cached progress for instant zero-delay seek when opening the video.
     */
    fun getCachedProgress(videoKey: String): ProgressInfo? {
        val pos = prefs.getLong("pos_$videoKey", -1L)
        if (pos <= 0L) return null

        val dur = prefs.getLong("dur_$videoKey", 0L)
        val completed = prefs.getBoolean("done_$videoKey", false)
        val time = prefs.getLong("time_$videoKey", 0L)

        return ProgressInfo(
            positionMs = pos,
            durationMs = dur,
            isCompleted = completed,
            timestamp = time
        )
    }

    /**
     * Retrieves playback progress, falling back to Room database if cache is cold.
     */
    suspend fun getProgress(videoKey: String): ProgressInfo? {
        val cached = getCachedProgress(videoKey)
        if (cached != null) return cached

        return try {
            val entity = database.videoPlaybackProgressDao().getProgress(videoKey) ?: return null
            ProgressInfo(
                positionMs = entity.lastPositionMs,
                durationMs = entity.totalDurationMs,
                isCompleted = entity.isCompleted,
                timestamp = entity.lastWatchedTimestamp
            ).also { info ->
                prefs.edit()
                    .putLong("pos_$videoKey", info.positionMs)
                    .putLong("dur_$videoKey", info.durationMs)
                    .putBoolean("done_$videoKey", info.isCompleted)
                    .putLong("time_$videoKey", info.timestamp)
                    .apply()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Flow of progress from Room database.
     */
    fun getProgressFlow(videoKey: String): Flow<VideoPlaybackProgressEntity?> {
        return database.videoPlaybackProgressDao().getProgressFlow(videoKey)
    }

    /**
     * Saves playback position. Called periodically during playback and when pausing/leaving.
     */
    fun saveProgress(
        videoKey: String,
        lessonId: String? = null,
        title: String = "",
        subjectName: String? = null,
        courseId: String? = null,
        positionMs: Long,
        durationMs: Long
    ) {
        if (videoKey.isBlank() || positionMs < 2000L) return

        val isCompleted = durationMs > 0 && (positionMs >= (durationMs * 0.80f) || (durationMs - positionMs) < 15_000L)
        val now = System.currentTimeMillis()

        // 1. Fast synchronous save
        prefs.edit()
            .putLong("pos_$videoKey", positionMs)
            .putLong("dur_$videoKey", durationMs)
            .putBoolean("done_$videoKey", isCompleted)
            .putLong("time_$videoKey", now)
            .apply()

        // 2. Persistent Room save (survives course switching, cache clearing)
        scope.launch {
            try {
                val percentage = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                val entity = VideoPlaybackProgressEntity(
                    videoKey = videoKey,
                    lessonId = lessonId,
                    title = title,
                    subjectName = subjectName,
                    courseId = courseId,
                    lastPositionMs = positionMs,
                    totalDurationMs = durationMs,
                    percentage = percentage,
                    isCompleted = isCompleted,
                    lastWatchedTimestamp = now
                )
                database.videoPlaybackProgressDao().saveProgress(entity)

                if (isCompleted && !lessonId.isNullOrBlank()) {
                    database.completedItemDao().markCompleted(
                        com.example.database.CompletedItemEntity(
                            itemId = lessonId,
                            itemType = "LESSON",
                            title = title,
                            subjectId = subjectName ?: "",
                            programId = courseId ?: "",
                            completedAt = now
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Resets video progress back to beginning (0ms).
     */
    fun resetProgress(videoKey: String) {
        prefs.edit()
            .remove("pos_$videoKey")
            .remove("dur_$videoKey")
            .remove("done_$videoKey")
            .remove("time_$videoKey")
            .apply()

        scope.launch {
            try {
                database.videoPlaybackProgressDao().deleteProgress(videoKey)
            } catch (_: Exception) {}
        }
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString().replace("-", "n")
        }
    }

    companion object {
        @Volatile
        private var instance: VideoProgressManager? = null

        fun getInstance(context: Context): VideoProgressManager {
            return instance ?: synchronized(this) {
                instance ?: VideoProgressManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
