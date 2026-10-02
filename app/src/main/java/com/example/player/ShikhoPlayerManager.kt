package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PlayerClassType(
    val labelBangla: String,
    val iconEmoji: String,
    val isLive: Boolean
) {
    ANIMATED("অ্যানিমেটেড ক্লাস", "🎬", false),
    RECORDED_LECTURE("রেকর্ডেড লেকচার", "📖", false),
    LIVE("লাইভ ক্লাস", "🔴", true);

    companion object {
        fun resolve(
            isLive: Boolean,
            contentType: String? = null,
            classType: String? = null,
            title: String? = null,
            url: String? = null
        ): PlayerClassType {
            if (isLive) return LIVE

            val lowerContent = (contentType ?: "").lowercase()
            val lowerClass = (classType ?: "").lowercase()
            val lowerTitle = (title ?: "").lowercase()
            val lowerUrl = (url ?: "").lowercase()

            if (lowerContent.contains("live") || lowerClass.contains("live") || lowerTitle.contains("live") || lowerUrl.contains("100ms.live")) {
                return LIVE
            }

            return RECORDED_LECTURE
        }
    }
}

@OptIn(UnstableApi::class)
data class VideoTrackQuality(
    val id: String,
    val label: String,
    val height: Int,
    val bitrate: Int,
    val trackGroup: Tracks.Group? = null,
    val trackIndex: Int = 0,
    val targetStreamUrl: String? = null
)

@OptIn(UnstableApi::class)
object ShikhoPlayerManager {

    const val DEFAULT_REFERER = "https://shikho.com/"
    const val DEFAULT_USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)"

    // High-performance streaming HTTP client with large connection pool and keep-alive
    val streamingHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 64
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Creates an OkHttp-based DataSource.Factory configured with Shikho's required CDN headers
     * and high-speed socket streaming properties.
     */
    fun createHttpDataSourceFactory(
        referer: String = DEFAULT_REFERER,
        userAgent: String = DEFAULT_USER_AGENT
    ): DataSource.Factory {
        return OkHttpDataSource.Factory(streamingHttpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(
                mapOf(
                    "referer" to referer,
                    "Referer" to referer,
                    "Origin" to "https://shikho.com",
                    "Accept-Encoding" to "identity",
                    "Connection" to "Keep-Alive"
                )
            )
    }

    /**
     * Optimized Smooth LoadControl:
     * - 1,000ms initial buffer for instant video playback start.
     * - 15,000ms (15s) min buffer to ensure zero stuttering during temporary network dips.
     * - 60,000ms (1 minute) max buffer ahead to smoothly stream without memory starvation.
     * - 2,000ms buffer after rebuffer for lightning-fast recovery from network dips.
     * - Dynamic target buffer allocation (no fixed 32MB cap that starves long lectures).
     */
    fun createAggressiveLoadControl(): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 15_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Initial bandwidth meter (defaults to 2.5 Mbps estimate for reliable 720p HD start)
     * so video opens smoothly and dynamically scales without choking mobile networks.
     */
    fun createBandwidthMeter(context: Context): DefaultBandwidthMeter {
        return DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(2_500_000L)
            .build()
    }

    /**
     * Resilient LoadErrorHandlingPolicy: Automatically retries up to 12 times with progressive
     * backoff during temporary network dips, Wi-Fi glitches, or cellular cell-handshakes so
     * 2-3 hour long lecture classes run smoothly without fatal buffering aborts.
     */
    fun createResilientLoadErrorHandlingPolicy(): androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy {
        return object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(12) {
            override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                return (loadErrorInfo.errorCount * 600L).coerceIn(400L, 3000L)
            }
            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 12
        }
    }

    /**
     * Creates a MediaSource properly configured for HLS (.m3u8), standard MP4 streams,
     * offline local files, and Live streaming with chunkless preparation for lightning-fast start.
     */
    fun createMediaSource(
        url: String,
        isLive: Boolean = false,
        classType: PlayerClassType = if (isLive) PlayerClassType.LIVE else PlayerClassType.RECORDED_LECTURE,
        dataSourceFactory: DataSource.Factory = createHttpDataSourceFactory(),
        context: Context? = null
    ): MediaSource {
        val isLocalFile = url.startsWith("/") || url.startsWith("file://")
        val uri = if (url.startsWith("/") && !url.startsWith("file://")) {
            Uri.fromFile(File(url))
        } else {
            Uri.parse(url)
        }

        val isHls = url.contains(".m3u8", ignoreCase = true) || url.contains("hls", ignoreCase = true) || isLive || classType == PlayerClassType.LIVE

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply {
                if (isHls) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                if (isLive || classType == PlayerClassType.LIVE) {
                    setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f)
                            .setMinPlaybackSpeed(0.98f)
                            .build()
                    )
                }
            }
            .build()

        val effectiveDataSourceFactory: DataSource.Factory = if (isLocalFile && context != null) {
            DefaultDataSource.Factory(context)
        } else {
            dataSourceFactory
        }

        val retryPolicy = createResilientLoadErrorHandlingPolicy()

        return if (isHls) {
            HlsMediaSource.Factory(effectiveDataSourceFactory)
                .setAllowChunklessPreparation(true) // Immediate playback start without waiting
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(effectiveDataSourceFactory)
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)
        }
    }

    /**
     * Builds and configures an ExoPlayer instance with YouTube-level zero-buffering LoadControl,
     * BandwidthMeter, OkHttp connection pooling, DefaultTrackSelector, and full AudioAttributes.
     */
    fun buildExoPlayer(
        context: Context,
        trackSelector: DefaultTrackSelector? = null,
        classType: PlayerClassType = PlayerClassType.RECORDED_LECTURE,
        dataSourceFactory: DataSource.Factory = createHttpDataSourceFactory()
    ): ExoPlayer {
        val retryPolicy = createResilientLoadErrorHandlingPolicy()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(retryPolicy)
        val seekIncrement = if (classType == PlayerClassType.ANIMATED) 5000L else 10000L
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(if (classType == PlayerClassType.ANIMATED) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val bandwidthMeter = createBandwidthMeter(context)
        val loadControl = createAggressiveLoadControl()
        val effectiveTrackSelector = trackSelector ?: DefaultTrackSelector(context, AdaptiveTrackSelection.Factory())

        val builder = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(effectiveTrackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(seekIncrement)
            .setSeekForwardIncrementMs(seekIncrement)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)

        return builder.build().apply {
            volume = 1.0f
            playWhenReady = true
        }
    }

    /**
     * Formats milliseconds into human-readable MM:SS or HH:MM:SS format with Bengali or English digits.
     */
    fun formatTime(timeMs: Long, inBengali: Boolean = false): String {
        if (timeMs <= 0) return if (inBengali) "০০:০০" else "00:00"
        val totalSeconds = (timeMs / 1000).toInt()
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        val formatted = if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

        return if (inBengali) toBengaliDigits(formatted) else formatted
    }

    private fun toBengaliDigits(input: String): String {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder()
        for (char in input) {
            if (char in '0'..'9') {
                sb.append(banglaDigits[char - '0'])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }
}
