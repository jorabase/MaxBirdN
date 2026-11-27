package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.utils.AppLang
import com.example.utils.isBangla
import com.example.utils.tr
import com.example.utils.toBengaliDigits
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.EnrolledProgram
import com.example.api.StudentLessonItem
import com.example.home.HomeViewModel
import com.example.ui.components.CourseProgressSection
import com.example.ui.components.CourseSwitcherBottomSheet
import com.example.ui.components.HomeHeader
import com.example.ui.components.SubjectFilterDialog
import com.example.ui.components.WeeklyRoutineSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = onNavigateToProfile,
    onNavigateToChangeSyllabus: () -> Unit = {},
    onNavigateToCourseTab: () -> Unit = {},
    onCourseSelected: (EnrolledProgram) -> Unit = {},
    onOpenCourse: (phaseId: String?) -> Unit = {},
    onOpenFullRoutine: () -> Unit = {},
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null,
    onNavigateToReportCard: (programId: String?, programTitle: String?, phaseId: String?) -> Unit = { _, _, _ -> },
    onNavigateToPracticeQuiz: (subjectCode: String, subjectTitle: String, subjectColor: String?) -> Unit = { _, _, _ -> },
    onNavigateToAnimatedLessons: (subjectCode: String, subjectTitle: String, subjectColor: String?, programId: String?, phaseId: String?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToNotificationHistory: () -> Unit = {},
    onOpenPdf: ((filePath: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wallpaperConfig by viewModel.wallpaperConfigFlow.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isDark = com.example.ui.theme.isAppInDarkTheme()

    // Mode for standalone Home Feature Subject Picker: "practice_quiz" or "animated_lessons"
    var activeFeaturePickerMode by remember { mutableStateOf<String?>(null) }

    // ===== LOGIC: Notice Popup Dialog (Admin Managed) =====
    if (uiState.showNoticePopup && uiState.notices.any { it.showAsPopup }) {
        val popupNotices = remember(uiState.notices) { uiState.notices.filter { it.showAsPopup } }
        com.example.notice.NoticePopupDialog(
            notices = popupNotices,
            onDismiss = { dontShowAgainToday ->
                viewModel.dismissNoticePopup(dontShowAgainToday)
            },
            onNoticeActionClick = { notice ->
                viewModel.dismissNoticePopup(false)
                handleNoticeAction(context, notice, onOpenPdf)
            }
        )
    }

    // Entrance animation
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
        if (uiState.activeProgram == null && !uiState.isLoading && uiState.enrolledPrograms.isEmpty()) {
            viewModel.loadData()
        }
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    val contentTranslation by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 48f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "contentTranslation"
    )

    // ===== LOGIC: Course Switcher Bottom Sheet (unchanged) =====
    if (uiState.showCourseSwitcher) {
        CourseSwitcherBottomSheet(
            enrolledPrograms = uiState.enrolledPrograms,
            activeProgram = uiState.activeProgram,
            onSelectProgram = { program ->
                viewModel.switchActiveCourse(program)
                onCourseSelected(program)
            },
            onDismiss = { viewModel.setCourseSwitcherVisible(false) },
            onChangeSyllabusClick = onNavigateToChangeSyllabus,
            onBrowseCoursesClick = onNavigateToCourseTab
        )
    }

    // ===== LOGIC: Subject Filter Dialog (unchanged) =====
    if (uiState.showSubjectFilterDialog) {
        SubjectFilterDialog(
            courseTitle = uiState.activeProgram?.title_bn ?: "",
            subjects = uiState.courseSubjects,
            selectedSubjectCodes = uiState.selectedSubjectCodes,
            isLoading = uiState.isCourseSubjectsLoading,
            isSaving = uiState.isSavingSubjectFilter,
            onToggleSubject = { code -> viewModel.toggleSubjectSelection(code) },
            onSelectAll = { viewModel.selectAllSubjects() },
            onClearAll = { viewModel.clearAllSubjectSelection() },
            onSave = { viewModel.saveSubjectFilter() },
            onDismiss = { viewModel.dismissSubjectFilterDialog() }
        )
    }

    // ===== LOGIC: Standalone Feature Subject Picker BottomSheet (Practice Quiz / Animated Lessons) =====
    if (activeFeaturePickerMode != null) {
        val availableSubjects = remember(uiState.courseSubjects, uiState.activeProgram?.id) {
            uiState.courseSubjects.distinctBy { it.code ?: it.display_bn }
        }
        LaunchedEffect(activeFeaturePickerMode, uiState.activeProgram?.id) {
            if (availableSubjects.isEmpty()) {
                viewModel.loadFeatureSubjects(forceRefresh = true)
            }
        }
        HomeFeatureSubjectPickerSheet(
            featureMode = activeFeaturePickerMode ?: "animated_lessons",
            activeCourseTitle = uiState.activeProgram?.title_bn,
            subjects = availableSubjects,
            isLoading = (uiState.isCourseSubjectsLoading || uiState.isFeatureSubjectsLoading) && availableSubjects.isEmpty(),
            onRetry = { viewModel.loadFeatureSubjects(forceRefresh = true) },
            onDismiss = { activeFeaturePickerMode = null },
            onSelectSubject = { subject ->
                val mode = activeFeaturePickerMode
                activeFeaturePickerMode = null
                val code = subject.code ?: ""
                val title = subject.display_bn ?: code
                val color = subject.color_code ?: "#4F46E5"
                if (mode == "practice_quiz") {
                    onNavigateToPracticeQuiz(code, title, color)
                } else {
                    val progId = uiState.activeProgram?.id
                    val phaseId = uiState.programPhases.find { it.is_current == true }?.id
                        ?: uiState.programPhases.firstOrNull()?.id
                    onNavigateToAnimatedLessons(code, title, color, progId, phaseId)
                }
            }
        )
    }

    // ===== LOGIC: Subtitle formatting (unchanged) =====
    val isBn = isBangla()
    val subtitle = remember(uiState.userClass, uiState.userGroup, uiState.userSchool, isBn) {
        val classDisplay = when (uiState.userClass) {
            "C11", "Class 11" -> if (isBn) "একাদশ শ্রেণি" else "Class 11"
            "C12", "Class 12" -> if (isBn) "দ্বাদশ শ্রেণি" else "Class 12"
            "C10", "Class 10" -> if (isBn) "দশম শ্রেণি" else "Class 10"
            "C9", "Class 9" -> if (isBn) "নবম শ্রেণি" else "Class 9"
            else -> uiState.userClass.ifBlank { if (isBn) "একাদশ শ্রেণি" else "Class 11" }
        }
        val groupDisplay = when (uiState.userGroup.lowercase()) {
            "humanities", "humanities_group" -> if (isBn) "মানবিক" else "Humanities"
            "science", "science_group" -> if (isBn) "বিজ্ঞান" else "Science"
            "business", "business_studies", "commerce" -> if (isBn) "ব্যবসায় শিক্ষা" else "Business"
            else -> uiState.userGroup.ifBlank { "" }
        }
        listOf(classDisplay, groupDisplay, uiState.userSchool)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { if (isBn) "এইচএসসি শিক্ষার্থী" else "HSC Student" }
    }

    val displayName = remember(uiState.userFirstName, uiState.userName, isBn) {
        val fallback = if (isBn) "শিক্ষার্থী" else "Student"
        when {
            uiState.userFirstName.isNotBlank() && uiState.userFirstName != "শিক্ষার্থী" && uiState.userFirstName != "Student" -> uiState.userFirstName
            uiState.userName.isNotBlank() && uiState.userName != "শিক্ষার্থী" && uiState.userName != "Student" -> uiState.userName
            else -> fallback
        }
    }

    val activeNoticesCount = remember(uiState.notices) { uiState.notices.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadData(isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeHeader(
                    userName = displayName,
                    subtitle = subtitle,
                    avatarUrl = uiState.userAvatar ?: "",
                    isPremium = uiState.isPremium,
                    activeCourseTitle = uiState.activeProgram?.title_bn ?: tr("Select Course", "কোর্স নির্বাচন করুন"),
                    onOpenCourseSwitcher = { viewModel.setCourseSwitcherVisible(true) },
                    onAvatarClick = { onNavigateToProfile() },
                    unreadNotificationCount = activeNoticesCount,
                    onNotificationClick = {
                        onNavigateToNotificationHistory()
                    },
                    wallpaperConfig = wallpaperConfig
                )

                // Content Container with smooth rounded top corners & glassmorphic top rim
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-16).dp)
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.28f else 0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 640.dp)
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                            .graphicsLayer {
                                alpha = contentAlpha
                                translationY = contentTranslation
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    // ===== LOGIC: Banner condition (unchanged) =====
                    if (displayName == "শিক্ষার্থী" || displayName == "Student" || uiState.userFirstName.isBlank()) {
                        AccountCompletionBanner(onCompleteClick = onNavigateToEditProfile)
                    }

                    when {
                        // ---------- Premium Shimmer Skeleton Loading ----------
                        uiState.isLoading -> {
                            ShimmerSkeletonSection()
                        }

                        // ---------- Error State ----------
                        uiState.errorMessage != null && uiState.enrolledPrograms.isEmpty() -> {
                            ErrorStateCard(
                                errorMessage = uiState.errorMessage,
                                onRetry = { viewModel.loadData(isRefresh = true) }
                            )
                        }

                        // ---------- Content ----------
                        else -> {
                            val selectedSubjectNames = remember(
                                uiState.courseSubjects,
                                uiState.selectedSubjectCodes
                            ) {
                                uiState.courseSubjects
                                    .filter { sub -> sub.code != null && uiState.selectedSubjectCodes.contains(sub.code) }
                                    .mapNotNull { sub -> sub.display_bn.takeIf { !it.isNullOrBlank() } ?: sub.code }
                            }

                            // Dynamic Notice Carousel / Banner (Admin Managed)
                            if (uiState.notices.isNotEmpty()) {
                                com.example.notice.HomeNoticeCarousel(
                                    notices = uiState.notices,
                                    onNoticeClick = { notice ->
                                        handleNoticeAction(context, notice, onOpenPdf)
                                    }
                                )
                            }

                            if (uiState.enrolledPrograms.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = tr("No Active Courses", "কোনো সক্রিয় কোর্স নেই"),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = tr(
                                                "Open the Courses tab to request access, start a free trial, or enroll in a course.",
                                                "কোর্স ট্যাবে গিয়ে ফ্রি ট্রায়াল নিতে বা কোর্সে ভর্তি হয়ে ক্লাস শুরু করুন।"
                                            ),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                        Button(
                                            onClick = onNavigateToCourseTab,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(tr("Browse Courses", "কোর্স দেখুন"), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            WeeklyRoutineSection(
                                lessons = uiState.filteredWeeklyRoutine,
                                isLoading = uiState.isRoutineLoading,
                                selectedSubjectsCount = uiState.selectedSubjectCodes.size,
                                totalSubjectsCount = uiState.courseSubjects.size,
                                selectedSubjectNames = selectedSubjectNames,
                                onSeeAllClick = { onOpenFullRoutine() },
                                onCustomizeSubjectsClick = { viewModel.openSubjectFilterDialog() },
                                onOpenLessonDetail = { lesson -> onOpenLessonDetail?.invoke(lesson) },
                                onNavigateToExam = onNavigateToExam
                            )

                            CourseProgressSection(
                                phases = uiState.programPhases,
                                onPhaseClick = { phase -> onOpenCourse(phase.id) }
                            )

                            // ============================================================
                            //  "ফিচারস" (Features) Section at the Bottom of Home Screen
                            //  (Excluding SHIKHO AI — 3 Options: Report Card, Practice Quiz, Animated Lessons)
                            // ============================================================
                            val avgProgressPct = remember(uiState.programPhases) {
                                val valid = uiState.programPhases.mapNotNull { it.course_progress_percentage }
                                if (valid.isNotEmpty()) valid.average().toInt().coerceIn(0, 100) else 0
                            }
                            HomeBottomFeaturesSection(
                                totalScorePercent = avgProgressPct,
                                onOpenReportCard = {
                                    val prog = uiState.activeProgram
                                    val phase = uiState.programPhases.find { it.is_current == true }
                                        ?: uiState.programPhases.firstOrNull()
                                    onNavigateToReportCard(prog?.id, prog?.title_bn, phase?.id)
                                },
                                onOpenPracticeQuiz = {
                                    viewModel.loadFeatureSubjects()
                                    activeFeaturePickerMode = "practice_quiz"
                                },
                                onOpenAnimatedLessons = {
                                    viewModel.loadFeatureSubjects()
                                    activeFeaturePickerMode = "animated_lessons"
                                }
                            )
                        }
                    }
                }
            }

                // Bottom spacing for floating nav bar
                Spacer(modifier = Modifier.height(104.dp))
            }
        }
    }
}

@Composable
private fun HomeBottomFeaturesSection(
    totalScorePercent: Int,
    onOpenReportCard: () -> Unit,
    onOpenPracticeQuiz: () -> Unit,
    onOpenAnimatedLessons: () -> Unit
) {
    val reportCardGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0B2252),
                Color(0xFF1D4ED8),
                Color(0xFF0284C7)
            )
        )
    }
    val quizCardGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF052E24),
                Color(0xFF047857),
                Color(0xFF0D9488)
            )
        )
    }
    val animatedCardGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF1C1347),
                Color(0xFF4338CA),
                Color(0xFF7C3AED)
            )
        )
    }

    val reportInteraction = remember { MutableInteractionSource() }
    val reportPressed by reportInteraction.collectIsPressedAsState()
    val reportScale by animateFloatAsState(
        targetValue = if (reportPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "reportCardScale"
    )

    val quizInteraction = remember { MutableInteractionSource() }
    val quizPressed by quizInteraction.collectIsPressedAsState()
    val quizScale by animateFloatAsState(
        targetValue = if (quizPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "quizCardScale"
    )

    val animInteraction = remember { MutableInteractionSource() }
    val animPressed by animInteraction.collectIsPressedAsState()
    val animScale by animateFloatAsState(
        targetValue = if (animPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "animCardScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    )
            )
            Text(
                text = tr("Features", "ফিচারস"),
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Top Row: 1. Total Score (Report Card) & 2. Practice Quiz
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Total Score -> Report Card (Royal Sapphire)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = reportScale; scaleY = reportScale }
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = reportInteraction,
                        indication = ripple(color = Color.White),
                        onClick = onOpenReportCard
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(reportCardGradient)
                ) {
                    // Ambient corner glow
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 24.dp, y = (-24).dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF38BDF8).copy(alpha = 0.38f), Color.Transparent)
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = tr("Total Score", "মোট স্কোর"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column {
                            Text(
                                text = "${toBengaliDigits(totalScorePercent)}%",
                                style = MaterialTheme.typography.displaySmall,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.28f))
                            ) {
                                val progressFraction = (totalScorePercent / 100f).coerceIn(0f, 1f)
                                if (progressFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progressFraction.coerceAtLeast(0.06f))
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF38BDF8), Color(0xFFFDE047))
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tr("Report Card", "রিপোর্ট কার্ড"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.95f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // Card 2: Practice Quiz (Deep Emerald-Teal)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = quizScale; scaleY = quizScale }
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = quizInteraction,
                        indication = ripple(color = Color.White),
                        onClick = onOpenPracticeQuiz
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(quizCardGradient)
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 24.dp, y = (-24).dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF34D399).copy(alpha = 0.35f), Color.Transparent)
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.55f)),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Quiz,
                                        contentDescription = null,
                                        tint = Color(0xFFA7F3D0),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.22f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFFFDE047),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column {
                            Text(
                                text = tr("Practice Quiz", "প্র্যাকটিস কুইজ"),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = tr("Chapter-wise MCQ practice", "অধ্যায়ভিত্তিক কুইজ প্র্যাকটিস"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.88f),
                                lineHeight = 17.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Bottom Card: 3. Animated Lessons (Midnight Violet-Royal)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Color(0xFFC4B5FD).copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = animScale; scaleY = animScale }
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = animInteraction,
                    indication = ripple(color = Color.White),
                    onClick = onOpenAnimatedLessons
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(animatedCardGradient)
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 34.dp, y = (-10).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFA78BFA).copy(alpha = 0.36f), Color.Transparent)
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(62.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "E=mc²",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF6D28D9)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Animated Lessons", "অ্যানিমেটেড লেসনস"),
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr("Visual concept videos for every subject", "সব বিষয়ের অ্যানিমেটেড ভিডিও"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.88f),
                            lineHeight = 18.sp
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeatureSubjectPickerSheet(
    featureMode: String,
    activeCourseTitle: String? = null,
    subjects: List<com.example.api.AcademicSubjectItem>,
    isLoading: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelectSubject: (com.example.api.AcademicSubjectItem) -> Unit
) {
    val isQuiz = featureMode == "practice_quiz"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isQuiz) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF6366F1).copy(alpha = 0.15f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isQuiz) Icons.Default.Quiz else Icons.Default.SmartDisplay,
                            contentDescription = null,
                            tint = if (isQuiz) Color(0xFF10B981) else Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isQuiz) tr("Practice Quiz", "প্র্যাকটিস কুইজ") else tr("Animated Lessons", "অ্যানিমেটেড লেসনস"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!activeCourseTitle.isNullOrBlank()) {
                        Text(
                            text = activeCourseTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isQuiz) Color(0xFF10B981) else Color(0xFF6366F1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = tr("Choose a subject to continue", "শুরু করতে একটি বিষয় বেছে নিন"),
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = tr("Loading subjects...", "বিষয়সমূহ লোড হচ্ছে..."),
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                subjects.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tr("No Subjects Found", "কোনো বিষয় পাওয়া যায়নি"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Retry", "পুনরায় চেষ্টা করুন"))
                            }
                        }
                    }
                }

                else -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(
                            count = subjects.size,
                            key = { idx -> "${subjects[idx].code}_${idx}" }
                        ) { idx ->
                            val sub = subjects[idx]
                            val subColor = remember(sub.color_code, sub.display_bn) {
                                try {
                                    if (!sub.color_code.isNullOrBlank()) {
                                        Color(android.graphics.Color.parseColor(sub.color_code))
                                    } else {
                                        com.example.utils.SubjectColorUtils.getColorScheme(sub.display_bn).textColor
                                    }
                                } catch (_: Exception) {
                                    com.example.utils.SubjectColorUtils.getColorScheme(sub.display_bn).textColor
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, subColor.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectSubject(sub) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    com.example.utils.SubjectIconBadge(
                                        iconUrl = sub.icon,
                                        subjectName = sub.display_bn,
                                        subjectCode = sub.code,
                                        color = subColor,
                                        size = 44.dp,
                                        iconSize = 24.dp
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sub.display_bn ?: sub.code ?: tr("Subject", "বিষয়"),
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isQuiz) tr("Chapter quizzes & tests", "অধ্যায়ভিত্তিক কুইজ") else tr("Animated concept videos", "অ্যানিমেটেড ভিডিও"),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = subColor.copy(alpha = 0.15f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isQuiz) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = subColor,
                                                modifier = Modifier.size(18.dp)
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
    }
}



@Composable
private fun ReportCardQuickBanner(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "reportBannerScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "reportBannerGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0xFF2563EB).copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF60A5FA).copy(alpha = 0.55f),
                    Color(0xFFF59E0B).copy(alpha = 0.45f),
                    Color(0xFF3B82F6).copy(alpha = 0.55f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF09152B),
                            Color(0xFF172E5C),
                            Color(0xFF1D4ED8)
                        )
                    )
                )
        ) {
            // Subtle Decorative Glowing Orb in Background
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.25f),
                        radius = size.height * 1.2f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.25f),
                    radius = size.height * 1.2f
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = glowPulse * 0.95f; scaleY = glowPulse * 0.95f }
                            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = Color(0xFFF59E0B))
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                            )
                            .border(1.dp, Color(0xFFFDE68A).copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tr("Report Card", "রিপোর্ট কার্ড"),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tr("Quarterly results & leaderboard", "রেজাল্ট ও মেধা তালিকা"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFBAE6FD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("View", "দেখুন"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  Shimmer Skeleton — premium loading placeholder
// ============================================================
@Composable
private fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.55f),
            base.copy(alpha = 0.18f),
            base.copy(alpha = 0.55f)
        ),
        start = Offset(-300f + (progress * 900f), 0f),
        end = Offset(300f + (progress * 900f), 300f)
    )
    return background(shimmerBrush)
}

@Composable
private fun ShimmerSkeletonSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Routine section skeleton
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(110.dp, 20.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmer()
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(80.dp, 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmer()
                )
            }
            // 7 day pills skeleton
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .shimmer()
                    )
                }
            }
            repeat(2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .shimmer()
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(180.dp, 14.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .shimmer()
                        )
                        Box(
                            Modifier
                                .size(110.dp, 11.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .shimmer()
                        )
                    }
                }
            }
        }

        // Progress section skeleton
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(140.dp, 18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer()
            )
            repeat(2) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .shimmer()
                )
            }
        }
    }
}

// ============================================================
//  Error State Card — icon + gradient retry button
// ============================================================
@Composable
private fun ErrorStateCard(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = tr("Unable to Load Courses", "কোর্স লোড করা যায়নি"),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage ?: tr("Check your internet connection and try again", "ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন"),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(22.dp))

            // Gradient retry button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = primary)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
                    .clickable { onRetry() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        tr("Try Again", "আবার চেষ্টা করুন"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ============================================================
//  Account Completion Banner — shine sweep + gradient CTA
// ============================================================
@Composable
fun AccountCompletionBanner(
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    // Shine sweep animation
    val shineTransition = rememberInfiniteTransition(label = "shine")
    val shineProgress by shineTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.12f),
                        secondary.copy(alpha = 0.09f),
                        primary.copy(alpha = 0.06f)
                    )
                )
            )
            .border(
                1.2.dp,
                Brush.linearGradient(
                    listOf(primary.copy(alpha = 0.5f), secondary.copy(alpha = 0.35f), primary.copy(alpha = 0.25f))
                ),
                RoundedCornerShape(24.dp)
            )
    ) {
        // Moving shine overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .graphicsLayer {
                    translationX = shineProgress * size.width
                    alpha = 0.12f
                }
                .background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Gradient icon badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(6.dp, CircleShape, spotColor = primary)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(primary, secondary))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Complete Profile", "প্রোফাইল সম্পূর্ণ করুন"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tr(
                            "Add your name and institution for personalized routine & alerts.",
                            "সঠিক সিলেবাস ও রুটিন পেতে নাম ও প্রতিষ্ঠানের তথ্য যোগ করুন।"
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gradient CTA button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = primary)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
                    .clickable { onCompleteClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("Complete Now", "এখনই করুন"),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Handles clicks on notice banners / dialog action buttons.
 * Opens PDF files via in-app PDF viewer or web links via system browser.
 */
private fun handleNoticeAction(
    context: Context,
    notice: com.example.notice.AppNotice,
    onOpenPdf: ((filePath: String, title: String) -> Unit)? = null
) {
    val rawUrl = notice.actionUrl?.trim() ?: return
    if (rawUrl.isBlank()) return

    if (rawUrl.endsWith(".pdf", ignoreCase = true) || rawUrl.contains(".pdf?", ignoreCase = true)) {
        if (onOpenPdf != null) {
            onOpenPdf(rawUrl, notice.title.ifBlank { AppLang.tr("Notice Document", "নোটিশ ডকুমেন্ট") })
            return
        }
    }

    try {
        var formattedUrl = rawUrl
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://") &&
            !formattedUrl.startsWith("tel:") && !formattedUrl.startsWith("mailto:")
        ) {
            formattedUrl = "https://$formattedUrl"
        }
        val uri = Uri.parse(formattedUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            AppLang.tr(
                "Could not open link: ${e.localizedMessage ?: "Browser error"}",
                "লিংক খোলা যায়নি: ${e.localizedMessage ?: "ব্রাউজার এরর"}"
            ),
            Toast.LENGTH_SHORT
        ).show()
    }
}
