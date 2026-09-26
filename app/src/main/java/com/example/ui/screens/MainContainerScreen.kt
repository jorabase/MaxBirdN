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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.SessionManager
import com.example.course.CourseViewModel
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
                    Brush.verticalGradient(listOf(Color(0xFF0B1120), Color(0xFF0F172A)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFEFF6FF), Color(0xFFF8FAFC)))
                }
            )
    ) {
        // Soft ambient glows (decorative)
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-100).dp, y = (-120).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF2D5BFF).copy(alpha = if (isDark) 0.15f else 0.08f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-110).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF00BFA6).copy(alpha = if (isDark) 0.12f else 0.07f), Color.Transparent)
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
                        onCourseSelected = { program ->
                            homeViewModel.switchActiveCourse(program)
                            courseViewModel.openCourse(program)
                            selectedIndex = 1
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
                        onNavigateToNotificationHistory = onNavigateToNotificationHistory
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
                        onPlayVideo = onPlayVideo
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
                    ComingSoonScreen(tabItem = NavigationItem.items[tabIndex])
                }
            }
        }

        // Floating Bottom Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            ElementalFloatingBottomBar(
                selectedIndex = selectedIndex,
                onTabSelected = { index ->
                    if (index != selectedIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedIndex = index
                        if (index == 1) {
                            courseViewModel.loadSubjects()
                        }
                    }
                }
            )
        }
    }
}

/**
 * Premium Floating Dock — glow shadow, icon bounce spring, gradient shine border
 */
private data class TabTheme(val pillBg: Color, val border: Color, val icon: Color)

@Composable
fun ElementalFloatingBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val themes = remember {
        listOf(
            TabTheme(Color(0xFF261908), Color(0xFFF59E0B), Color(0xFFFBBF24)), // Amber
            TabTheme(Color(0xFF082744), Color(0xFF0284C7), Color(0xFF38BDF8)), // Azure
            TabTheme(Color(0xFF0C243D), Color(0xFF38BDF8), Color(0xFF7DD3FC)), // Sky
            TabTheme(Color(0xFF201338), Color(0xFFA855F7), Color(0xFFC084FC))  // Violet
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .height(66.dp)
            .shadow(
                elevation = 26.dp,
                shape = RoundedCornerShape(33.dp),
                spotColor = Color.Black.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(33.dp),
        color = Color(0xFF07152B),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(Color(0xFF1E3A5F).copy(alpha = 0.9f), Color(0xFF2D5BFF).copy(alpha = 0.45f), Color(0xFF1E3A5F).copy(alpha = 0.9f))
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top shine line
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 32.dp)
                    .height(1.dp)
                    .width(120.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
            NavigationItem.items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val theme = themes.getOrElse(index) { themes.last() }

                // Icon bounce on selection
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.18f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "iconScale$index"
                )

                // Unselected → selected color transition
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) theme.icon else Color(0xFF94A3B8),
                    animationSpec = tween(250),
                    label = "iconTint$index"
                )

                // Press feedback
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
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "TabPillTransition$index"
                ) { selected ->
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = theme.pillBg,
                            border = BorderStroke(
                                1.5.dp,
                                Brush.horizontalGradient(listOf(theme.border, theme.border.copy(alpha = 0.5f), theme.border))
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .height(46.dp)
                                .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                                .clickable(interactionSource = interaction, indication = null) { onTabSelected(index) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = item.selectedIcon,
                                    contentDescription = item.title,
                                    tint = theme.icon,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                                .clickable(interactionSource = interaction, indication = null) { onTabSelected(index) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = item.unselectedIcon,
                                contentDescription = item.title,
                                tint = iconTint,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = iconTint
                            )
                        }
                    }
                }
            }
        }
    }
}
}
