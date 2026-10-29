package com.example.ui.screens

import android.app.Activity
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Int.toBengaliDigits(): String = this.toString().toBengaliDigits()

private fun String.toBengaliDigits(): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder(this.length)
    for (ch in this) {
        if (ch in '0'..'9') {
            sb.append(bnDigits[ch - '0'])
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

private fun formatDownloadDateBangla(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return try {
        val sdf = SimpleDateFormat("dd MMM, yyyy", Locale.US)
        val formatted = sdf.format(Date(timestampMs))
        formatted
            .replace("Jan", "জানু")
            .replace("Feb", "ফেব্রু")
            .replace("Mar", "মার্চ")
            .replace("Apr", "এপ্রিল")
            .replace("May", "মে")
            .replace("Jun", "জুন")
            .replace("Jul", "জুলাই")
            .replace("Aug", "আগস্ট")
            .replace("Sep", "সেপ্টে")
            .replace("Oct", "অক্টো")
            .replace("Nov", "নভে")
            .replace("Dec", "ডিসে")
            .toBengaliDigits()
    } catch (_: Exception) {
        ""
    }
}

private fun formatSpeedChipBangla(speedMBs: Float): String {
    if (speedMBs <= 0.005f) return "সংযোগ হচ্ছে..."
    return if (speedMBs >= 1.0f) {
        String.format(Locale.US, "%.1f মেগাবাইট/সে.", speedMBs).toBengaliDigits()
    } else {
        val kb = (speedMBs * 1024f).coerceAtLeast(1f)
        String.format(Locale.US, "%.0f কেবি/সে.", kb).toBengaliDigits()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    onOpenPdf: ((filePath: String, title: String) -> Unit)? = null,
    isOfflineOnly: Boolean = false,
    onNavigateOnline: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isDark = isSystemInDarkTheme()

    val allDownloads by downloadManager.getAllDownloads().collectAsState(initial = emptyList())
    val activeSpeeds by downloadManager.activeSpeedsFlow.collectAsState()

    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) } // 0: All, 1: Videos, 2: PDFs
    var selectedSortOrder by rememberSaveable { mutableIntStateOf(0) } // 0: Newest, 1: Largest, 2: Title
    var searchQuery by rememberSaveable { mutableStateOf("") }
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
            it.status == DownloadedItemEntity.STATUS_DOWNLOADING ||
                it.status == DownloadedItemEntity.STATUS_PAUSED
        }
    }

    val completedAndFailedDownloads = remember(allDownloads) {
        allDownloads.filter {
            it.status == DownloadedItemEntity.STATUS_COMPLETED ||
                it.status == DownloadedItemEntity.STATUS_FAILED
        }
    }

    val completedCount = remember(allDownloads) {
        allDownloads.count { it.status == DownloadedItemEntity.STATUS_COMPLETED }
    }

    val videoCount = remember(completedAndFailedDownloads) {
        completedAndFailedDownloads.count { it.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true) }
    }

    val pdfCount = remember(completedAndFailedDownloads) {
        completedAndFailedDownloads.count { it.fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true) }
    }

    // Filter only Completed & Failed items in main library so active items are not duplicated below!
    val filteredList = remember(completedAndFailedDownloads, selectedCategoryIndex, selectedSortOrder, searchQuery) {
        val base = completedAndFailedDownloads.filter { item ->
            val matchesCategory = when (selectedCategoryIndex) {
                1 -> item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)
                2 -> item.fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true)
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery.trim(), ignoreCase = true) ||
                (item.subtitle?.contains(searchQuery.trim(), ignoreCase = true) == true)
            matchesCategory && matchesQuery
        }
        when (selectedSortOrder) {
            1 -> base.sortedByDescending { it.totalBytes }
            2 -> base.sortedBy { it.title }
            else -> base.sortedByDescending { it.createdAt }
        }
    }

    // Storage math
    val appUsedBytes = remember(allDownloads) {
        allDownloads.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
    }
    val deviceFreeBytes = remember(allDownloads.size) {
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

    // Deletion Confirmation Dialog
    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "ডাউনলোড মুছে ফেলবেন?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.5.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "\"${item.title}\" ফাইলটি আপনার অফলাইন মেমোরি থেকে স্থায়ীভাবে মুছে ফেলা হবে।",
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("মুছে ফেলুন", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { itemToDelete = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("বাতিল", fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(22.dp)
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
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOfflineOnly) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Offline Mode",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .testTag("downloads_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "অফলাইন ডাউনলোড সেন্টার",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isOfflineOnly) {
                                    "অফলাইন মোড • ইন্টারনেট ছাড়াই ক্লাস ও নোট দেখুন"
                                } else {
                                    "হাই-স্পিড ভিডিও ও লেকচার শিট ম্যানেজার"
                                },
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${completedCount.toBengaliDigits()} টি ফাইল",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                            }
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
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Network restored banner in offline mode
                if (isOfflineOnly && autoDetectedOnline) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF34D399) else Color(0xFF16A34A),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ইন্টারনেট সংযোগ সচল হয়েছে!",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color(0xFF14532D)
                                    )
                                    Text(
                                        text = "সম্পূর্ণ অনলাইন অ্যাপে ফিরে যেতে ট্যাপ করুন",
                                        fontSize = 11.5.sp,
                                        color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF166534)
                                    )
                                }
                                Button(
                                    onClick = { onNavigateOnline?.invoke() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "অনলাইনে যান",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Storage Gauge Meter Card
                item {
                    StorageGaugeCard(
                        appUsedBytes = appUsedBytes,
                        deviceFreeBytes = deviceFreeBytes,
                        deviceTotalBytes = deviceTotalBytes,
                        completedCount = completedCount,
                        downloadManager = downloadManager,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 10.dp)
                    )
                }

                // Active Downloading / Paused Section
                if (activeDownloads.isNotEmpty()) {
                    item {
                        val anyDownloading = activeDownloads.any { it.status == DownloadedItemEntity.STATUS_DOWNLOADING }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(if (anyDownloading) Color(0xFF0284C7) else Color(0xFFF59E0B))
                                    )
                                    Text(
                                        text = "চলমান ডাউনলোড (${activeDownloads.size.toBengaliDigits()})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                if (activeDownloads.size > 1) {
                                    TextButton(
                                        onClick = {
                                            if (anyDownloading) {
                                                downloadManager.pauseAllDownloads()
                                            } else {
                                                downloadManager.resumeAllDownloads()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (anyDownloading) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = Color(0xFF0284C7)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (anyDownloading) "সব পজ করুন" else "সব চালু করুন",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            activeDownloads.forEach { activeItem ->
                                ActiveDownloadProgressCard(
                                    item = activeItem,
                                    speedMBs = activeSpeeds[activeItem.id] ?: 0f,
                                    downloadManager = downloadManager,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Search Bar + Category Filter Chips + Sort Options
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "ডাউনলোডকৃত ভিডিও বা নোট খুঁজুন...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            focusManager.clearFocus()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )

                        // Scrollable Filter Chips & Sort Button Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val categories = listOf(
                                "সবগুলো (${completedAndFailedDownloads.size.toBengaliDigits()})" to Icons.Default.Folder,
                                "ভিডিও (${videoCount.toBengaliDigits()})" to Icons.Default.PlayCircleFilled,
                                "নোট/পিডিএফ (${pdfCount.toBengaliDigits()})" to Icons.Default.PictureAsPdf
                            )
                            categories.forEachIndexed { index, (label, icon) ->
                                val isSelected = selectedCategoryIndex == index
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategoryIndex = index },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0284C7),
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        selectedBorderColor = Color(0xFF0284C7)
                                    )
                                )
                            }

                            // Cycle Sort Order Chip
                            val sortLabel = when (selectedSortOrder) {
                                1 -> "সাইজ অনুযায়ী"
                                2 -> "নাম অনুযায়ী"
                                else -> "নতুন প্রথমে"
                            }
                            AssistChip(
                                onClick = { selectedSortOrder = (selectedSortOrder + 1) % 3 },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF0284C7)
                                    )
                                },
                                label = {
                                    Text(
                                        text = sortLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }

                // File List or Empty View
                if (filteredList.isEmpty()) {
                    if (activeDownloads.isEmpty()) {
                        item {
                            EmptyDownloadsView(
                                isVideosTab = selectedCategoryIndex != 2,
                                isSearching = searchQuery.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp)
                            )
                        }
                    }
                } else {
                    items(filteredList, key = { it.id }) { item ->
                        DownloadedItemCard(
                            item = item,
                            downloadManager = downloadManager,
                            onClick = {
                                if (item.status != DownloadedItemEntity.STATUS_COMPLETED) {
                                    downloadManager.resumeDownload(item.id)
                                    Toast.makeText(
                                        context,
                                        "ডাউনলোড পুনরায় শুরু করা হচ্ছে...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@DownloadedItemCard
                                }

                                val localFile = File(item.localFilePath)
                                if (!localFile.exists() || localFile.length() == 0L) {
                                    Toast.makeText(
                                        context,
                                        "ফাইলটি ডিভাইসে পাওয়া যায়নি, পুনরায় ডাউনলোড করা হচ্ছে",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    downloadManager.resumeDownload(item.id)
                                    return@DownloadedItemCard
                                }

                                if (item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)) {
                                    onPlayVideo(
                                        item.localFilePath,
                                        item.title,
                                        item.subtitle ?: "অফলাইন লেকচার",
                                        "#0284C7",
                                        false
                                    )
                                } else {
                                    if (onOpenPdf != null) {
                                        onOpenPdf(item.localFilePath, item.title)
                                    } else {
                                        activePdfViewerItem = item
                                    }
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
    completedCount: Int,
    downloadManager: AppFileDownloadManager,
    modifier: Modifier = Modifier
) {
    val appUsedStr = remember(appUsedBytes) { downloadManager.formatFileSize(appUsedBytes, inBengali = true) }
    val freeStr = remember(deviceFreeBytes) { downloadManager.formatFileSize(deviceFreeBytes, inBengali = true) }
    val isLowStorage = deviceFreeBytes in 1L..(500L * 1024L * 1024L) // < 500 MB free

    val targetFraction = remember(appUsedBytes, deviceTotalBytes) {
        if (deviceTotalBytes > 0L) {
            (appUsedBytes.toFloat() / deviceTotalBytes.toFloat()).coerceIn(0.02f, 1f)
        } else {
            0.05f
        }
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "storageFraction"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0284C7).copy(alpha = 0.18f),
                                        Color(0xFF38BDF8).copy(alpha = 0.10f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "অফলাইন স্টোরেজ মিটার",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "সর্বমোট সংরক্ষিত ফাইল: ${completedCount.toBengaliDigits()} টি",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLowStorage) {
                        Color(0xFFEF4444).copy(alpha = 0.14f)
                    } else {
                        Color(0xFF10B981).copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = if (isLowStorage) "মেমোরি কম" else "মেমোরি ঠিক আছে",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLowStorage) Color(0xFFDC2626) else Color(0xFF059669),
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dual Storage Meter Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF06B6D4))
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
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7))
                    )
                    Text(
                        text = "অ্যাপ ডাউনলোড: $appUsedStr",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isLowStorage) Color(0xFFEF4444) else Color(0xFF10B981))
                    )
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
    speedMBs: Float,
    downloadManager: AppFileDownloadManager,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val isPaused = item.status == DownloadedItemEntity.STATUS_PAUSED

    val downloadedStr = remember(item.downloadedBytes) {
        downloadManager.formatFileSize(item.downloadedBytes, inBengali = true)
    }
    val totalStr = remember(item.totalBytes) {
        if (item.totalBytes > 0L) downloadManager.formatFileSize(item.totalBytes, inBengali = true) else "..."
    }

    val animatedProgress by animateFloatAsState(
        targetValue = item.progressFraction,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "activeDownloadProgress"
    )

    val cardBg = when {
        isPaused && isDark -> Color(0xFF291D0A)
        isPaused && !isDark -> Color(0xFFFFFBEB)
        !isPaused && isDark -> Color(0xFF0C223B)
        else -> Color(0xFFF0F9FF)
    }

    val borderCol = when {
        isPaused -> Color(0xFFF59E0B).copy(alpha = if (isDark) 0.55f else 0.7f)
        else -> Color(0xFF38BDF8).copy(alpha = if (isDark) 0.45f else 0.7f)
    }

    val primaryTextCol = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryTextCol = if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderCol),
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isPaused) {
                                Color(0xFFF59E0B).copy(alpha = 0.18f)
                            } else {
                                Color(0xFF0284C7).copy(alpha = 0.18f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PauseCircleFilled else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isPaused) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val statusLine = if (isPaused) {
                        "পজ করা রয়েছে • $downloadedStr / $totalStr (${item.progressPercent.toBengaliDigits()}%)"
                    } else {
                        val speedText = formatSpeedChipBangla(speedMBs)
                        "$downloadedStr / $totalStr • $speedText (${item.progressPercent.toBengaliDigits()}%)"
                    }
                    Text(
                        text = statusLine,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTextCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            if (isPaused) {
                                downloadManager.resumeDownload(item.id)
                            } else {
                                downloadManager.pauseDownload(item.id)
                            }
                        },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isPaused) {
                                Color(0xFF10B981).copy(alpha = 0.18f)
                            } else {
                                Color(0xFF0284C7).copy(alpha = 0.18f)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) Color(0xFF10B981) else Color(0xFF0284C7),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { downloadManager.cancelDownload(item.id) },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(if (isPaused) Color(0xFFF59E0B) else Color(0xFF0284C7))
                )
            }
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
    val isDark = isSystemInDarkTheme()
    val isVideo = item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)
    val isFailed = item.status == DownloadedItemEntity.STATUS_FAILED
    val isPaused = item.status == DownloadedItemEntity.STATUS_PAUSED

    val subjectLabel = item.subtitle?.takeIf { it.isNotBlank() }
        ?: if (isVideo) "ভিডিও লেকচার" else "লেকচার শিট"

    val sizeText = remember(item.totalBytes, item.downloadedBytes) {
        val bytesToShow = if (item.totalBytes > 0L) item.totalBytes else item.downloadedBytes
        if (bytesToShow > 0L) downloadManager.formatFileSize(bytesToShow, inBengali = true) else ""
    }

    val dateText = remember(item.createdAt) {
        formatDownloadDateBangla(item.createdAt)
    }

    val accentColor = when {
        isFailed -> Color(0xFFEF4444)
        isVideo -> Color(0xFF0284C7)
        else -> Color(0xFF7C3AED)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isFailed) {
                Color(0xFFEF4444).copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
            }
        ),
        shadowElevation = 1.5.dp,
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
            // Type / Status Icon Container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = if (isDark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isFailed -> Icons.Default.ErrorOutline
                        isVideo -> Icons.Default.PlayCircleFilled
                        else -> Icons.Default.PictureAsPdf
                    },
                    contentDescription = null,
                    tint = accentColor,
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
                        color = accentColor.copy(alpha = 0.14f),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = if (isFailed) "ডাউনলোড ব্যর্থ" else subjectLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (sizeText.isNotBlank()) {
                        Text(
                            text = "• $sizeText",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (dateText.isNotBlank() && !isFailed) {
                        Text(
                            text = "• $dateText",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1
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

                if (isFailed) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "পুনরায় ডাউনলোড শুরু করতে ট্যাপ করুন",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF4444)
                    )
                }
            }

            // Actions (Retry / Delete)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isFailed || isPaused) {
                    FilledTonalIconButton(
                        onClick = { downloadManager.resumeDownload(item.id) },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF0284C7).copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
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
    modifier: Modifier = Modifier,
    isSearching: Boolean = false
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF0284C7).copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isSearching -> Icons.Default.SearchOff
                    isVideosTab -> Icons.Default.CloudDownload
                    else -> Icons.Default.PictureAsPdf
                },
                contentDescription = null,
                tint = Color(0xFF0284C7).copy(alpha = 0.8f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                isSearching -> "কোনো ফাইল খুঁজে পাওয়া যায়নি"
                isVideosTab -> "কোনো ডাউনলোডকৃত ভিডিও নেই"
                else -> "কোনো ডাউনলোডকৃত নোট বা পিডিএফ নেই"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSearching) {
                "অনুগ্রহ করে অন্য কোনো নাম বা কি-ওয়ার্ড দিয়ে খুঁজে দেখুন"
            } else {
                "ডাউনলোড করা ক্লাস ও নোট এখানে ইন্টারনেট ছাড়াই অফলাইনে দেখার জন্য জমা থাকবে"
            },
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
