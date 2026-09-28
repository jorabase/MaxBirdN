package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val TAG = "SlideViewerDialog"

enum class PdfViewMode {
    VERTICAL_SCROLL, // Continuous vertical scrolling
    HORIZONTAL_SLIDES // Slide presentation mode (swipe left / right)
}

enum class ReadingTheme {
    DAY,     // Normal white paper
    SEPIA,   // Eye comfort warm paper
    NIGHT    // Inverted dark mode
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
 * Controller to manage PdfRenderer lifecycle and multi-page rendering safely.
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
    private val cache = LruCache<Int, Bitmap>(35)
    private val lock = Any()

    var isInitialized by mutableStateOf(false)
        private set
    var initError by mutableStateOf<String?>(null)
        private set

    fun init() {
        if (isInitialized) return
        try {
            if (!file.exists() || file.length() == 0L) {
                initError = "পিডিএফ ফাইলটি পাওয়া যায়নি বা ফাইলের সাইজ শূন্য।"
                return
            }
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
            val count = renderer!!.pageCount
            pageCount = count

            // Probe first page aspect ratio for placeholder sizing
            if (count > 0) {
                synchronized(lock) {
                    try {
                        val firstPage = renderer!!.openPage(0)
                        val w = firstPage.width.toFloat()
                        val h = firstPage.height.toFloat()
                        pageAspectRatios[0] = if (h > 0) w / h else 1.414f // 16:9 or standard slide ratio default
                        firstPage.close()
                    } catch (_: Exception) {}
                }
            }

            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PdfRenderer: ${e.message}", e)
            initError = "পিডিএফ ফাইলটি রিড করতে ব্যর্থ হয়েছে: ${e.localizedMessage ?: "অজ্ঞাত ত্রুটি"}"
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.Default) {
        if (!isInitialized || pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        cache.get(pageIndex)?.let { return@withContext it }

        val activeRenderer = renderer ?: return@withContext null

        synchronized(lock) {
            try {
                cache.get(pageIndex)?.let { return@synchronized it }

                val page = activeRenderer.openPage(pageIndex)
                try {
                    val pWidth = page.width
                    val pHeight = page.height
                    val aspect = if (pHeight > 0) pWidth.toFloat() / pHeight.toFloat() else 1.414f
                    pageAspectRatios[pageIndex] = aspect

                    // Render at high resolution (1200px to 2600px) so slide texts, diagrams and equations are razor sharp
                    val renderWidth = targetWidthPx.coerceIn(1200, 2600)
                    val renderHeight = (renderWidth / aspect).toInt().coerceAtLeast(100)

                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    cache.put(pageIndex, bitmap)
                    bitmap
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering page $pageIndex: ${e.message}", e)
                null
            }
        }
    }

    fun getCachedBitmap(pageIndex: Int): Bitmap? = cache.get(pageIndex)

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
fun SlideViewerDialog(
    slideUrl: String,
    title: String,
    initialRemoteUrl: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val activity = remember(context) { context.findActivity() }

    // Screen orientation state
    var isLandscapeMode by remember { mutableStateOf(false) }

    // Restore original orientation when dialog closes
    DisposableEffect(Unit) {
        onDispose {
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    // User viewing preferences
    var viewMode by remember { mutableStateOf(PdfViewMode.VERTICAL_SCROLL) }
    var readingTheme by remember { mutableStateOf(ReadingTheme.DAY) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showShareBottomSheet by remember { mutableStateOf(false) }

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
                val safeFileName = "slide_${Math.abs(remoteCandidateUrl.hashCode())}.pdf"
                val tempFile = File(cacheDir, safeFileName)

                // If already cached on disk, reuse immediately
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
                    val outputStream = FileOutputStream(tempFile)

                    val buffer = ByteArray(8192)
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = when (readingTheme) {
                ReadingTheme.DAY -> Color(0xFF0F172A)
                ReadingTheme.SEPIA -> Color(0xFF1C1917)
                ReadingTheme.NIGHT -> Color(0xFF000000)
            }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar (Can be hidden in Fullscreen mode for immersive reading)
                AnimatedVisibility(
                    visible = !isFullscreen,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    PdfViewerTopBar(
                        title = title,
                        isOffline = isOfflineAvailable,
                        pageCount = rendererState?.pageCount ?: 0,
                        viewMode = viewMode,
                        readingTheme = readingTheme,
                        isLandscape = isLandscapeMode,
                        downloadedItem = effectiveDownloadedItem,
                        onDismiss = onDismiss,
                        onToggleOrientation = {
                            val newLandscape = !isLandscapeMode
                            isLandscapeMode = newLandscape
                            activity?.requestedOrientation = if (newLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        },
                        onShareClick = {
                            showShareBottomSheet = true
                        },
                        onToggleViewMode = {
                            viewMode = if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                PdfViewMode.HORIZONTAL_SLIDES
                            } else {
                                PdfViewMode.VERTICAL_SCROLL
                            }
                        },
                        onCycleTheme = {
                            readingTheme = when (readingTheme) {
                                ReadingTheme.DAY -> ReadingTheme.SEPIA
                                ReadingTheme.SEPIA -> ReadingTheme.NIGHT
                                ReadingTheme.NIGHT -> ReadingTheme.DAY
                            }
                        },
                        onToggleFullscreen = {
                            isFullscreen = !isFullscreen
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
                        }
                    )
                }

                // Main Viewer Body - Clean, Edge-to-Edge reading canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            when (readingTheme) {
                                ReadingTheme.DAY -> Color(0xFF0F172A)
                                ReadingTheme.SEPIA -> Color(0xFF292524)
                                ReadingTheme.NIGHT -> Color(0xFF000000)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // 1. Fetching remote PDF
                        isFetchingRemote -> {
                            RemoteLoadingCard(
                                progress = remoteDownloadProgress,
                                title = title
                            )
                        }

                        // 2. Fetch or Init Error
                        remoteErrorMessage != null || rendererState?.initError != null -> {
                            val err = remoteErrorMessage ?: rendererState?.initError ?: "অজানা ত্রুটি"
                            PdfErrorCard(
                                errorMessage = err,
                                onRetry = {
                                    remoteErrorMessage = null
                                    cachedFileState = null
                                }
                            )
                        }

                        // 3. Renderer active and initialized
                        rendererState != null && rendererState.isInitialized && rendererState.pageCount > 0 -> {
                            NativePdfViewerContent(
                                renderer = rendererState,
                                viewMode = viewMode,
                                readingTheme = readingTheme,
                                isFullscreen = isFullscreen,
                                onToggleFullscreen = { isFullscreen = !isFullscreen },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 4. Default / Preparing file
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "সুরক্ষিত ইন-অ্যাপ রিডার প্রস্তুত হচ্ছে...",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Share Bottom Sheet / Dialog with 2 clean options: File Share & Link Copy
        if (showShareBottomSheet) {
            PdfShareBottomSheet(
                title = title,
                pdfFile = targetPdfFile,
                pdfUrl = remoteCandidateUrl ?: effectiveDownloadedItem?.remoteUrl,
                onDismiss = { showShareBottomSheet = false }
            )
        }
    }
}

@Composable
private fun PdfViewerTopBar(
    title: String,
    isOffline: Boolean,
    pageCount: Int,
    viewMode: PdfViewMode,
    readingTheme: ReadingTheme,
    isLandscape: Boolean,
    downloadedItem: DownloadedItemEntity?,
    onDismiss: () -> Unit,
    onToggleOrientation: () -> Unit,
    onShareClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onCycleTheme: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Surface(
        color = Color(0xFF0B1120),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .testTag("pdf_viewer_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "বন্ধ করুন",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Title & Status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "ইন-অ্যাপ পিডিএফ স্লাইড" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (pageCount > 0) {
                        Text(
                            text = "${toBengaliDigits(pageCount)} টি স্লাইড",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 1. Landscape / Orientation Toggle Button
            IconButton(
                onClick = onToggleOrientation,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isLandscape) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenRotation,
                    contentDescription = if (isLandscape) "পোর্ট্রেট মোড" else "ল্যান্ডস্কেপ মোড",
                    tint = if (isLandscape) Color(0xFF38BDF8) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 2. Share Button (File send & Copy link)
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "শেয়ার করুন",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 3. Mode Switcher: Continuous Scroll vs Single Slide Presentation
            IconButton(
                onClick = onToggleViewMode,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (viewMode == PdfViewMode.VERTICAL_SCROLL) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                    contentDescription = if (viewMode == PdfViewMode.VERTICAL_SCROLL) "স্লাইড মোড" else "স্ক্রল মোড",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 4. Theme Switcher: Day -> Sepia -> Night
            IconButton(
                onClick = onCycleTheme,
                modifier = Modifier.size(38.dp)
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
                    modifier = Modifier.size(20.dp)
                )
            }

            // 5. In-App Download Action
            when (downloadedItem?.status) {
                DownloadedItemEntity.STATUS_DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadedItem.progressFraction },
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DownloadedItemEntity.STATUS_COMPLETED -> {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "অফলাইনে সংরক্ষিত",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                else -> {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "ইন-অ্যাপ অফলাইন ডাউনলোড",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NativePdfViewerContent(
    renderer: PdfPageRenderer,
    viewMode: PdfViewMode,
    readingTheme: ReadingTheme,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val verticalListState = rememberLazyListState()
    val horizontalPagerState = rememberPagerState(pageCount = { renderer.pageCount })

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val currentVisiblePage = remember(viewMode) {
        derivedStateOf {
            if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                (verticalListState.firstVisibleItemIndex + 1).coerceAtMost(renderer.pageCount)
            } else {
                (horizontalPagerState.currentPage + 1).coerceAtMost(renderer.pageCount)
            }
        }
    }

    val displayWidthPx = context.resources.displayMetrics.widthPixels
    val renderTargetWidth = remember(displayWidthPx) {
        (displayWidthPx * 2.5f).toInt().coerceIn(1200, 2600)
    }

    // ColorFilter for Night/Sepia Reading modes
    val colorFilter = remember(readingTheme) {
        when (readingTheme) {
            ReadingTheme.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f,  0f, 255f,
                         0f, -1f,  0f,  0f, 255f,
                         0f,  0f, -1f,  0f, 255f,
                         0f,  0f,  0f,  1f,   0f
                    )
                )
            )
            ReadingTheme.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.94f, 0f, 0f, 0f, 20f,
                        0f, 0.88f, 0f, 0f, 15f,
                        0f, 0f, 0.74f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ReadingTheme.DAY -> null
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            totalPages = renderer.pageCount,
            currentPage = currentVisiblePage.value,
            onDismiss = { showJumpDialog = false },
            onPageSelected = { targetPage ->
                showJumpDialog = false
                val idx = (targetPage - 1).coerceIn(0, renderer.pageCount - 1)
                coroutineScope.launch {
                    if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                        verticalListState.animateScrollToItem(idx)
                    } else {
                        horizontalPagerState.animateScrollToPage(idx)
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4.0f)
                    scale = newScale
                    if (newScale > 1f) {
                        panOffset += pan
                    } else {
                        panOffset = Offset.Zero
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onToggleFullscreen()
                    },
                    onDoubleTap = {
                        scale = when {
                            scale < 1.4f -> 2.0f
                            scale < 2.5f -> 3.0f
                            else -> 1.0f
                        }
                        if (scale == 1.0f) {
                            panOffset = Offset.Zero
                        }
                    }
                )
            }
    ) {
        // Mode 1: Continuous Vertical Scrolling - EDGE-TO-EDGE FULL WIDTH (0dp side padding)
        if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
            LazyColumn(
                state = verticalListState,
                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
            ) {
                items(
                    count = renderer.pageCount,
                    key = { pageIndex -> pageIndex }
                ) { pageIndex ->
                    PdfPageCard(
                        pageIndex = pageIndex,
                        renderer = renderer,
                        colorFilter = colorFilter,
                        readingTheme = readingTheme,
                        targetWidthPx = renderTargetWidth
                    )
                }
            }
        } else {
            // Mode 2: Horizontal Slide Presentation Mode - FULL WIDTH
            HorizontalPager(
                state = horizontalPagerState,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                pageSpacing = 0.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
            ) { pageIndex ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PdfPageCard(
                        pageIndex = pageIndex,
                        renderer = renderer,
                        colorFilter = colorFilter,
                        readingTheme = readingTheme,
                        targetWidthPx = renderTargetWidth
                    )
                }
            }
        }

        // Floating Bottom Controls Dock - Modern, sleek & comfortable
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Zoom Out Button
                    IconButton(
                        onClick = {
                            val newScale = (scale - 0.25f).coerceAtLeast(1f)
                            scale = newScale
                            if (newScale <= 1f) panOffset = Offset.Zero
                        },
                        enabled = scale > 1f,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Zoom Out",
                            tint = if (scale > 1f) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Zoom Level Badge (Tap to reset to 100%)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (scale > 1.05f) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                }
                            }
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = if (scale > 1.05f) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
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
                            contentDescription = "Zoom In",
                            tint = if (scale < 4.0f) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(18.dp)
                            .padding(horizontal = 4.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    // Previous Page Scroll Button
                    IconButton(
                        onClick = {
                            val prev = (currentVisiblePage.value - 2).coerceAtLeast(0)
                            coroutineScope.launch {
                                if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                    verticalListState.animateScrollToItem(prev)
                                } else {
                                    horizontalPagerState.animateScrollToPage(prev)
                                }
                            }
                        },
                        enabled = currentVisiblePage.value > 1,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (viewMode == PdfViewMode.VERTICAL_SCROLL) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Previous Page",
                            tint = if (currentVisiblePage.value > 1) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Page Indicator Pill (Tap to Jump)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    showJumpDialog = true
                                }
                            }
                    ) {
                        Text(
                            text = "${toBengaliDigits(currentVisiblePage.value)} / ${toBengaliDigits(renderer.pageCount)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }

                    // Next Page Scroll Button
                    IconButton(
                        onClick = {
                            val next = currentVisiblePage.value.coerceAtMost(renderer.pageCount - 1)
                            coroutineScope.launch {
                                if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
                                    verticalListState.animateScrollToItem(next)
                                } else {
                                    horizontalPagerState.animateScrollToPage(next)
                                }
                            }
                        },
                        enabled = currentVisiblePage.value < renderer.pageCount,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (viewMode == PdfViewMode.VERTICAL_SCROLL) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next Page",
                            tint = if (currentVisiblePage.value < renderer.pageCount) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean edge-to-edge PDF slide card with maximum readability and zero wasted margin.
 */
@Composable
private fun PdfPageCard(
    pageIndex: Int,
    renderer: PdfPageRenderer,
    colorFilter: ColorFilter?,
    readingTheme: ReadingTheme,
    targetWidthPx: Int
) {
    var pageBitmap by remember(pageIndex) {
        mutableStateOf(renderer.getCachedBitmap(pageIndex))
    }

    val aspectRatio = renderer.pageAspectRatios[pageIndex] ?: 1.414f

    LaunchedEffect(pageIndex, renderer) {
        if (pageBitmap == null) {
            val bmp = renderer.renderPage(pageIndex, targetWidthPx)
            pageBitmap = bmp
        }
    }

    Surface(
        shape = RectangleShape,
        color = when (readingTheme) {
            ReadingTheme.DAY -> Color.White
            ReadingTheme.SEPIA -> Color(0xFFF7F3E9)
            ReadingTheme.NIGHT -> Color(0xFF1E1E20)
        },
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center
        ) {
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "স্লাইড ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Rendering placeholder
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "স্লাইড ${toBengaliDigits(pageIndex + 1)} লোড হচ্ছে...",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
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
    onDismiss: () -> Unit
) {
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
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
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
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
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
            Text(text = "স্লাইড পরিবর্তন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                Text(
                    text = "১ থেকে ${toBengaliDigits(totalPages)} এর মধ্যে যে স্লাইডে যেতে চান তার নম্বর লিখুন:",
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
                        Text("প্রথম স্লাইড", fontSize = 11.sp)
                    }
                    FilledTonalButton(
                        onClick = { onPageSelected(totalPages) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("শেষ স্লাইড", fontSize = 11.sp)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
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
