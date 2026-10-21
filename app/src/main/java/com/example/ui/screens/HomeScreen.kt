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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.gamification.GamificationStats
import com.example.gamification.rememberGamificationStats
import com.example.utils.toBengaliDigits
import androidx.compose.runtime.*
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

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = onNavigateToProfile,
    onNavigateToChangeSyllabus: () -> Unit = {},
    onCourseSelected: (EnrolledProgram) -> Unit = {},
    onOpenCourse: (phaseId: String?) -> Unit = {},
    onOpenFullRoutine: () -> Unit = {},
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null,
    onNavigateToReportCard: (programId: String?, programTitle: String?, phaseId: String?) -> Unit = { _, _, _ -> },
    onNavigateToNotificationHistory: () -> Unit = {},
    onOpenPdf: ((filePath: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val wallpaperConfig by viewModel.wallpaperConfigFlow.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

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

    // ===== LOGIC: Scroll-driven collapsible header (unchanged) =====
    var isHeaderVisible by remember { mutableStateOf(true) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollState.value) {
        val currentOffset = scrollState.value
        val delta = currentOffset - previousScrollOffset
        if (delta > 20 && currentOffset > 80) {
            isHeaderVisible = false
        } else if (delta < -20 || currentOffset <= 40) {
            isHeaderVisible = true
        }
        previousScrollOffset = currentOffset
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
            onChangeSyllabusClick = onNavigateToChangeSyllabus
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

    // ===== LOGIC: Subtitle formatting (unchanged) =====
    val subtitle = remember(uiState.userClass, uiState.userGroup, uiState.userSchool) {
        val classDisplay = when (uiState.userClass) {
            "C11", "Class 11" -> "একাদশ শ্রেণি"
            "C12", "Class 12" -> "দ্বাদশ শ্রেণি"
            "C10", "Class 10" -> "দশম শ্রেণি"
            "C9", "Class 9" -> "নবম শ্রেণি"
            else -> uiState.userClass.ifBlank { "একাদশ শ্রেণি" }
        }
        val groupDisplay = when (uiState.userGroup.lowercase()) {
            "humanities", "humanities_group" -> "মানবিক"
            "science", "science_group" -> "বিজ্ঞান"
            "business", "business_studies", "commerce" -> "ব্যবসায় শিক্ষা"
            else -> uiState.userGroup.ifBlank { "" }
        }
        listOf(classDisplay, groupDisplay, uiState.userSchool)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "এইচএসসি শিক্ষার্থী" }
    }

    // ===== LOGIC: Display name (unchanged) =====
    val displayName = remember(uiState.userFirstName, uiState.userName) {
        when {
            uiState.userFirstName.isNotBlank() && uiState.userFirstName != "শিক্ষার্থী" -> uiState.userFirstName
            uiState.userName.isNotBlank() && uiState.userName != "শিক্ষার্থী" -> uiState.userName
            else -> "শিক্ষার্থী"
        }
    }

    val activeNoticesCount = remember(uiState.notices) { uiState.notices.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(
                userName = displayName,
                subtitle = subtitle,
                avatarUrl = uiState.userAvatar ?: "",
                isPremium = uiState.isPremium,
                activeCourseTitle = uiState.activeProgram?.title_bn ?: "কোর্স নির্বাচন করো",
                onOpenCourseSwitcher = { viewModel.setCourseSwitcherVisible(true) },
                onAvatarClick = { onNavigateToProfile() },
                unreadNotificationCount = activeNoticesCount,
                onNotificationClick = {
                    onNavigateToNotificationHistory()
                    Toast.makeText(
                        context,
                        "আগামী ৭ দিনের ক্লাসের এলার্ম ও নোটিফিকেশন সেট করা আছে!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                wallpaperConfig = wallpaperConfig
            )

            // Content Container with smooth rounded top corners & glassmorphic top rim
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-14).dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
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
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = contentTranslation
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Subtle top drag-handle aesthetic pill
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    )

                    // ===== LOGIC: Banner condition (unchanged) =====
                    if (displayName == "শিক্ষার্থী" || uiState.userFirstName.isBlank()) {
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

                            // Gamified Player HUD & Daily Quest Arena Card
                            val gamificationStats = rememberGamificationStats(context)
                            GamifiedQuestArenaCard(
                                stats = gamificationStats,
                                onStartQuestClick = {
                                    val phase = uiState.programPhases.find { it.is_current == true }
                                        ?: uiState.programPhases.firstOrNull()
                                    onOpenCourse(phase?.id)
                                },
                                onProfileClick = onNavigateToProfile
                            )

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

                            // Report Card & Leaderboard Premium Interactive Card
                            ReportCardQuickBanner(
                                onClick = {
                                    val prog = uiState.activeProgram
                                    val phase = uiState.programPhases.find { it.is_current == true }
                                        ?: uiState.programPhases.firstOrNull()
                                    onNavigateToReportCard(prog?.id, prog?.title_bn, phase?.id)
                                }
                            )

                            CourseProgressSection(
                                phases = uiState.programPhases,
                                onPhaseClick = { phase -> onOpenCourse(phase.id) }
                            )
                        }
                    }
                }
            }

            // Bottom spacing for floating nav bar
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun GamifiedQuestArenaCard(
    stats: GamificationStats,
    onStartQuestClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    var showQuestsExpanded by remember { mutableStateOf(true) }
    val infiniteTransition = rememberInfiniteTransition(label = "questArenaAnim")
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameScale"
    )
    val animatedXpProgress by animateFloatAsState(
        targetValue = stats.levelProgressFraction,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "xpBarAnim"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.3.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF8B5CF6).copy(alpha = 0.65f),
                    Color(0xFFF59E0B).copy(alpha = 0.55f),
                    Color(0xFF38BDF8).copy(alpha = 0.65f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color(0xFF6366F1).copy(alpha = 0.4f))
            .clip(RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B132B),
                            Color(0xFF1E1B4B),
                            Color(0xFF172554)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Level Shield + Rank Title + Streak Pill + XP Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onProfileClick() }
                    ) {
                        // Glowing Level Shield
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = Color(0xFF8B5CF6))
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
                                    )
                                )
                                .border(1.2.dp, Color(0xFFC4B5FD), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "LV",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFDDD6FE),
                                    lineHeight = 9.sp
                                )
                                Text(
                                    text = toBengaliDigits(stats.level),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = stats.rankTitleBn,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "মোট অর্জিত: ${toBengaliDigits(stats.totalXp)} XP",
                                color = Color(0xFF93C5FD),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Daily Streak Flame Badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFF97316).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFFB923C).copy(alpha = 0.7f)),
                        modifier = Modifier.clickable {
                            showQuestsExpanded = !showQuestsExpanded
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = Color(0xFFFB923C),
                                modifier = Modifier
                                    .size(17.dp)
                                    .graphicsLayer {
                                        scaleX = flameScale
                                        scaleY = flameScale
                                    }
                            )
                            Text(
                                text = "${toBengaliDigits(stats.streakDays)} দিন স্ট্রিক",
                                color = Color(0xFFFED7AA),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animated XP Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "লেভেল ${toBengaliDigits(stats.level + 1)}-এ যেতে আরও ${toBengaliDigits((stats.nextLevelXpThreshold - stats.currentLevelXp).coerceAtLeast(10))} XP প্রয়োজন",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${toBengaliDigits((stats.levelProgressFraction * 100).toInt())}%",
                            color = Color(0xFFFDE047),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedXpProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B))
                                    )
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Daily Quests Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQuestsExpanded = !showQuestsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "আজকের ডেইলি স্টাডি মিশন",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    val completedCount = stats.dailyQuests.count { it.isCompleted }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (completedCount == stats.dailyQuests.size) {
                            Color(0xFF10B981).copy(alpha = 0.25f)
                        } else {
                            Color.White.copy(alpha = 0.1f)
                        }
                    ) {
                        Text(
                            text = "${toBengaliDigits(completedCount)}/${toBengaliDigits(stats.dailyQuests.size)} সম্পন্ন",
                            color = if (completedCount == stats.dailyQuests.size) Color(0xFF34D399) else Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showQuestsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stats.dailyQuests.forEach { quest ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (quest.isCompleted) {
                                    Color(0xFF064E3B).copy(alpha = 0.45f)
                                } else {
                                    Color.White.copy(alpha = 0.07f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (quest.isCompleted) Color(0xFF10B981).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onStartQuestClick() }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (quest.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (quest.isCompleted) Color(0xFF34D399) else Color(0xFFA78BFA),
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = quest.titleBn,
                                            color = if (quest.isCompleted) Color(0xFFD1FAE5) else Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = quest.subtitleBn,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (quest.isCompleted) {
                                            Color(0xFF10B981).copy(alpha = 0.25f)
                                        } else {
                                            Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        }
                                    ) {
                                        Text(
                                            text = if (quest.isCompleted) "✓ +${toBengaliDigits(quest.xpReward)} XP" else "+${toBengaliDigits(quest.xpReward)} XP",
                                            color = if (quest.isCompleted) Color(0xFF34D399) else Color(0xFFFDE047),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                text = "রিপোর্ট কার্ড ও লিডারবোর্ড",
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
                            text = "কোয়ার্টার রেজাল্ট ও মেধা তালিকা দেখো",
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
                            text = "দেখো",
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
                text = "কোর্স লোড করা যায়নি",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage ?: "অনুগ্রহ করে ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করো",
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
                        "আবার চেষ্টা করো",
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
                        text = "আপনার অ্যাকাউন্ট সম্পূর্ণ করুন 🎉",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "সঠিক সিলেবাস ও ক্লাসের নোটিফিকেশন পেতে আপনার পূর্ণাঙ্গ নাম ও কলেজের তথ্য যোগ করুন।",
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
                        text = "এখনই সম্পূর্ণ করুন",
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
            onOpenPdf(rawUrl, notice.title.ifBlank { "নোটিশ ডকুমেন্টস" })
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
            "লিংক ওপেন করা সম্ভব হয়নি: ${e.localizedMessage ?: "ব্রাউজার এরর"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
