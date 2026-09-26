package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    val allDownloads by downloadManager.getAllDownloads().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Video, 1: PDF
    var itemToDelete by remember { mutableStateOf<DownloadedItemEntity?>(null) }
    var activePdfViewerItem by remember { mutableStateOf<DownloadedItemEntity?>(null) }
    var isCheckingNetworkManually by remember { mutableStateOf(false) }

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

    // In offline mode, user cannot navigate to normal online screens
    // Double back to exit the app
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

    val videoDownloads = remember(allDownloads) {
        allDownloads.filter { it.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true) }
    }
    val pdfDownloads = remember(allDownloads) {
        allDownloads.filter { it.fileType.equals(DownloadedItemEntity.FILE_TYPE_PDF, ignoreCase = true) }
    }

    val currentList = if (selectedTab == 0) videoDownloads else pdfDownloads

    // In-App PDF Viewer for downloaded PDFs
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
            title = {
                Text(
                    text = "ডাউনলোড মুছে ফেলবেন?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিত যে \"${item.title}\" ফাইলটি আপনার অফলাইন স্টোরেজ থেকে ডিলিট করতে চান?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadManager.deleteDownloadedFile(item.id)
                        itemToDelete = null
                        Toast.makeText(context, "ফাইলটি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
                            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                text = "অফলাইন ডাউনলোড",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "সংরক্ষিত ভিডিও ও লেকচার নোটস",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Total Downloads Count Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "${allDownloads.size} টি ফাইল",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Tab Switcher (ভিডিও লেকচার | ই-বুক ও নোট)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Tab 0: ভিডিও
                                val isTab0 = selectedTab == 0
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = if (isTab0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shadowElevation = if (isTab0) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable { selectedTab = 0 }
                                        .testTag("tab_download_videos")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "ভিডিও লেকচার (${videoDownloads.size})",
                                            fontSize = 13.sp,
                                            fontWeight = if (isTab0) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTab0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Tab 1: পিডিএফ
                                val isTab1 = selectedTab == 1
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = if (isTab1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shadowElevation = if (isTab1) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable { selectedTab = 1 }
                                        .testTag("tab_download_pdfs")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "ই-বুক ও নোট (${pdfDownloads.size})",
                                            fontSize = 13.sp,
                                            fontWeight = if (isTab1) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTab1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
            Column(modifier = Modifier.fillMaxSize()) {

                // ==========================================
                // REAL-TIME AUTO RESTORE BANNER (IF ONLINE)
                // ==========================================
                AnimatedVisibility(
                    visible = isOfflineOnly && autoDetectedOnline,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ইন্টারনেট সংযোগ পাওয়া গেছে!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14532D)
                                )
                                Text(
                                    text = "অনলাইন মোডে ফিরে যেতে ট্যাপ করুন",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF166534)
                                )
                            }
                            Button(
                                onClick = { onNavigateOnline?.invoke() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("অনলাইনে যান", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }



                // ==========================================
                // DOWNLOADS LIST
                // ==========================================
                if (currentList.isEmpty()) {
                    EmptyDownloadsView(
                        isVideosTab = selectedTab == 0,
                        isOfflineOnly = isOfflineOnly,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        items(currentList, key = { it.id }) { item ->
                            DownloadedItemCard(
                                item = item,
                                onClick = {
                                    if (item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)) {
                                        onPlayVideo(
                                            item.localFilePath,
                                            item.title,
                                            item.subtitle ?: "সাধারণ",
                                            "#0072EC",
                                            false
                                        )
                                    } else {
                                        activePdfViewerItem = item
                                    }
                                },
                                onDelete = { itemToDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedItemCard(
    item: DownloadedItemEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val isVideo = item.fileType.equals(DownloadedItemEntity.FILE_TYPE_VIDEO, ignoreCase = true)
    val isDownloading = item.status == DownloadedItemEntity.STATUS_DOWNLOADING
    val isFailed = item.status == DownloadedItemEntity.STATUS_FAILED

    val subjectLabel = item.subtitle?.takeIf { it.isNotBlank() } ?: if (isVideo) "ভিডিও" else "পিডিএফ"
    val sizeText = remember(item.totalBytes) {
        if (item.totalBytes > 0) downloadManager.formatFileSize(item.totalBytes) else ""
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isDownloading, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type Icon / Status Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isVideo) Color(0xFFE0F2FE) else Color(0xFFFEF3C7)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.PlayCircleFilled else Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = if (isVideo) Color(0xFF0284C7) else Color(0xFFD97706),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // File Details
                Column(modifier = Modifier.weight(1f)) {
                    // Subject & Size pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = subjectLabel,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (sizeText.isNotBlank()) {
                            Text(
                                text = "•  $sizeText",
                                fontSize = 11.sp,
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

                // Right Delete Action
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

            // Progress Bar if Downloading
            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { item.progressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${item.progressPercent}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsView(
    isVideosTab: Boolean,
    isOfflineOnly: Boolean = false,
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
            text = "সংরক্ষিত ফাইল এখানে দেখা যাবে",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
