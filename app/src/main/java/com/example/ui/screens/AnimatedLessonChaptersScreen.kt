package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.AcademicChapterItem
import com.example.course.CourseViewModel
import com.example.utils.AppLang
import com.example.utils.toBengaliDigits
import com.example.utils.tr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedLessonChaptersScreen(
    subjectId: String,
    subjectTitle: String,
    subjectColorHex: String? = null,
    programId: String? = null,
    phaseId: String? = null,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, chapterName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var chaptersList by remember { mutableStateOf<List<AcademicChapterItem>>(emptyList()) }

    val themeColor = remember(subjectColorHex) {
        try {
            if (!subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } else {
                Color(0xFF8B5CF6)
            }
        } catch (_: Exception) {
            Color(0xFF8B5CF6)
        }
    }

    fun loadChapters() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val courseState = viewModel.uiState.value
                val effectiveProgId = programId?.takeIf { it.isNotBlank() }
                    ?: courseState.programId.takeIf { it.isNotBlank() }
                    ?: ""
                val effectivePhaseId = phaseId?.takeIf { it.isNotBlank() }
                    ?: courseState.selectedPhase?.id?.takeIf { it.isNotBlank() }
                    ?: courseState.activePhaseId.takeIf { it.isNotBlank() }
                    ?: ""

                val fetched = viewModel.getAllChaptersForAnimatedLessons(
                    programId = effectiveProgId,
                    phaseId = effectivePhaseId,
                    subjectCode = subjectId,
                    subjectTitle = subjectTitle
                )

                chaptersList = fetched

                if (chaptersList.isEmpty()) {
                    errorMessage = AppLang.tr("No chapters found for this subject", "এই বিষয়ের কোনো অধ্যায় পাওয়া যায়নি")
                }
            } catch (e: Exception) {
                errorMessage = AppLang.tr(
                    "Failed to load chapters: ${e.localizedMessage ?: "Network error"}",
                    "অধ্যায় লোড করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক এরর"}"
                )
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(subjectId, programId, phaseId) {
        loadChapters()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    themeColor.copy(alpha = 0.08f),
                                    themeColor.copy(alpha = 0.16f)
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = subjectTitle.ifBlank { tr("Animated Lessons", "অ্যানিমেটেড লেসন") },
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (chaptersList.isNotEmpty()) {
                                    tr("${chaptersList.size} Chapters", "মোট ${toBengaliDigits(chaptersList.size)} টি অধ্যায়")
                                } else {
                                    tr("Select a chapter", "অধ্যায় নির্বাচন করুন")
                                },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Decorative Subject Watermark / Emblem on the right (matching Screenshot 2)
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "অ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBBF24),
                                modifier = Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 2.dp)
                            )
                            Text(
                                text = "খ",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 0.dp)
                            )
                            Text(
                                text = "ক",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-2).dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("animated_lesson_chapters_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = themeColor,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = tr("Loading chapters...", "অধ্যায়সমূহ লোড হচ্ছে..."),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null && chaptersList.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: tr("No chapters found", "কোনো অধ্যায় পাওয়া যায়নি"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadChapters() },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Retry", "পুনরায় চেষ্টা করুন"))
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = chaptersList,
                            key = { idx, ch -> "${ch.chapter_id ?: ch.id}_$idx" }
                        ) { index, chapter ->
                            val effectiveId = chapter.chapter_id?.takeIf { it.isNotBlank() } ?: chapter.id
                            val chapterName = chapter.effectiveName
                            val rawNo = chapter.effectiveNo
                            val serialText = when (rawNo) {
                                is Number -> tr("Chapter ${rawNo.toInt()}", "অধ্যায় ${toBengaliDigits(rawNo.toInt())}")
                                is String -> if (rawNo.isNotBlank()) tr("Chapter $rawNo", "অধ্যায় ${toBengaliDigits(rawNo)}") else tr("Chapter $index", "অধ্যায় ${toBengaliDigits(index)}")
                                else -> tr("Chapter $index", "অধ্যায় ${toBengaliDigits(index)}")
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        onChapterClick(effectiveId, chapterName)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = chapterName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 22.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = serialText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFE0E7FF),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Animated Lessons",
                                                tint = Color(0xFF3B4FE4),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (index < chaptersList.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
