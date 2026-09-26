package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.utils.toBengaliDigits
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import coil.request.ImageRequest
import com.example.api.EnrolledProgram
import com.example.api.OtherProgram
import com.example.course.CourseUiState
import com.example.course.CourseViewModel
import com.example.course.SubjectWithProgress
import com.example.ui.components.getProgramBadge
import com.example.utils.SubjectIconBadge
import com.example.utils.SubjectColorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSubjectsScreen(
    viewModel: CourseViewModel,
    onSubjectClick: (subjectCode: String, subjectTitle: String, subjectColor: String) -> Unit,
    onCourseSelected: ((EnrolledProgram) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedProgram = uiState.selectedCourseProgram

    // Fetch enrolled programs on launch if all lists are empty
    LaunchedEffect(Unit) {
        if (uiState.enrolledPrograms.isEmpty() && uiState.freePrograms.isEmpty() && uiState.otherPrograms.isEmpty()) {
            viewModel.fetchEnrolledPrograms()
        }
    }

    Scaffold(
        topBar = {
            if (selectedProgram != null) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.closeCourseDetails() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to my courses",
                                tint = Color(0xFF1E293B)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedProgram.title_bn ?: "কোর্স",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "বিষয়সমূহ ও অধ্যায়সূচি",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.testTag("course_subjects_screen")
    ) { paddingValues ->
        val isRefreshing = uiState.isProgramsLoading || uiState.isSubjectsLoading
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.fetchEnrolledPrograms()
                if (selectedProgram != null) {
                    viewModel.loadSubjects(forceRefresh = true)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = selectedProgram,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "CourseScreenTransition"
            ) { activeProgram ->
                if (activeProgram == null) {
                    // ==========================================
                    // VIEW 1: All Courses List View
                    // ==========================================
                    MyCoursesListView(
                        uiState = uiState,
                        onOpenEnrolledCourse = { program ->
                            viewModel.openCourse(program)
                            onCourseSelected?.invoke(program)
                        },
                        onOpenOtherCourse = { otherProgram ->
                            val converted = otherProgram.toEnrolledProgram()
                            viewModel.openCourse(converted)
                            onCourseSelected?.invoke(converted)
                        },
                        onEnrollOtherCourse = { otherProgram ->
                            viewModel.enrollInCourse(otherProgram, context) { newlyEnrolled ->
                                onCourseSelected?.invoke(newlyEnrolled)
                            }
                        },
                        onRefresh = {
                            viewModel.fetchEnrolledPrograms()
                        }
                    )
                } else {
                    // ==========================================
                    // VIEW 2: Course Subjects View
                    // ==========================================
                    CourseSubjectsDetailView(
                        activeProgram = activeProgram,
                        uiState = uiState,
                        viewModel = viewModel,
                        onSubjectClick = onSubjectClick,
                        onSwitchCourse = { viewModel.closeCourseDetails() }
                    )
                }
            }
        }
    }
}

/**
 * Filter chip for course categories
 */
@Composable
private fun CourseFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFF475569),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * Course List View showing courses with rich, modern design and zero white overlay
 */
@Composable
private fun MyCoursesListView(
    uiState: CourseUiState,
    onOpenEnrolledCourse: (EnrolledProgram) -> Unit,
    onOpenOtherCourse: (OtherProgram) -> Unit,
    onEnrollOtherCourse: (OtherProgram) -> Unit,
    onRefresh: () -> Unit
) {
    val enrolled = uiState.enrolledPrograms
    val free = uiState.freePrograms
    val other = uiState.otherPrograms
    val isLoading = uiState.isProgramsLoading

    var selectedFilter by remember { mutableStateOf("all") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header matching page background
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "আমার কোর্স",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp
                    )
                    if (enrolled.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                        ) {
                            Text(
                                text = "${toBengaliDigits(enrolled.size)}টি সক্রিয়",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "চলমান শিক্ষাবর্ষ ও ভর্তি হওয়া কোর্সসমূহ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
        }

        // Category Filter Chips if multiple categories exist - LazyRow prevents vertical text wrapping
        if (free.isNotEmpty() || other.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CourseFilterChip(
                        label = "সকল (${enrolled.size + free.size + other.size})",
                        selected = selectedFilter == "all",
                        onClick = { selectedFilter = "all" }
                    )
                }
                if (enrolled.isNotEmpty()) {
                    item {
                        CourseFilterChip(
                            label = "আমার (${enrolled.size})",
                            selected = selectedFilter == "enrolled",
                            onClick = { selectedFilter = "enrolled" }
                        )
                    }
                }
                if (free.isNotEmpty()) {
                    item {
                        CourseFilterChip(
                            label = "ফ্রি (${free.size})",
                            selected = selectedFilter == "free",
                            onClick = { selectedFilter = "free" }
                        )
                    }
                }
                if (other.isNotEmpty()) {
                    item {
                        CourseFilterChip(
                            label = "অন্যান্য (${other.size})",
                            selected = selectedFilter == "other",
                            onClick = { selectedFilter = "other" }
                        )
                    }
                }
            }
        }

        if (isLoading && enrolled.isEmpty() && free.isEmpty() && other.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF0284C7),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "কোর্স লোড হচ্ছে...",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        } else if (!isLoading && enrolled.isEmpty() && free.isEmpty() && other.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "কোনো কোর্স পাওয়া যায়নি",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Button(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("পুনরায় চেষ্টা করুন")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Enrolled Section
                if (enrolled.isNotEmpty() && (selectedFilter == "all" || selectedFilter == "enrolled")) {
                    item {
                        Text(
                            text = "আমার তালিকাভুক্ত কোর্স",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(
                        items = enrolled,
                        key = { "enrolled_${it.id}" }
                    ) { program ->
                        EnrolledCourseBannerCard(
                            program = program,
                            onClick = { onOpenEnrolledCourse(program) }
                        )
                    }
                }

                // Free Section
                if (free.isNotEmpty() && (selectedFilter == "all" || selectedFilter == "free")) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ফ্রি কোর্সসমূহ",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(
                        items = free,
                        key = { "free_${it.id}" }
                    ) { program ->
                        FreeCourseBannerCard(
                            program = program,
                            isEnrolling = uiState.enrollingProgramId == program.id,
                            onOpen = { onOpenOtherCourse(program) },
                            onEnroll = { onEnrollOtherCourse(program) }
                        )
                    }
                }

                // Other Section
                if (other.isNotEmpty() && (selectedFilter == "all" || selectedFilter == "other")) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "অন্যান্য কোর্সসমূহ",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(
                        items = other,
                        key = { "other_${it.id}" }
                    ) { program ->
                        OtherCourseBannerCard(
                            program = program,
                            isEnrolling = uiState.enrollingProgramId == program.id,
                            onOpen = { onOpenOtherCourse(program) },
                            onEnroll = { onEnrollOtherCourse(program) }
                        )
                    }
                }
            }
        }
    }
}

private fun getCourseGradient(title: String): Brush {
    return when {
        title.contains("Think", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF090D24), Color(0xFF1E1B4B), Color(0xFF311042))
        )
        title.contains("মানবিক", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF4A0E1A), Color(0xFF701A2C), Color(0xFF8B1D38), Color(0xFF3E0A15))
        )
        title.contains("বিজ্ঞান", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF091F38), Color(0xFF113D6B), Color(0xFF1B558C), Color(0xFF07182B))
        )
        title.contains("ব্যবসায়", ignoreCase = true) || title.contains("কমার্স", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA), Color(0xFF16143A))
        )
        title.contains("Next Champ", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF075985))
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF064E3B), Color(0xFF047857), Color(0xFF065F46), Color(0xFF022C21))
        )
    }
}

@Composable
private fun EnrolledCourseBannerCard(
    program: EnrolledProgram,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn?.trim() ?: "কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // Top Row: Status badge on left
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "ভর্তি হয়েছো" badge with verified checkmark
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.45f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "ভর্তি হয়েছো",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFECFDF5)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Centered Banner Graphic Box
                if (!program.banner_url.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(program.banner_url)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Course Title
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Full Width High-Contrast Action Button: "শেখো"
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(onClick = onClick)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "শেখো",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeCourseBannerCard(
    program: OtherProgram,
    isEnrolling: Boolean,
    onOpen: () -> Unit,
    onEnroll: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn ?: "ফ্রি কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF16A34A),
                        border = BorderStroke(1.dp, Color(0xFF4ADE80))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "সম্পূর্ণ ফ্রি!",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!program.banner_url.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(program.banner_url)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dual Action Buttons: "শেখো" (Direct Open) and "ভর্তি হন" (Enroll)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button 1: শেখো
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(1.2.dp, Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(onClick = onOpen)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "শেখো",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    // Button 2: ভর্তি হন
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(enabled = !isEnrolling, onClick = onEnroll)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            if (isEnrolling) {
                                CircularProgressIndicator(
                                    color = Color(0xFF15803D),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ভর্তি হচ্ছে...",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ভর্তি হন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
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

@Composable
private fun OtherCourseBannerCard(
    program: OtherProgram,
    isEnrolling: Boolean,
    onOpen: () -> Unit,
    onEnroll: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn ?: "কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }
    val trialDays = program.trial_duration ?: 3

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Badge Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${toBengaliDigits(trialDays)} দিনের ফ্রি ট্রায়াল",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFEF3C7)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!program.banner_url.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(program.banner_url)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dual Action Buttons: "শেখো" (Direct Open) and "ভর্তি হন" (Enroll)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button 1: শেখো
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(1.2.dp, Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(onClick = onOpen)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "শেখো",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    // Button 2: ভর্তি হন
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(enabled = !isEnrolling, onClick = onEnroll)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            if (isEnrolling) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0F172A),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ভর্তি হচ্ছে...",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ভর্তি হন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
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

/**
 * Course Subjects Grid View shown when a user opens a course
 */
@Composable
private fun CourseSubjectsDetailView(
    activeProgram: EnrolledProgram,
    uiState: com.example.course.CourseUiState,
    viewModel: CourseViewModel,
    onSubjectClick: (subjectCode: String, subjectTitle: String, subjectColor: String) -> Unit,
    onSwitchCourse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Clean, Compact Header (No redundant bulky cards)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "কোর্সের বিষয়সমূহ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.subjects.isNotEmpty()) {
                    Text(
                        text = "মোট ${toBengaliDigits(uiState.subjects.size)} টি বিষয় অন্তর্ভুক্ত",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onSwitchCourse,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "অন্য কোর্স",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Main Grid or Loading / Error State
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                uiState.isSubjectsLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "বিষয়সমূহ লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.subjectsErrorMessage != null && uiState.subjects.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.subjectsErrorMessage ?: "বিষয় পাওয়া যায়নি",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.loadSubjects(forceRefresh = true) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                uiState.subjects.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "এই কোর্সে কোনো বিষয় পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.subjects,
                            key = { it.subject.code ?: it.subject.display_bn ?: "" }
                        ) { subjectWithProgress ->
                            SubjectGridCard(
                                item = subjectWithProgress,
                                onClick = {
                                    val code = subjectWithProgress.subject.code ?: ""
                                    val title = subjectWithProgress.subject.display_bn ?: ""
                                    val color = subjectWithProgress.subject.color_code ?: "#0072EC"
                                    viewModel.selectSubject(code, title, color)
                                    onSubjectClick(code, title, color)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectGridCard(
    item: SubjectWithProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subject = item.subject

    val parsedColor = remember(subject.color_code, subject.display_bn) {
        try {
            if (!subject.color_code.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subject.color_code))
            } else {
                SubjectColorUtils.getColorScheme(subject.display_bn).textColor
            }
        } catch (_: Exception) {
            SubjectColorUtils.getColorScheme(subject.display_bn).textColor
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = parsedColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(parsedColor.copy(alpha = 0.04f))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubjectIconBadge(
                        iconUrl = subject.icon,
                        subjectName = subject.display_bn,
                        subjectCode = subject.code,
                        color = parsedColor,
                        size = 44.dp,
                        iconSize = 24.dp
                    )

                    Surface(
                        shape = CircleShape,
                        color = parsedColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = parsedColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = subject.display_bn ?: subject.code ?: "বিষয়",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                    modifier = Modifier.heightIn(min = 38.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chapter count pill / Progress
                if (item.totalChapters > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = parsedColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${toBengaliDigits(item.completedChapters)}/${toBengaliDigits(item.totalChapters)} অধ্যায়",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = parsedColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${toBengaliDigits(item.progressPercentage)}%",
                            fontSize = 11.sp,
                            color = parsedColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val progress = (item.progressPercentage / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = parsedColor,
                        trackColor = parsedColor.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = parsedColor.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = "অধ্যায়সমূহ দেখুন",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = parsedColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
