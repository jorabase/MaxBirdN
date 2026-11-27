package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.api.TaggableResourceItem
import com.example.smartnotes.SmartNotesUiEvent
import com.example.smartnotes.SmartNotesViewModel
import com.example.ui.components.AppGridLoadingSkeleton
import com.example.ui.components.AttachmentListDialog
import com.example.ui.components.SlideViewerDialog
import com.example.utils.AppLang
import com.example.utils.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterResourcesScreen(
    chapterId: String,
    chapterName: String,
    subjectCode: String,
    phaseId: String?,
    programId: String? = null,
    viewModel: SmartNotesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val parsedSubjectColor = remember(uiState.subjectColor) {
        try {
            if (uiState.subjectColor.isNotBlank()) {
                Color(android.graphics.Color.parseColor(uiState.subjectColor))
            } else {
                Color(0xFF2563EB)
            }
        } catch (_: Exception) {
            Color(0xFF2563EB)
        }
    }

    LaunchedEffect(chapterId, subjectCode, phaseId, programId) {
        viewModel.loadChapterResources(
            chapterId = chapterId,
            phaseId = phaseId,
            chapterName = chapterName,
            subjectCode = subjectCode,
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
                    // Handled by activeAttachment / SlideViewerDialog
                }
            }
        }
    }

    // In-app PDF Viewer Dialog
    uiState.activeAttachment?.let { attachment ->
        SlideViewerDialog(
            slideUrl = attachment.url ?: "",
            title = attachment.title ?: chapterName,
            onDismiss = {
                viewModel.dismissActiveAttachment()
            }
        )
    }

    // Attachment List Dialog
    val tagAttachments = uiState.selectedTagAttachments
    if (tagAttachments != null && uiState.activeAttachment == null) {
        AttachmentListDialog(
            attachments = tagAttachments,
            title = uiState.selectedTagTitle ?: tr("Documents", "ডকুমেন্ট লিস্ট"),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("chapter_resources_back_button")
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
                            text = chapterName.ifBlank { tr("Resources", "রিসোর্স") },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tr("Notes & E-Books", "রিসোর্স ও ই-বুক"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("chapter_resources_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isChapterResourcesLoading -> {
                    AppGridLoadingSkeleton(
                        modifier = Modifier.fillMaxSize(),
                        rows = 3
                    )
                }

                uiState.chapterResources.isEmpty() -> {
                    EmptyResourceView(
                        message = tr("No resources found for this chapter", "এই অধ্যায়ের জন্য কোনো রিসোর্স পাওয়া যায়নি"),
                        onRetry = {
                            viewModel.loadChapterResources(
                                chapterId = chapterId,
                                phaseId = phaseId,
                                chapterName = chapterName,
                                subjectCode = subjectCode,
                                programId = programId
                            )
                        }
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        items(uiState.chapterResources, key = { it.id ?: it.title ?: "" }) { resource ->
                            val isOpeningThis = uiState.isOpeningPdf && uiState.openingTagId == resource.id

                            ResourceGridCard(
                                resource = resource,
                                accentColor = parsedSubjectColor,
                                isLoading = isOpeningThis,
                                onClick = {
                                    val tagId = resource.id ?: return@ResourceGridCard
                                    viewModel.openResourcePdf(
                                        subjectCode = subjectCode,
                                        chapterId = chapterId,
                                        tagId = tagId,
                                        isSubjectSpecific = false,
                                        resourceTitle = "${resource.title ?: AppLang.tr("Note", "নোট")} - $chapterName"
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
