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
import java.io.RandomAccessFile
import java.net.URI
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

    // Ultra-optimized high-throughput HTTP client for maximum mobile network saturation
    private val httpClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 64
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
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

    companion object {
        private const val TAG = "AppFileDownloadManager"
        private const val BUFFER_SIZE = 131072 // 128KB ultra high performance buffer
        private const val PARALLEL_WORKERS = 32 // 32 concurrent workers for maximum 4G/5G mobile speed

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

            val isHls = fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true) &&
                    (remoteUrl.contains(".m3u8", ignoreCase = true) || remoteUrl.contains("shikho", ignoreCase = true) || remoteUrl.contains("playlist", ignoreCase = true))

            val sanitizedId = id.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")

            val targetFile = if (isHls) {
                val streamDir = File(downloadVaultDir, "hls_$sanitizedId")
                File(streamDir, "playlist.m3u8")
            } else {
                val extension = if (fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true)) "pdf" else "mp4"
                File(downloadVaultDir, "${fileType.lowercase()}_${sanitizedId}.$extension")
            }

            // Check if already completed and file exists
            val existingItem = downloadedItemDao.getDownloadedItemByIdOnce(id)
            if (existingItem != null && existingItem.status == DownloadedItemEntity.STATUS_COMPLETED && targetFile.exists() && targetFile.length() > 0) {
                Log.d(TAG, "Item $id is already downloaded. Skipping duplicate download.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "এই ফাইলটি ইতোমধ্যে অ্যাপে সফলভাবে ডাউনলোড করা হয়েছে", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val initialEntity = DownloadedItemEntity(
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
                Log.w(TAG, "Download cancelled for id: $id")
                try {
                    if (targetFile.exists()) {
                        if (isHls) targetFile.parentFile?.deleteRecursively() else targetFile.delete()
                    }
                    downloadedItemDao.deleteDownloadedItem(id)
                } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for id: $id: ${e.message}", e)
                try {
                    if (targetFile.exists()) {
                        if (isHls) targetFile.parentFile?.deleteRecursively() else targetFile.delete()
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
     * Downloads an HLS stream (.m3u8) with full Video AND Audio tracks at maximum mobile network speed.
     * Uses 32 concurrent worker threads, handles init fMP4 segments, encryption keys, and audio tracks.
     */
    private suspend fun downloadHlsStreamInternal(
        id: String,
        title: String,
        initialEntity: DownloadedItemEntity,
        streamDir: File,
        remoteUrl: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting ultra-fast HLS download for: $remoteUrl")

        if (streamDir.exists()) {
            streamDir.deleteRecursively()
        }
        streamDir.mkdirs()

        // 1. Comprehensive Master Playlist & Audio Detection
        var masterPlaylistUrl: String? = null
        var masterPlaylistContent: String? = null

        var videoStreamUrl: String = remoteUrl
        var audioStreamUrl: String? = null

        // Fetch initial remoteUrl content
        val initialContent = fetchTextWithRetry(remoteUrl) ?: throw Exception("Failed to connect to video stream: $remoteUrl")

        if (initialContent.contains("#EXT-X-STREAM-INF") || initialContent.contains("#EXT-X-MEDIA:TYPE=AUDIO")) {
            // remoteUrl is already the master playlist
            masterPlaylistUrl = remoteUrl
            masterPlaylistContent = initialContent
        } else {
            // remoteUrl is a variant stream, probe potential master playlist URLs to locate audio tracks
            val candidateMasterUrls = mutableListOf<String>()

            if (remoteUrl.contains(Regex("""/(1080p|720p|480p|360p|240p|144p)/(video|media)\.m3u8""", RegexOption.IGNORE_CASE))) {
                candidateMasterUrls.add(remoteUrl.replace(Regex("""/(1080p|720p|480p|360p|240p|144p)/(video|media)\.m3u8""", RegexOption.IGNORE_CASE), "/playlist.m3u8"))
                candidateMasterUrls.add(remoteUrl.replace(Regex("""/(1080p|720p|480p|360p|240p|144p)/(video|media)\.m3u8""", RegexOption.IGNORE_CASE), "/master.m3u8"))
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
                val cContent = fetchTextWithRetry(cUrl)
                if (cContent != null && (cContent.contains("#EXT-X-STREAM-INF") || cContent.contains("#EXT-X-MEDIA:TYPE=AUDIO"))) {
                    masterPlaylistUrl = cUrl
                    masterPlaylistContent = cContent
                    Log.d(TAG, "Located parent HLS master playlist: $masterPlaylistUrl")
                    break
                }
            }
        }

        // Parse Audio and Video from Master Playlist if available
        if (masterPlaylistContent != null && masterPlaylistUrl != null) {
            // Extract Audio stream URI (supporting single quotes, double quotes, or unquoted)
            val audioRegex = Regex("""#EXT-X-MEDIA:TYPE=AUDIO[^\\n]*?URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
            val audioMatch = audioRegex.find(masterPlaylistContent)
            if (audioMatch != null) {
                val relAudio = audioMatch.groupValues[1]
                audioStreamUrl = resolveUrl(masterPlaylistUrl, relAudio)
                Log.d(TAG, "Discovered separate HLS Audio stream URL: $audioStreamUrl")
            }

            // If remoteUrl was the master playlist, pick the best video variant
            if (remoteUrl == masterPlaylistUrl) {
                val lines = masterPlaylistContent.lines()
                val variants = mutableListOf<String>()
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                            variants.add(nextLine)
                        }
                    }
                }
                if (variants.isNotEmpty()) {
                    // Default to 720p/480p or first available
                    val preferred = variants.firstOrNull { it.contains("720p") || it.contains("480p") || it.contains("stream_1") || it.contains("stream_2") }
                        ?: variants.first()
                    videoStreamUrl = resolveUrl(masterPlaylistUrl, preferred)
                    Log.d(TAG, "Selected Video stream variant: $videoStreamUrl")
                }
            }
        }

        // 2. Fetch Video Playlist Content
        val videoPlaylistContent = if (videoStreamUrl == remoteUrl && !initialContent.contains("#EXT-X-STREAM-INF")) {
            initialContent
        } else {
            fetchTextWithRetry(videoStreamUrl) ?: throw Exception("Failed to fetch video playlist from $videoStreamUrl")
        }

        // Also check if videoPlaylistContent itself has audio media tag
        if (audioStreamUrl == null) {
            val audioRegex = Regex("""#EXT-X-MEDIA:TYPE=AUDIO[^\\n]*?URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
            val audioMatch = audioRegex.find(videoPlaylistContent)
            if (audioMatch != null) {
                val relAudio = audioMatch.groupValues[1]
                audioStreamUrl = resolveUrl(videoStreamUrl, relAudio)
                Log.d(TAG, "Discovered Audio stream inside video playlist: $audioStreamUrl")
            }
        }

        val downloadTasks = mutableListOf<DownloadChunkTask>()

        // 3. Process Video Playlist & init/key chunks
        val rewrittenVideoContent = processAndRewritePlaylist(
            baseUrl = videoStreamUrl,
            playlistContent = videoPlaylistContent,
            prefix = "video_seg",
            streamDir = streamDir,
            tasks = downloadTasks
        )
        File(streamDir, "video.m3u8").writeText(rewrittenVideoContent)

        // 4. Process Audio Playlist & init/key chunks (if present)
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
                Log.d(TAG, "Successfully prepared separate Audio track for offline playback.")
            }
        }

        // 5. Create Master playlist.m3u8
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
            // Muxed stream where audio is interleaved inside video TS segments
            masterFile.writeText(rewrittenVideoContent)
        }

        val totalTasks = downloadTasks.size
        if (totalTasks == 0) {
            throw Exception("No downloadable media segments found in stream!")
        }

        Log.d(TAG, "Starting ultra-fast parallel download of $totalTasks chunks with $PARALLEL_WORKERS workers")

        // 6. Execute downloads with 32 parallel workers for maximum mobile network saturation
        val semaphore = Semaphore(PARALLEL_WORKERS)
        val downloadedBytesCounter = AtomicLong(0L)
        val completedCount = AtomicInteger(0)

        var lastDbUpdateTime = 0L
        var lastSpeedCheckTime = System.currentTimeMillis()
        var bytesSinceLastCheck = 0L
        var currentSpeedMBs = 0f

        try {
            coroutineScope {
                val jobs = downloadTasks.map { task ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            ensureActive()
                            downloadSegmentToFileWithRetry(
                                url = task.url,
                                targetFile = task.destinationFile,
                                onBytesDownloaded = { bytesRead ->
                                    downloadedBytesCounter.addAndGet(bytesRead.toLong())
                                    synchronized(this@AppFileDownloadManager) {
                                        bytesSinceLastCheck += bytesRead
                                    }
                                }
                            )

                            val done = completedCount.incrementAndGet()
                            val now = System.currentTimeMillis()
                            val timeDiffMs = now - lastSpeedCheckTime
                            if (timeDiffMs >= 300L) {
                                currentSpeedMBs = (bytesSinceLastCheck.toFloat() / (1024f * 1024f)) / (timeDiffMs.toFloat() / 1000f)
                                lastSpeedCheckTime = now
                                bytesSinceLastCheck = 0L
                            }

                            if (now - lastDbUpdateTime > 250L || done == totalTasks) {
                                lastDbUpdateTime = now
                                val currentBytes = downloadedBytesCounter.get()
                                val progressPercent = (done * 100 / totalTasks).coerceIn(0, 100)
                                val estimatedTotal = if (done > 0) ((currentBytes.toDouble() / done) * totalTasks).toLong() else currentBytes

                                downloadedItemDao.insertOrUpdate(
                                    initialEntity.copy(
                                        downloadedBytes = currentBytes,
                                        totalBytes = if (estimatedTotal > 0) estimatedTotal else currentBytes,
                                        status = DownloadedItemEntity.STATUS_DOWNLOADING,
                                        localFilePath = masterFile.absolutePath
                                    )
                                )

                                AppDownloadNotificationHelper.showDownloadProgressNotification(
                                    context = context,
                                    id = id,
                                    title = title,
                                    progressPercent = progressPercent,
                                    downloadedBytes = currentBytes,
                                    totalBytes = if (estimatedTotal > 0) estimatedTotal else currentBytes,
                                    speedMBs = currentSpeedMBs
                                )
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            val finalBytes = downloadedBytesCounter.get()
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
            Log.d(TAG, "Ultra-fast HLS download complete! Total size: $finalBytes bytes ($totalTasks chunks) for id: $id")
        } catch (e: CancellationException) {
            AppDownloadNotificationHelper.cancelNotification(context, id)
            streamDir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            AppDownloadNotificationHelper.showDownloadFailedNotification(context, id, title)
            streamDir.deleteRecursively()
            throw e
        }
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
        val isAudio = prefix.startsWith("audio")

        for (line in playlistContent.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    sb.append("\n")
                }
                trimmed.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    // Extract fMP4/CMAF init segment: #EXT-X-MAP:URI="init.mp4"
                    val mapRegex = Regex("""#EXT-X-MAP:URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
                    val match = mapRegex.find(trimmed)
                    if (match != null) {
                        val initUrl = resolveUrl(baseUrl, match.groupValues[1])
                        val initFileName = "${prefix}_init.mp4"
                        tasks.add(DownloadChunkTask(initUrl, File(streamDir, initFileName)))
                        sb.append("#EXT-X-MAP:URI=\"$initFileName\"\n")
                    } else {
                        sb.append(line).append("\n")
                    }
                }
                trimmed.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    // Extract AES-128 key: #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                    val keyUriRegex = Regex("""URI=["']?([^"'\s,]+)["']?""", RegexOption.IGNORE_CASE)
                    val match = keyUriRegex.find(trimmed)
                    if (match != null) {
                        val keyUrl = resolveUrl(baseUrl, match.groupValues[1])
                        val keyFileName = "${prefix}_key.bin"
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
                    .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                    .addHeader("referer", "https://shikho.com/")
                    .addHeader("Referer", "https://shikho.com/")
                    .addHeader("Origin", "https://shikho.com")
                    .addHeader("Connection", "Keep-Alive")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    return response.body!!.string()
                }
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    delay(120L * attempt)
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
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                    .addHeader("referer", "https://shikho.com/")
                    .addHeader("Referer", "https://shikho.com/")
                    .addHeader("Origin", "https://shikho.com")
                    .addHeader("Accept-Encoding", "identity")
                    .addHeader("Connection", "Keep-Alive")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    throw Exception("HTTP chunk error ${response.code}")
                }
                val body = response.body!!
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                tempFile.outputStream().buffered(BUFFER_SIZE).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    val input = body.byteStream().buffered(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        onBytesDownloaded(bytesRead)
                    }
                    out.flush()
                }
                tempFile.renameTo(targetFile)
                return
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(100L * attempt)
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
     * Downloads a standard regular file (e.g. PDF or MP4) using parallel Range chunks for maximum speed.
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

        val headRequest = Request.Builder()
            .url(remoteUrl)
            .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
            .addHeader("referer", "https://shikho.com/")
            .addHeader("Referer", "https://shikho.com/")
            .addHeader("Origin", "https://shikho.com")
            .addHeader("Connection", "Keep-Alive")
            .build()

        val headResponse = httpClient.newCall(headRequest).execute()
        if (!headResponse.isSuccessful) {
            throw Exception("HTTP download failed with code: ${headResponse.code}")
        }

        val contentLength = headResponse.body?.contentLength() ?: 0L
        val acceptRanges = headResponse.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true || contentLength > 2 * 1024 * 1024

        val totalBytes = if (contentLength > 0) contentLength else 0L
        downloadedItemDao.insertOrUpdate(initialEntity.copy(totalBytes = totalBytes))

        // If file is > 2MB and server supports Range, perform 8-way parallel multithreaded download
        if (contentLength > 2 * 1024 * 1024 && acceptRanges) {
            val numChunks = 8
            val chunkSize = contentLength / numChunks
            val tempParts = Array(numChunks) { index ->
                File(targetFile.parentFile, "${targetFile.name}.part$index")
            }

            val downloadedBytesCounter = AtomicLong(0L)
            var lastDbUpdateTime = 0L
            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L
            var currentSpeedMBs = 0f

            try {
                coroutineScope {
                    val jobs = (0 until numChunks).map { index ->
                        val startByte = index * chunkSize
                        val endByte = if (index == numChunks - 1) contentLength - 1 else (index + 1) * chunkSize - 1

                        async(Dispatchers.IO) {
                            val rangeRequest = Request.Builder()
                                .url(remoteUrl)
                                .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                                .addHeader("referer", "https://shikho.com/")
                                .addHeader("Referer", "https://shikho.com/")
                                .addHeader("Origin", "https://shikho.com")
                                .addHeader("Range", "bytes=$startByte-$endByte")
                                .build()

                            val response = httpClient.newCall(rangeRequest).execute()
                            if (!response.isSuccessful || response.body == null) {
                                throw Exception("Chunk download error ${response.code}")
                            }

                            val partFile = tempParts[index]
                            partFile.outputStream().buffered(BUFFER_SIZE).use { out ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                val inStream = response.body!!.byteStream().buffered(BUFFER_SIZE)
                                var bytesRead: Int
                                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                    ensureActive()
                                    out.write(buffer, 0, bytesRead)
                                    val current = downloadedBytesCounter.addAndGet(bytesRead.toLong())
                                    synchronized(this@AppFileDownloadManager) {
                                        bytesSinceLastCheck += bytesRead
                                    }

                                    val now = System.currentTimeMillis()
                                    val timeDiffMs = now - lastSpeedCheckTime
                                    if (timeDiffMs >= 300L) {
                                        currentSpeedMBs = (bytesSinceLastCheck.toFloat() / (1024f * 1024f)) / (timeDiffMs.toFloat() / 1000f)
                                        lastSpeedCheckTime = now
                                        bytesSinceLastCheck = 0L
                                    }

                                    if (now - lastDbUpdateTime > 250L || current >= totalBytes) {
                                        lastDbUpdateTime = now
                                        val progressPercent = if (totalBytes > 0) (current * 100 / totalBytes).toInt().coerceIn(0, 100) else 0

                                        downloadedItemDao.insertOrUpdate(
                                            initialEntity.copy(
                                                downloadedBytes = current,
                                                totalBytes = totalBytes,
                                                status = DownloadedItemEntity.STATUS_DOWNLOADING
                                            )
                                        )

                                        AppDownloadNotificationHelper.showDownloadProgressNotification(
                                            context = context,
                                            id = id,
                                            title = title,
                                            progressPercent = progressPercent,
                                            downloadedBytes = current,
                                            totalBytes = totalBytes,
                                            speedMBs = currentSpeedMBs
                                        )
                                    }
                                }
                                out.flush()
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                // Merge all parts into the target file
                targetFile.outputStream().buffered(BUFFER_SIZE * 2).use { outStream ->
                    for (part in tempParts) {
                        part.inputStream().buffered(BUFFER_SIZE * 2).use { inStream ->
                            inStream.copyTo(outStream, BUFFER_SIZE * 2)
                        }
                        part.delete()
                    }
                    outStream.flush()
                }
            } catch (e: Exception) {
                for (part in tempParts) {
                    try { part.delete() } catch (_: Exception) {}
                }
                throw e
            }
        } else {
            // Single connection stream with high speed buffer
            val body = headResponse.body!!
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tempFile.outputStream().buffered(BUFFER_SIZE).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastDbUpdateTime = System.currentTimeMillis()
                var lastSpeedCheckTime = System.currentTimeMillis()
                var bytesSinceLastCheck = 0L
                var currentSpeedMBs = 0f

                val inputStream = body.byteStream().buffered(BUFFER_SIZE)
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive()
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    bytesSinceLastCheck += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiffMs = now - lastSpeedCheckTime
                    if (timeDiffMs >= 300L) {
                        currentSpeedMBs = (bytesSinceLastCheck.toFloat() / (1024f * 1024f)) / (timeDiffMs.toFloat() / 1000f)
                        lastSpeedCheckTime = now
                        bytesSinceLastCheck = 0L
                    }

                    if (now - lastDbUpdateTime > 250L || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                        lastDbUpdateTime = now
                        val currentTotal = if (totalBytes > 0) totalBytes else downloadedBytes
                        val progressPercent = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0

                        downloadedItemDao.insertOrUpdate(
                            initialEntity.copy(
                                downloadedBytes = downloadedBytes,
                                totalBytes = currentTotal,
                                status = DownloadedItemEntity.STATUS_DOWNLOADING
                            )
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
            tempFile.renameTo(targetFile)
        }

        val finalTotal = if (totalBytes > 0) totalBytes else targetFile.length()
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
                        if (file.name.endsWith(".m3u8")) {
                            file.parentFile?.deleteRecursively()
                        } else {
                            file.delete()
                        }
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
                        if (file.isDirectory) {
                            file.deleteRecursively()
                        } else if (file.name.endsWith(".m3u8")) {
                            file.parentFile?.deleteRecursively()
                        } else {
                            file.delete()
                        }
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
