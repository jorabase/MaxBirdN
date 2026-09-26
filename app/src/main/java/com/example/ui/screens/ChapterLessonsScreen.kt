package com.example.ui.screens

import androidx.compose.animation.*
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
import com.example.api.StudentLessonItem
import com.example.auth.SessionManager
import com.example.course.CourseViewModel
import com.example.ui.components.chapter.*
import com.example.utils.ClassTypeUtils
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterLessonsScreen(
    chapterId: String,
    chapterName: String,
    chapterStatus: String,
    initialTab: Int = 0,
    subjectCode: String = "",
    subjectTitle: String = "",
    subjectColorHex: String? = null,
    viewModel: CourseViewModel,
    completedItemRepository: com.example.database.CompletedItemRepository? = null,
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    onNavigateToPracticeQuiz: ((subjectCode: String, subjectTitle: String, subjectColor: String?, chapterId: String, chapterName: String) -> Unit)? = null,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapterName: String) -> Unit)? = null,
    onNavigateToChapterResources: ((chapterId: String, chapterName: String, subjectCode: String, phaseId: String) -> Unit)? = null,
    onNavigateToAnimatedLessons: ((chapterId: String, chapterName: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    var lessonCompletionCounter by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()

    var selectedFilter by remember(initialTab) {
        mutableStateOf(
            if (initialTab == 1) ChapterContentFilter.EXAM else ChapterContentFilter.ALL
        )
    }

    // Explicitly Separate Live Classes, Recorded Classes, and Exams
    val liveLessons = remember(uiState.lessons) {
        uiState.lessons.filter { !it.isExam && (it.isLiveNow || it.isUpcoming) }
    }
    val recordedLessons = remember(uiState.lessons) {
        uiState.lessons.filter { !it.isExam && !it.isLiveNow && !it.isUpcoming }
    }
    val examLessons = remember(uiState.lessons) {
        uiState.lessons.filter { it.isExam }
    }

    val displayedLessons = remember(uiState.lessons, selectedFilter, liveLessons, recordedLessons, examLessons) {
        when (selectedFilter) {
            ChapterContentFilter.ALL -> uiState.lessons
            ChapterContentFilter.LIVE -> liveLessons
            ChapterContentFilter.RECORDED -> recordedLessons
            ChapterContentFilter.EXAM -> examLessons
        }
    }

    val effectiveSubjectCode = subjectCode.ifBlank { uiState.selectedSubjectCode }
    val effectiveSubjectTitle = subjectTitle.ifBlank { uiState.selectedSubjectTitle }
    val effectiveSubjectColor = subjectColorHex?.ifBlank { null } ?: uiState.selectedSubjectColor

    val subjectColor = remember(effectiveSubjectColor) {
        try {
            if (effectiveSubjectColor.isNotBlank()) {
                Color(android.graphics.Color.parseColor(effectiveSubjectColor))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    LaunchedEffect(chapterId, uiState.programId, uiState.activePhaseId) {
        val matching = uiState.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val altId = matching?.chapter_id?.takeIf { it != chapterId } ?: matching?.id?.takeIf { it != chapterId }
        viewModel.loadLessonsForChapter(
            chapterId = chapterId,
            altChapterId = altId,
            chapterName = chapterName,
            chapterStatus = chapterStatus
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
                            text = chapterName.ifBlank { "ক্লাস ও পরীক্ষা তালিকা" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (effectiveSubjectTitle.isNotBlank()) {
                            Text(
                                text = effectiveSubjectTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = subjectColor
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("chapter_lessons_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLessonsLoading -> {
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
                            text = "ক্লাস ও লেকচার লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.lessons.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
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
                                    imageVector = Icons.Default.PlayLesson,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        val errorMsg = uiState.lessonsErrorMessage
                        val displayMsg = if (errorMsg.isNullOrBlank() || errorMsg.contains("আইডি") || errorMsg.contains("http") || errorMsg.contains("HTTP") || errorMsg.contains("এরর") || errorMsg.contains("Error")) {
                            "এই অধ্যায়ে কোনো ক্লাস বা লেকচার পাওয়া যায়নি"
                        } else {
                            errorMsg
                        }
                        Text(
                            text = displayMsg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val matching = uiState.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
                                val altId = matching?.chapter_id?.takeIf { it != chapterId } ?: matching?.id?.takeIf { it != chapterId }
                                viewModel.loadLessonsForChapter(
                                    chapterId = chapterId,
                                    altChapterId = altId,
                                    chapterName = chapterName,
                                    chapterStatus = chapterStatus
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
                        // 1. Top Shortcuts: Practice Quiz, E-Book, Animated Lessons
                        item {
                            ChapterFeatureShortcuts(
                                onOpenPracticeQuiz = {
                                    onNavigateToPracticeQuiz?.invoke(
                                        effectiveSubjectCode,
                                        effectiveSubjectTitle,
                                        effectiveSubjectColor,
                                        chapterId,
                                        chapterName
                                    )
                                },
                                onOpenEbook = {
                                    onNavigateToChapterResources?.invoke(
                                        chapterId,
                                        chapterName,
                                        effectiveSubjectCode,
                                        uiState.activePhaseId ?: ""
                                    )
                                },
                                onOpenAnimatedLessons = {
                                    val matching = uiState.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
                                    val targetChapterId = matching?.chapter_id ?: matching?.id ?: chapterId
                                    onNavigateToAnimatedLessons?.invoke(
                                        targetChapterId,
                                        chapterName
                                    )
                                }
                            )
                        }

                        // 2. Distinct Filter Tabs: All, Live Classes, Recorded Classes, Live Exams
                        item {
                            ChapterFilterTabs(
                                selectedFilter = selectedFilter,
                                onFilterSelected = { selectedFilter = it },
                                totalCount = uiState.lessons.size,
                                liveCount = liveLessons.size,
                                recordedCount = recordedLessons.size,
                                examCount = examLessons.size,
                                subjectColor = subjectColor
                            )
                        }

                        // 3. Section Title with Count
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (selectedFilter) {
                                        ChapterContentFilter.ALL -> "সকল ক্লাস ও পরীক্ষা (${toBengaliDigits(displayedLessons.size)}টি)"
                                        ChapterContentFilter.LIVE -> "লাইভ ক্লাসসমূহ (${toBengaliDigits(displayedLessons.size)}টি)"
                                        ChapterContentFilter.RECORDED -> "রেকর্ড ভিডিও লেকচার (${toBengaliDigits(displayedLessons.size)}টি)"
                                        ChapterContentFilter.EXAM -> "লাইভ পরীক্ষা ও মডেল টেস্ট (${toBengaliDigits(displayedLessons.size)}টি)"
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 4. Lessons List
                        if (displayedLessons.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (selectedFilter) {
                                                ChapterContentFilter.LIVE -> "এই অধ্যায়ে বর্তমানে কোনো লাইভ ক্লাস নেই"
                                                ChapterContentFilter.RECORDED -> "এই অধ্যায়ে কোনো রেকর্ড ক্লাস নেই"
                                                ChapterContentFilter.EXAM -> "এই অধ্যায়ে কোনো পরীক্ষা নেই"
                                                ChapterContentFilter.ALL -> "কোনো ক্লাস বা পরীক্ষা পাওয়া যায়নি"
                                            },
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = displayedLessons,
                                key = { it.id }
                            ) { lesson ->
                                val isCompleted = sessionManager.isLessonCompleted(lesson.id) ||
                                        completedItemRepository?.isCompletedSync(lesson.id) == true ||
                                        completedItemRepository?.isCompletedSync(lesson.content_id) == true ||
                                        lesson.user_activity_state.equals("COMPLETED", ignoreCase = true) ||
                                        lesson.user_activity_state.equals("ATTENDED", ignoreCase = true)

                                val currentCounter = lessonCompletionCounter

                                ChapterLessonItemCard(
                                    lesson = lesson,
                                    isCompleted = isCompleted,
                                    subjectColor = subjectColor,
                                    onClick = {
                                        sessionManager.markLessonCompleted(lesson.id)
                                        if (!lesson.content_id.isNullOrBlank()) sessionManager.markLessonCompleted(lesson.content_id)
                                        lessonCompletionCounter++
                                        coroutineScope.launch {
                                            completedItemRepository?.markCompleted(
                                                itemId = lesson.id,
                                                itemType = if (lesson.isExam) "EXAM" else "LESSON",
                                                title = lesson.title ?: "",
                                                subjectId = lesson.subject_id ?: "",
                                                programId = lesson.program_id ?: "",
                                                chapterId = lesson.chapter_id ?: ""
                                            )
                                        }
                                        val isExamLesson = lesson.isExam ||
                                                lesson.content_type?.contains("EXAM", ignoreCase = true) == true ||
                                                lesson.class_type?.contains("EXAM", ignoreCase = true) == true

                                        if (lesson.isModelTest && onOpenLessonDetail != null) {
                                            onOpenLessonDetail(lesson)
                                        } else if (isExamLesson && onNavigateToExam != null) {
                                            viewModel.selectLesson(lesson)
                                            val sessionId = lesson.session_id?.takeIf { it.isNotBlank() }
                                                ?: lesson.live_class?.session_id?.takeIf { it.isNotBlank() }
                                                ?: lesson.content_id?.takeIf { it.isNotBlank() }
                                                ?: lesson.id
                                            val formattedTitle = ClassTypeUtils.formatLessonTitle(lesson.title ?: "অধ্যায় পরীক্ষা")
                                            onNavigateToExam(sessionId, lesson.id, formattedTitle, chapterName)
                                        } else if (onOpenLessonDetail != null) {
                                            onOpenLessonDetail(lesson)
                                        } else {
                                            viewModel.selectLesson(lesson)
                                            val videoUrl = lesson.resolvedVideoUrl
                                                ?: lesson.live_class?.resolvedVideoUrl
                                                ?: lesson.live_class?.recording_url
                                                ?: ""
                                            val title = ClassTypeUtils.formatLessonTitle(lesson.title)
                                            val isLive = lesson.isLive && !lesson.isRecorded
                                            onPlayVideo(
                                                videoUrl,
                                                title,
                                                effectiveSubjectTitle,
                                                effectiveSubjectColor,
                                                isLive
                                            )
                                        }
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
