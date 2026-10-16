package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: DownloadedItemEntity)

    @Query(
        """
        UPDATE downloaded_items 
        SET downloadedBytes = :downloadedBytes, 
            totalBytes = CASE WHEN :totalBytes > 0 THEN :totalBytes ELSE totalBytes END,
            status = :status,
            localFilePath = :localFilePath
        WHERE id = :id
        """
    )
    suspend fun updateDownloadProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        status: String,
        localFilePath: String
    ): Int

    @Query("UPDATE downloaded_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String): Int

    @Query("UPDATE downloaded_items SET status = 'PAUSED' WHERE status = 'DOWNLOADING'")
    suspend fun markAllActiveAsPaused(): Int

    @Query("DELETE FROM downloaded_items WHERE id = :id")
    suspend fun deleteDownloadedItem(id: String)

    @Query("DELETE FROM downloaded_items WHERE status = 'FAILED'")
    suspend fun deleteAllFailedDownloads()

    @Query("DELETE FROM downloaded_items")
    suspend fun deleteAllDownloads()

    @Query("SELECT * FROM downloaded_items WHERE id = :id")
    fun getDownloadedItemById(id: String): Flow<DownloadedItemEntity?>

    @Query("SELECT * FROM downloaded_items WHERE id = :id")
    suspend fun getDownloadedItemByIdOnce(id: String): DownloadedItemEntity?

    @Query("SELECT * FROM downloaded_items WHERE localFilePath = :path LIMIT 1")
    suspend fun getDownloadedItemByPath(path: String): DownloadedItemEntity?

    @Query("SELECT * FROM downloaded_items WHERE localFilePath = :path LIMIT 1")
    fun getDownloadedItemByPathFlow(path: String): Flow<DownloadedItemEntity?>

    @Query("SELECT * FROM downloaded_items WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun getAllCompletedDownloads(): Flow<List<DownloadedItemEntity>>

    @Query("SELECT * FROM downloaded_items ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedItemEntity>>

    @Query("SELECT * FROM downloaded_items WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getDownloadsByStatusOnce(status: String): List<DownloadedItemEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_items WHERE id = :id AND status = 'COMPLETED')")
    fun isItemDownloaded(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_items WHERE id = :id AND status = 'COMPLETED')")
    suspend fun isItemDownloadedOnce(id: String): Boolean
}
