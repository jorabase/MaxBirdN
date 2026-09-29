package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.download.DownloadQualityOption

@Composable
fun VideoDownloadQualityDialog(
    videoUrl: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirmDownload: (option: DownloadQualityOption) -> Unit,
    downloadedItem: DownloadedItemEntity? = null
) {
    val context = LocalContext.current
    val downloadManager = remember(context) { AppFileDownloadManager.getInstance(context) }

    var qualityOptions by remember(videoUrl) {
        mutableStateOf<List<DownloadQualityOption>>(emptyList())
    }
    var isLoading by remember(videoUrl) {
        mutableStateOf(true)
    }

    LaunchedEffect(videoUrl) {
        isLoading = true
        try {
            val realOptions = downloadManager.getRealAvailableDownloadQualities(videoUrl)
            val sizedOptions = downloadManager.calculateRealQualitySizes(videoUrl, realOptions)
            qualityOptions = if (sizedOptions.isNotEmpty()) sizedOptions else AppFileDownloadManager.getAvailableDownloadQualities(videoUrl)
        } catch (_: Exception) {
            qualityOptions = AppFileDownloadManager.getAvailableDownloadQualities(videoUrl)
        }
        isLoading = false
    }

    var selectedOption by remember(qualityOptions) {
        mutableStateOf(qualityOptions.firstOrNull { it.isRecommended } ?: qualityOptions.getOrNull(1) ?: qualityOptions.firstOrNull())
    }

    LaunchedEffect(qualityOptions) {
        if (qualityOptions.isNotEmpty() && (selectedOption == null || !qualityOptions.any { it.id == selectedOption?.id })) {
            selectedOption = qualityOptions.firstOrNull { it.isRecommended } ?: qualityOptions.getOrNull(1) ?: qualityOptions.firstOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false, onClick = {})
                    .padding(vertical = 16.dp)
                    .testTag("video_download_quality_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header with Icon and Subtitle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "অফলাইন ভিডিও ডাউনলোড",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = title.ifBlank { "পছন্দের রেজুলেশন সিলেক্ট করে সেভ করুন" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Options Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 380.dp)
                    ) {
                        if (isLoading && qualityOptions.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
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
                                    text = "ভিডিও রেজুলেশন ও সম্ভাব্য সাইজ যাচাই করা হচ্ছে...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                qualityOptions.forEach { option ->
                                    val isSelected = selectedOption?.id == option.id
                                    val isDownloaded = downloadedItem != null &&
                                            downloadedItem.status == DownloadedItemEntity.STATUS_COMPLETED &&
                                            (downloadedItem.remoteUrl == option.targetM3u8Url ||
                                                    (downloadedItem.remoteUrl.contains(option.id) && option.id != "original"))

                                    val scale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.01f else 1f,
                                        label = "card_scale"
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        },
                                        border = BorderStroke(
                                            width = if (isSelected) 1.8.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .scale(scale)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { selectedOption = option }
                                            .testTag("download_quality_option_${option.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Radio Icon
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(22.dp)
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = option.labelBangla,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )

                                                    if (option.isRecommended) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "সেরা পছন্দ",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF059669),
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }

                                                    if (isDownloaded) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFF0284C7).copy(alpha = 0.15f)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF0284C7),
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text(
                                                                    text = "সংরক্ষিত",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF0284C7)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }


                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Estimated Size Badge
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = option.estimatedSizeBangla,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "বাতিল",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        val isAlreadyDownloaded = downloadedItem != null && downloadedItem.status == DownloadedItemEntity.STATUS_COMPLETED
                        Button(
                            onClick = {
                                selectedOption?.let { onConfirmDownload(it) }
                            },
                            enabled = selectedOption != null && !isAlreadyDownloaded,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAlreadyDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(46.dp)
                                .testTag("confirm_download_button")
                        ) {
                            Icon(
                                imageVector = if (isAlreadyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAlreadyDownloaded) "ইতিমধ্যে সেভ করা" else "ডাউনলোড শুরু",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
