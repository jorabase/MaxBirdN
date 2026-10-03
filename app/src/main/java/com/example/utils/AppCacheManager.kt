package com.example.utils

import android.content.Context
import android.util.Log
import com.example.player.ShikhoPlayerManager
import java.io.File

object AppCacheManager {
    private const val TAG = "AppCacheManager"

    /**
     * Calculates the total size of all cache directories in bytes.
     */
    fun getTotalCacheSizeBytes(context: Context): Long {
        var totalBytes = 0L
        try {
            context.cacheDir?.let { totalBytes += getFolderSize(it) }
            context.externalCacheDir?.let { totalBytes += getFolderSize(it) }
            context.codeCacheDir?.let { totalBytes += getFolderSize(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size: ${e.message}")
        }
        return totalBytes
    }

    /**
     * Formats total cache size in human-friendly Bengali string (e.g. "12.4 MB" or "0.0 MB").
     */
    fun getFormattedCacheSize(context: Context): String {
        val bytes = getTotalCacheSizeBytes(context)
        return when {
            bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024f * 1024f))
            bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes.toFloat() / 1024f)
            else -> "0.0 MB"
        }
    }

    /**
     * Completely purges all app caches:
     * - ExoPlayer media stream cache
     * - In-app PDF preview cache
     * - Image/Network caches in cacheDir and externalCacheDir
     */
    fun clearAllAppCache(context: Context): Boolean {
        return try {
            // 1. Release ExoPlayer SimpleCache first
            ShikhoPlayerManager.clearMediaCache(context)

            // 2. Wipe standard cacheDir contents
            context.cacheDir?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    try {
                        file.deleteRecursively()
                    } catch (_: Exception) {}
                }
            }

            // 3. Wipe externalCacheDir if present
            context.externalCacheDir?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    try {
                        file.deleteRecursively()
                    } catch (_: Exception) {}
                }
            }

            Log.i(TAG, "✅ All app caches cleared successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing app cache: ${e.message}")
            false
        }
    }

    /**
     * Auto-trims cache if it grows beyond a reasonable threshold (35MB).
     */
    fun autoTrimExcessiveCache(context: Context) {
        try {
            val totalBytes = getTotalCacheSizeBytes(context)
            if (totalBytes > 35L * 1024L * 1024L) {
                Log.w(TAG, "Cache size exceeded threshold (${totalBytes / (1024 * 1024)}MB). Auto-trimming...")
                clearAllAppCache(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-trim cache error: ${e.message}")
        }
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            dir.walkTopDown().filter { it.isFile }.forEach {
                size += it.length()
            }
        } catch (_: Exception) {}
        return size
    }
}
