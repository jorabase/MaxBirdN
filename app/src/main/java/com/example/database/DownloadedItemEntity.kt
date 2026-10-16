package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_items")
data class DownloadedItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val fileType: String, // "VIDEO" or "PDF"
    val remoteUrl: String,
    val localFilePath: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = STATUS_DOWNLOADING, // "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val FILE_TYPE_VIDEO = "VIDEO"
        const val FILE_TYPE_PDF = "PDF"

        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }

    val isVideo: Boolean
        get() = fileType.equals(FILE_TYPE_VIDEO, ignoreCase = true)

    val isPdf: Boolean
        get() = fileType.equals(FILE_TYPE_PDF, ignoreCase = true)

    val isCompleted: Boolean
        get() = status.equals(STATUS_COMPLETED, ignoreCase = true)

    val isDownloading: Boolean
        get() = status.equals(STATUS_DOWNLOADING, ignoreCase = true)

    val isPaused: Boolean
        get() = status.equals(STATUS_PAUSED, ignoreCase = true)

    val isFailed: Boolean
        get() = status.equals(STATUS_FAILED, ignoreCase = true)

    val isActive: Boolean
        get() = isDownloading || isPaused

    val safeDownloadedBytes: Long
        get() = downloadedBytes.coerceAtLeast(0L)

    val safeTotalBytes: Long
        get() = totalBytes.coerceAtLeast(0L)

    val remainingBytes: Long
        get() = (safeTotalBytes - safeDownloadedBytes).coerceAtLeast(0L)

    val progressFraction: Float
        get() = when {
            isCompleted -> 1f
            safeTotalBytes > 0L -> (safeDownloadedBytes.toFloat() / safeTotalBytes.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val progressPercent: Int
        get() = when {
            isCompleted -> 100
            else -> (progressFraction * 100f).toInt().coerceIn(0, 100)
        }
}
