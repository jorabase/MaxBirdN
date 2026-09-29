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
    val status: String = STATUS_DOWNLOADING, // "DOWNLOADING", "COMPLETED", "FAILED"
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

    val progressFraction: Float
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}
