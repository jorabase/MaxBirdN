package com.example.ui.components

import android.content.Context
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
import androidx.compose.foundation.border
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
                        pageAspectRatios[0] = if (h > 0) w / h else 0.707f
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
                    val aspect = if (pHeight > 0) pWidth.toFloat() / pHeight.toFloat() else 0.707f
                    pageAspectRatios[pageIndex] = aspect

                    // Render at high resolution (1080px to 2400px) so text and equations stay razor sharp
                    val renderWidth = targetWidthPx.coerceIn(1080, 2400)
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

    // Resolve target file strictly for in-app viewing
    val targetPdfFile = remember(
        isDirectLocal,
        directCleanPath,
        effectiveDownloadedItem,
        cachedFileState
    ) {
        // Priority 1: Direct valid local file passed as slideUrl
        if (isDirectLocal && directCleanPath.isNotBlank()) {
            val f = File(directCleanPath)
            if (f.exists() && f.length() > 0) return@remember f
        }

        // Priority 2: Offline Vault file from DownloadedItemEntity
        if (effectiveDownloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) {
            val f = File(effectiveDownloadedItem.localFilePath)
            if (f.exists() && f.length() > 0) return@remember f
        }

        // Priority 3: Cached downloaded remote file in cache dir
        if (cachedFileState != null && cachedFileState!!.exists() && cachedFileState!!.length() > 0) {
            return@remember cachedFileState
        }

        // Check if pre-cached file exists for remoteCandidateUrl
        if (!remoteCandidateUrl.isNullOrBlank()) {
            val safeHash = remoteCandidateUrl.hashCode().toString().replace("-", "n")
            val cacheDir = File(context.cacheDir, "pdf_preview_cache").apply { if (!exists()) mkdirs() }
            val existingCacheFile = File(cacheDir, "preview_${safeHash}.pdf")
            if (existingCacheFile.exists() && existingCacheFile.length() > 0) {
                return@remember existingCacheFile
            }
        }

        null
    }

    // Download remote URL into cache if not locally available
    LaunchedEffect(slideUrl, targetPdfFile, remoteCandidateUrl) {
        if (targetPdfFile == null && !remoteCandidateUrl.isNullOrBlank()) {
            isFetchingRemote = true
            remoteErrorMessage = null
            remoteDownloadProgress = 0f

            val safeHash = remoteCandidateUrl.hashCode().toString().replace("-", "n")
            val cacheDir = File(context.cacheDir, "pdf_preview_cache").apply { if (!exists()) mkdirs() }
            val tempFile = File(cacheDir, "preview_${safeHash}.pdf")

            try {
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(25, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()

                    val request = Request.Builder()
                        .url(remoteCandidateUrl)
                        .addHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
                        .addHeader("referer", "https://shikho.com/")
                        .addHeader("Origin", "https://shikho.com")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful || response.body == null) {
                        throw Exception("সার্ভার থেকে ফাইল পাওয়া যায়নি (HTTP ${response.code})")
                    }

                    val body = response.body!!
                    val totalLength = body.contentLength()
                    var bytesCopied = 0L

                    var inputStream: InputStream? = null
                    var outputStream: FileOutputStream? = null

                    try {
                        inputStream = body.byteStream()
                        outputStream = FileOutputStream(tempFile)
                        val buffer = ByteArray(16384)
                        var read: Int
                        var lastProgressUpdate = System.currentTimeMillis()

                        while (inputStream.read(buffer).also { read = it } != -1) {
                            ensureActive()
                            outputStream.write(buffer, 0, read)
                            bytesCopied += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 150L || bytesCopied == totalLength) {
                                lastProgressUpdate = now
                                if (totalLength > 0) {
                                    val prog = (bytesCopied.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) {
                                        remoteDownloadProgress = prog
                                    }
                                }
                            }
                        }
                        outputStream.flush()
                    } finally {
                        try { inputStream?.close() } catch (_: Exception) {}
                        try { outputStream?.close() } catch (_: Exception) {}
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

    var showNotebookLMDialog by remember { mutableStateOf(false) }

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
                // Top Header Bar (Can be hidden in Fullscreen mode)
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
                        downloadedItem = effectiveDownloadedItem,
                        onDismiss = onDismiss,
                        onNotebookLMClick = { showNotebookLMDialog = true },
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

                // Main Viewer Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            when (readingTheme) {
                                ReadingTheme.DAY -> Color(0xFF1E293B)
                                ReadingTheme.SEPIA -> Color(0xFF292524)
                                ReadingTheme.NIGHT -> Color(0xFF0F0F10)
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

                    // Floating NotebookLM & AI Study Pill
                    if (rendererState != null && rendererState.isInitialized && rendererState.pageCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF4F46E5),
                            shadowElevation = 8.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 24.dp, end = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showNotebookLMDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "নোটবুক এলএম",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNotebookLMDialog) {
            NotebookLMStudyDialog(
                title = title,
                currentPage = 1,
                totalPageCount = rendererState?.pageCount ?: 1,
                targetFile = targetPdfFile,
                remoteUrl = remoteCandidateUrl ?: effectiveDownloadedItem?.remoteUrl,
                onDismiss = { showNotebookLMDialog = false }
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
    downloadedItem: DownloadedItemEntity?,
    onDismiss: () -> Unit,
    onNotebookLMClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onCycleTheme: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "ইন-অ্যাপ পিডিএফ নোট" },
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
                                text = "✓ অফলাইন সংরক্ষিত",
                                fontSize = 10.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "অ্যাপ সুরক্ষিত ভিউ",
                                fontSize = 10.sp,
                                color = Color(0xFF7DD3FC),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (pageCount > 0) {
                        Text(
                            text = "${toBengaliDigits(pageCount)} টি পৃষ্ঠা",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // NotebookLM & AI Study Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF4F46E5).copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.7f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onNotebookLMClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = "NotebookLM",
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "NotebookLM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Mode Switcher: Continuous Scroll vs Single Slide Presentation
            IconButton(
                onClick = onToggleViewMode,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (viewMode == PdfViewMode.VERTICAL_SCROLL) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                    contentDescription = if (viewMode == PdfViewMode.VERTICAL_SCROLL) "স্লাইড মোড" else "স্ক্রল মোড",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Theme Switcher: Day -> Sepia -> Night
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
                    modifier = Modifier.size(20.dp)
                )
            }

            // Fullscreen Toggle
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "ফুলস্ক্রিন",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // In-App Download Action
            when (downloadedItem?.status) {
                DownloadedItemEntity.STATUS_DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(4.dp),
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
                        modifier = Modifier.size(36.dp)
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
                        modifier = Modifier.size(36.dp)
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
        (displayWidthPx * 2.0f).toInt().coerceIn(1080, 2400)
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
                    val newScale = (scale * zoom).coerceIn(1f, 3.5f)
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
        // Mode 1: Continuous Vertical Scrolling
        if (viewMode == PdfViewMode.VERTICAL_SCROLL) {
            LazyColumn(
                state = verticalListState,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
            // Mode 2: Horizontal Slide Presentation Mode
            HorizontalPager(
                state = horizontalPagerState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp),
                pageSpacing = 16.dp,
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

        // Floating Bottom Controls
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
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

                    // Zoom Level Badge (Reset button)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (scale > 1.05f) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Zoom In Button
                    IconButton(
                        onClick = {
                            scale = (scale + 0.25f).coerceAtMost(3.5f)
                        },
                        enabled = scale < 3.5f,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom In",
                            tint = if (scale < 3.5f) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(20.dp)
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
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

    val aspectRatio = renderer.pageAspectRatios[pageIndex] ?: 0.707f

    LaunchedEffect(pageIndex, renderer) {
        if (pageBitmap == null) {
            val bmp = renderer.renderPage(pageIndex, targetWidthPx)
            pageBitmap = bmp
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when (readingTheme) {
            ReadingTheme.DAY -> Color.White
            ReadingTheme.SEPIA -> Color(0xFFF7F3E9)
            ReadingTheme.NIGHT -> Color(0xFF1E1E20)
        },
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (readingTheme) {
                ReadingTheme.DAY -> Color(0xFFE2E8F0)
                ReadingTheme.SEPIA -> Color(0xFFE7E0D3)
                ReadingTheme.NIGHT -> Color(0xFF333338)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
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
                    contentDescription = "পৃষ্ঠা ${pageIndex + 1}",
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
                        text = "পৃষ্ঠা ${toBengaliDigits(pageIndex + 1)} লোড হচ্ছে...",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
            Text(text = "পৃষ্ঠা পরিবর্তন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
