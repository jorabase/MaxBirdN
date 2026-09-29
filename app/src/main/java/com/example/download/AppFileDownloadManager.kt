package com.example.download

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.database.AppDatabase
import com.example.database.DownloadedItemDao
import com.example.database.DownloadedItemEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadQualityOption(
    val id: String, // "720p", "480p", "360p"
    val labelBangla: String,
    val descriptionBangla: String,
    val estimatedSizeBangla: String,
    val targetM3u8Url: String,
    val isRecommended: Boolean = false
)

class AppFileDownloadManager private constructor(
    private val context: Context,
    private val downloadedItemDao: DownloadedItemDao
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(10, 10, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val downloadVaultDir: File by lazy {
        File(context.filesDir, "secured_media_vault").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    companion object {
        private const val TAG = "AppFileDownloadManager"
        private const val BUFFER_SIZE = 32768 // 32KB high performance buffer

        @Volatile
        private var INSTANCE: AppFileDownloadManager? = null

        fun getInstance(context: Context): AppFileDownloadManager {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val instance = AppFileDownloadManager(
                    context = context.applicationContext,
                    downloadedItemDao = db.downloadedItemDao()
                )
                INSTANCE = instance
                instance
            }
        }

        /**
         * Resolves the target resolution M3U8 URL given any Shikho master or variant URL.
         */
        fun resolveQualityUrl(baseUrl: String, quality: String): String {
            if (baseUrl.isBlank()) return baseUrl

            if (baseUrl.contains("/playlist.m3u8")) {
                return baseUrl.replace("/playlist.m3u8", "/$quality/media.m3u8")
            }

            if (baseUrl.contains(Regex("/(1080p|720p|480p|360p|240p|144p)/(video|media)\\.m3u8"))) {
                return baseUrl.replace(Regex("/(1080p|720p|480p|360p|240p|144p)/(video|media)\\.m3u8"), "/$quality/media.m3u8")
            }

            if (baseUrl.contains(Regex("/stream_\\d+/stream\\.m3u8"))) {
                val streamIndex = when (quality) {
                    "1080p" -> "stream_0"
                    "720p" -> "stream_1"
                    "480p" -> "stream_2"
                    "360p" -> "stream_3"
                    "240p" -> "stream_4"
                    else -> "stream_2"
                }
                return baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/$streamIndex/stream.m3u8")
            }

            return baseUrl
        }

        /**
         * Returns download quality options available dynamically for the video URL.
         */
        fun getAvailableDownloadQualities(inputUrl: String): List<DownloadQualityOption> {
            if (inputUrl.isBlank()) return emptyList()

            val url1080 = resolveQualityUrl(inputUrl, "1080p")
            val url720 = resolveQualityUrl(inputUrl, "720p")
            val url480 = resolveQualityUrl(inputUrl, "480p")
            val url360 = resolveQualityUrl(inputUrl, "360p")
            val url240 = resolveQualityUrl(inputUrl, "240p")

            if (url1080 == url720 && url720 == url480 && url480 == url360 && url360 == inputUrl) {
                return listOf(
                    DownloadQualityOption(
                        id = "original",
                        labelBangla = "মূল ভিডিও কোয়ালিটি",
                        descriptionBangla = "ভিডিওটির মূল স্ট্রিম অনুযায়ী ডাউনলোড হবে",
                        estimatedSizeBangla = "স্ট্রিম সাইজ অনুযায়ী",
                        targetM3u8Url = inputUrl,
                        isRecommended = true
                    )
                )
            }

            return listOf(
                DownloadQualityOption(
                    id = "1080p",
                    labelBangla = "1080p (ফুল এইচডি)",
                    descriptionBangla = "সর্বোচ্চ মান ও সেরা স্পষ্টতা",
                    estimatedSizeBangla = "গণনা করা হচ্ছে...",
                    targetM3u8Url = url1080,
                    isRecommended = false
                ),
                DownloadQualityOption(
                    id = "720p",
                    labelBangla = "720p (এইচডি)",
                    descriptionBangla = "উচ্চ মান ও সবচেয়ে স্পষ্ট ভিডিও",
                    estimatedSizeBangla = "গণনা করা হচ্ছে...",
                    targetM3u8Url = url720,
                    isRecommended = false
                ),
                DownloadQualityOption(
                    id = "480p",
                    labelBangla = "480p (মাঝারি - সেরা পছন্দ)",
                    descriptionBangla = "সাশ্রয়ী ইন্টারনেট ও স্পষ্ট ভিডিও",
                    estimatedSizeBangla = "গণনা করা হচ্ছে...",
                    targetM3u8Url = url480,
                    isRecommended = true
                ),
                DownloadQualityOption(
                    id = "360p",
                    labelBangla = "360p (কম ডাটা)",
                    descriptionBangla = "দ্রুত ডাউনলোড ও কম ডাটা খরচ",
                    estimatedSizeBangla = "গণনা করা হচ্ছে...",
                    targetM3u8Url = url360,
                    isRecommended = false
                ),
                DownloadQualityOption(
                    id = "240p",
                    labelBangla = "240p (অতি কম ডাটা)",
                    descriptionBangla = "দুর্বল ইন্টারনেটে দ্রুত ডাউনলোড",
                    estimatedSizeBangla = "গণনা করা হচ্ছে...",
                    targetM3u8Url = url240,
                    isRecommended = false
                )
            )
        }
    }

    /**
     * Fetches and parses the master playlist to get the actual available resolutions and real target URIs.
     */
    suspend fun getRealAvailableDownloadQualities(inputUrl: String): List<DownloadQualityOption> = withContext(Dispatchers.IO) {
        if (inputUrl.isBlank()) return@withContext emptyList()

        try {
            val request = Request.Builder()
                .url(inputUrl)
                .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                .addHeader("referer", "https://shikho.com/")
                .addHeader("Referer", "https://shikho.com/")
                .addHeader("Origin", "https://shikho.com")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return@withContext getAvailableDownloadQualities(inputUrl)
            }

            val playlistContent = response.body!!.string()
            if (!playlistContent.contains("#EXT-X-STREAM-INF")) {
                return@withContext listOf(
                    DownloadQualityOption(
                        id = "original",
                        labelBangla = "মূল ভিডিও কোয়ালিটি",
                        descriptionBangla = "ভিডিওটির মূল স্ট্রিম অনুযায়ী ডাউনলোড হবে",
                        estimatedSizeBangla = "স্ট্রিম সাইজ অনুযায়ী",
                        targetM3u8Url = inputUrl,
                        isRecommended = true
                    )
                )
            }

            val lines = playlistContent.lines()
            val parsedOptions = mutableListOf<DownloadQualityOption>()

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
                    val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""

                    if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                        val fullTargetUrl = resolveUrl(inputUrl, nextLine)

                        val qualityId = if (resMatch != null) {
                            val width = resMatch.groupValues[1].toInt()
                            val height = resMatch.groupValues[2].toInt()
                            when {
                                height >= 1080 || width >= 1920 -> "1080p"
                                height >= 720 || width >= 1280 -> "720p"
                                height >= 480 || width >= 840 -> "480p"
                                height >= 360 || width >= 600 -> "360p"
                                else -> "240p"
                            }
                        } else {
                            val nextLineLower = nextLine.lowercase()
                            when {
                                nextLineLower.contains("1080p") || nextLineLower.contains("stream_0") -> "1080p"
                                nextLineLower.contains("720p") || nextLineLower.contains("stream_1") -> "720p"
                                nextLineLower.contains("480p") || nextLineLower.contains("stream_2") -> "480p"
                                nextLineLower.contains("360p") || nextLineLower.contains("stream_3") -> "360p"
                                nextLineLower.contains("240p") || nextLineLower.contains("stream_4") -> "240p"
                                else -> "480p"
                            }
                        }

                        val (label, desc) = when (qualityId) {
                            "1080p" -> "1080p (ফুল এইচডি)" to "সর্বোচ্চ মান ও সেরা স্পষ্টতা"
                            "720p" -> "720p (এইচডি)" to "উচ্চ মান ও সবচেয়ে স্পষ্ট ভিডিও"
                            "480p" -> "480p (মাঝারি - সেরা পছন্দ)" to "সাশ্রয়ী ইন্টারনেট ও স্পষ্ট ভিডিও"
                            "360p" -> "360p (কম ডাটা)" to "দ্রুত ডাউনলোড ও কম ডাটা খরচ"
                            else -> "240p (অতি কম ডাটা)" to "দুর্বল ইন্টারনেটে দ্রুত ডাউনলোড"
                        }

                        parsedOptions.add(
                            DownloadQualityOption(
                                id = qualityId,
                                labelBangla = label,
                                descriptionBangla = desc,
                                estimatedSizeBangla = "গণনা করা হচ্ছে...",
                                targetM3u8Url = fullTargetUrl,
                                isRecommended = false
                            )
                        )
                    }
                }
            }

            if (parsedOptions.isEmpty()) {
                return@withContext getAvailableDownloadQualities(inputUrl)
            }

            val distinctOptions = parsedOptions.distinctBy { it.id }

            val has480p = distinctOptions.any { it.id == "480p" }
            val has360p = distinctOptions.any { it.id == "360p" }
            val recommendedId = when {
                has480p -> "480p"
                has360p -> "360p"
                else -> distinctOptions.first().id
            }

            return@withContext distinctOptions.map { option ->
                option.copy(isRecommended = option.id == recommendedId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching available qualities dynamically: ${e.message}", e)
            return@withContext getAvailableDownloadQualities(inputUrl)
        }
    }

    /**
     * Calculates the real file sizes based on the variant's total duration and bitrate.
     */
    suspend fun calculateRealQualitySizes(inputUrl: String, options: List<DownloadQualityOption>): List<DownloadQualityOption> = withContext(Dispatchers.IO) {
        if (options.isEmpty()) return@withContext options

        try {
            val masterRequest = Request.Builder()
                .url(inputUrl)
                .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                .addHeader("referer", "https://shikho.com/")
                .addHeader("Referer", "https://shikho.com/")
                .build()

            val masterResponse = httpClient.newCall(masterRequest).execute()
            val qualityBandwidths = mutableMapOf<String, Long>()

            if (masterResponse.isSuccessful && masterResponse.body != null) {
                val masterContent = masterResponse.body!!.string()
                val lines = masterContent.lines()

                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        val bandwidthMatch = Regex("(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\\d+)").find(line)
                        val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
                        val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""

                        val bandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull()
                        if (bandwidth != null) {
                            val qualityId = if (resMatch != null) {
                                val width = resMatch.groupValues[1].toInt()
                                val height = resMatch.groupValues[2].toInt()
                                when {
                                    height >= 1080 || width >= 1920 -> "1080p"
                                    height >= 720 || width >= 1280 -> "720p"
                                    height >= 480 || width >= 840 -> "480p"
                                    height >= 360 || width >= 600 -> "360p"
                                    else -> "240p"
                                }
                            } else {
                                val nextLineLower = nextLine.lowercase()
                                when {
                                    nextLineLower.contains("1080p") || nextLineLower.contains("stream_0") -> "1080p"
                                    nextLineLower.contains("720p") || nextLineLower.contains("stream_1") -> "720p"
                                    nextLineLower.contains("480p") || nextLineLower.contains("stream_2") -> "480p"
                                    nextLineLower.contains("360p") || nextLineLower.contains("stream_3") -> "360p"
                                    nextLineLower.contains("240p") || nextLineLower.contains("stream_4") -> "240p"
                                    else -> null
                                }
                            }
                            if (qualityId != null) {
                                qualityBandwidths[qualityId] = bandwidth
                            }
                        }
                    }
                }
            }

            val representativeOption = options.firstOrNull() ?: return@withContext options

            val variantRequest = Request.Builder()
                .url(representativeOption.targetM3u8Url)
                .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                .addHeader("referer", "https://shikho.com/")
                .addHeader("Referer", "https://shikho.com/")
                .build()

            val variantResponse = httpClient.newCall(variantRequest).execute()
            if (!variantResponse.isSuccessful || variantResponse.body == null) {
                return@withContext options
            }

            val variantContent = variantResponse.body!!.string()
            var totalDurationSeconds = 0.0
            val extinfRegex = Regex("#EXTINF:([\\d.]+)")
            for (match in extinfRegex.findAll(variantContent)) {
                val duration = match.groupValues[1].toDoubleOrNull()
                if (duration != null) {
                    totalDurationSeconds += duration
                }
            }

            if (totalDurationSeconds <= 0) {
                return@withContext options
            }

            return@withContext options.map { option ->
                val bandwidth = qualityBandwidths[option.id] ?: when (option.id) {
                    "1080p" -> 2200000L
                    "720p" -> 1025000L
                    "480p" -> 811000L
                    "360p" -> 591000L
                    "240p" -> 250000L
                    else -> 500000L
                }

                val calculatedSizeBytes = (bandwidth * totalDurationSeconds) / 8
                val formattedSize = formatFileSize(calculatedSizeBytes.toLong(), inBengali = true)

                option.copy(
                    estimatedSizeBangla = "~$formattedSize"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating real quality sizes: ${e.message}", e)
            return@withContext options
        }
    }

    /**
     * Start downloading a file (HLS Video or PDF) into the secured internal vault directory.
     */
    fun downloadFile(
        id: String,
        title: String,
        subtitle: String? = null,
        fileType: String,
        remoteUrl: String
    ): Job {
        // Cancel existing job if running
        activeJobs[id]?.cancel()

        val job = coroutineScope.launch {
            if (remoteUrl.isBlank()) {
                Log.e(TAG, "Download URL is empty for id: $id")
                return@launch
            }

            // Sanitize filename
            val extension = if (fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true)) "pdf" else "mp4"
            val sanitizedId = id.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            val targetFile = File(downloadVaultDir, "${fileType.lowercase()}_${sanitizedId}.$extension")

            // Check if already completed and file exists
            val existingItem = downloadedItemDao.getDownloadedItemByIdOnce(id)
            if (existingItem != null && existingItem.status == DownloadedItemEntity.STATUS_COMPLETED && targetFile.exists() && targetFile.length() > 0) {
                Log.d(TAG, "Item $id is already downloaded. Skipping duplicate download.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "এই ফাইলটি ইতোমধ্যে অ্যাপে সফলভাবে ডাউনলোড করা হয়েছে", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            var initialEntity = DownloadedItemEntity(
                id = id,
                title = title,
                subtitle = subtitle,
                fileType = fileType,
                remoteUrl = remoteUrl,
                localFilePath = targetFile.absolutePath,
                totalBytes = 0L,
                downloadedBytes = 0L,
                status = DownloadedItemEntity.STATUS_DOWNLOADING,
                createdAt = System.currentTimeMillis()
            )
            downloadedItemDao.insertOrUpdate(initialEntity)

            val isHls = fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true) &&
                    (remoteUrl.contains(".m3u8", ignoreCase = true) || remoteUrl.contains("shikho", ignoreCase = true) || remoteUrl.contains("playlist", ignoreCase = true))

            try {
                if (isHls) {
                    downloadHlsStreamInternal(
                        id = id,
                        title = title,
                        initialEntity = initialEntity,
                        targetFile = targetFile,
                        variantUrl = remoteUrl
                    )
                } else {
                    downloadRegularFileInternal(
                        id = id,
                        title = title,
                        initialEntity = initialEntity,
                        targetFile = targetFile,
                        remoteUrl = remoteUrl
                    )
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Download cancelled for id: $id")
                try {
                    if (targetFile.exists()) targetFile.delete()
                    downloadedItemDao.deleteDownloadedItem(id)
                } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for id: $id: ${e.message}", e)
                try {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                } catch (_: Exception) {}

                downloadedItemDao.insertOrUpdate(
                    initialEntity.copy(
                        status = DownloadedItemEntity.STATUS_FAILED,
                        downloadedBytes = 0L
                    )
                )
            } finally {
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
        return job
    }

    /**
     * Downloads an HLS stream (.m3u8) by downloading all its TS chunks and saving them into a single playable MP4/TS file.
     */
    private suspend fun downloadHlsStreamInternal(
        id: String,
        title: String,
        initialEntity: DownloadedItemEntity,
        targetFile: File,
        variantUrl: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching HLS playlist from: $variantUrl")

        val request = Request.Builder()
            .url(variantUrl)
            .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
            .addHeader("referer", "https://shikho.com/")
            .addHeader("Referer", "https://shikho.com/")
            .addHeader("Origin", "https://shikho.com")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful || response.body == null) {
            throw Exception("Failed to fetch playlist: HTTP ${response.code}")
        }

        val playlistContent = response.body!!.string()
        var effectivePlaylistContent = playlistContent
        var effectiveBaseUrl = variantUrl

        // If it's a master playlist, parse the child stream
        if (playlistContent.contains("#EXT-X-STREAM-INF")) {
            val lines = playlistContent.lines()
            val subUrls = mutableListOf<String>()
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (!nextLine.startsWith("#") && nextLine.isNotBlank()) {
                        subUrls.add(nextLine)
                    }
                }
            }
            if (subUrls.isNotEmpty()) {
                val chosenSubUrl = subUrls.first()
                val fullSubUrl = resolveUrl(variantUrl, chosenSubUrl)
                effectiveBaseUrl = fullSubUrl

                val subRequest = Request.Builder()
                    .url(fullSubUrl)
                    .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                    .addHeader("referer", "https://shikho.com/")
                    .addHeader("Referer", "https://shikho.com/")
                    .addHeader("Origin", "https://shikho.com")
                    .build()

                val subResponse = httpClient.newCall(subRequest).execute()
                if (subResponse.isSuccessful && subResponse.body != null) {
                    effectivePlaylistContent = subResponse.body!!.string()
                }
            }
        }

        // Parse all TS segments
        val segmentUrls = mutableListOf<String>()
        for (line in effectivePlaylistContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val resolved = resolveUrl(effectiveBaseUrl, trimmed)
                segmentUrls.add(resolved)
            }
        }

        if (segmentUrls.isEmpty()) {
            throw Exception("No video segments found in playlist")
        }

        val totalSegments = segmentUrls.size
        Log.d(TAG, "Found $totalSegments TS segments for id: $id. Starting concurrent download with max throughput...")

        if (targetFile.exists() && initialEntity.downloadedBytes == 0L) {
            targetFile.delete()
        }

        var downloadedBytes = 0L
        val outputStream = FileOutputStream(targetFile, true)
        var lastDbUpdateTime = 0L
        var lastSpeedCheckTime = System.currentTimeMillis()
        var bytesSinceLastCheck = 0L
        var currentSpeedMBs = 0f

        val semaphore = kotlinx.coroutines.sync.Semaphore(6)
        val downloadedChunksMap = ConcurrentHashMap<Int, ByteArray>()

        try {
            coroutineScope {
                val downloadJobs = segmentUrls.mapIndexed { idx, segmentUrl ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            ensureActive()
                            val chunkData = downloadSegmentWithRetry(segmentUrl, maxRetries = 5)
                            downloadedChunksMap[idx] = chunkData
                        }
                    }
                }

                var writeIndex = 0
                while (writeIndex < totalSegments) {
                    ensureActive()

                    while (!downloadedChunksMap.containsKey(writeIndex)) {
                        ensureActive()
                        delay(15L)
                    }

                    val chunkData = downloadedChunksMap.remove(writeIndex) ?: break
                    outputStream.write(chunkData)
                    downloadedBytes += chunkData.size
                    bytesSinceLastCheck += chunkData.size
                    writeIndex++

                    val now = System.currentTimeMillis()
                    val timeDiffMs = now - lastSpeedCheckTime
                    if (timeDiffMs >= 500L) {
                        currentSpeedMBs = (bytesSinceLastCheck.toFloat() / (1024f * 1024f)) / (timeDiffMs.toFloat() / 1000f)
                        lastSpeedCheckTime = now
                        bytesSinceLastCheck = 0L
                    }

                    if (now - lastDbUpdateTime > 400L || writeIndex == totalSegments) {
                        lastDbUpdateTime = now
                        val estimatedTotal = ((downloadedBytes.toDouble() / writeIndex) * totalSegments).toLong()
                        val progressPercent = (writeIndex * 100 / totalSegments).coerceIn(0, 100)

                        downloadedItemDao.insertOrUpdate(
                            initialEntity.copy(
                                downloadedBytes = downloadedBytes,
                                totalBytes = if (estimatedTotal > 0) estimatedTotal else downloadedBytes,
                                status = DownloadedItemEntity.STATUS_DOWNLOADING
                            )
                        )

                        AppDownloadNotificationHelper.showDownloadProgressNotification(
                            context = context,
                            id = id,
                            title = title,
                            progressPercent = progressPercent,
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (estimatedTotal > 0) estimatedTotal else downloadedBytes,
                            speedMBs = currentSpeedMBs
                        )
                    }
                }

                downloadJobs.awaitAll()
            }

            outputStream.flush()

            downloadedItemDao.insertOrUpdate(
                initialEntity.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = downloadedBytes,
                    status = DownloadedItemEntity.STATUS_COMPLETED,
                    localFilePath = targetFile.absolutePath
                )
            )

            AppDownloadNotificationHelper.showDownloadCompleteNotification(
                context = context,
                id = id,
                title = title,
                totalBytes = downloadedBytes
            )
            Log.d(TAG, "HLS video download complete! Total size: $downloadedBytes bytes for id: $id")
        } catch (e: CancellationException) {
            AppDownloadNotificationHelper.cancelNotification(context, id)
            throw e
        } catch (e: Exception) {
            AppDownloadNotificationHelper.showDownloadFailedNotification(context, id, title)
            throw e
        } finally {
            try {
                outputStream.close()
            } catch (_: Exception) {}
        }
    }

    private suspend fun downloadSegmentWithRetry(url: String, maxRetries: Int = 3): ByteArray {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                    .addHeader("referer", "https://shikho.com/")
                    .addHeader("Referer", "https://shikho.com/")
                    .addHeader("Origin", "https://shikho.com")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    return response.body!!.bytes()
                } else {
                    throw Exception("HTTP chunk error ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(350L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Failed to download segment chunk after $maxRetries retries")
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        return try {
            if (relativeOrAbsolute.startsWith("/")) {
                val uri = URI(baseUrl)
                "${uri.scheme}://${uri.authority}$relativeOrAbsolute"
            } else {
                val baseWithoutFile = baseUrl.substringBeforeLast('/')
                "$baseWithoutFile/$relativeOrAbsolute"
            }
        } catch (_: Exception) {
            val baseWithoutFile = baseUrl.substringBeforeLast('/')
            "$baseWithoutFile/$relativeOrAbsolute"
        }
    }

    /**
     * Downloads a standard regular file (e.g. PDF).
     */
    private suspend fun downloadRegularFileInternal(
        id: String,
        title: String,
        initialEntity: DownloadedItemEntity,
        targetFile: File,
        remoteUrl: String
    ) = withContext(Dispatchers.IO) {
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = Request.Builder()
            .url(remoteUrl)
            .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
            .addHeader("referer", "https://shikho.com/")
            .addHeader("Referer", "https://shikho.com/")
            .addHeader("Origin", "https://shikho.com")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful || response.body == null) {
            throw Exception("HTTP download failed with code: ${response.code}")
        }

        val body = response.body!!
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) contentLength else 0L

        downloadedItemDao.insertOrUpdate(initialEntity.copy(totalBytes = totalBytes))

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var downloadedBytes = 0L
            var lastDbUpdateTime = System.currentTimeMillis()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                ensureActive()
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastDbUpdateTime > 350L || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                    lastDbUpdateTime = now
                    val currentTotal = if (totalBytes > 0) totalBytes else downloadedBytes
                    downloadedItemDao.insertOrUpdate(
                        initialEntity.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = currentTotal,
                            status = DownloadedItemEntity.STATUS_DOWNLOADING
                        )
                    )
                }
            }

            outputStream.flush()

            val finalTotal = if (totalBytes > 0) totalBytes else downloadedBytes
            downloadedItemDao.insertOrUpdate(
                initialEntity.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = finalTotal,
                    status = DownloadedItemEntity.STATUS_COMPLETED,
                    localFilePath = targetFile.absolutePath
                )
            )
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Pause an active download.
     */
    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null) {
                    downloadedItemDao.insertOrUpdate(
                        item.copy(status = DownloadedItemEntity.STATUS_PAUSED)
                    )
                    AppDownloadNotificationHelper.showDownloadProgressNotification(
                        context = context,
                        id = id,
                        title = item.title,
                        progressPercent = item.progressPercent,
                        downloadedBytes = item.downloadedBytes,
                        totalBytes = item.totalBytes,
                        speedMBs = 0f,
                        isPaused = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing download $id: ${e.message}")
            }
        }
    }

    /**
     * Resume a paused or failed download.
     */
    fun resumeDownload(id: String) {
        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null) {
                    downloadFile(
                        id = item.id,
                        title = item.title,
                        subtitle = item.subtitle,
                        fileType = item.fileType,
                        remoteUrl = item.remoteUrl
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming download $id: ${e.message}")
            }
        }
    }

    /**
     * Cancel an active download.
     */
    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null && item.status == DownloadedItemEntity.STATUS_DOWNLOADING) {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    downloadedItemDao.deleteDownloadedItem(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling download $id: ${e.message}")
            }
        }
    }

    /**
     * Delete a downloaded file completely from vault and database.
     */
    fun deleteDownloadedFile(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null) {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                downloadedItemDao.deleteDownloadedItem(id)
                Log.d(TAG, "Deleted downloaded item: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting downloaded file: $id: ${e.message}")
            }
        }
    }

    /**
     * Reactive flow check if item is completed downloaded.
     */
    fun isItemDownloaded(id: String): Flow<Boolean> {
        return downloadedItemDao.isItemDownloaded(id)
    }

    /**
     * Single check if item is completed downloaded and file exists.
     */
    suspend fun isItemDownloadedOnce(id: String): Boolean {
        val downloaded = downloadedItemDao.isItemDownloadedOnce(id)
        if (!downloaded) return false
        val item = downloadedItemDao.getDownloadedItemByIdOnce(id) ?: return false
        val file = File(item.localFilePath)
        return file.exists() && file.length() > 0
    }

    fun getDownloadedItemById(id: String): Flow<DownloadedItemEntity?> {
        return downloadedItemDao.getDownloadedItemById(id)
    }

    fun getDownloadedItemByPath(path: String): Flow<DownloadedItemEntity?> {
        return downloadedItemDao.getDownloadedItemByPathFlow(path)
    }

    suspend fun getDownloadedItemByPathOnce(path: String): DownloadedItemEntity? {
        return downloadedItemDao.getDownloadedItemByPath(path)
    }

    suspend fun getDownloadedItemByIdOnce(id: String): DownloadedItemEntity? {
        val item = downloadedItemDao.getDownloadedItemByIdOnce(id) ?: return null
        val file = File(item.localFilePath)
        if (item.status == DownloadedItemEntity.STATUS_COMPLETED && (!file.exists() || file.length() == 0L)) {
            // File was removed from storage somehow, cleanup
            downloadedItemDao.deleteDownloadedItem(id)
            return null
        }
        return item
    }

    fun getAllCompletedDownloads(): Flow<List<DownloadedItemEntity>> {
        return downloadedItemDao.getAllCompletedDownloads()
    }

    fun getAllDownloads(): Flow<List<DownloadedItemEntity>> {
        return downloadedItemDao.getAllDownloads()
    }

    /**
     * Gets existing local file if downloaded and valid.
     */
    fun getLocalFile(item: DownloadedItemEntity): File? {
        val file = File(item.localFilePath)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Helper to format bytes into readable MB/KB string with Bengali digits.
     */
    fun formatFileSize(bytes: Long, inBengali: Boolean = true): String {
        if (bytes <= 0) return if (inBengali) "০ মেগাবাইট" else "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        val formatted = when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", kb)
        }

        return if (inBengali) {
            val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
            var res = formatted
            for (i in 0..9) {
                res = res.replace('0' + i, bnDigits[i])
            }
            res.replace("GB", "জিবি").replace("MB", "মেগাবাইট").replace("KB", "কেবি")
        } else {
            formatted
        }
    }
}
