package com.example.download

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.database.AppDatabase
import com.example.database.DownloadedItemDao
import com.example.database.DownloadedItemEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DownloadQualityOption(
    val id: String, // "1080p", "720p", "480p", "360p", "240p", "original"
    val labelBangla: String,
    val descriptionBangla: String,
    val estimatedSizeBangla: String,
    val targetM3u8Url: String,
    val isRecommended: Boolean = false
)

data class DownloadChunkTask(
    val url: String,
    val destinationFile: File
)

class AppFileDownloadManager private constructor(
    private val context: Context,
    private val downloadedItemDao: DownloadedItemDao
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()

    private val _activeSpeedsFlow = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeSpeedsFlow: StateFlow<Map<String, Float>> = _activeSpeedsFlow.asStateFlow()

    // High-throughput HTTP client for mobile network saturation without socket leaks
    private val httpClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 64
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(64, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
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

    init {
        // Recover any downloads that were left in DOWNLOADING state when the app process was killed
        coroutineScope.launch {
            try {
                val stuckItems = downloadedItemDao.getDownloadsByStatusOnce(DownloadedItemEntity.STATUS_DOWNLOADING)
                for (item in stuckItems) {
                    if (!activeJobs.containsKey(item.id)) {
                        downloadedItemDao.updateStatus(item.id, DownloadedItemEntity.STATUS_PAUSED)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to recover interrupted downloads on startup: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AppFileDownloadManager"
        private const val BUFFER_SIZE = 131072 // 128KB high-performance buffer
        private const val PARALLEL_WORKERS = 24 // Balanced high-speed concurrent workers for 4G/5G/Wi-Fi
        private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)"
        private const val REFERER_URL = "https://shikho.com/"
        private const val ORIGIN_URL = "https://shikho.com"

        @Volatile
        private var INSTANCE: AppFileDownloadManager? = null

        fun getInstance(context: Context): AppFileDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = AppDatabase.getDatabase(context)
                    AppFileDownloadManager(
                        context = context.applicationContext,
                        downloadedItemDao = db.downloadedItemDao()
                    ).also { INSTANCE = it }
                }
            }
        }

        private fun qualitySortOrder(qualityId: String): Int = when (qualityId.lowercase()) {
            "1080p" -> 1
            "720p" -> 2
            "480p" -> 3
            "360p" -> 4
            "240p" -> 5
            "144p" -> 6
            else -> 0
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
                return baseUrl.replace(
                    Regex("/(1080p|720p|480p|360p|240p|144p)/(video|media)\\.m3u8"),
                    "/$quality/media.m3u8"
                )
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

    private fun Request.Builder.addStandardHeaders(): Request.Builder {
        return this
            .header("User-Agent", USER_AGENT)
            .header("referer", REFERER_URL)
            .header("Referer", REFERER_URL)
            .header("Origin", ORIGIN_URL)
            .header("Connection", "Keep-Alive")
    }

    private fun updateSpeedForId(id: String, speedMBs: Float) {
        _activeSpeedsFlow.update { current ->
            if (speedMBs <= 0f) {
                current - id
            } else {
                current + (id to speedMBs)
            }
        }
    }

    fun getDownloadSpeedMBs(id: String): Float {
        return _activeSpeedsFlow.value[id] ?: 0f
    }

    /**
     * Fetches and parses the master playlist to get the actual available resolutions and real target URIs.
     */
    suspend fun getRealAvailableDownloadQualities(inputUrl: String): List<DownloadQualityOption> = withContext(Dispatchers.IO) {
        if (inputUrl.isBlank()) return@withContext emptyList()

        try {
            val request = Request.Builder()
                .url(inputUrl)
                .addStandardHeaders()
                .build()

            val playlistContent = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            } ?: return@withContext getAvailableDownloadQualities(inputUrl)

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
                    val nextLine = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }?.trim() ?: ""

                    if (nextLine.isNotBlank()) {
                        val fullTargetUrl = resolveUrl(inputUrl, nextLine)

                        val qualityId = if (resMatch != null) {
                            val width = resMatch.groupValues[1].toInt()
                            val height = resMatch.groupValues[2].toInt()
                            val minDim = minOf(width, height)
                            val maxDim = maxOf(width, height)
                            when {
                                minDim >= 1080 || maxDim >= 1920 -> "1080p"
                                minDim >= 720 || maxDim >= 1280 -> "720p"
                                minDim >= 480 || maxDim >= 840 -> "480p"
                                minDim >= 360 || maxDim >= 600 -> "360p"
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

            val distinctOptions = parsedOptions
                .distinctBy { it.id }
                .sortedBy { qualitySortOrder(it.id) }

            val recommendedId = when {
                distinctOptions.any { it.id == "480p" } -> "480p"
                distinctOptions.any { it.id == "360p" } -> "360p"
                distinctOptions.any { it.id == "720p" } -> "720p"
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
    suspend fun calculateRealQualitySizes(
        inputUrl: String,
        options: List<DownloadQualityOption>
    ): List<DownloadQualityOption> = withContext(Dispatchers.IO) {
        if (options.isEmpty()) return@withContext options

        try {
            val masterRequest = Request.Builder()
                .url(inputUrl)
                .addStandardHeaders()
                .build()

            val qualityBandwidths = mutableMapOf<String, Long>()
            var audioBandwidth = 0L

            val masterContent = httpClient.newCall(masterRequest).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }

            if (masterContent != null) {
                val lines = masterContent.lines()
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        val avgBwMatch = Regex("AVERAGE-BANDWIDTH=(\\d+)").find(line)
                        val peakBwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                        val bandwidth = avgBwMatch?.groupValues?.get(1)?.toLongOrNull()
                            ?: peakBwMatch?.groupValues?.get(1)?.toLongOrNull()

                        val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
                        val nextLine = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }?.trim() ?: ""

                        if (bandwidth != null) {
                            val qualityId = if (resMatch != null) {
                                val width = resMatch.groupValues[1].toInt()
                                val height = resMatch.groupValues[2].toInt()
                                val minDim = minOf(width, height)
                                val maxDim = maxOf(width, height)
                                when {
                                    minDim >= 1080 || maxDim >= 1920 -> "1080p"
                                    minDim >= 720 || maxDim >= 1280 -> "720p"
                                    minDim >= 480 || maxDim >= 840 -> "480p"
                                    minDim >= 360 || maxDim >= 600 -> "360p"
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
                    } else if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO", ignoreCase = true)) {
                        audioBandwidth = 96_000L
                    }
                }
            }

            val representativeOption = options.firstOrNull() ?: return@withContext options

            val variantContent = if (
                representativeOption.targetM3u8Url == inputUrl &&
                masterContent != null &&
                masterContent.contains("#EXTINF:")
            ) {
                masterContent
            } else {
                val variantRequest = Request.Builder()
                    .url(representativeOption.targetM3u8Url)
                    .addStandardHeaders()
                    .build()
                httpClient.newCall(variantRequest).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            } ?: return@withContext options

            var totalDurationSeconds = 0.0
            val extinfRegex = Regex("#EXTINF:([\\d.]+)")
            for (match in extinfRegex.findAll(variantContent)) {
                val duration = match.groupValues[1].toDoubleOrNull()
                if (duration != null) {
                    totalDurationSeconds += duration
                }
            }

            if (totalDurationSeconds <= 0.0) {
                return@withContext options
            }

            return@withContext options.map { option ->
                val baseBandwidth = qualityBandwidths[option.id] ?: when (option.id) {
                    "1080p" -> 2_200_000L
                    "720p" -> 1_050_000L
                    "480p" -> 800_000L
                    "360p" -> 580_000L
                    "240p" -> 260_000L
                    else -> 650_000L
                }
                val totalBandwidth = baseBandwidth + audioBandwidth
                val calculatedSizeBytes = ((totalBandwidth * totalDurationSeconds) / 8.0).toLong()
                val formattedSize = formatFileSize(calculatedSizeBytes, inBengali = true)

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
     * Start or resume downloading a file (HLS Video or PDF) into the secured internal vault directory.
     * Supports true resume from partial segments/chunks without losing prior progress.
     */
    fun downloadFile(
        id: String,
        title: String,
        subtitle: String? = null,
        fileType: String,
        remoteUrl: String
    ): Job {
        pausedIds.remove(id)
        cancelledIds.remove(id)
        activeJobs[id]?.cancel()

        val job = coroutineScope.launch {
            if (remoteUrl.isBlank()) {
                Log.e(TAG, "Download URL is empty for id: $id")
                return@launch
            }

            val isHls = fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true) &&
                (remoteUrl.contains(".m3u8", ignoreCase = true) ||
                    remoteUrl.contains("shikho", ignoreCase = true) ||
                    remoteUrl.contains("playlist", ignoreCase = true))

            val sanitizedId = id.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")

            val targetFile = if (isHls) {
                val streamDir = File(downloadVaultDir, "hls_$sanitizedId")
                File(streamDir, "playlist.m3u8")
            } else {
                val extension = if (fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true)) "pdf" else "mp4"
                File(downloadVaultDir, "${fileType.lowercase()}_${sanitizedId}.$extension")
            }

            // Check if already completed and local file is valid
            val existingItem = downloadedItemDao.getDownloadedItemByIdOnce(id)
            if (existingItem != null &&
                existingItem.status == DownloadedItemEntity.STATUS_COMPLETED &&
                targetFile.exists() &&
                targetFile.length() > 0
            ) {
                Log.d(TAG, "Item $id is already downloaded. Skipping duplicate download.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "এই ফাইলটি ইতোমধ্যে ডাউনলোড করা আছে", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Preserve previous progress when resuming from PAUSED or FAILED state
            val initialDownloaded = if (existingItem != null && existingItem.remoteUrl == remoteUrl) {
                existingItem.downloadedBytes.coerceAtLeast(0L)
            } else {
                0L
            }
            val initialTotal = if (existingItem != null && existingItem.remoteUrl == remoteUrl) {
                existingItem.totalBytes.coerceAtLeast(0L)
            } else {
                0L
            }

            val initialEntity = DownloadedItemEntity(
                id = id,
                title = title,
                subtitle = subtitle ?: existingItem?.subtitle,
                fileType = fileType,
                remoteUrl = remoteUrl,
                localFilePath = targetFile.absolutePath,
                totalBytes = initialTotal,
                downloadedBytes = initialDownloaded,
                status = DownloadedItemEntity.STATUS_DOWNLOADING,
                createdAt = existingItem?.createdAt ?: System.currentTimeMillis()
            )
            downloadedItemDao.insertOrUpdate(initialEntity)

            try {
                if (isHls) {
                    val streamDir = targetFile.parentFile ?: File(downloadVaultDir, "hls_$sanitizedId")
                    downloadHlsStreamInternal(
                        id = id,
                        title = title,
                        initialEntity = initialEntity,
                        streamDir = streamDir,
                        remoteUrl = remoteUrl
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
                updateSpeedForId(id, 0f)
                val wasPaused = pausedIds.contains(id)
                val wasCancelled = cancelledIds.remove(id)
                if (wasPaused && !wasCancelled) {
                    Log.i(TAG, "Download paused for id: $id, preserving downloaded segments")
                    val latest = downloadedItemDao.getDownloadedItemByIdOnce(id) ?: initialEntity
                    downloadedItemDao.updateStatus(id, DownloadedItemEntity.STATUS_PAUSED)
                    AppDownloadNotificationHelper.showDownloadProgressNotification(
                        context = context,
                        id = id,
                        title = title,
                        progressPercent = latest.progressPercent,
                        downloadedBytes = latest.downloadedBytes,
                        totalBytes = latest.totalBytes,
                        speedMBs = 0f,
                        isPaused = true
                    )
                } else {
                    Log.w(TAG, "Download cancelled for id: $id, cleaning up partial files")
                    AppDownloadNotificationHelper.cancelNotification(context, id)
                    cleanupFilesForItem(targetFile, isHls)
                    downloadedItemDao.deleteDownloadedItem(id)
                }
                throw e
            } catch (e: Exception) {
                updateSpeedForId(id, 0f)
                Log.e(TAG, "Download failed for id: $id: ${e.message}", e)
                // Keep already downloaded segments/parts on disk so Retry can resume seamlessly!
                val latest = downloadedItemDao.getDownloadedItemByIdOnce(id) ?: initialEntity
                downloadedItemDao.insertOrUpdate(
                    latest.copy(
                        status = DownloadedItemEntity.STATUS_FAILED
                    )
                )
                AppDownloadNotificationHelper.showDownloadFailedNotification(context, id, title)
            } finally {
                updateSpeedForId(id, 0f)
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
        return job
    }

    private fun cleanupFilesForItem(targetFile: File, isHls: Boolean) {
        try {
            if (isHls) {
                targetFile.parentFile?.deleteRecursively()
            } else {
                if (targetFile.exists()) targetFile.delete()
                val parent = targetFile.parentFile
                val prefix = targetFile.name
                parent?.listFiles()?.forEach { file ->
                    if (file.name == "$prefix.tmp" || file.name.startsWith("$prefix.part")) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Downloads an HLS stream (.m3u8) with full Video AND Audio tracks.
     * Supports true Pause & Resume by skipping already-downloaded verified segment files!
     */
    private suspend fun downloadHlsStreamInternal(
        id: String,
        title: String,
        initialEntity: DownloadedItemEntity,
        streamDir: File,
        remoteUrl: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting/Resuming HLS download for: $remoteUrl")

        if (!streamDir.exists()) {
            streamDir.mkdirs()
        } else {
            // Clean up any leftover .tmp files from interrupted segments, keeping completed segments intact!
            streamDir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".tmp")) {
                    try { f.delete() } catch (_: Exception) {}
                }
            }
        }

        var masterPlaylistUrl: String? = null
        var masterPlaylistContent: String? = null
        var videoStreamUrl: String = remoteUrl
        var audioStreamUrl: String? = null

        val initialContent = fetchTextWithRetry(remoteUrl)
            ?: throw Exception("Failed to connect to video stream: $remoteUrl")

        if (initialContent.contains("#EXT-X-STREAM-INF") || initialContent.contains("#EXT-X-MEDIA:TYPE=AUDIO")) {
            masterPlaylistUrl = remoteUrl
            masterPlaylistContent = initialContent
        } else {
            val candidateMasterUrls = mutableListOf<String>()

            if (remoteUrl.contains(Regex("""/(1080p|720p|480p|360p|240p|144p)/(video|media)\.m3u8""", RegexOption.IGNORE_CASE))) {
                candidateMasterUrls.add(
                    remoteUrl.replace(
                        Regex("""/(1080p|720p|480p|360p|240p|144p)/(video|media)\.m3u8""", RegexOption.IGNORE_CASE),
                        "/playlist.m3u8"
                    )
                )
                candidateMasterUrls.add(
                    remoteUrl.replace(
                        Regex("""/(1080p|720p|480p|360p|240p|144p)/(video|media)\.m3u8""", RegexOption.IGNORE_CASE),
                        "/master.m3u8"
                    )
                )
            }

            if (remoteUrl.contains(Regex("""/stream_\d+/stream\.m3u8""", RegexOption.IGNORE_CASE))) {
                candidateMasterUrls.add(remoteUrl.replace(Regex("""/stream_\d+/stream\.m3u8""", RegexOption.IGNORE_CASE), "/stream.m3u8"))
                candidateMasterUrls.add(remoteUrl.replace(Regex("""/stream_\d+/stream\.m3u8""", RegexOption.IGNORE_CASE), "/playlist.m3u8"))
                candidateMasterUrls.add(remoteUrl.replace(Regex("""/stream_\d+/stream\.m3u8""", RegexOption.IGNORE_CASE), "/master.m3u8"))
            }

            val parentBase = remoteUrl.substringBeforeLast('/')
            val grandparentBase = parentBase.substringBeforeLast('/')
            candidateMasterUrls.add("$parentBase/playlist.m3u8")
            candidateMasterUrls.add("$parentBase/master.m3u8")
            candidateMasterUrls.add("$grandparentBase/playlist.m3u8")
            candidateMasterUrls.add("$grandparentBase/master.m3u8")

            for (cUrl in candidateMasterUrls.distinct()) {
                if (cUrl == remoteUrl) continue
                val cContent = fetchTextWithRetry(cUrl, maxRetries = 2)
                if (cContent != null && (cContent.contains("#EXT-X-STREAM-INF") || cContent.contains("#EXT-X-MEDIA:TYPE=AUDIO"))) {
                    masterPlaylistUrl = cUrl
                    masterPlaylistContent = cContent
                    break
                }
            }
        }

        if (masterPlaylistContent != null && masterPlaylistUrl != null) {
            val audioRegex = Regex("""#EXT-X-MEDIA:TYPE=AUDIO[^\n]*?URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
            val audioMatch = audioRegex.find(masterPlaylistContent)
            if (audioMatch != null) {
                val relAudio = audioMatch.groupValues[1]
                audioStreamUrl = resolveUrl(masterPlaylistUrl, relAudio)
            }

            if (remoteUrl == masterPlaylistUrl) {
                val lines = masterPlaylistContent.lines()
                val variants = mutableListOf<String>()
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        val nextLine = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }?.trim()
                        if (!nextLine.isNullOrBlank()) {
                            variants.add(nextLine)
                        }
                    }
                }
                if (variants.isNotEmpty()) {
                    val preferred = variants.firstOrNull {
                        it.contains("720p") || it.contains("480p") || it.contains("stream_1") || it.contains("stream_2")
                    } ?: variants.first()
                    videoStreamUrl = resolveUrl(masterPlaylistUrl, preferred)
                }
            }
        }

        val videoPlaylistContent = if (videoStreamUrl == remoteUrl && !initialContent.contains("#EXT-X-STREAM-INF")) {
            initialContent
        } else {
            fetchTextWithRetry(videoStreamUrl) ?: throw Exception("Failed to fetch video playlist from $videoStreamUrl")
        }

        if (audioStreamUrl == null) {
            val audioRegex = Regex("""#EXT-X-MEDIA:TYPE=AUDIO[^\n]*?URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
            val audioMatch = audioRegex.find(videoPlaylistContent)
            if (audioMatch != null) {
                val relAudio = audioMatch.groupValues[1]
                audioStreamUrl = resolveUrl(videoStreamUrl, relAudio)
            }
        }

        val downloadTasks = mutableListOf<DownloadChunkTask>()

        val rewrittenVideoContent = processAndRewritePlaylist(
            baseUrl = videoStreamUrl,
            playlistContent = videoPlaylistContent,
            prefix = "video_seg",
            streamDir = streamDir,
            tasks = downloadTasks
        )
        File(streamDir, "video.m3u8").writeText(rewrittenVideoContent)

        var hasSeparateAudio = false
        if (audioStreamUrl != null) {
            val audioPlaylistContent = fetchTextWithRetry(audioStreamUrl)
            if (audioPlaylistContent != null) {
                val rewrittenAudioContent = processAndRewritePlaylist(
                    baseUrl = audioStreamUrl,
                    playlistContent = audioPlaylistContent,
                    prefix = "audio_seg",
                    streamDir = streamDir,
                    tasks = downloadTasks
                )
                File(streamDir, "audio.m3u8").writeText(rewrittenAudioContent)
                hasSeparateAudio = true
            }
        }

        val masterFile = File(streamDir, "playlist.m3u8")
        if (hasSeparateAudio) {
            val masterM3u8 = """
                #EXTM3U
                #EXT-X-VERSION:6
                #EXT-X-INDEPENDENT-SEGMENTS
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Audio",DEFAULT=YES,AUTOSELECT=YES,URI="audio.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=3500000,AUDIO="audio"
                video.m3u8
            """.trimIndent()
            masterFile.writeText(masterM3u8)
        } else {
            masterFile.writeText(rewrittenVideoContent)
        }

        val totalTasks = downloadTasks.size
        if (totalTasks == 0) {
            throw Exception("No downloadable media segments found in stream!")
        }

        // Pre-scan already completed segments for instant resume support
        var preExistingBytes = 0L
        var preExistingCompleted = 0
        val pendingTasks = mutableListOf<DownloadChunkTask>()
        for (task in downloadTasks) {
            if (task.destinationFile.exists() && task.destinationFile.length() > 0L) {
                preExistingBytes += task.destinationFile.length()
                preExistingCompleted++
            } else {
                pendingTasks.add(task)
            }
        }

        val semaphore = Semaphore(PARALLEL_WORKERS)
        val downloadedBytesCounter = AtomicLong(preExistingBytes)
        val completedCount = AtomicInteger(preExistingCompleted)

        val progressLock = Any()
        var lastDbUpdateTime = 0L
        var lastSpeedCheckTime = System.currentTimeMillis()
        var bytesSinceLastCheck = 0L
        var currentSpeedMBs = 0f

        // Emit immediate status if resuming from existing segments
        if (preExistingCompleted > 0 && pendingTasks.isNotEmpty()) {
            val resumePercent = (preExistingCompleted * 100 / totalTasks).coerceIn(0, 99)
            val estTotal = ((preExistingBytes.toDouble() / preExistingCompleted) * totalTasks).toLong()
            downloadedItemDao.updateDownloadProgress(
                id = id,
                downloadedBytes = preExistingBytes,
                totalBytes = estTotal,
                status = DownloadedItemEntity.STATUS_DOWNLOADING,
                localFilePath = masterFile.absolutePath
            )
            AppDownloadNotificationHelper.showDownloadProgressNotification(
                context = context,
                id = id,
                title = title,
                progressPercent = resumePercent,
                downloadedBytes = preExistingBytes,
                totalBytes = estTotal,
                speedMBs = 0f
            )
        }

        coroutineScope {
            val jobs = pendingTasks.map { task ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        ensureActive()
                        if (pausedIds.contains(id) || cancelledIds.contains(id)) {
                            throw CancellationException("Download paused or cancelled")
                        }

                        downloadSegmentToFileWithRetry(
                            url = task.url,
                            targetFile = task.destinationFile,
                            onBytesDownloaded = { bytesRead ->
                                downloadedBytesCounter.addAndGet(bytesRead.toLong())
                                synchronized(progressLock) {
                                    bytesSinceLastCheck += bytesRead
                                }
                            }
                        )

                        val done = completedCount.incrementAndGet()
                        val now = System.currentTimeMillis()
                        var shouldEmitUpdate = false
                        var speedSnapshot = 0f

                        synchronized(progressLock) {
                            val timeDiffMs = now - lastSpeedCheckTime
                            if (timeDiffMs >= 300L) {
                                val instantSpeed = (bytesSinceLastCheck.toFloat() / (1024f * 1024f)) / (timeDiffMs.toFloat() / 1000f)
                                currentSpeedMBs = if (currentSpeedMBs <= 0.01f) {
                                    instantSpeed
                                } else {
                                    (currentSpeedMBs * 0.35f) + (instantSpeed * 0.65f)
                                }
                                lastSpeedCheckTime = now
                                bytesSinceLastCheck = 0L
                            }
                            if (now - lastDbUpdateTime >= 280L || done == totalTasks) {
                                lastDbUpdateTime = now
                                shouldEmitUpdate = true
                                speedSnapshot = currentSpeedMBs
                            }
                        }

                        if (shouldEmitUpdate && !pausedIds.contains(id) && !cancelledIds.contains(id)) {
                            val currentBytes = downloadedBytesCounter.get()
                            val progressPercent = (done * 100 / totalTasks).coerceIn(0, 100)
                            val estimatedTotal = if (done > 0) {
                                ((currentBytes.toDouble() / done) * totalTasks).toLong().coerceAtLeast(currentBytes)
                            } else {
                                currentBytes
                            }

                            updateSpeedForId(id, speedSnapshot)

                            downloadedItemDao.updateDownloadProgress(
                                id = id,
                                downloadedBytes = currentBytes,
                                totalBytes = estimatedTotal,
                                status = DownloadedItemEntity.STATUS_DOWNLOADING,
                                localFilePath = masterFile.absolutePath
                            )

                            AppDownloadNotificationHelper.showDownloadProgressNotification(
                                context = context,
                                id = id,
                                title = title,
                                progressPercent = progressPercent,
                                downloadedBytes = currentBytes,
                                totalBytes = estimatedTotal,
                                speedMBs = speedSnapshot
                            )
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        val finalBytes = downloadedBytesCounter.get()
        updateSpeedForId(id, 0f)
        downloadedItemDao.insertOrUpdate(
            initialEntity.copy(
                downloadedBytes = finalBytes,
                totalBytes = finalBytes,
                status = DownloadedItemEntity.STATUS_COMPLETED,
                localFilePath = masterFile.absolutePath
            )
        )

        AppDownloadNotificationHelper.showDownloadCompleteNotification(
            context = context,
            id = id,
            title = title,
            totalBytes = finalBytes
        )
        Log.d(TAG, "HLS download complete! Total size: $finalBytes bytes ($totalTasks chunks) for id: $id")
    }

    private fun processAndRewritePlaylist(
        baseUrl: String,
        playlistContent: String,
        prefix: String,
        streamDir: File,
        tasks: MutableList<DownloadChunkTask>
    ): String {
        val sb = StringBuilder()
        var segmentIndex = 0
        var mapIndex = 0
        var keyIndex = 0
        val isAudio = prefix.startsWith("audio")

        for (line in playlistContent.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    sb.append("\n")
                }
                trimmed.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val mapRegex = Regex("""URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
                    val match = mapRegex.find(trimmed)
                    if (match != null) {
                        val initUrl = resolveUrl(baseUrl, match.groupValues[1])
                        val initFileName = "${prefix}_init_${mapIndex++}.mp4"
                        tasks.add(DownloadChunkTask(initUrl, File(streamDir, initFileName)))
                        val rewrittenMapLine = trimmed.replace(match.value, "URI=\"$initFileName\"")
                        sb.append(rewrittenMapLine).append("\n")
                    } else {
                        sb.append(line).append("\n")
                    }
                }
                trimmed.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val keyUriRegex = Regex("""URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
                    val match = keyUriRegex.find(trimmed)
                    if (match != null) {
                        val keyUrl = resolveUrl(baseUrl, match.groupValues[1])
                        val keyFileName = "${prefix}_key_${keyIndex++}.bin"
                        tasks.add(DownloadChunkTask(keyUrl, File(streamDir, keyFileName)))
                        val rewrittenKeyLine = trimmed.replace(match.value, "URI=\"$keyFileName\"")
                        sb.append(rewrittenKeyLine).append("\n")
                    } else {
                        sb.append(line).append("\n")
                    }
                }
                !trimmed.startsWith("#") -> {
                    val resolvedUrl = resolveUrl(baseUrl, trimmed)
                    val ext = when {
                        trimmed.contains(".m4s", ignoreCase = true) -> "m4s"
                        trimmed.contains(".aac", ignoreCase = true) -> "aac"
                        trimmed.contains(".mp4", ignoreCase = true) -> "mp4"
                        isAudio -> "aac"
                        else -> "ts"
                    }
                    val localFileName = "${prefix}_${segmentIndex}.${ext}"
                    tasks.add(DownloadChunkTask(resolvedUrl, File(streamDir, localFileName)))
                    sb.append(localFileName).append("\n")
                    segmentIndex++
                }
                else -> {
                    sb.append(line).append("\n")
                }
            }
        }

        val result = sb.toString()
        return if (!result.contains("#EXT-X-ENDLIST")) {
            result.trimEnd() + "\n#EXT-X-ENDLIST\n"
        } else {
            result
        }
    }

    private suspend fun fetchTextWithRetry(url: String, maxRetries: Int = 3): String? {
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addStandardHeaders()
                    .build()

                val content = httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
                if (content != null) return content
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt < maxRetries) {
                    delay(150L * attempt)
                }
            }
        }
        return null
    }

    private suspend fun downloadSegmentToFileWithRetry(
        url: String,
        targetFile: File,
        onBytesDownloaded: (Int) -> Unit,
        maxRetries: Int = 4
    ) {
        if (targetFile.exists() && targetFile.length() > 0L) {
            return
        }

        var lastException: Exception? = null
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        for (attempt in 1..maxRetries) {
            var bytesWrittenThisAttempt = 0
            try {
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(url)
                    .addStandardHeaders()
                    .header("Accept-Encoding", "identity")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful || response.body == null) {
                        throw Exception("HTTP chunk error ${response.code}")
                    }
                    val body = response.body!!
                    tempFile.outputStream().buffered(BUFFER_SIZE).use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        val input = body.byteStream().buffered(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            out.write(buffer, 0, bytesRead)
                            bytesWrittenThisAttempt += bytesRead
                            onBytesDownloaded(bytesRead)
                        }
                        out.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                return
            } catch (e: CancellationException) {
                if (bytesWrittenThisAttempt > 0) {
                    onBytesDownloaded(-bytesWrittenThisAttempt)
                }
                try { tempFile.delete() } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                if (bytesWrittenThisAttempt > 0) {
                    onBytesDownloaded(-bytesWrittenThisAttempt)
                }
                try { tempFile.delete() } catch (_: Exception) {}
                lastException = e
                if (attempt < maxRetries) {
                    delay(150L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Failed to download segment after $maxRetries retries: $url")
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
     * Downloads a standard regular file (e.g. PDF or MP4) with true Range resume and connection safety.
     */
    private suspend fun downloadRegularFileInternal(
        id: String,
        title: String,
        initialEntity: DownloadedItemEntity,
        targetFile: File,
        remoteUrl: String
    ) = withContext(Dispatchers.IO) {
        // Probe metadata using a lightweight 1-byte Range request so we never leak a full GET stream
        var contentLength = 0L
        var acceptRanges = false

        val probeRequest = Request.Builder()
            .url(remoteUrl)
            .addStandardHeaders()
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=0-0")
            .build()

        httpClient.newCall(probeRequest).execute().use { response ->
            if (response.code == 206) {
                acceptRanges = true
                val contentRange = response.header("Content-Range") // e.g. "bytes 0-0/1234567"
                val totalFromRange = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                contentLength = totalFromRange ?: response.body?.contentLength() ?: 0L
            } else if (response.isSuccessful) {
                acceptRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                contentLength = response.header("Content-Length")?.toLongOrNull()
                    ?: response.body?.contentLength()
                    ?: 0L
            } else {
                throw Exception("HTTP probe failed with code: ${response.code}")
            }
        }

        val totalBytes = contentLength.coerceAtLeast(0L)
        if (totalBytes > 0L) {
            downloadedItemDao.updateDownloadProgress(
                id = id,
                downloadedBytes = initialEntity.downloadedBytes.coerceAtMost(totalBytes),
                totalBytes = totalBytes,
                status = DownloadedItemEntity.STATUS_DOWNLOADING,
                localFilePath = targetFile.absolutePath
            )
        }

        val progressLock = Any()
        var lastDbUpdateTime = 0L
        var lastSpeedCheckTime = System.currentTimeMillis()
        var bytesSinceLastCheck = 0L
        var currentSpeedMBs = 0f

        // Multi-part range download with per-part resume for files > 2MB when server supports Range
        if (totalBytes > 2 * 1024 * 1024 && acceptRanges) {
            val numChunks = 8
            val chunkSize = totalBytes / numChunks
            val tempParts = Array(numChunks) { index ->
                File(targetFile.parentFile, "${targetFile.name}.part$index")
            }

            // Calculate already downloaded bytes across existing part files
            var existingPartBytes = 0L
            for (index in 0 until numChunks) {
                val startByte = index * chunkSize
                val endByte = if (index == numChunks - 1) totalBytes - 1 else (index + 1) * chunkSize - 1
                val expectedPartLen = endByte - startByte + 1
                val partFile = tempParts[index]
                if (partFile.exists()) {
                    if (partFile.length() > expectedPartLen) {
                        partFile.delete()
                    } else {
                        existingPartBytes += partFile.length()
                    }
                }
            }

            val downloadedBytesCounter = AtomicLong(existingPartBytes)

            coroutineScope {
                val jobs = (0 until numChunks).map { index ->
                    val startByte = index * chunkSize
                    val endByte = if (index == numChunks - 1) totalBytes - 1 else (index + 1) * chunkSize - 1
                    val expectedPartLen = endByte - startByte + 1
                    val partFile = tempParts[index]

                    async(Dispatchers.IO) {
                        val alreadyDone = if (partFile.exists()) partFile.length() else 0L
                        if (alreadyDone >= expectedPartLen) {
                            return@async
                        }

                        val resumeStartByte = startByte + alreadyDone
                        val rangeRequest = Request.Builder()
                            .url(remoteUrl)
                            .addStandardHeaders()
                            .header("Accept-Encoding", "identity")
                            .header("Range", "bytes=$resumeStartByte-$endByte")
                            .build()

                        httpClient.newCall(rangeRequest).execute().use { response ->
                            if (!response.isSuccessful || response.body == null) {
                                throw Exception("Chunk download error ${response.code}")
                            }

                            val appendMode = alreadyDone > 0L && response.code == 206
                            if (!appendMode && alreadyDone > 0L) {
                                downloadedBytesCounter.addAndGet(-alreadyDone)
                            }

                            FileOutputStream(partFile, appendMode).buffered(BUFFER_SIZE).use { out ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                val inStream = response.body!!.byteStream().buffered(BUFFER_SIZE)
                                var bytesRead: Int
                                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                    ensureActive()
                                    if (pausedIds.contains(id) || cancelledIds.contains(id)) {
                                        throw CancellationException("Download paused or cancelled")
                                    }

                                    out.write(buffer, 0, bytesRead)
                                    val current = downloadedBytesCounter.addAndGet(bytesRead.toLong())

                                    val now = System.currentTimeMillis()
                                    var shouldEmit = false
                                    var speedSnapshot = 0f

                                    synchronized(progressLock) {
                                        bytesSinceLastCheck += bytesRead
                                        val timeDiffMs = now - lastSpeedCheckTime
                                        if (timeDiffMs >= 300L) {
                                            val instantSpeed = (bytesSinceLastCheck.toFloat() / (1024f * 1024f)) / (timeDiffMs.toFloat() / 1000f)
                                            currentSpeedMBs = if (currentSpeedMBs <= 0.01f) instantSpeed else (currentSpeedMBs * 0.35f) + (instantSpeed * 0.65f)
                                            lastSpeedCheckTime = now
                                            bytesSinceLastCheck = 0L
                                        }
                                        if (now - lastDbUpdateTime >= 280L || current >= totalBytes) {
                                            lastDbUpdateTime = now
                                            shouldEmit = true
                                            speedSnapshot = currentSpeedMBs
                                        }
                                    }

                                    if (shouldEmit && !pausedIds.contains(id) && !cancelledIds.contains(id)) {
                                        val progressPercent = if (totalBytes > 0) (current * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
                                        updateSpeedForId(id, speedSnapshot)

                                        downloadedItemDao.updateDownloadProgress(
                                            id = id,
                                            downloadedBytes = current,
                                            totalBytes = totalBytes,
                                            status = DownloadedItemEntity.STATUS_DOWNLOADING,
                                            localFilePath = targetFile.absolutePath
                                        )

                                        AppDownloadNotificationHelper.showDownloadProgressNotification(
                                            context = context,
                                            id = id,
                                            title = title,
                                            progressPercent = progressPercent,
                                            downloadedBytes = current,
                                            totalBytes = totalBytes,
                                            speedMBs = speedSnapshot
                                        )
                                    }
                                }
                                out.flush()
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            // Merge all parts into the final target file
            val mergedTemp = File(targetFile.parentFile, "${targetFile.name}.tmp")
            mergedTemp.outputStream().buffered(BUFFER_SIZE * 2).use { outStream ->
                for (part in tempParts) {
                    part.inputStream().buffered(BUFFER_SIZE * 2).use { inStream ->
                        inStream.copyTo(outStream, BUFFER_SIZE * 2)
                    }
                    part.delete()
                }
                outStream.flush()
            }
            if (targetFile.exists()) targetFile.delete()
            if (!mergedTemp.renameTo(targetFile)) {
                mergedTemp.copyTo(targetFile, overwrite = true)
                mergedTemp.delete()
            }
        } else {
            // Single connection stream with Range resume support
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            val existingLen = if (tempFile.exists()) tempFile.length() else 0L

            val requestBuilder = Request.Builder()
                .url(remoteUrl)
                .addStandardHeaders()
                .header("Accept-Encoding", "identity")

            if (existingLen > 0L && acceptRanges && (totalBytes == 0L || existingLen < totalBytes)) {
                requestBuilder.header("Range", "bytes=$existingLen-")
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    throw Exception("HTTP download failed with code: ${response.code}")
                }

                val isResumed = existingLen > 0L && response.code == 206
                var downloadedBytes = if (isResumed) existingLen else 0L

                FileOutputStream(tempFile, isResumed).buffered(BUFFER_SIZE).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    val inputStream = response.body!!.byteStream().buffered(BUFFER_SIZE)

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        if (pausedIds.contains(id) || cancelledIds.contains(id)) {
                            throw CancellationException("Download paused or cancelled")
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastCheck += bytesRead

                        val now = System.currentTimeMillis()
                        val timeDiffMs = now - lastSpeedCheckTime
                        if (timeDiffMs >= 300L) {
                            val instantSpeed = (bytesSinceLastCheck.toFloat() / (1024f * 1024f)) / (timeDiffMs.toFloat() / 1000f)
                            currentSpeedMBs = if (currentSpeedMBs <= 0.01f) instantSpeed else (currentSpeedMBs * 0.35f) + (instantSpeed * 0.65f)
                            lastSpeedCheckTime = now
                            bytesSinceLastCheck = 0L
                        }

                        if (now - lastDbUpdateTime >= 280L || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                            lastDbUpdateTime = now
                            val currentTotal = if (totalBytes > 0) totalBytes else downloadedBytes
                            val progressPercent = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
                            updateSpeedForId(id, currentSpeedMBs)

                            downloadedItemDao.updateDownloadProgress(
                                id = id,
                                downloadedBytes = downloadedBytes,
                                totalBytes = currentTotal,
                                status = DownloadedItemEntity.STATUS_DOWNLOADING,
                                localFilePath = targetFile.absolutePath
                            )

                            AppDownloadNotificationHelper.showDownloadProgressNotification(
                                context = context,
                                id = id,
                                title = title,
                                progressPercent = progressPercent,
                                downloadedBytes = downloadedBytes,
                                totalBytes = currentTotal,
                                speedMBs = currentSpeedMBs
                            )
                        }
                    }
                    outputStream.flush()
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }

        val finalTotal = if (totalBytes > 0) totalBytes else targetFile.length()
        updateSpeedForId(id, 0f)
        downloadedItemDao.insertOrUpdate(
            initialEntity.copy(
                downloadedBytes = finalTotal,
                totalBytes = finalTotal,
                status = DownloadedItemEntity.STATUS_COMPLETED,
                localFilePath = targetFile.absolutePath
            )
        )

        AppDownloadNotificationHelper.showDownloadCompleteNotification(
            context = context,
            id = id,
            title = title,
            totalBytes = finalTotal
        )
    }

    /**
     * Pause an active download while preserving all downloaded segments/chunks on disk.
     */
    fun pauseDownload(id: String) {
        pausedIds.add(id)
        updateSpeedForId(id, 0f)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null && item.status != DownloadedItemEntity.STATUS_COMPLETED) {
                    downloadedItemDao.updateStatus(id, DownloadedItemEntity.STATUS_PAUSED)
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
     * Pause all currently active downloads.
     */
    fun pauseAllDownloads() {
        coroutineScope.launch {
            val activeItems = downloadedItemDao.getDownloadsByStatusOnce(DownloadedItemEntity.STATUS_DOWNLOADING)
            for (item in activeItems) {
                pauseDownload(item.id)
            }
        }
    }

    /**
     * Resume a paused or failed download from where it left off.
     */
    fun resumeDownload(id: String) {
        pausedIds.remove(id)
        cancelledIds.remove(id)
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
     * Resume all paused or failed downloads.
     */
    fun resumeAllDownloads() {
        coroutineScope.launch {
            val pausedItems = downloadedItemDao.getDownloadsByStatusOnce(DownloadedItemEntity.STATUS_PAUSED)
            val failedItems = downloadedItemDao.getDownloadsByStatusOnce(DownloadedItemEntity.STATUS_FAILED)
            for (item in pausedItems + failedItems) {
                resumeDownload(item.id)
            }
        }
    }

    /**
     * Cancel an active, paused, or failed download and remove partial files and notification.
     */
    fun cancelDownload(id: String) {
        pausedIds.remove(id)
        cancelledIds.add(id)
        updateSpeedForId(id, 0f)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        AppDownloadNotificationHelper.cancelNotification(context, id)

        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null) {
                    val file = File(item.localFilePath)
                    val isHls = file.name.endsWith(".m3u8", ignoreCase = true) ||
                        file.parentFile?.name?.startsWith("hls_") == true
                    cleanupFilesForItem(file, isHls)
                }
                downloadedItemDao.deleteDownloadedItem(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling download $id: ${e.message}")
            } finally {
                cancelledIds.remove(id)
            }
        }
    }

    /**
     * Delete a downloaded file completely from vault, notification tray, and database.
     */
    fun deleteDownloadedFile(id: String) {
        pausedIds.remove(id)
        cancelledIds.add(id)
        updateSpeedForId(id, 0f)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        AppDownloadNotificationHelper.cancelNotification(context, id)

        coroutineScope.launch {
            try {
                val item = downloadedItemDao.getDownloadedItemByIdOnce(id)
                if (item != null) {
                    val file = File(item.localFilePath)
                    val isHls = file.isDirectory ||
                        file.name.endsWith(".m3u8", ignoreCase = true) ||
                        file.parentFile?.name?.startsWith("hls_") == true
                    cleanupFilesForItem(file, isHls)
                }
                downloadedItemDao.deleteDownloadedItem(id)
                Log.d(TAG, "Deleted downloaded item: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting downloaded file: $id: ${e.message}")
            } finally {
                cancelledIds.remove(id)
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
     * Helper to format bytes into readable GB/MB/KB string with Bengali digits.
     */
    fun formatFileSize(bytes: Long, inBengali: Boolean = true): String {
        if (bytes <= 0L) return if (inBengali) "০ মেগাবাইট" else "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        val formatted = when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }

        return if (inBengali) {
            val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
            val sb = StringBuilder(formatted.length + 8)
            for (ch in formatted) {
                if (ch in '0'..'9') {
                    sb.append(bnDigits[ch - '0'])
                } else {
                    sb.append(ch)
                }
            }
            sb.toString()
                .replace("GB", "জিবি")
                .replace("MB", "মেগাবাইট")
                .replace("KB", "কেবি")
        } else {
            formatted
        }
    }
}
