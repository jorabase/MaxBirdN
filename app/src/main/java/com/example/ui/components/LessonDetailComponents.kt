package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.LessonAttachmentItem
import com.example.api.StudentLessonItem
import com.example.utils.downloadFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collapsible Topics Accordion Section for Lesson Details.
 */
@Composable
fun LessonTopicsAccordion(
    lesson: StudentLessonItem?,
    subjectThemeColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topics = lesson?.topics?.takeIf { it.isNotEmpty() }
        ?: lesson?.live_class?.topics
        ?: emptyList()
    val topicTitles = topics.map { it.displayTitle }.filter { it.isNotBlank() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF1F7FF) // Soft ice-blue from screenshot
        ),
        border = BorderStroke(
            1.dp,
            Color(0xFFDCEAFE)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            val rotationState by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                label = "accordion_rotation"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ক্লাসের বিষয়বস্তু",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB) // Blue from screenshot
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "সংকোচন করুন" else "প্রসারিত করুন",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.rotate(rotationState)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        color = Color(0xFFBFDBFE).copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (topicTitles.isNotEmpty()) {
                        topicTitles.forEach { topic ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "✦",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE11D48) // Rose/magenta bullet
                                )
                                Text(
                                    text = topic,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "✦",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE11D48)
                            )
                            Text(
                                text = lesson?.title ?: "এই ক্লাসের সকল মূল আলোচ্য বিষয় অন্তর্ভুক্ত রয়েছে",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Slide and Documents List Section for Lesson Details ("ক্লাস রিসোর্সেস").
 */
@Composable
fun LessonDocumentsSection(
    lesson: StudentLessonItem?,
    context: Context,
    coroutineScope: CoroutineScope,
    isLoading: Boolean = false,
    onRefreshLesson: (() -> Unit)?,
    onViewAttachment: (LessonAttachmentItem) -> Unit,
    onOpenChapterResources: (() -> Unit)? = null,
    onOpenSubjectResources: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isRefreshingSlide by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "ক্লাস রিসোর্সেস",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        val allAttachments = lesson?.allAttachments ?: emptyList()
        val fallbackSlideUrl = lesson?.resolvedSlideUrl ?: lesson?.live_class?.lectureSlideUrl
        val primarySlideAttachment = remember(allAttachments, fallbackSlideUrl) {
            allAttachments.firstOrNull { it.file_type.equals("pdf", ignoreCase = true) || it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }
                ?: allAttachments.firstOrNull()
                ?: fallbackSlideUrl?.takeIf { it.isNotBlank() }?.let {
                    LessonAttachmentItem(
                        title = "লেকচার স্লাইড (PDF)",
                        url = it,
                        file_type = "pdf"
                    )
                }
        }

        // Primary Resource Card (লেকচার স্লাইড)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF2563EB)
                        )
                        Column {
                            Text(
                                text = "লেকচার স্লাইড খোঁজা হচ্ছে...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "সার্ভার থেকে ডেটা লোড করা হচ্ছে",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (primarySlideAttachment != null && !primarySlideAttachment.downloadUrl.isNullOrBlank()) {
                    val downloadManager = remember { com.example.download.AppFileDownloadManager.getInstance(context) }
                    val downloadUrl = primarySlideAttachment.downloadUrl
                    val pdfId = remember(downloadUrl) { "pdf_" + (downloadUrl?.hashCode().toString()) }
                    val downloadedPdf by downloadManager.getDownloadedItemById(pdfId).collectAsState(initial = null)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onViewAttachment(primarySlideAttachment)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEBF3FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CoPresent,
                                    contentDescription = "লেকচার স্লাইড",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "লেকচার স্লাইড",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (downloadedPdf?.status) {
                                        com.example.database.DownloadedItemEntity.STATUS_COMPLETED -> "ইন-অ্যাপ সংরক্ষিত • অফলাইনে দেখতে ট্যাপ করুন"
                                        com.example.database.DownloadedItemEntity.STATUS_DOWNLOADING -> "ডাউনলোড হচ্ছে... (${downloadedPdf?.progressPercent}%)"
                                        else -> primarySlideAttachment.displayTitle.takeIf { it.isNotBlank() && it != "লেকচার স্লাইড" } ?: "পিডিএফ স্লাইড ও নোটস পড়ুন"
                                    },
                                    fontSize = 12.sp,
                                    color = if (downloadedPdf?.status == com.example.database.DownloadedItemEntity.STATUS_COMPLETED) Color(0xFF10B981) else Color(0xFF2563EB),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!downloadUrl.isNullOrBlank()) {
                                when (downloadedPdf?.status) {
                                    com.example.database.DownloadedItemEntity.STATUS_DOWNLOADING -> {
                                        CircularProgressIndicator(
                                            progress = { downloadedPdf?.progressFraction ?: 0f },
                                            color = Color(0xFF2563EB),
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(20.dp).padding(2.dp)
                                        )
                                    }
                                    com.example.database.DownloadedItemEntity.STATUS_COMPLETED -> {
                                        IconButton(
                                            onClick = {
                                                Toast.makeText(context, "এই ফাইলটি ইতোমধ্যে ডাউনলোড করা রয়েছে", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DownloadDone,
                                                contentDescription = "ডাউনলোড সম্পন্ন",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        IconButton(
                                            onClick = {
                                                downloadFile(context, downloadUrl, primarySlideAttachment.displayTitle)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "ডাউনলোড",
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "দেখুন",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Any extra attachments (e.g. solution sheets, exercises)
                    val extraAttachments = allAttachments.filter { it.downloadUrl != primarySlideAttachment.downloadUrl }
                    if (extraAttachments.isNotEmpty()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.8.dp
                        )
                        extraAttachments.forEach { extra ->
                            val extraUrl = extra.downloadUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewAttachment(extra) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color(0xFFE11D48),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = extra.displayTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Empty state row when slide hasn't been uploaded yet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CoPresent,
                                    contentDescription = "লেকচার স্লাইড",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "লেকচার স্লাইড",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "এই ক্লাসের স্লাইড শীঘ্রই আপলোড করা হবে",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (onRefreshLesson != null) {
                            IconButton(
                                onClick = {
                                    if (!isRefreshingSlide) {
                                        isRefreshingSlide = true
                                        onRefreshLesson.invoke()
                                        Toast.makeText(context, "স্লাইড আপডেট চেক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            delay(1500)
                                            isRefreshingSlide = false
                                        }
                                    }
                                }
                            ) {
                                if (isRefreshingSlide) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "রিফ্রেশ",
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Side-by-side resources cards (চ্যাপ্টার রিসোর্সেস & সাবজেক্ট রিসোর্সেস)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. চ্যাপ্টার রিসোর্সেস
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (onOpenChapterResources != null) {
                            onOpenChapterResources.invoke()
                        } else {
                            Toast.makeText(context, "চ্যাপ্টার রিসোর্সেস লোড করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFFD97706), // Warm amber
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "চ্যাপ্টার রিসোর্সেস",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. সাবজেক্ট রিসোর্সেস
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (onOpenSubjectResources != null) {
                            onOpenSubjectResources.invoke()
                        } else {
                            Toast.makeText(context, "সাবজেক্ট রিসোর্সেস লোড করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = null,
                        tint = Color(0xFF059669), // Emerald
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "সাবজেক্ট রিসোর্সেস",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
