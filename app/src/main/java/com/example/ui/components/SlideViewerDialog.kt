package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.database.AppDatabase
import com.example.database.DownloadedItemEntity
import com.example.database.SavedItemDao
import com.example.database.SavedItemEntity
import com.example.download.AppFileDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "SlideViewerDialog"

enum class PdfViewMode {
    VERTICAL_SCROLL,   // Continuous vertical scrolling with multi-page zoom
    HORIZONTAL_SLIDES  // Single-slide presentation mode (swipe left / right)
}

enum class ReadingTheme {
    DAY,    // Crisp white paper on dark slate canvas
    SEPIA,  // Eye-comfort warm paper
    NIGHT   // Inverted AMOLED dark mode
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * High-performance PdfRenderer controller with LruCache, aspect-ratio pre-probing,
 * and on-demand high-DPI re-rendering when zoomed in.
 */
class PdfPageRenderer(
    val file: File,
    private val context: Context
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var pageCount by mutableIntStateOf(0)
        private set

    val pageAspectRatios = mutableStateMapOf<Int, Float>()
    private var defaultAspectRatio by mutableFloatStateOf(1.777f)

    // Memory-bounded LruCache (up to 64 MB or 1/5th of max heap) for crisp ARGB_8888 slides
    private val maxCacheBytes: Int = ((Runtime.getRuntime().maxMemory() / 5L)
        .coerceIn(24L * 1024L * 1024L, 64L * 1024L * 1024L)).toInt()

    private val cache = object : LruCache<Int, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.allocationByteCount.coerceAtLeast(1)
        }
    }

    private val lock = Any()

    var isInitialized by mutableStateOf(false)
        private set
    var initError by mutableStateOf<String?>(null)
        private set

    fun getAspectRatio(pageIndex: Int): Float {
        return pageAspectRatios[pageIndex] ?: defaultAspectRatio
    }

    suspend fun init() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            if (!file.exists() || file.length() == 0L) {
                withContext(Dispatchers.Main) {
                    initError = "পিডিএফ ফাইলটি পাওয়া যায়নি বা ফাইলের সাইজ শূন্য।"
                }
                return@withContext
            }
            val openedPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val createdRenderer = PdfRenderer(openedPfd)
            val count = createdRenderer.pageCount
            pfd = openedPfd
            renderer = createdRenderer

            var firstAspect = 1.777f
            if (count > 0) {
                synchronized(lock) {
                    try {
                        val firstPage = createdRenderer.openPage(0)
                        val w = firstPage.width.toFloat()
                        val h = firstPage.height.toFloat()
                        if (h > 0f && w > 0f) {
                            firstAspect = w / h
                        }
                        firstPage.close()
                    } catch (_: Exception) {}
                }
            }

            withContext(Dispatchers.Main) {
                defaultAspectRatio = firstAspect
                if (count > 0) {
                    pageAspectRatios[0] = firstAspect
                }
                pageCount = count
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PdfRenderer: ${e.message}", e)
            withContext(Dispatchers.Main) {
                initError = "পিডিএফ ফাইলটি রিড করতে ব্যর্থ হয়েছে: ${e.localizedMessage ?: "অজ্ঞাত ত্রুটি"}"
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int, forceHighRes: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        if (!isInitialized || pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        val desiredWidth = if (forceHighRes) {
            targetWidthPx.coerceIn(1600, 2800)
        } else {
            targetWidthPx.coerceIn(1180, 1720)
        }

        cache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled && cached.width >= (desiredWidth * 0.85f).toInt()) {
                return@withContext cached
            }
        }

        val activeRenderer = renderer ?: return@withContext null
        ensureActive()

        var detectedAspect: Float? = null
        val renderedBitmap: Bitmap? = synchronized(lock) {
            try {
                cache.get(pageIndex)?.let { cached ->
                    if (!cached.isRecycled && cached.width >= (desiredWidth * 0.85f).toInt()) {
                        return@synchronized cached
                    }
                }

                val page = activeRenderer.openPage(pageIndex)
                try {
                    val pWidth = page.width
                    val pHeight = page.height
                    val aspect = if (pHeight > 0 && pWidth > 0) pWidth.toFloat() / pHeight.toFloat() else defaultAspectRatio
                    detectedAspect = aspect

                    // CRITICAL: Android's PdfRenderer.Page.render STRICTLY requires Bitmap.Config.ARGB_8888
                    val renderWidth = desiredWidth
                    val renderHeight = (renderWidth / aspect).roundToInt().coerceAtLeast(100)

                    val bitmap = try {
                        Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    } catch (_: OutOfMemoryError) {
                        cache.evictAll()
                        val fallbackWidth = 1000
                        val fallbackHeight = (fallbackWidth / aspect).roundToInt().coerceAtLeast(100)
                        Bitmap.createBitmap(fallbackWidth, fallbackHeight, Bitmap.Config.ARGB_8888)
                    }

                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    cache.put(pageIndex, bitmap)
                    bitmap
                } finally {
                    try {
                        page.close()
                    } catch (_: Exception) {}
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error rendering page $pageIndex: ${e.message}", e)
                null
            }
        }

        if (detectedAspect != null) {
            withContext(Dispatchers.Main.immediate) {
                if (pageAspectRatios[pageIndex] != detectedAspect) {
                    pageAspectRatios[pageIndex] = detectedAspect!!
                }
            }
        }

        renderedBitmap
    }

    fun getCachedBitmap(pageIndex: Int): Bitmap? {
        val bmp = cache.get(pageIndex)
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun close() {
        synchronized(lock) {
            try {
                renderer?.close()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
            renderer = null
            pfd = null
            cache.evictAll()
            isInitialized = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerContent(
    slideUrl: String,
    title: String,
    initialRemoteUrl: String? = null,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val activity = remember(context) { context.findActivity() }

    val configuration = LocalConfiguration.current
    val isSystemLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isLandscapeRequested by rememberSaveable { mutableStateOf(false) }
    val isLandscapeMode = isLandscapeRequested || isSystemLandscape

    // Restore original orientation and system bars only when leaving the PDF viewer completely
    DisposableEffect(Unit) {
        onDispose {
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.window?.let { actWin ->
                    val controller = WindowCompat.getInsetsController(actWin, actWin.decorView)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            } catch (_: Exception) {}
        }
    }

    // Normalize inputs
    val isDirectLocal = remember(slideUrl) {
        slideUrl.startsWith("/") || slideUrl.startsWith("file://")
    }
    val directCleanPath = remember(slideUrl, isDirectLocal) {
        if (isDirectLocal) {
            if (slideUrl.startsWith("file://")) slideUrl.removePrefix("file://") else slideUrl
        } else ""
    }

    val remoteCandidateUrl = remember(slideUrl, initialRemoteUrl, isDirectLocal) {
        when {
            !isDirectLocal && (slideUrl.startsWith("http://") || slideUrl.startsWith("https://")) -> slideUrl
            !initialRemoteUrl.isNullOrBlank() && (initialRemoteUrl.startsWith("http://") || initialRemoteUrl.startsWith("https://")) -> initialRemoteUrl
            else -> null
        }
    }

    val downloadId = remember(remoteCandidateUrl, title) {
        if (!remoteCandidateUrl.isNullOrBlank()) {
            "pdf_" + (remoteCandidateUrl.hashCode().toString() + "_" + title.hashCode().toString()).replace("-", "n")
        } else {
            "pdf_local_" + (slideUrl.hashCode().toString() + "_" + title.hashCode().toString()).replace("-", "n")
        }
    }

    val downloadedItemById by downloadManager.getDownloadedItemById(downloadId).collectAsState(initial = null)
    val downloadedItemByPath by if (directCleanPath.isNotBlank()) {
        downloadManager.getDownloadedItemByPath(directCleanPath).collectAsState(initial = null)
    } else {
        remember { mutableStateOf<DownloadedItemEntity?>(null) }
    }

    val effectiveDownloadedItem = downloadedItemById ?: downloadedItemByPath

    // Remote downloading to cache state
    var isFetchingRemote by remember { mutableStateOf(false) }
    var remoteDownloadProgress by remember { mutableFloatStateOf(0f) }
    var remoteErrorMessage by remember { mutableStateOf<String?>(null) }
    var cachedFileState by remember { mutableStateOf<File?>(null) }

    // User viewing preferences (persisted across rotation via rememberSaveable)
    var viewModeOrdinal by rememberSaveable { mutableIntStateOf(PdfViewMode.VERTICAL_SCROLL.ordinal) }
    val viewMode = PdfViewMode.entries.getOrElse(viewModeOrdinal) { PdfViewMode.VERTICAL_SCROLL }

    var readingThemeOrdinal by rememberSaveable { mutableIntStateOf(ReadingTheme.DAY.ordinal) }
    val readingTheme = ReadingTheme.entries.getOrElse(readingThemeOrdinal) { ReadingTheme.DAY }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var currentSavedPage by rememberSaveable(slideUrl) { mutableIntStateOf(0) }

    var showShareBottomSheet by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var canViewProfiles by remember {
        mutableStateOf(com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context))
    }

    LaunchedEffect(slideUrl) {
        val freshPerm = com.example.security.DeviceActivationRepository.checkProfileViewPermission(
            context = context,
            forceRefresh = true
        )
        canViewProfiles = freshPerm
        if (!freshPerm) {
            showShareBottomSheet = false
        }
    }

    // Resolve target file strictly for in-app viewing
    val targetPdfFile = remember(
        isDirectLocal,
        directCleanPath,
        effectiveDownloadedItem,
        cachedFileState
    ) {
        when {
            isDirectLocal && directCleanPath.isNotBlank() -> {
                val f = File(directCleanPath)
                if (f.exists() && f.length() > 0) f else null
            }
            effectiveDownloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED &&
                    !effectiveDownloadedItem.localFilePath.isNullOrBlank() -> {
                val f = File(effectiveDownloadedItem.localFilePath!!)
                if (f.exists() && f.length() > 0) f else null
            }
            cachedFileState != null && cachedFileState!!.exists() && cachedFileState!!.length() > 0 -> {
                cachedFileState
            }
            else -> null
        }
    }

    // Auto download remote PDF into cache if not available locally
    LaunchedEffect(targetPdfFile, remoteCandidateUrl) {
        if (targetPdfFile == null && !remoteCandidateUrl.isNullOrBlank() && !isFetchingRemote) {
            isFetchingRemote = true
            remoteDownloadProgress = 0f
            remoteErrorMessage = null

            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val cacheDir = File(context.cacheDir, "pdf_preview_cache").apply { mkdirs() }
                val safeFileName = "slide_${abs(remoteCandidateUrl.hashCode())}.pdf"
                val tempFile = File(cacheDir, safeFileName)

                if (tempFile.exists() && tempFile.length() > 0) {
                    cachedFileState = tempFile
                    isFetchingRemote = false
                    return@LaunchedEffect
                }

                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(remoteCandidateUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android) ShikhoApp/1.0")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw Exception("সার্ভার থেকে ফাইলটি লোড করা যায়নি (HTTP ${response.code})")
                    }

                    val body = response.body ?: throw Exception("সার্ভার থেকে খালি রেসপন্স এসেছে")
                    val contentLength = body.contentLength()
                    val inputStream: InputStream = body.byteStream()
                    val partFile = File(cacheDir, "${safeFileName}.part")
                    val outputStream = FileOutputStream(partFile)

                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    inputStream.use { input ->
                        outputStream.use { output ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (contentLength > 0) {
                                    val prog = totalRead.toFloat() / contentLength.toFloat()
                                    withContext(Dispatchers.Main) {
                                        remoteDownloadProgress = prog
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (partFile.exists() && partFile.length() > 0L) {
                        if (tempFile.exists()) tempFile.delete()
                        partFile.renameTo(tempFile)
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    cachedFileState = tempFile
                    isFetchingRemote = false
                } else {
                    throw Exception("খালি বা অসম্পূর্ণ ফাইল ডাউনলোড হয়েছে")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading PDF preview: ${e.message}", e)
                isFetchingRemote = false
                remoteErrorMessage = e.localizedMessage ?: "পিডিএফ লোড করা সম্ভব হয়নি"
            }
        }
    }

    // Pdf renderer state
    val rendererState = remember(targetPdfFile) {
        targetPdfFile?.let { PdfPageRenderer(it, context) }
    }

    LaunchedEffect(rendererState) {
        rendererState?.init()
    }

    DisposableEffect(rendererState) {
        onDispose {
            rendererState?.close()
        }
    }

    val isOfflineAvailable = (isDirectLocal && targetPdfFile != null) ||
            (effectiveDownloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED)

    val savedDao: SavedItemDao = remember { AppDatabase.getDatabase(context).savedItemDao() }
    var isSlideBookmarked by remember { mutableStateOf(false) }
    val bookmarkItemId = remember(downloadId) { "note_$downloadId" }
    LaunchedEffect(bookmarkItemId) {
        savedDao.isItemSaved(bookmarkItemId).collect {
            isSlideBookmarked = it
        }
    }

    // Manage system bars and display cutout for full-bleed zero-gap rendering
    LaunchedEffect(isLandscapeMode, isFullscreen, configuration.orientation) {
        activity?.window?.let { actWin ->
            WindowCompat.setDecorFitsSystemWindows(actWin, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                actWin.attributes = actWin.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val controller = WindowCompat.getInsetsController(actWin, actWin.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isLandscapeMode || isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val canvasBgColor = when (readingTheme) {
        ReadingTheme.DAY -> Color(0xFF0B1120)
        ReadingTheme.SEPIA -> Color(0xFF1C1917)
        ReadingTheme.NIGHT -> Color(0xFF000000)
    }

    var topBarHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBgColor)
            .clipToBounds()
    ) {
        // 1. Main Full-Bleed Viewer Area
        when {
            isFetchingRemote -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RemoteLoadingCard(
                        progress = remoteDownloadProgress,
                        title = title
                    )
                }
            }

            remoteErrorMessage != null || rendererState?.initError != null -> {
                val err = remoteErrorMessage ?: rendererState?.initError ?: "অজানা ত্রুটি"
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PdfErrorCard(
                        errorMessage = err,
                        onRetry = {
                            remoteErrorMessage = null
                            cachedFileState = null
                        }
                    )
                }
            }

            rendererState != null && rendererState.isInitialized && rendererState.pageCount > 0 -> {
                NativePdfViewerContent(
                    renderer = rendererState,
                    viewMode = viewMode,
                    readingTheme = readingTheme,
                    isFullscreen = isFullscreen,
                    isLandscape = isLandscapeMode,
                    topBarHeightPx = topBarHeightPx,
                    currentSavedPage = currentSavedPage,
                    onCurrentPageChanged = { pageIdx -> currentSavedPage = pageIdx },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onOpenThumbnails = { showThumbnailsSheet = true },
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF38BDF8),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "পিডিএফ রিডার প্রস্তুত হচ্ছে...",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. Top Header Bar (Overlays smoothly at top, hides in Fullscreen mode)
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PdfViewerTopBar(
                title = title,
                isOffline = isOfflineAvailable,
                currentPage = (currentSavedPage + 1).coerceAtLeast(1),
                pageCount = rendererState?.pageCount ?: 0,
                viewMode = viewMode,
                readingTheme = readingTheme,
                isLandscape = isLandscapeMode,
                downloadedItem = effectiveDownloadedItem,
                canShare = canViewProfiles,
                isBookmarked = isSlideBookmarked,
                onDismiss = onBack,
                onOpenThumbnails = {
                    if ((rendererState?.pageCount ?: 0) > 0) {
                        showThumbnailsSheet = true
                    }
                },
                onToggleOrientation = {
                    val newLandscape = !isLandscapeMode
                    isLandscapeRequested = newLandscape
                    activity?.requestedOrientation = if (newLandscape) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                },
                onShareClick = {
                    if (canViewProfiles) {
                        showShareBottomSheet = true
                    }
                },
                onToggleViewMode = {
                    viewModeOrdinal = if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                        PdfViewMode.HORIZONTAL_SLIDES.ordinal
                    } else {
                        PdfViewMode.VERTICAL_SCROLL.ordinal
                    }
                },
                onCycleTheme = {
                    readingThemeOrdinal = when (readingTheme) {
                        ReadingTheme.DAY -> ReadingTheme.SEPIA.ordinal
                        ReadingTheme.SEPIA -> ReadingTheme.NIGHT.ordinal
                        ReadingTheme.NIGHT -> ReadingTheme.DAY.ordinal
                    }
                },
                onBookmarkClick = {
                    coroutineScope.launch {
                        if (isSlideBookmarked) {
                            savedDao.deleteSavedItemById(bookmarkItemId)
                            Toast.makeText(context, "সংরক্ষণ থেকে সরানো হয়েছে", Toast.LENGTH_SHORT).show()
                        } else {
                            val targetUrl = remoteCandidateUrl ?: effectiveDownloadedItem?.remoteUrl ?: slideUrl
                            val entity = SavedItemEntity(
                                id = bookmarkItemId,
                                type = SavedItemEntity.TYPE_NOTE,
                                title = title.ifBlank { "লেকচার স্লাইড ও নোট" },
                                subtitle = "লেকচার নোট ও স্লাইড",
                                contentJson = SavedItemEntity.buildNoteContentJson(
                                    url = targetUrl,
                                    pdfTitle = title
                                ),
                                timestamp = System.currentTimeMillis()
                            )
                            savedDao.insertSavedItem(entity)
                            Toast.makeText(context, "লেকচার নোটটি সফলভাবে সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDownloadClick = {
                    val urlToDownload = remoteCandidateUrl ?: effectiveDownloadedItem?.remoteUrl
                    if (!urlToDownload.isNullOrBlank()) {
                        downloadManager.downloadFile(
                            id = downloadId,
                            title = title,
                            subtitle = "পিডিএফ লেকচার নোট",
                            fileType = DownloadedItemEntity.FILE_TYPE_PDF,
                            remoteUrl = urlToDownload
                        )
                        Toast.makeText(context, "ইন-অ্যাপ অফলাইন ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "এই ফাইলটি ইতোমধ্যে অ্যাপে অফলাইনে সংরক্ষিত রয়েছে", Toast.LENGTH_SHORT).show()
                    }
                },
                onHeightMeasured = { measured -> topBarHeightPx = measured }
            )
        }
    }

    // Thumbnail Page Grid Sheet
    if (showThumbnailsSheet && rendererState != null && rendererState.isInitialized) {
        PdfThumbnailsBottomSheet(
            renderer = rendererState,
            currentPageIndex = currentSavedPage,
            onSelectPage = { selectedIdx ->
                currentSavedPage = selectedIdx
                showThumbnailsSheet = false
            },
            onDismiss = { showThumbnailsSheet = false }
        )
    }

    // Share Bottom Sheet
    if (showShareBottomSheet && canViewProfiles) {
        PdfShareBottomSheet(
            title = title,
            pdfFile = targetPdfFile,
            pdfUrl = remoteCandidateUrl ?: effectiveDownloadedItem?.remoteUrl,
            canShare = canViewProfiles,
            onDismiss = { showShareBottomSheet = false }
        )
    }
}

@Composable
fun SlideViewerDialog(
    slideUrl: String,
    title: String,
    initialRemoteUrl: String? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            decorFitsSystemWindows = false
        )
    ) {
        val configuration = LocalConfiguration.current
        val dialogView = LocalView.current
        val dialogWindow = remember(dialogView) {
            var parent = dialogView.parent
            var win: android.view.Window? = null
            while (parent != null) {
                if (parent is DialogWindowProvider) {
                    win = parent.window
                    break
                }
                parent = parent.parent
            }
            win
        }

        // Enforce 100% edge-to-edge zero-margin window on initial composition & every orientation change
        LaunchedEffect(dialogWindow, configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
            dialogWindow?.let { win ->
                win.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
                win.decorView.setBackgroundColor(AndroidColor.BLACK)
                win.decorView.setPadding(0, 0, 0, 0)
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.addFlags(
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND
                )
                win.setDimAmount(1.0f)
                win.statusBarColor = AndroidColor.BLACK
                win.navigationBarColor = AndroidColor.BLACK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    win.attributes = win.attributes.apply {
                        width = WindowManager.LayoutParams.MATCH_PARENT
                        height = WindowManager.LayoutParams.MATCH_PARENT
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                win.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
        }

        SideEffect {
            dialogWindow?.let { win ->
                win.decorView.setPadding(0, 0, 0, 0)
                win.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
        }

        PdfViewerContent(
            slideUrl = slideUrl,
            title = title,
            initialRemoteUrl = initialRemoteUrl,
            onBack = onDismiss
        )
    }
}

@Composable
private fun PdfViewerTopBar(
    title: String,
    isOffline: Boolean,
    currentPage: Int,
    pageCount: Int,
    viewMode: PdfViewMode,
    readingTheme: ReadingTheme,
    isLandscape: Boolean,
    downloadedItem: DownloadedItemEntity?,
    canShare: Boolean = false,
    isBookmarked: Boolean = false,
    onBookmarkClick: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenThumbnails: () -> Unit,
    onToggleOrientation: () -> Unit,
    onShareClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onCycleTheme: () -> Unit,
    onDownloadClick: () -> Unit,
    onHeightMeasured: (Int) -> Unit = {}
) {
    Surface(
        color = Color(0xFF0B1120).copy(alpha = 0.96f),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height) }
    ) {
        val actionIconsRow: @Composable () -> Unit = {
            // 1. All Pages Grid / Thumbnail Navigator
            if (pageCount > 1) {
                IconButton(
                    onClick = onOpenThumbnails,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "সকল স্লাইড দেখুন",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // 2. Landscape / Orientation Toggle
            IconButton(
                onClick = onToggleOrientation,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isLandscape) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenRotation,
                    contentDescription = if (isLandscape) "পোর্ট্রেট মোড" else "ল্যান্ডস্কেপ মোড",
                    tint = if (isLandscape) Color(0xFF38BDF8) else Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            // 3. Mode Switcher: Continuous Vertical Scroll vs Horizontal Slide Presentation
            IconButton(
                onClick = onToggleViewMode,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (viewMode == PdfViewMode.VERTICAL_SCROLL) Icons.Default.ViewCarousel else Icons.Default.ViewAgenda,
                    contentDescription = if (viewMode == PdfViewMode.VERTICAL_SCROLL) "স্লাইড মোড" else "স্ক্রল মোড",
                    tint = if (viewMode == PdfViewMode.HORIZONTAL_SLIDES) Color(0xFF38BDF8) else Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            // 4. Reading Theme Switcher: Day -> Sepia -> Night
            IconButton(
                onClick = onCycleTheme,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = when (readingTheme) {
                        ReadingTheme.DAY -> Icons.Default.WbSunny
                        ReadingTheme.SEPIA -> Icons.Default.AutoStories
                        ReadingTheme.NIGHT -> Icons.Default.NightsStay
                    },
                    contentDescription = "রিডিং থিম",
                    tint = when (readingTheme) {
                        ReadingTheme.DAY -> Color(0xFFFBBF24)
                        ReadingTheme.SEPIA -> Color(0xFFFDE68A)
                        ReadingTheme.NIGHT -> Color(0xFF93C5FD)
                    },
                    modifier = Modifier.size(19.dp)
                )
            }

            // 5. Share Button (if permitted)
            if (canShare) {
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "শেয়ার করুন",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        val downloadAndBookmarkRow: @Composable () -> Unit = {
            // 6. Offline Download Action
            when (downloadedItem?.status) {
                DownloadedItemEntity.STATUS_DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadedItem.progressFraction },
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DownloadedItemEntity.STATUS_COMPLETED -> {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "অফলাইনে সংরক্ষিত",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                else -> {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "অফলাইন ডাউনলোড",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // 7. Bookmark Button
            IconButton(
                onClick = onBookmarkClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "সংরক্ষণ থেকে সরান" else "নোট সংরক্ষণ করুন",
                    tint = if (isBookmarked) Color(0xFFF59E0B) else Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .testTag("pdf_viewer_close_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "বন্ধ করুন",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "লেকচার স্লাইড ও পিডিএফ" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isOffline) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "✓ অফলাইন",
                                    fontSize = 10.sp,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (pageCount > 0) {
                            Text(
                                text = "পৃষ্ঠা ${toBengaliDigits(currentPage)} / ${toBengaliDigits(pageCount)}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    actionIconsRow()
                    downloadAndBookmarkRow()
                }
            }
        } else {
            // Portrait: Spacious 2-Row Top Bar so Title & Offline badge NEVER wrap or crush
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // Row 1: Back + Full Title + Download & Bookmark
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("pdf_viewer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "বন্ধ করুন",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = title.ifBlank { "লেকচার স্লাইড ও পিডিএফ" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        downloadAndBookmarkRow()
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )

                // Row 2: Offline Status + Page Counter + Scrollable Tools Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 6.dp, top = 3.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isOffline) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                border = BorderStroke(0.6.dp, Color(0xFF34D399).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "✓ অফলাইন",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (pageCount > 0) {
                            Text(
                                text = "পৃষ্ঠা ${toBengaliDigits(currentPage)} / ${toBengaliDigits(pageCount)}",
                                fontSize = 11.5.sp,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        actionIconsRow()
                    }
                }
            }
        }
    }
}

private fun computeSlideMaxPan(
    targetScale: Float,
    aspectRatio: Float,
    containerW: Float,
    containerH: Float
): Pair<Float, Float> {
    if (targetScale <= 1f || containerW <= 0f || containerH <= 0f) return 0f to 0f
    val safeAspect = aspectRatio.coerceAtLeast(0.25f)
    val containerAspect = containerW / containerH
    val renderedW: Float
    val renderedH: Float
    if (safeAspect > containerAspect) {
        renderedW = containerW
        renderedH = containerW / safeAspect
    } else {
        renderedH = containerH
        renderedW = containerH * safeAspect
    }
    val maxX = ((renderedW * targetScale) - containerW).coerceAtLeast(0f) / 2f
    val maxY = ((renderedH * targetScale) - containerH).coerceAtLeast(0f) / 2f
    return maxX to maxY
}

/**
 * Core PDF & Slide Reader Engine:
 * - True multi-page zoom + scroll in VERTICAL_SCROLL mode: zooming scales each page's layout height
 *   and pans horizontally within clamped bounds, while vertical drag/fling natively scrolls LazyColumn
 *   so every page from 1 to N loads and scrolls seamlessly at any zoom level (100% - 400%).
 * - Preserves exact page position when switching between Vertical Scroll and Horizontal Slide modes
 *   or rotating between Portrait and Landscape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativePdfViewerContent(
    renderer: PdfPageRenderer,
    viewMode: PdfViewMode,
    readingTheme: ReadingTheme,
    isFullscreen: Boolean,
    isLandscape: Boolean,
    topBarHeightPx: Int = 0,
    currentSavedPage: Int,
    onCurrentPageChanged: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenThumbnails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val safeInitialPage = currentSavedPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
    val verticalListState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitialPage)
    val horizontalPagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { renderer.pageCount }
    )

    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var slidePanOffsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showJumpDialog by remember { mutableStateOf(false) }

    // Sync active page between Vertical Scroll and Horizontal Pager when viewMode changes
    LaunchedEffect(viewMode) {
        val targetIdx = currentSavedPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
        if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
            if (verticalListState.firstVisibleItemIndex != targetIdx) {
                verticalListState.scrollToItem(targetIdx)
            }
        } else {
            if (horizontalPagerState.currentPage != targetIdx) {
                horizontalPagerState.scrollToPage(targetIdx)
            }
        }
    }

    // Also scroll when currentSavedPage is updated externally (e.g. from Thumbnail Sheet)
    LaunchedEffect(currentSavedPage) {
        val targetIdx = currentSavedPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
        if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
            val visibleIndices = verticalListState.layoutInfo.visibleItemsInfo.map { it.index }
            if (targetIdx !in visibleIndices && !verticalListState.isScrollInProgress) {
                verticalListState.scrollToItem(targetIdx)
            }
        } else {
            if (horizontalPagerState.currentPage != targetIdx && !horizontalPagerState.isScrollInProgress) {
                horizontalPagerState.scrollToPage(targetIdx)
            }
        }
    }

    // Track current visible page index (0-based)
    val currentVisiblePageIndex by remember(viewMode, renderer.pageCount) {
        derivedStateOf {
            if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                val layoutInfo = verticalListState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centerItem = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                }
                (centerItem?.index ?: verticalListState.firstVisibleItemIndex)
                    .coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
            } else {
                horizontalPagerState.currentPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(currentVisiblePageIndex) {
        onCurrentPageChanged(currentVisiblePageIndex)
    }

    val currentPageAspect = renderer.getAspectRatio(currentVisiblePageIndex).coerceAtLeast(0.25f)

    // Clamp horizontal & slide pan whenever scale, container dimensions, or page aspect changes
    val maxPanX = remember(scale, viewMode, currentPageAspect, containerSize) {
        if (scale <= 1f || containerSize.width <= 0) 0f
        else if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
            (containerSize.width * (scale - 1f)) / 2f
        } else {
            computeSlideMaxPan(scale, currentPageAspect, containerSize.width.toFloat(), containerSize.height.toFloat()).first
        }
    }
    val maxSlidePanY = remember(scale, viewMode, currentPageAspect, containerSize) {
        if (scale <= 1f || viewMode == PdfViewMode.VERTICAL_SCROLL || containerSize.height <= 0) 0f
        else {
            computeSlideMaxPan(scale, currentPageAspect, containerSize.width.toFloat(), containerSize.height.toFloat()).second
        }
    }

    LaunchedEffect(maxPanX, maxSlidePanY, scale) {
        if (scale <= 1.01f) {
            panOffsetX = 0f
            slidePanOffsetY = 0f
        } else {
            panOffsetX = panOffsetX.coerceIn(-maxPanX, maxPanX)
            slidePanOffsetY = slidePanOffsetY.coerceIn(-maxSlidePanY, maxSlidePanY)
        }
    }

    val currentVisiblePageNumber = currentVisiblePageIndex + 1

    val displayWidthPx = context.resources.displayMetrics.widthPixels
    val baseRenderWidthPx = remember(displayWidthPx) {
        (displayWidthPx * 1.6f).roundToInt().coerceIn(1280, 1720)
    }

    // ColorFilter for Night / Sepia Reading themes
    val colorFilter = remember(readingTheme) {
        when (readingTheme) {
            ReadingTheme.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f
                    )
                )
            )
            ReadingTheme.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0f,    0f,    0f, 18f,
                        0f,    0.89f, 0f,    0f, 12f,
                        0f,    0f,    0.76f, 0f,  0f,
                        0f,    0f,    0f,    1f,  0f
                    )
                )
            )
            ReadingTheme.DAY -> null
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            totalPages = renderer.pageCount,
            currentPage = currentVisiblePageNumber,
            onDismiss = { showJumpDialog = false },
            onPageSelected = { targetPage ->
                showJumpDialog = false
                val idx = (targetPage - 1).coerceIn(0, renderer.pageCount - 1)
                onCurrentPageChanged(idx)
                coroutineScope.launch {
                    if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                        verticalListState.scrollToItem(idx)
                    } else {
                        horizontalPagerState.scrollToPage(idx)
                    }
                }
            }
        )
    }

    val measuredTopBarDp = with(density) { topBarHeightPx.toDp() }
    val topBarPadding = if (!isFullscreen) {
        if (topBarHeightPx > 0) measuredTopBarDp
        else if (isLandscape) 52.dp else 118.dp
    } else 0.dp

    val bottomContentClearance = if (!isFullscreen) {
        if (renderer.pageCount > 1 && !isLandscape) 128.dp else 92.dp
    } else 16.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .clipToBounds()
            // Initial-pass Multi-touch Pinch-Zoom & Smart Pan Handler (never loses pinch to LazyColumn/HorizontalPager!)
            .pointerInput(viewMode, renderer) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var gestureTotalPanX = 0f
                    var gestureTotalPanY = 0f
                    var isPinching = false
                    var lockedHorizontalPan = false

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val activePointers = event.changes.count { it.pressed }

                        if (activePointers >= 2) {
                            isPinching = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val oldScale = scale
                            val newScale = (oldScale * zoomChange).coerceIn(1f, 4.0f)
                            val scaleRatio = if (oldScale > 0f) newScale / oldScale else 1f

                            val aspect = renderer.getAspectRatio(currentVisiblePageIndex).coerceAtLeast(0.25f)
                            val (newMaxX, newMaxSlideY) = if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                (if (newScale > 1f) (w * (newScale - 1f)) / 2f else 0f) to 0f
                            } else {
                                computeSlideMaxPan(newScale, aspect, w, h)
                            }

                            val focusX = if (centroid != Offset.Unspecified) centroid.x - w / 2f else 0f
                            val updatedPanX = (panOffsetX - focusX) * scaleRatio + focusX + panChange.x

                            scale = newScale
                            panOffsetX = if (newScale > 1.01f) updatedPanX.coerceIn(-newMaxX, newMaxX) else 0f

                            if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                // Keep the vertical point under the pinch fingers anchored smoothly as page height expands
                                val firstItemTop = verticalListState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.toFloat() ?: 0f
                                val focusYFromTop = if (centroid != Offset.Unspecified) {
                                    (centroid.y - firstItemTop).coerceIn(0f, h * 2f)
                                } else h / 2f
                                val zoomScrollDelta = focusYFromTop * (scaleRatio - 1f)
                                val combinedDeltaY = (zoomScrollDelta - panChange.y).coerceIn(-h, h)
                                if (abs(combinedDeltaY) > 0.25f) {
                                    verticalListState.dispatchRawDelta(combinedDeltaY)
                                }
                            } else {
                                val focusY = if (centroid != Offset.Unspecified) centroid.y - h / 2f else 0f
                                val updatedPanY = (slidePanOffsetY - focusY) * scaleRatio + focusY + panChange.y
                                slidePanOffsetY = if (newScale > 1.01f) {
                                    updatedPanY.coerceIn(-newMaxSlideY, newMaxSlideY)
                                } else 0f
                            }

                            // Consume multi-touch events in Initial pass so children never steal pinch
                            event.changes.forEach { change: PointerInputChange ->
                                change.consume()
                            }
                        } else if (activePointers == 1 && !isPinching && scale > 1.01f) {
                            val panChange = event.calculatePan()
                            if (panChange != Offset.Zero) {
                                gestureTotalPanX += panChange.x
                                gestureTotalPanY += panChange.y
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val aspect = renderer.getAspectRatio(currentVisiblePageIndex).coerceAtLeast(0.25f)

                                if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                    val curMaxX = (w * (scale - 1f)) / 2f
                                    if (!lockedHorizontalPan &&
                                        abs(gestureTotalPanX) > touchSlop &&
                                        abs(gestureTotalPanX) > abs(gestureTotalPanY) * 1.15f
                                    ) {
                                        lockedHorizontalPan = true
                                    }
                                    if (lockedHorizontalPan && curMaxX > 0f) {
                                        val nextX = (panOffsetX + panChange.x).coerceIn(-curMaxX, curMaxX)
                                        panOffsetX = nextX
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                } else {
                                    val (curMaxX, curMaxSlideY) = computeSlideMaxPan(scale, aspect, w, h)
                                    val pastSlop = abs(gestureTotalPanX) > touchSlop || abs(gestureTotalPanY) > touchSlop
                                    if (pastSlop) {
                                        panOffsetX = (panOffsetX + panChange.x).coerceIn(-curMaxX, curMaxX)
                                        slidePanOffsetY = (slidePanOffsetY + panChange.y).coerceIn(-curMaxSlideY, curMaxSlideY)
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // Tap & Double-Tap Gesture Detector (Double-tap zooms smoothly into the tapped focal point)
            .pointerInput(viewMode, renderer) {
                detectTapGestures(
                    onTap = {
                        onToggleFullscreen()
                    },
                    onDoubleTap = { tapOffset ->
                        val oldScale = scale
                        val nextScale = when {
                            oldScale < 1.5f -> 2.2f
                            oldScale < 2.8f -> 3.2f
                            else -> 1.0f
                        }
                        if (nextScale <= 1.01f) {
                            scale = 1.0f
                            panOffsetX = 0f
                            slidePanOffsetY = 0f
                        } else {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val scaleRatio = if (oldScale > 0f) nextScale / oldScale else 1f
                            val aspect = renderer.getAspectRatio(currentVisiblePageIndex).coerceAtLeast(0.25f)
                            val (newMaxPanX, newMaxSlidePanY) = if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                (w * (nextScale - 1f)) / 2f to 0f
                            } else {
                                computeSlideMaxPan(nextScale, aspect, w, h)
                            }
                            val focusX = tapOffset.x - w / 2f
                            val targetPanX = (panOffsetX - focusX) * scaleRatio + focusX
                            scale = nextScale
                            panOffsetX = targetPanX.coerceIn(-newMaxPanX, newMaxPanX)

                            if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                slidePanOffsetY = 0f
                                val firstItemTop = verticalListState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.toFloat() ?: 0f
                                val focusYFromTop = (tapOffset.y - firstItemTop).coerceIn(0f, h * 2f)
                                val scrollCompensation = focusYFromTop * (scaleRatio - 1f) + (tapOffset.y - h / 2f) * 0.35f
                                coroutineScope.launch {
                                    kotlinx.coroutines.yield()
                                    verticalListState.scrollBy(scrollCompensation)
                                }
                            } else {
                                val focusY = tapOffset.y - h / 2f
                                val targetPanY = (slidePanOffsetY - focusY) * scaleRatio + focusY
                                slidePanOffsetY = targetPanY.coerceIn(-newMaxSlidePanY, newMaxSlidePanY)
                            }
                        }
                    }
                )
            }
    ) {
        val availableWidthDp = maxWidth
        val availableHeightDp = maxHeight

        // Mode 1: Continuous Vertical Scrolling (True Multi-Page Zoom + Virtualized LazyColumn)
        if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
            LazyColumn(
                state = verticalListState,
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = topBarPadding + 4.dp,
                    bottom = bottomContentClearance
                ),
                verticalArrangement = Arrangement.spacedBy((6f * scale).dp),
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                items(
                    count = renderer.pageCount,
                    key = { pageIndex -> pageIndex }
                ) { pageIndex ->
                    val aspect = renderer.getAspectRatio(pageIndex).coerceAtLeast(0.25f)
                    val basePageHeightDp = availableWidthDp / aspect
                    val zoomedPageHeightDp = basePageHeightDp * scale

                    PdfPageCard(
                        pageIndex = pageIndex,
                        totalPages = renderer.pageCount,
                        renderer = renderer,
                        colorFilter = colorFilter,
                        readingTheme = readingTheme,
                        targetWidthPx = baseRenderWidthPx,
                        scale = scale,
                        panOffsetX = panOffsetX,
                        panOffsetY = 0f,
                        basePageWidthDp = availableWidthDp,
                        basePageHeightDp = basePageHeightDp,
                        zoomedPageHeightDp = zoomedPageHeightDp,
                        isCurrentVisiblePage = (pageIndex == currentVisiblePageIndex),
                        isSlideMode = false
                    )
                }
            }
        } else {
            // Mode 2: Horizontal Slide Presentation Mode
            HorizontalPager(
                state = horizontalPagerState,
                userScrollEnabled = scale <= 1.05f,
                contentPadding = PaddingValues(0.dp),
                pageSpacing = 8.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topBarPadding,
                        bottom = if (!isFullscreen) (if (renderer.pageCount > 1 && !isLandscape) 120.dp else 84.dp) else 0.dp
                    )
                    .clipToBounds()
            ) { pageIndex ->
                PdfPageCard(
                    pageIndex = pageIndex,
                    totalPages = renderer.pageCount,
                    renderer = renderer,
                    colorFilter = colorFilter,
                    readingTheme = readingTheme,
                    targetWidthPx = baseRenderWidthPx,
                    scale = scale,
                    panOffsetX = panOffsetX,
                    panOffsetY = slidePanOffsetY,
                    basePageWidthDp = availableWidthDp,
                    basePageHeightDp = availableHeightDp,
                    zoomedPageHeightDp = availableHeightDp,
                    isCurrentVisiblePage = (pageIndex == currentVisiblePageIndex),
                    isSlideMode = true
                )
            }
        }

        // Quick Floating "100% Reset Zoom" Pill when zoomed in during Fullscreen mode
        AnimatedVisibility(
            visible = isFullscreen && scale > 1.05f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        scale = 1f
                        panOffsetX = 0f
                        slidePanOffsetY = 0f
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${toBengaliDigits((scale * 100).roundToInt())}% • রিসেট",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Sleek Floating Bottom HUD: Scrubber Slider + Zoom Controls + Page Navigation
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0B1120).copy(alpha = 0.95f),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = if (isLandscape) 4.dp else 8.dp
                    )
                ) {
                    // Fast Page Scrubber Slider (no cramped fixed height so thumb & track never collide with controls)
                    if (renderer.pageCount > 1 && !isLandscape) {
                        var sliderValue by remember(currentVisiblePageIndex) {
                            mutableFloatStateOf(currentVisiblePageIndex.toFloat())
                        }
                        var isDraggingSlider by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "১",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Slider(
                                value = if (isDraggingSlider) sliderValue else currentVisiblePageIndex.toFloat(),
                                onValueChange = { newVal ->
                                    isDraggingSlider = true
                                    sliderValue = newVal
                                    val targetIdx = newVal.roundToInt().coerceIn(0, renderer.pageCount - 1)
                                    onCurrentPageChanged(targetIdx)
                                    coroutineScope.launch {
                                        if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                            verticalListState.scrollToItem(targetIdx)
                                        } else {
                                            horizontalPagerState.scrollToPage(targetIdx)
                                        }
                                    }
                                },
                                onValueChangeFinished = {
                                    isDraggingSlider = false
                                },
                                valueRange = 0f..(renderer.pageCount - 1).coerceAtLeast(1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = Color(0xFF38BDF8),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                thumb = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF38BDF8),
                                        border = BorderStroke(1.5.dp, Color.White),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.size(if (isDraggingSlider) 18.dp else 14.dp)
                                    ) {}
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                text = toBengaliDigits(renderer.pageCount),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Bottom Controls Row: Zoom Out | Zoom % Reset | Zoom In | Divider | Prev | Page Pill | Next
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // Zoom Out Button
                        IconButton(
                            onClick = {
                                val newScale = (scale - 0.25f).coerceAtLeast(1f)
                                scale = newScale
                                if (newScale <= 1.01f) {
                                    panOffsetX = 0f
                                    slidePanOffsetY = 0f
                                }
                            },
                            enabled = scale > 1.01f,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "জুম আউট",
                                tint = if (scale > 1.01f) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Zoom Level Pill (Tap to Reset to 100%)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (scale > 1.05f) Color(0xFF0284C7).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scale = 1f
                                    panOffsetX = 0f
                                    slidePanOffsetY = 0f
                                }
                        ) {
                            Text(
                                text = "${toBengaliDigits((scale * 100).roundToInt())}%",
                                color = if (scale > 1.05f) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.9f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Zoom In Button
                        IconButton(
                            onClick = {
                                scale = (scale + 0.25f).coerceAtMost(4.0f)
                            },
                            enabled = scale < 4.0f,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "জুম ইন",
                                tint = if (scale < 4.0f) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(18.dp)
                                .padding(horizontal = 6.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )

                        // Previous Page Button
                        IconButton(
                            onClick = {
                                val prev = (currentVisiblePageIndex - 1).coerceAtLeast(0)
                                onCurrentPageChanged(prev)
                                coroutineScope.launch {
                                    if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                        verticalListState.animateScrollToItem(prev)
                                    } else {
                                        horizontalPagerState.animateScrollToPage(prev)
                                    }
                                }
                            },
                            enabled = currentVisiblePageIndex > 0,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                                },
                                contentDescription = "আগের পৃষ্ঠা",
                                tint = if (currentVisiblePageIndex > 0) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        // Page Indicator Pill (Tap to open Jump Dialog)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showJumpDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${toBengaliDigits(currentVisiblePageNumber)} / ${toBengaliDigits(renderer.pageCount)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Next Page Button
                        IconButton(
                            onClick = {
                                val next = (currentVisiblePageIndex + 1).coerceAtMost(renderer.pageCount - 1)
                                onCurrentPageChanged(next)
                                coroutineScope.launch {
                                    if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                        verticalListState.animateScrollToItem(next)
                                    } else {
                                        horizontalPagerState.animateScrollToPage(next)
                                    }
                                }
                            },
                            enabled = currentVisiblePageIndex < renderer.pageCount - 1,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                    Icons.Default.KeyboardArrowDown
                                } else {
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                                },
                                contentDescription = "পরের পৃষ্ঠা",
                                tint = if (currentVisiblePageIndex < renderer.pageCount - 1) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual PDF Page Card:
 * - In VERTICAL_SCROLL mode: layout height scales dynamically with `scale` (`zoomedPageHeightDp`),
 *   so LazyColumn scrolls naturally across all pages even when zoomed to 3x or 4x!
 * - Automatically upgrades visible page bitmap to High-DPI when zoomed >= 1.75x.
 * - Uses smart debounce when not cached so fast flinging through 90 pages never stalls the renderer queue.
 */
@Composable
private fun PdfPageCard(
    pageIndex: Int,
    totalPages: Int,
    renderer: PdfPageRenderer,
    colorFilter: ColorFilter?,
    readingTheme: ReadingTheme,
    targetWidthPx: Int,
    scale: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    basePageWidthDp: androidx.compose.ui.unit.Dp,
    basePageHeightDp: androidx.compose.ui.unit.Dp,
    zoomedPageHeightDp: androidx.compose.ui.unit.Dp,
    isCurrentVisiblePage: Boolean,
    isSlideMode: Boolean = false
) {
    var pageBitmap by remember(pageIndex, renderer) {
        mutableStateOf(renderer.getCachedBitmap(pageIndex))
    }

    val aspectRatio = renderer.getAspectRatio(pageIndex).coerceAtLeast(0.25f)
    val needsHighRes = isCurrentVisiblePage && scale >= 1.55f
    val zoomTier = when {
        isCurrentVisiblePage && scale >= 2.6f -> 2
        isCurrentVisiblePage && scale >= 1.55f -> 1
        else -> 0
    }

    LaunchedEffect(pageIndex, renderer, renderer.isInitialized, targetWidthPx, zoomTier) {
        if (!renderer.isInitialized) return@LaunchedEffect

        val zoomWidthTarget = when (zoomTier) {
            2 -> (targetWidthPx * 1.75f).roundToInt()
            1 -> (targetWidthPx * 1.45f).roundToInt()
            else -> targetWidthPx
        }
        val currentBmp = pageBitmap
        if (currentBmp == null || currentBmp.isRecycled) {
            if (!isCurrentVisiblePage) {
                // Brief debounce for off-center pages so fast scrolling cancels intermediate renders
                delay(45L)
            }
            var bmp = renderer.renderPage(pageIndex, zoomWidthTarget, forceHighRes = needsHighRes)
            if (bmp == null) {
                delay(120L)
                bmp = renderer.renderPage(pageIndex, zoomWidthTarget, forceHighRes = needsHighRes)
            }
            if (bmp != null && !bmp.isRecycled) {
                pageBitmap = bmp
            }
        } else if (needsHighRes && currentBmp.width < zoomWidthTarget.coerceAtMost(2600)) {
            // Upgrade current zoomed page to crisp high-DPI after user settles on zoom
            delay(160L)
            val hiBmp = renderer.renderPage(pageIndex, zoomWidthTarget, forceHighRes = true)
            if (hiBmp != null && !hiBmp.isRecycled) {
                pageBitmap = hiBmp
            }
        }

        // Pre-warm adjacent pages for zero-wait scrolling
        if (isCurrentVisiblePage) {
            val nextIdx = pageIndex + 1
            if (nextIdx < renderer.pageCount && renderer.getCachedBitmap(nextIdx) == null) {
                renderer.renderPage(nextIdx, targetWidthPx, forceHighRes = false)
            }
            val prevIdx = pageIndex - 1
            if (prevIdx >= 0 && renderer.getCachedBitmap(prevIdx) == null) {
                renderer.renderPage(prevIdx, targetWidthPx, forceHighRes = false)
            }
        }
    }

    val cardColor = when (readingTheme) {
        ReadingTheme.DAY -> Color.White
        ReadingTheme.SEPIA -> Color(0xFFF7F3E9)
        ReadingTheme.NIGHT -> Color(0xFF141416)
    }

    if (isSlideMode) {
        // Slide Presentation Mode: Fits cleanly inside both width and height, zoomable inside container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1.77f
            val cardModifier = if (aspectRatio > containerRatio) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = cardColor,
                shadowElevation = 4.dp,
                modifier = cardModifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffsetX
                        translationY = panOffsetY
                    }
            ) {
                PageBitmapOrPlaceholder(
                    pageBitmap = pageBitmap,
                    pageIndex = pageIndex,
                    totalPages = totalPages,
                    colorFilter = colorFilter,
                    contentScale = ContentScale.Fit
                )
            }
        }
    } else {
        // Continuous Vertical Scroll Mode:
        // Outer Box has layout height = zoomedPageHeightDp so LazyColumn natively scrolls through the entire zoomed page!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(zoomedPageHeightDp)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RectangleShape,
                color = cardColor,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .width(basePageWidthDp)
                    .height(basePageHeightDp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffsetX
                    }
            ) {
                PageBitmapOrPlaceholder(
                    pageBitmap = pageBitmap,
                    pageIndex = pageIndex,
                    totalPages = totalPages,
                    colorFilter = colorFilter,
                    contentScale = ContentScale.FillWidth
                )
            }

            // Subtle Page Number Watermark Badge in top-right corner of each page
            Surface(
                shape = RoundedCornerShape(bottomStart = 8.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.62f),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = "${toBengaliDigits(pageIndex + 1)}/${toBengaliDigits(totalPages)}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PageBitmapOrPlaceholder(
    pageBitmap: Bitmap?,
    pageIndex: Int,
    totalPages: Int,
    colorFilter: ColorFilter?,
    contentScale: ContentScale
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap != null && !pageBitmap.isRecycled) {
            Image(
                bitmap = pageBitmap.asImageBitmap(),
                contentDescription = "স্লাইড ${pageIndex + 1}",
                contentScale = contentScale,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF0284C7),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "পৃষ্ঠা ${toBengaliDigits(pageIndex + 1)} / ${toBengaliDigits(totalPages)} লোড হচ্ছে...",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * All Pages Thumbnail Grid Navigator Bottom Sheet for quick visual jumping across slides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfThumbnailsBottomSheet(
    renderer: PdfPageRenderer,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (currentPageIndex - 2).coerceAtLeast(0)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "সকল স্লাইড ও পৃষ্ঠা (${toBengaliDigits(renderer.pageCount)}টি)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "যেকোনো পৃষ্ঠায় দ্রুত যেতে ট্যাপ করুন",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                state = gridState,
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = renderer.pageCount,
                    key = { it }
                ) { pageIdx ->
                    val isSelected = pageIdx == currentPageIndex
                    var thumbBitmap by remember(pageIdx, renderer) {
                        mutableStateOf(renderer.getCachedBitmap(pageIdx))
                    }
                    LaunchedEffect(pageIdx, renderer) {
                        if (thumbBitmap == null || thumbBitmap?.isRecycled == true) {
                            delay(30L)
                            val bmp = renderer.renderPage(pageIdx, 600, forceHighRes = false)
                            if (bmp != null && !bmp.isRecycled) {
                                thumbBitmap = bmp
                            }
                        }
                    }

                    val aspect = renderer.getAspectRatio(pageIdx).coerceAtLeast(0.5f)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectPage(pageIdx) }
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbBitmap != null && !thumbBitmap!!.isRecycled) {
                                    Image(
                                        bitmap = thumbBitmap!!.asImageBitmap(),
                                        contentDescription = "পৃষ্ঠা ${pageIdx + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B))
                                    .padding(vertical = 5.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "পৃষ্ঠা ${toBengaliDigits(pageIdx + 1)}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Share Bottom Sheet with 2 clear options:
 * 1. File Share (send via WhatsApp, Telegram, Drive, etc.)
 * 2. Copy Link (copy URL to clipboard)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfShareBottomSheet(
    title: String,
    pdfFile: File?,
    pdfUrl: String?,
    canShare: Boolean,
    onDismiss: () -> Unit
) {
    if (!canShare) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "পিডিএফ শেয়ার করুন",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title.ifBlank { "পিডিএফ লেকচার ফাইল" },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Option 1: Share PDF File
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        if (!canShare || !com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context)) {
                            return@clickable
                        }
                        if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    pdfFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "পিডিএফ ফাইল পাঠান"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "ফাইল শেয়ার করতে ব্যর্থ হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "ফাইলটি এখনো সম্পূর্ণ ডাউনলোড হয়নি, অনুগ্রহ করে অপেক্ষা করুন", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ফাইল পাঠান (Share File)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "হোয়াটসঅ্যাপ, টেলিগ্রাম বা অন্য কোনো অ্যাপে পিডিএফ ফাইলটি সরাসরি পাঠান",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Copy Link
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        if (!canShare || !com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context)) {
                            return@clickable
                        }
                        val linkToCopy = pdfUrl
                        if (!linkToCopy.isNullOrBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("PDF Link", linkToCopy)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "পিডিএফ লিংক ক্লিপবোর্ডে কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "এই ফাইলের অনলাইন লিংক পাওয়া যায়নি (লোকাল ফাইল)", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "লিংক কপি করুন (Copy Link)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "পিডিএফ ডাউনলোডের আসল লিংকটি কপি করুন",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun JumpToPageDialog(
    totalPages: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    var inputText by remember { mutableStateOf(currentPage.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "স্লাইড / পৃষ্ঠা পরিবর্তন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                Text(
                    text = "১ থেকে ${toBengaliDigits(totalPages)} এর মধ্যে যে পৃষ্ঠায় যেতে চান তার নম্বর লিখুন:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { str ->
                        val filtered = str.filter { it.isDigit() }
                        inputText = filtered
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onPageSelected(1) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("প্রথম পৃষ্ঠা", fontSize = 11.sp)
                    }
                    FilledTonalButton(
                        onClick = { onPageSelected(totalPages) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("শেষ পৃষ্ঠা", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = inputText.toIntOrNull()
                    if (p != null && p in 1..totalPages) {
                        onPageSelected(p)
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("যান", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
private fun RemoteLoadingCard(
    progress: Float,
    title: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "পিডিএফ প্রস্তুত করা হচ্ছে...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color(0xFF38BDF8),
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${toBengaliDigits((progress * 100).toInt())}% সম্পন্ন",
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                CircularProgressIndicator(
                    color = Color(0xFF38BDF8),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfErrorCard(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "পিডিএফ ভিউ করতে সমস্যা হয়েছে",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("পুনরায় চেষ্টা করুন")
            }
        }
    }
}

private fun toBengaliDigits(number: Int): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val str = number.toString()
    val sb = java.lang.StringBuilder()
    for (ch in str) {
        if (ch in '0'..'9') {
            sb.append(bnDigits[ch - '0'])
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}
