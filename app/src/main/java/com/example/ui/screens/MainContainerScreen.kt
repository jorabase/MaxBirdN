package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.SessionManager
import com.example.course.CourseViewModel
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.home.HomeViewModel
import com.example.ui.navigation.NavigationItem

@Composable
fun MainContainerScreen(
    homeViewModel: HomeViewModel,
    courseViewModel: CourseViewModel,
    sessionManager: SessionManager,
    onNavigateToSubjectChapters: (subjectCode: String, subjectTitle: String, subjectColor: String) -> Unit = { _, _, _ -> },
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangeSyllabus: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCourseEnrollment: () -> Unit = {},
    onNavigateToFullRoutine: () -> Unit = {},
    onNavigateToSavedItems: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToReportCard: (programId: String?, programTitle: String?, phaseId: String?) -> Unit = { _, _, _ -> },
    onOpenLessonDetail: (com.example.api.StudentLessonItem) -> Unit = {},
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit = { _, _, _, _, _ -> },
    onOpenPdf: ((filePath: String, title: String) -> Unit)? = null,
    onNavigateToNotificationHistory: () -> Unit = {},
    onNavigateToNotification: () -> Unit = {},
    onNavigateToHeaderWallpaper: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    var lastBackPressedTime by remember { mutableLongStateOf(0L) }

    // Observe active downloads count for live bottom dock badge indicator
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val allDownloads by downloadManager.getAllDownloads().collectAsState(initial = emptyList())
    val activeDownloadsCount = remember(allDownloads) {
        allDownloads.count { it.status == DownloadedItemEntity.STATUS_DOWNLOADING }
    }

    // Double Back Press to Exit (3 seconds window) & Tab back navigation
    BackHandler {
        if (selectedIndex != 0) {
            selectedIndex = 0
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressedTime < 3000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressedTime = currentTime
                Toast.makeText(context, "অ্যাপ থেকে বের হতে আবার ব্যাক চাপুন", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Brush.verticalGradient(listOf(Color(0xFF070D19), Color(0xFF0F172A), Color(0xFF09101D)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFEFF6FF), Color(0xFFF8FAFC)))
                }
            )
    ) {
        // Soft ambient glows (decorative)
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = (-110).dp, y = (-120).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF2D5BFF).copy(alpha = if (isDark) 0.16f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(290.dp)
                .align(Alignment.TopEnd)
                .offset(x = 95.dp, y = (-110).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF00BFA6).copy(alpha = if (isDark) 0.13f else 0.07f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Tab Content — directional slide + fade transition
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialOffsetX = { it / 14 * direction }
                    )) togetherWith
                    (fadeOut(tween(160)) +
                        slideOutHorizontally(
                            tween(200),
                            targetOffsetX = { -it / 18 * direction }
                        ))
            },
            label = "TabContentTransition",
            modifier = Modifier.fillMaxSize()
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToProfile = { selectedIndex = 3 },
                        onNavigateToEditProfile = onNavigateToEditProfile,
                        onNavigateToChangeSyllabus = onNavigateToChangeSyllabus,
                        onNavigateToCourseTab = { selectedIndex = 1 },
                        onCourseSelected = { program ->
                            homeViewModel.switchActiveCourse(program)
                            courseViewModel.openCourse(program)
                            Toast.makeText(
                                context,
                                "${program.title_bn ?: "কোর্স"} নির্বাচিত হয়েছে ✨",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onOpenCourse = { phaseId ->
                            val activeProg = homeViewModel.uiState.value.activeProgram
                            if (activeProg != null) {
                                courseViewModel.switchProgram(
                                    newProgramId = activeProg.id,
                                    newProgramTitle = activeProg.title_bn ?: "",
                                    batchId = activeProg.enrollment_details?.batch_id,
                                    classCode = activeProg.classes?.firstOrNull(),
                                    targetPhaseId = phaseId
                                )
                            } else {
                                courseViewModel.loadSubjects(targetPhaseId = phaseId)
                            }
                            selectedIndex = 1
                        },
                        onOpenFullRoutine = onNavigateToFullRoutine,
                        onOpenLessonDetail = onOpenLessonDetail,
                        onNavigateToExam = onNavigateToExam,
                        onNavigateToReportCard = onNavigateToReportCard,
                        onNavigateToNotificationHistory = onNavigateToNotificationHistory,
                        onOpenPdf = onOpenPdf
                    )
                }
                1 -> {
                    CourseSubjectsScreen(
                        viewModel = courseViewModel,
                        onSubjectClick = onNavigateToSubjectChapters,
                        onCourseSelected = { program ->
                            homeViewModel.switchActiveCourse(program)
                        }
                    )
                }
                2 -> {
                    DownloadsScreen(
                        onBack = { selectedIndex = 0 },
                        onPlayVideo = onPlayVideo,
                        onOpenPdf = onOpenPdf
                    )
                }
                3 -> {
                    SettingsScreen(
                        sessionManager = sessionManager,
                        onNavigateToEditProfile = onNavigateToEditProfile,
                        onNavigateToChangeSyllabus = onNavigateToChangeSyllabus,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToCourseEnrollment = onNavigateToCourseEnrollment,
                        onNavigateToSavedItems = onNavigateToSavedItems,
                        onNavigateToDownloads = { selectedIndex = 2 },
                        onNavigateToReportCard = { onNavigateToReportCard(null, null, null) },
                        onNavigateToNotification = onNavigateToNotification,
                        onNavigateToHeaderWallpaper = onNavigateToHeaderWallpaper,
                        onLogout = onLogout
                    )
                }
                else -> {
                    ComingSoonScreen(
                        tabItem = NavigationItem.items.getOrElse(tabIndex) { NavigationItem.items[0] }
                    )
                }
            }
        }

        // Floating Bottom Bar with subtle bottom fade scrim so scrolling text never clashes with dock corners
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            ElementalFloatingBottomBar(
                selectedIndex = selectedIndex,
                onTabSelected = { index ->
                    if (index != selectedIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedIndex = index
                        if (index == 1) {
                            courseViewModel.fetchEnrolledPrograms()
                            courseViewModel.loadSubjects()
                        }
                    }
                },
                activeDownloadsCount = activeDownloadsCount
            )
        }
    }
}

/**
 * Premium Floating Dock — glow shadow, icon bounce spring, gradient shine border, and live download indicator
 */
private data class TabTheme(val pillBg: Color, val border: Color, val icon: Color)

@Composable
fun ElementalFloatingBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeDownloadsCount: Int = 0
) {
    val themes = remember {
        listOf(
            TabTheme(Color(0xFF281A07), Color(0xFFF59E0B), Color(0xFFFBBF24)), // Amber (Home)
            TabTheme(Color(0xFF072541), Color(0xFF0284C7), Color(0xFF38BDF8)), // Azure (Courses)
            TabTheme(Color(0xFF0B243E), Color(0xFF38BDF8), Color(0xFF7DD3FC)), // Sky (Downloads)
            TabTheme(Color(0xFF21123A), Color(0xFFA855F7), Color(0xFFC084FC))  // Violet (Settings)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(62.dp)
            .shadow(
                elevation = 22.dp,
                shape = RoundedCornerShape(31.dp),
                spotColor = Color.Black.copy(alpha = 0.55f)
            ),
        shape = RoundedCornerShape(31.dp),
        color = Color(0xFF071428),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF1E3A5F).copy(alpha = 0.9f),
                    Color(0xFF38BDF8).copy(alpha = 0.45f),
                    Color(0xFF1E3A5F).copy(alpha = 0.9f)
                )
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top specular highlight line
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 36.dp)
                    .height(1.dp)
                    .width(130.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationItem.items.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    val theme = themes.getOrElse(index) { themes.last() }
                    val showDownloadBadge = index == 2 && activeDownloadsCount > 0

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "iconScale$index"
                    )

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) theme.icon else Color(0xFF94A3B8),
                        animationSpec = tween(250),
                        label = "iconTint$index"
                    )

                    val interaction = remember { MutableInteractionSource() }
                    val isPressed by interaction.collectIsPressedAsState()
                    val itemScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "itemScale$index"
                    )

                    AnimatedContent(
                        targetState = isSelected,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )).togetherWith(fadeOut(tween(140)))
                        },
                        label = "TabPillTransition$index"
                    ) { selected ->
                        if (selected) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = theme.pillBg,
                                border = BorderStroke(
                                    1.2.dp,
                                    Brush.horizontalGradient(
                                        listOf(theme.border, theme.border.copy(alpha = 0.55f), theme.border)
                                    )
                                ),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .height(40.dp)
                                    .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                                    .clickable(interactionSource = interaction, indication = null) {
                                        onTabSelected(index)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                ) {
                                    Box {
                                        Icon(
                                            imageVector = item.selectedIcon,
                                            contentDescription = item.title,
                                            tint = theme.icon,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                        )
                                        if (showDownloadBadge) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 2.dp, y = (-2).dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                                    .clickable(interactionSource = interaction, indication = null) {
                                        onTabSelected(index)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box {
                                    Icon(
                                        imageVector = item.unselectedIcon,
                                        contentDescription = item.title,
                                        tint = iconTint,
                                        modifier = Modifier
                                            .size(19.dp)
                                            .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                    )
                                    if (showDownloadBadge) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 3.dp, y = (-2).dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF38BDF8))
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = iconTint,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
