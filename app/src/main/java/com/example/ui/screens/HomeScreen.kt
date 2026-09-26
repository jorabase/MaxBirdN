package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val wallpaperConfig by viewModel.wallpaperConfigFlow.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

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
    LaunchedEffect(Unit) { contentVisible = true }
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
                unreadNotificationCount = 0,
                onNotificationClick = {
                    android.widget.Toast.makeText(context, "আগামী ৭ দিনের ক্লাসের এলার্ম ও নোটিফিকেশন সেট করা আছে!", android.widget.Toast.LENGTH_SHORT).show()
                },
                wallpaperConfig = wallpaperConfig
            )

            // Content Container with smooth rounded top corners (bKash style overlay)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-10).dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = contentTranslation
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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

                        Spacer(modifier = Modifier.height(4.dp))

                        // Report Card & Leaderboard Quick Banner Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    val prog = uiState.activeProgram
                                    val phase = uiState.programPhases.find { it.is_current == true } ?: uiState.programPhases.firstOrNull()
                                    onNavigateToReportCard(prog?.id, prog?.title_bn, phase?.id)
                                },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF2563EB))
                                        )
                                    )
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = "রিপোর্ট কার্ড ও লিডারবোর্ড",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "কোয়ার্টার রেজাল্ট ও মেধা তালিকা দেখো",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF93C5FD)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

                        Spacer(modifier = Modifier.height(6.dp))

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
            base.copy(alpha = 0.15f),
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp, 16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmer()
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(70.dp, 14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmer()
                )
            }
            repeat(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(140.dp, 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer()
            )
            repeat(2) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "কোর্স লোড করা যায়নি",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage ?: "অনুগ্রহ করে আবার চেষ্টা করো",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(22.dp))

            // Gradient retry button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
                    .clickable { onRetry() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "আবার চেষ্টা করো",
                        fontSize = 14.sp,
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
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.10f),
                        secondary.copy(alpha = 0.08f),
                        primary.copy(alpha = 0.06f)
                    )
                )
            )
            .border(
                1.2.dp,
                Brush.linearGradient(
                    listOf(primary.copy(alpha = 0.45f), secondary.copy(alpha = 0.35f), primary.copy(alpha = 0.2f))
                ),
                RoundedCornerShape(22.dp)
            )
    ) {
        // Moving shine overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
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
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gradient icon badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(primary, secondary))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "আপনার অ্যাকাউন্ট সম্পূর্ণ করুন 🎉",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "সঠিক সিলেবাস ও ক্লাসের নোটিফিকেশন পেতে আপনার পূর্ণাঙ্গ নাম ও কলেজের তথ্য যোগ করুন।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gradient CTA button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
                    .clickable { onCompleteClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
