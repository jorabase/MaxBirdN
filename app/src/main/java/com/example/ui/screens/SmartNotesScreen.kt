package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.api.AcademicChapterItem
import com.example.api.TaggableResourceItem
import com.example.smartnotes.SmartNotesUiEvent
import com.example.smartnotes.SmartNotesViewModel
import com.example.ui.components.AttachmentListDialog
import com.example.ui.components.SlideViewerDialog

private fun toBengaliDigits(number: Any): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return number.toString().map { char ->
        if (char in '0'..'9') bnDigits[char - '0'] else char
    }.joinToString("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartNotesScreen(
    subjectCode: String,
    subjectTitle: String,
    subjectColorHex: String?,
    phaseId: String?,
    programId: String? = null,
    viewModel: SmartNotesViewModel,
    onBack: () -> Unit,
    onNavigateToChapterResources: (chapterId: String, chapterName: String, subjectCode: String, phaseId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val parsedSubjectColor = remember(subjectColorHex) {
        try {
            if (!subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } else {
                Color(0xFF2563EB)
            }
        } catch (_: Exception) {
            Color(0xFF2563EB)
        }
    }

    LaunchedEffect(subjectCode, phaseId, programId) {
        viewModel.initialize(
            subjectCode = subjectCode,
            subjectTitle = subjectTitle,
            subjectColor = subjectColorHex,
            phaseId = phaseId,
            programId = programId
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SmartNotesUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is SmartNotesUiEvent.OpenPdfUrl -> {
                    // SlideViewerDialog handles in-app reading, but user can also launch external view if needed
                }
            }
        }
    }

    // In-app PDF Viewer Dialog
    if (uiState.activeAttachment != null) {
        val attachment = uiState.activeAttachment!!
        SlideViewerDialog(
            slideUrl = attachment.url ?: "",
            title = attachment.title ?: "ই-বুক / স্মার্ট নোট",
            onDismiss = {
                viewModel.dismissActiveAttachment()
            }
        )
    }

    // Attachment List Dialog
    if (uiState.selectedTagAttachments != null && uiState.activeAttachment == null) {
        AttachmentListDialog(
            attachments = uiState.selectedTagAttachments!!,
            title = uiState.selectedTagTitle ?: "ডকুমেন্ট লিস্ট",
            accentColor = parsedSubjectColor,
            onAttachmentClick = { attachment ->
                viewModel.selectAttachment(attachment)
            },
            onDismiss = {
                viewModel.dismissTagAttachments()
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
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("smart_notes_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(parsedSubjectColor)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = subjectTitle.ifBlank { "স্মার্ট নোট ও ই-বুক" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "ই-বুক ও রিসোর্সেস",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Two-Tab Pill Switcher (স্ক্রিনশটের মতো: সাবজেক্ট রিসোর্সেস | চ্যাপ্টার রিসোর্সেস)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
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
                                // Tab 0: সাবজেক্ট রিসোর্সেস
                                val isTab0 = uiState.selectedTab == 0
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = if (isTab0) parsedSubjectColor else Color.Transparent,
                                    shadowElevation = if (isTab0) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable { viewModel.setSelectedTab(0) }
                                        .testTag("tab_subject_resources")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "সাবজেক্ট রিসোর্সেস",
                                            fontSize = 13.sp,
                                            fontWeight = if (isTab0) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTab0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Tab 1: চ্যাপ্টার রিসোর্সেস
                                val isTab1 = uiState.selectedTab == 1
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = if (isTab1) parsedSubjectColor else Color.Transparent,
                                    shadowElevation = if (isTab1) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable { viewModel.setSelectedTab(1) }
                                        .testTag("tab_chapter_resources")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "চ্যাপ্টার রিসোর্সেস",
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
        modifier = modifier.testTag("smart_notes_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.selectedTab) {
                0 -> {
                    // ==========================================
                    // Tab 1: সাবজেক্ট রিসোর্সেস (3 Columns Grid)
                    // ==========================================
                    when {
                        uiState.isSubjectResourcesLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = parsedSubjectColor,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "রিসোর্স লোড হচ্ছে...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        uiState.subjectResources.isEmpty() -> {
                            EmptyResourceView(
                                message = "এই বিষয়ের কোনো সাবজেক্ট রিসোর্স পাওয়া যায়নি",
                                onRetry = {
                                    viewModel.loadSubjectResources(subjectCode, phaseId)
                                }
                            )
                        }

                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.subjectResources, key = { it.id ?: it.title ?: "" }) { resource ->
                                    val isOpeningThis = uiState.isOpeningPdf && uiState.openingTagId == resource.id

                                    ResourceGridCard(
                                        resource = resource,
                                        accentColor = parsedSubjectColor,
                                        isLoading = isOpeningThis,
                                        onClick = {
                                            val tagId = resource.id ?: return@ResourceGridCard
                                            viewModel.openResourcePdf(
                                                subjectCode = subjectCode,
                                                tagId = tagId,
                                                isSubjectSpecific = true,
                                                resourceTitle = resource.title ?: "ই-বুক"
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // ==========================================
                    // Tab 2: চ্যাপ্টার রিসোর্সেস (Chapter List)
                    // ==========================================
                    when {
                        uiState.isChaptersLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = parsedSubjectColor,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "অধ্যায়সমূহ লোড হচ্ছে...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        uiState.chapters.isEmpty() -> {
                            EmptyResourceView(
                                message = "কোনো অধ্যায় পাওয়া যায়নি",
                                onRetry = {
                                    viewModel.loadChapters(subjectCode, phaseId)
                                }
                            )
                        }

                        else -> {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.chapters, key = { it.id ?: it.chapter_id ?: "" }) { chapter ->
                                    ChapterResourceListItem(
                                        chapter = chapter,
                                        accentColor = parsedSubjectColor,
                                        onClick = {
                                            val chId = chapter.id ?: chapter.chapter_id ?: ""
                                            val chName = chapter.effectiveName
                                            onNavigateToChapterResources(
                                                chId,
                                                chName,
                                                subjectCode,
                                                phaseId ?: ""
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResourceGridCard(
    resource: TaggableResourceItem,
    accentColor: Color,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .testTag("resource_card_${resource.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!resource.icon_url.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(resource.icon_url)
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = resource.title,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title
                Text(
                    text = resource.title ?: "রিসোর্স",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
    }
}

@Composable
fun ChapterResourceListItem(
    chapter: AcademicChapterItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.5.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("chapter_resource_item_${chapter.id ?: chapter.chapter_id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (chapter.effectiveNo != null) {
                    Text(
                        text = "অধ্যায় ${toBengaliDigits(chapter.effectiveNo.toString())}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = chapter.effectiveName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Blue circular chevron icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Open Chapter Resources",
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyResourceView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("পুনরায় চেষ্টা করুন")
        }
    }
}
