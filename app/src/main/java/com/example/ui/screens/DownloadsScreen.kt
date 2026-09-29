package com.example.ui.screens

import android.app.Activity
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.ui.components.SlideViewerDialog
import com.example.utils.NetworkUtils
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    isOfflineOnly: Boolean = false,
    onNavigateOnline: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val allDownloads by downloadManager.getAllDownloads().collectAsState(initial = emptyList())

    var selectedCategoryIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Videos, 2: PDFs
    var searchQuery by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<DownloadedItemEntity?>(null) }
    var activePdfViewerItem by remember { mutableStateOf<DownloadedItemEntity?>(null) }

    // Real-time network detection while in offline mode
    var autoDetectedOnline by remember { mutableStateOf(false) }
    LaunchedEffect(isOfflineOnly) {
        if (isOfflineOnly) {
            NetworkUtils.observeNetworkConnectivity(context).collect { isConnected ->
                if (isConnected) {
                    val reachable = NetworkUtils.isInternetReachable(context, timeoutMs = 2000)
                    autoDetectedOnline = reachable
                } else {
                    autoDetectedOnline = false
                }
            }
        }
    }

    // In offline mode, double back press to exit
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = isOfflineOnly) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "অ্যাপ থেকে বের হতে আবার ব্যাক চাপুন", Toast.LENGTH_SHORT).show()
        }
    }

    val activeDownloads = remember(allDownloads) {
        allDownloads.filter {
            it.status == DownloadedItemEntity.STATUS_DOWNLOADING || it.status == DownloadedItemEntity.STATUS_PAUSED
        }
    }

    val completedDownloads = remember(allDownloads) {
        allDownloads.filter { it.status == DownloadedItemEntity.STATUS_COMPLETED }
    }

    val filteredList = remember(allDownloads, selectedCategoryIndex, searchQuery) {
        allDownloads.filter { item ->
            val matchesCategory = when (selectedCategoryIndex) {
                1 -> item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)
                2 -> item.fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true)
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    (item.subtitle?.contains(searchQuery, ignoreCase = true) == true)
            matchesCategory && matchesQuery
        }
    }

    // Storage math
    val appUsedBytes = remember(allDownloads) {
        allDownloads.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
    }
    val deviceFreeBytes = remember {
        try { Environment.getDataDirectory().freeSpace } catch (_: Exception) { 0L }
    }
    val deviceTotalBytes = remember {
        try { Environment.getDataDirectory().totalSpace } catch (_: Exception) { 0L }
    }

    // In-App PDF Viewer
    if (activePdfViewerItem != null) {
        val pdfItem = activePdfViewerItem!!
        SlideViewerDialog(
            slideUrl = pdfItem.localFilePath,
            title = pdfItem.title,
            initialRemoteUrl = pdfItem.remoteUrl,
            onDismiss = { activePdfViewerItem = null }
        )
    }

    // Deletion Modal
    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(24.dp))
                }
            },
            title = {
                Text(
                    text = "ডাউনলোড মুছে ফেলবেন?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "\"${item.title}\" ফাইলটি আপনার অফলাইন ডিভাইস মেমোরি থেকে মুছে ফেলা হবে।",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        downloadManager.deleteDownloadedFile(item.id)
                        itemToDelete = null
                        Toast.makeText(context, "ফাইলটি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("মুছে ফেলুন", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOfflineOnly) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEE2E2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Offline Mode",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .testTag("downloads_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "অফলাইন ডাউনলোড সেন্টার",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "হাই-স্পিড ভিডিও ও ই-বুক ম্যানেজার",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${completedDownloads.size} টি ফাইল",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("downloads_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Network restored banner
                if (isOfflineOnly && autoDetectedOnline) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Wifi, null, tint = Color(0xFF16A34A), modifier = Modifier.size(22.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ইন্টারনেট সংযোগ চালু হয়েছে!", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14532D))
                                    Text("অনলাইন মোডে ফিরে যেতে ট্যাপ করুন", fontSize = 11.sp, color = Color(0xFF166534))
                                }
                                Button(
                                    onClick = { onNavigateOnline?.invoke() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("অনলাইনে যান", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Top Storage Gauge Meter Card
                item {
                    StorageGaugeCard(
                        appUsedBytes = appUsedBytes,
                        deviceFreeBytes = deviceFreeBytes,
                        deviceTotalBytes = deviceTotalBytes,
                        downloadManager = downloadManager,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 10.dp)
                    )
                }

                // Active Downloading / Paused Section
                if (activeDownloads.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7))
                                )
                                Text(
                                    text = "চলমান ডাউনলোডসমূহ (${activeDownloads.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            activeDownloads.forEach { activeItem ->
                                ActiveDownloadProgressCard(
                                    item = activeItem,
                                    downloadManager = downloadManager,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Category Tabs & Search Bar
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Category Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val categories = listOf("সবগুলো (${allDownloads.size})", "ভিডিও (${allDownloads.count { it.fileType == DownloadedItemEntity.FILE_TYPE_VIDEO }})", "নোট/পিডিএফ (${allDownloads.count { it.fileType == DownloadedItemEntity.FILE_TYPE_PDF }})")
                            categories.forEachIndexed { index, label ->
                                val isSelected = selectedCategoryIndex == index
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategoryIndex = index },
                                    label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0284C7),
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }

                // File List or Empty View
                if (filteredList.isEmpty()) {
                    item {
                        EmptyDownloadsView(
                            isVideosTab = selectedCategoryIndex != 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp)
                        )
                    }
                } else {
                    items(filteredList, key = { it.id }) { item ->
                        DownloadedItemCard(
                            item = item,
                            downloadManager = downloadManager,
                            onClick = {
                                if (item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)) {
                                    onPlayVideo(
                                        item.localFilePath,
                                        item.title,
                                        item.subtitle ?: "অফলাইন লেকচার",
                                        "#0284C7",
                                        false
                                    )
                                } else {
                                    activePdfViewerItem = item
                                }
                            },
                            onDelete = { itemToDelete = item },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageGaugeCard(
    appUsedBytes: Long,
    deviceFreeBytes: Long,
    deviceTotalBytes: Long,
    downloadManager: AppFileDownloadManager,
    modifier: Modifier = Modifier
) {
    val appUsedStr = remember(appUsedBytes) { downloadManager.formatFileSize(appUsedBytes, inBengali = true) }
    val freeStr = remember(deviceFreeBytes) { downloadManager.formatFileSize(deviceFreeBytes, inBengali = true) }

    val usedFraction = remember(appUsedBytes, deviceTotalBytes) {
        if (deviceTotalBytes > 0) (appUsedBytes.toFloat() / deviceTotalBytes.toFloat()).coerceIn(0.01f, 1f) else 0.05f
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SdStorage, null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "স্টোরেজ মেজারমেন্ট",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "মেমোরি ঠিক আছে",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dual Storage Meter Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(usedFraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF0284C7)))
                    Text(
                        text = "শিখো অ্যাপ ভিডিও: $appUsedStr",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Text(
                        text = "খালি জায়গা: $freeStr",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadProgressCard(
    item: DownloadedItemEntity,
    downloadManager: AppFileDownloadManager,
    modifier: Modifier = Modifier
) {
    val isPaused = item.status == DownloadedItemEntity.STATUS_PAUSED
    val isFailed = item.status == DownloadedItemEntity.STATUS_FAILED

    val downloadedStr = remember(item.downloadedBytes) { downloadManager.formatFileSize(item.downloadedBytes, inBengali = true) }
    val totalStr = remember(item.totalBytes) { downloadManager.formatFileSize(item.totalBytes, inBengali = true) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isPaused) Color(0xFFFFFBEB) else Color(0xFFF0F9FF),
        border = BorderStroke(1.dp, if (isPaused) Color(0xFFFCD34D) else Color(0xFFBAE6FD)),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPaused) Color(0xFFFEF3C7) else Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PauseCircleFilled else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isPaused) Color(0xFFD97706) else Color(0xFF0284C7),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isPaused) "পজ করা রয়েছে • $downloadedStr" else "$downloadedStr / $totalStr (${item.progressPercent}%)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            if (isPaused) downloadManager.resumeDownload(item.id) else downloadManager.pauseDownload(item.id)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = Color(0xFF0284C7)
                        )
                    }

                    IconButton(
                        onClick = { downloadManager.cancelDownload(item.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cancel", tint = Color(0xFFEF4444))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isPaused) Color(0xFFF59E0B) else Color(0xFF0284C7),
                trackColor = Color(0xFFE2E8F0)
            )
        }
    }
}

@Composable
fun DownloadedItemCard(
    item: DownloadedItemEntity,
    downloadManager: AppFileDownloadManager,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)
    val isFailed = item.status == DownloadedItemEntity.STATUS_FAILED
    val isPaused = item.status == DownloadedItemEntity.STATUS_PAUSED

    val subjectLabel = item.subtitle?.takeIf { it.isNotBlank() } ?: if (isVideo) "ভিডিও লেকচার" else "লেকচার শিট"
    val sizeText = remember(item.totalBytes) {
        if (item.totalBytes > 0) downloadManager.formatFileSize(item.totalBytes, inBengali = true) else ""
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Type Icon / Status Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isVideo) Color(0xFFE0F2FE) else Color(0xFFEDE9FE)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.PlayCircleFilled else Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = if (isVideo) Color(0xFF0284C7) else Color(0xFF7C3AED),
                    modifier = Modifier.size(26.dp)
                )
            }

            // File Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isVideo) Color(0xFF0284C7).copy(alpha = 0.12f) else Color(0xFF7C3AED).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = subjectLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVideo) Color(0xFF0284C7) else Color(0xFF7C3AED),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (sizeText.isNotBlank()) {
                        Text(
                            text = "•  $sizeText",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions (Play/View + Delete)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isFailed || isPaused) {
                    IconButton(
                        onClick = { downloadManager.resumeDownload(item.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "Retry", tint = Color(0xFF0284C7))
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsView(
    isVideosTab: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideosTab) Icons.Default.CloudDownload else Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isVideosTab) "কোনো ডাউনলোডকৃত ভিডিও নেই" else "কোনো ডাউনলোডকৃত নোট নেই",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "সংরক্ষিত ফাইল এখানে অফলাইনে দেখার জন্য জমা থাকবে",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
