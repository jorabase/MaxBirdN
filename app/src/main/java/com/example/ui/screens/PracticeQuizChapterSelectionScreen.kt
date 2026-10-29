package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.api.HierarchyChapterItem
import com.example.quiz.PracticeQuizUiState
import com.example.quiz.PracticeQuizViewModel

private fun toBengaliDigits(number: Any): String {
    val english = number.toString()
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val builder = StringBuilder()
    for (char in english) {
        if (char in '0'..'9') {
            builder.append(banglaDigits[char - '0'])
        } else {
            builder.append(char)
        }
    }
    return builder.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeQuizChapterSelectionScreen(
    subjectCode: String,
    subjectTitle: String,
    subjectColorHex: String?,
    targetChapterId: String? = null,
    targetChapterName: String? = null,
    viewModel: PracticeQuizViewModel,
    onBack: () -> Unit,
    onProceedToCountSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val subjectColor = remember(subjectColorHex) {
        try {
            if (!subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    LaunchedEffect(subjectCode, targetChapterId) {
        if (uiState.chapters.isEmpty() || uiState.subjectCode != subjectCode || uiState.targetChapterId != targetChapterId) {
            viewModel.loadSubjectChapters(
                subjectCode = subjectCode,
                subjectTitle = subjectTitle,
                subjectColorHex = subjectColorHex,
                targetChapterId = targetChapterId,
                targetChapterName = targetChapterName
            )
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
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
                                .testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "প্র্যাকটিস কুইজ",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "ধাপ ১/৩ • অধ্যায় নির্বাচন",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Selected count pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = subjectColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, subjectColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${toBengaliDigits(uiState.selectedChaptersCount)}টি নির্বাচিত",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = subjectColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.33f)
                                .background(subjectColor)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val isEnabled = uiState.selectedChapterIds.isNotEmpty()
                    Button(
                        onClick = onProceedToCountSelection,
                        enabled = isEnabled,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = subjectColor,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("proceed_button")
                    ) {
                        Text(
                            text = if (isEnabled) {
                                "এগিয়ে যাও (${toBengaliDigits(uiState.selectedChaptersCount)}টি অধ্যায়)"
                            } else {
                                "অধ্যায় সিলেক্ট করুন"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("practice_quiz_chapter_selection_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                            color = subjectColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "কুইজের অধ্যায়সমূহ লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.chaptersError != null && uiState.chapters.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.chaptersError ?: "অধ্যায় লোড করা যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.loadSubjectChapters(
                                    subjectCode = subjectCode,
                                    subjectTitle = subjectTitle,
                                    subjectColorHex = subjectColorHex
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Subject Info Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = subjectColor.copy(alpha = 0.14f),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (!uiState.subjectIcon.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = uiState.subjectIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.MenuBook,
                                                    contentDescription = null,
                                                    tint = subjectColor,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = subjectTitle.ifBlank { "বিষয় অধ্যায়সমূহ" },
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "মোট সক্রিয় প্রশ্ন: ${toBengaliDigits(uiState.totalActiveQuestionsInSubject)}টি",
                                            fontSize = 12.sp,
                                            color = subjectColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Section Header / Single Chapter Mode indicator
                        item {
                            if (uiState.isSingleChapterMode) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = subjectColor.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, subjectColor.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "এই অধ্যায়ের প্র্যাকটিস কুইজ",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = subjectColor
                                            )
                                            Text(
                                                text = "নির্দিষ্ট অধ্যায় থেকে প্রশ্নের সেট তৈরি হবে",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = { viewModel.showAllChapters() },
                                            colors = ButtonDefaults.textButtonColors(contentColor = subjectColor)
                                        ) {
                                            Text("সব অধ্যায়", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "অধ্যায় ও টপিক সিলেক্ট করো",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                        }

                        // "Select All Chapters" Card (only in multi-chapter mode)
                        if (!uiState.isSingleChapterMode && uiState.chapters.size > 1) {
                            item {
                                val isAllSelected = uiState.areAllChaptersSelected
                                val checkScale by animateFloatAsState(
                                    targetValue = if (isAllSelected) 1f else 0.85f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "checkScale"
                                )

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isAllSelected) subjectColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        width = if (isAllSelected) 1.5.dp else 1.dp,
                                        color = if (isAllSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { viewModel.toggleSelectAllChapters() }
                                        .testTag("select_all_chapters_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isAllSelected) subjectColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(
                                                width = 1.5.dp,
                                                color = if (isAllSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.size(24.dp).scale(checkScale)
                                        ) {
                                            if (isAllSelected) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "সকল অধ্যায়",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAllSelected) subjectColor else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "সবগুলো অধ্যায় একসাথে অনুশীলন করতে",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Individual Chapters
                        items(
                            items = uiState.chapters,
                            key = { it.id }
                        ) { chapter ->
                            ChapterSelectionItem(
                                chapter = chapter,
                                isSelected = uiState.selectedChapterIds.contains(chapter.id),
                                subjectColor = subjectColor,
                                onToggle = { viewModel.toggleChapterSelection(chapter.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterSelectionItem(
    chapter: HierarchyChapterItem,
    isSelected: Boolean,
    subjectColor: Color,
    onToggle: () -> Unit
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "itemCheckScale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) subjectColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) subjectColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isSelected) 1.5.dp else 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .testTag("chapter_item_${chapter.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) subjectColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (isSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.size(22.dp).scale(checkScale)
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.name ?: "অধ্যায়",
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val questionCount = chapter.total_active_questions ?: 0
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = subjectColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${toBengaliDigits(questionCount)}টি প্রশ্ন",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = subjectColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (chapter.no != null && chapter.no.toString().isNotBlank()) {
                        Text(
                            text = "অধ্যায় ${toBengaliDigits(chapter.no.toString())}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
