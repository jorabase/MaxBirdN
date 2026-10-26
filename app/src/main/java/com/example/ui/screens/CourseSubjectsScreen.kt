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
import com.example.auth.SessionManager
import com.example.course.CourseAccessState
import com.example.course.CourseCommerceItemState
import com.example.course.CourseUiState
import com.example.course.CourseViewModel
import com.example.course.SubjectWithProgress
import com.example.ui.components.CoursePurchasePaymentBottomSheet
import com.example.ui.components.getProgramBadge
import com.example.utils.SubjectIconBadge
import com.example.utils.SubjectColorUtils
import androidx.compose.ui.draw.blur

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
    val sessionManager = remember(context) { SessionManager(context) }

    // Always refresh programs & course commerce/installment status when screen opens
    LaunchedEffect(Unit) {
        viewModel.fetchEnrolledPrograms()
    }

    val activePaymentProgId = uiState.activePaymentSheetProgramId
    if (!activePaymentProgId.isNullOrBlank()) {
        val activePaymentCourseTitle = uiState.activePaymentSheetCourseTitle.ifBlank { "কোর্স" }
        val commerceItem = uiState.commerceSnapshot.resolveCourseItemState(
            programId = activePaymentProgId,
            titleBn = activePaymentCourseTitle,
            perms = uiState.deviceCoursePermissions
        )
        CoursePurchasePaymentBottomSheet(
            courseTitle = activePaymentCourseTitle,
            commerceState = commerceItem,
            paymentSettings = uiState.commerceSnapshot.paymentSettings,
            defaultUserPhone = sessionManager.getUserPhone() ?: "",
            isSubmitting = uiState.submittingPaymentProgramId == activePaymentProgId,
            onDismiss = { viewModel.dismissPaymentSheet() },
            onSubmitPayment = { method, senderMobile, amount, trxId, plan ->
                viewModel.submitCoursePayment(
                    programId = activePaymentProgId,
                    courseTitle = activePaymentCourseTitle,
                    paymentMethod = method,
                    senderMobile = senderMobile,
                    amount = amount,
                    transactionId = trxId,
                    selectedPlan = plan,
                    context = context
                )
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectedProgram != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
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
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to my courses",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedProgram.title_bn ?: "কোর্স",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "বিষয়সমূহ ও কুয়েস্ট ম্যাপ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
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
                        onPayInstallment = { programId, titleBn ->
                            viewModel.openPaymentSheetForCourse(programId, titleBn)
                        },
                        onRequestCourseAccess = { otherProgram ->
                            viewModel.requestCoursePurchaseAccess(
                                programId = otherProgram.id,
                                programTitle = otherProgram.title_bn ?: "কোর্স",
                                bannerUrl = otherProgram.banner_url,
                                context = context
                            )
                        },
                        onOpenPurchaseSheet = { otherProgram ->
                            viewModel.openPaymentSheetForCourse(
                                programId = otherProgram.id,
                                courseTitle = otherProgram.title_bn ?: "কোর্স"
                            )
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
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * Course List View showing active enrolled courses and blurred/locked or purchasable courses
 */
@Composable
private fun MyCoursesListView(
    uiState: CourseUiState,
    onOpenEnrolledCourse: (EnrolledProgram) -> Unit,
    onPayInstallment: (programId: String, titleBn: String) -> Unit,
    onRequestCourseAccess: (OtherProgram) -> Unit,
    onOpenPurchaseSheet: (OtherProgram) -> Unit,
    onRefresh: () -> Unit
) {
    val enrolled = uiState.enrolledPrograms
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "আমার কোর্স ও মিশন",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    if (enrolled.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${toBengaliDigits(enrolled.size)}টি সক্রিয়",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enrolled.isEmpty()) "নিচের কোর্সগুলো থেকে এক্সেস নিয়ে এককালীন বা কিস্তিতে ভর্তি হও" else "চলমান শিক্ষাবর্ষ ও ভর্তি হওয়া কোর্সসমূহ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "রিফ্রেশ",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Category Filter Chips
        if (other.isNotEmpty() || enrolled.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CourseFilterChip(
                        label = "সকল কোর্স (${toBengaliDigits(enrolled.size + other.size)})",
                        selected = selectedFilter == "all",
                        onClick = { selectedFilter = "all" }
                    )
                }
                if (enrolled.isNotEmpty()) {
                    item {
                        CourseFilterChip(
                            label = "আমার সক্রিয় (${toBengaliDigits(enrolled.size)})",
                            selected = selectedFilter == "enrolled",
                            onClick = { selectedFilter = "enrolled" }
                        )
                    }
                }
                if (other.isNotEmpty()) {
                    item {
                        CourseFilterChip(
                            label = "কোর্স কিনুন / এক্সেস নিন (${toBengaliDigits(other.size)})",
                            selected = selectedFilter == "other",
                            onClick = { selectedFilter = "other" }
                        )
                    }
                }
            }
        }

        if (isLoading && enrolled.isEmpty() && other.isEmpty()) {
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
        } else if (!isLoading && enrolled.isEmpty() && other.isEmpty()) {
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
                // Enrolled Section (Active Unlocked Courses)
                if (enrolled.isNotEmpty() && (selectedFilter == "all" || selectedFilter == "enrolled")) {
                    item {
                        Text(
                            text = "আমার তালিকাভুক্ত সক্রিয় কোর্স",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(
                        items = enrolled,
                        key = { "enrolled_${it.id}" }
                    ) { program ->
                        val commerceItem = uiState.commerceSnapshot.resolveCourseItemState(
                            programId = program.id,
                            titleBn = program.title_bn,
                            perms = uiState.deviceCoursePermissions
                        )
                        EnrolledCourseBannerCard(
                            program = program,
                            commerceState = commerceItem,
                            onClick = { onOpenEnrolledCourse(program) },
                            onPayNextInstallment = {
                                onPayInstallment(program.id, program.title_bn ?: "কোর্স")
                            }
                        )
                    }
                }

                // Locked / Blurred / Purchasable Courses Section
                if (other.isNotEmpty() && (selectedFilter == "all" || selectedFilter == "other")) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (enrolled.isEmpty()) "সকল কোর্সসমূহ (এক্সেস নিন ও ভর্তি হোন)" else "অন্যান্য কোর্সসমূহ (এক্সেস নিন ও কিনুন)",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "পছন্দের কোর্সে 'কোর্সে এক্সেস পান' ক্লিক করলে অ্যাডমিন দাম ও কিস্তি সেট করে দিবেন",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(
                        items = other,
                        key = { "other_${it.id}" }
                    ) { program ->
                        val commerceItem = uiState.commerceSnapshot.resolveCourseItemState(
                            programId = program.id,
                            titleBn = program.title_bn,
                            perms = uiState.deviceCoursePermissions
                        )
                        OtherCourseBannerCard(
                            program = program,
                            commerceState = commerceItem,
                            isRequestingAccess = uiState.requestingAccessProgramId == program.id,
                            onRequestAccess = { onRequestCourseAccess(program) },
                            onOpenPaymentSheet = { onOpenPurchaseSheet(program) },
                            onRefreshStatus = onRefresh
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
    commerceState: CourseCommerceItemState,
    onClick: () -> Unit,
    onPayNextInstallment: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn?.trim() ?: "কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }
    val hasActiveInstallmentDue = commerceState.dueAmount > 0.0

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
                // Top Row: Status badge on left + Installment badge if applicable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                text = "ভর্তি হয়েছো (আনলকড)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFECFDF5)
                            )
                        }
                    }

                    if (hasActiveInstallmentDue) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = "কিস্তি চলমান (${commerceState.getRemainingDaysTextBn()})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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

                // If bought on installments and still has remaining balance, show installment tracker box
                if (hasActiveInstallmentDue) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "পরিশোধিত: ${CourseCommerceItemState.formatTakaBn(commerceState.paidAmount)} / ${CourseCommerceItemState.formatTakaBn(commerceState.totalPrice)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4ADE80)
                                )
                                Text(
                                    text = "বকেয়া: ${CourseCommerceItemState.formatTakaBn(commerceState.dueAmount)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                            }

                            val progress = if (commerceState.totalPrice > 0) {
                                (commerceState.paidAmount / commerceState.totalPrice).toFloat().coerceIn(0f, 1f)
                            } else 0.5f

                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                color = Color(0xFF34D399),
                                trackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            val nextAmt = if (commerceState.nextInstallmentAmount > 0) commerceState.nextInstallmentAmount else commerceState.dueAmount
                            val dueDateText = commerceState.getFormattedNextDueDateBn()
                            Text(
                                text = buildString {
                                    append("পরবর্তী কিস্তি: ${CourseCommerceItemState.formatTakaBn(nextAmt)}")
                                    if (dueDateText.isNotBlank()) {
                                        append(" • শেষ তারিখ: $dueDateText (${commerceState.getRemainingDaysTextBn()})")
                                    }
                                },
                                fontSize = 11.5.sp,
                                color = Color(0xFFFEF3C7)
                            )

                            if (commerceState.rejectionReason.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "❌ কিস্তি পেমেন্ট রিজেক্ট হয়েছে: ${commerceState.rejectionReason}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            if (commerceState.hasPendingInstallmentPayment) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "⏳ পরবর্তী কিস্তির পেমেন্ট যাচাই চলছে (TrxID: ${commerceState.pendingInstallmentTxId})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFEF3C7),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onPayNextInstallment)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 9.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payment,
                                            contentDescription = null,
                                            tint = Color(0xFF0F172A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "পরবর্তী কিস্তি পরিশোধ করুন (${CourseCommerceItemState.formatTakaBn(nextAmt)})",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
private fun OtherCourseBannerCard(
    program: OtherProgram,
    commerceState: CourseCommerceItemState,
    isRequestingAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenPaymentSheet: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn ?: "কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }
    val state = commerceState.effectiveState

    // কোর্সটি কখন ঝাপসা (Blurred) দেখাবে?
    // যখন স্টুডেন্ট এখনো এক্সেস পায়নি (LOCKED_BLURRED বা ACCESS_REQUESTED)
    val isBlurredLocked = state == CourseAccessState.LOCKED_BLURRED || state == CourseAccessState.ACCESS_REQUESTED

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(
            width = 1.2.dp,
            color = when (state) {
                CourseAccessState.APPROVED_TO_BUY -> Color(0xFF34D399).copy(alpha = 0.65f)
                CourseAccessState.PAYMENT_REJECTED, CourseAccessState.INSTALLMENT_OVERDUE -> Color(0xFFF87171).copy(alpha = 0.7f)
                else -> Color.White.copy(alpha = 0.18f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable {
                when (state) {
                    CourseAccessState.LOCKED_BLURRED -> if (!isRequestingAccess) onRequestAccess()
                    CourseAccessState.ACCESS_REQUESTED, CourseAccessState.PAYMENT_PENDING -> onRefreshStatus()
                    CourseAccessState.APPROVED_TO_BUY,
                    CourseAccessState.PAYMENT_REJECTED,
                    CourseAccessState.INSTALLMENT_OVERDUE -> onOpenPaymentSheet()
                    CourseAccessState.ACTIVE -> {}
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Dynamic Status Badge & Price Tag (when unlocked for purchase)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (badgeText, badgeBg, badgeBorder, badgeIcon) = when (state) {
                        CourseAccessState.LOCKED_BLURRED -> Quadruple(
                            "লকড ও ঝাপসা কোর্স",
                            Color.Black.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.35f),
                            Icons.Default.Lock
                        )
                        CourseAccessState.ACCESS_REQUESTED -> Quadruple(
                            "এক্সেস রিকোয়েস্ট পেন্ডিং",
                            Color(0xFFF59E0B).copy(alpha = 0.28f),
                            Color(0xFFFBBF24).copy(alpha = 0.65f),
                            Icons.Default.Schedule
                        )
                        CourseAccessState.APPROVED_TO_BUY -> Quadruple(
                            "কেনার এক্সেস অনুমোদিত",
                            Color(0xFF10B981).copy(alpha = 0.25f),
                            Color(0xFF34D399).copy(alpha = 0.65f),
                            Icons.Default.LockOpen
                        )
                        CourseAccessState.PAYMENT_PENDING -> Quadruple(
                            "পেমেন্ট যাচাই চলছে",
                            Color(0xFF3B82F6).copy(alpha = 0.28f),
                            Color(0xFF60A5FA).copy(alpha = 0.65f),
                            Icons.Default.HourglassTop
                        )
                        CourseAccessState.PAYMENT_REJECTED -> Quadruple(
                            "পেমেন্ট রিজেক্ট হয়েছে",
                            Color(0xFFEF4444).copy(alpha = 0.32f),
                            Color(0xFFF87171).copy(alpha = 0.75f),
                            Icons.Default.ErrorOutline
                        )
                        CourseAccessState.INSTALLMENT_OVERDUE -> Quadruple(
                            "কিস্তির মেয়াদ শেষ • লকড",
                            Color(0xFFEF4444).copy(alpha = 0.35f),
                            Color(0xFFF87171).copy(alpha = 0.8f),
                            Icons.Default.WarningAmber
                        )
                        CourseAccessState.ACTIVE -> Quadruple(
                            "সক্রিয়",
                            Color(0xFF10B981).copy(alpha = 0.25f),
                            Color(0xFF34D399).copy(alpha = 0.6f),
                            Icons.Default.CheckCircle
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = badgeText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Show Admin-set Custom Price Badge ONLY when Admin has approved access (no longer blurred!)
                    if (!isBlurredLocked && commerceState.totalPrice > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFEF08A),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "মূল্য: ${CourseCommerceItemState.formatTakaBn(commerceState.totalPrice)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Course Banner Box: Blurred & Locked until Admin grants purchase access!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!program.banner_url.isNullOrBlank()) {
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
                                .then(
                                    if (isBlurredLocked) Modifier.blur(16.dp)
                                    else if (state == CourseAccessState.INSTALLMENT_OVERDUE) Modifier.blur(8.dp)
                                    else Modifier
                                )
                        )
                    }

                    // Frosted Glass Lock Overlay when course is blurred/locked
                    if (isBlurredLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF0F172A).copy(alpha = 0.72f),
                                            Color(0xFF1E293B).copy(alpha = 0.85f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (state == CourseAccessState.ACCESS_REQUESTED) Icons.Default.HourglassTop else Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (state == CourseAccessState.ACCESS_REQUESTED) {
                                        "অ্যাডমিন অনুমোদনের অপেক্ষায়..."
                                    } else {
                                        "কোর্সটি লক করা আছে • এক্সেস নিয়ে দাম দেখুন"
                                    },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (state == CourseAccessState.INSTALLMENT_OVERDUE) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF7F1D1D).copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = Color(0xFFFEE2E2),
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "কিস্তির সময় শেষ হওয়ায় এক্সেস বন্ধ রয়েছে",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Course Title
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBlurredLocked) Color.White.copy(alpha = 0.85f) else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // State-specific Details Box (Installment options, Rejection reason, Pending info, or Overdue details)
                when (state) {
                    CourseAccessState.LOCKED_BLURRED -> {
                        Text(
                            text = "এই কোর্সটি কিনতে বা কিস্তির দাম জানতে নিচের 'কোর্সে এক্সেস পান' বাটনে ক্লিক করুন। অ্যাডমিন আপনাকে কেনার এক্সেস ও দাম নির্ধারণ করে দেবেন।",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )
                    }

                    CourseAccessState.ACCESS_REQUESTED -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.32f),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⏳ আপনার রিকোয়েস্ট অ্যাডমিন অ্যাপে পাঠানো হয়েছে! অ্যাডমিন এই কোর্সের দাম ও কিস্তি সেট করে এপ্রুভ করলেই ঝাপসা ভাব কেটে যাবে এবং আপনি কিনতে পারবেন।",
                                fontSize = 12.5.sp,
                                color = Color(0xFFFEF3C7),
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    CourseAccessState.APPROVED_TO_BUY -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.32f),
                            border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(13.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "নির্ধারিত কোর্স ফি:",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = CourseCommerceItemState.formatTakaBn(commerceState.totalPrice),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF4ADE80)
                                    )
                                }

                                if (commerceState.allowInstallments && commerceState.installmentPlans.size > 1) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "💳 কিস্তিতে ভর্তির সুবিধা রয়েছে:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFEF08A)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    commerceState.installmentPlans.filter { it.installmentsCount > 1 }.take(3).forEach { plan ->
                                        Text(
                                            text = "• ${plan.titleBn}",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.92f),
                                            lineHeight = 17.sp
                                        )
                                    }
                                }

                                if (commerceState.adminNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "অ্যাডমিন নোট: ${commerceState.adminNote}",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFBAE6FD)
                                    )
                                }
                            }
                        }
                    }

                    CourseAccessState.PAYMENT_PENDING -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⏳ আপনার পেমেন্ট যাচাই চলছে",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF93C5FD)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "মাধ্যম: ${commerceState.lastPaymentMethod} • পাঠানো টাকা: ${CourseCommerceItemState.formatTakaBn(commerceState.lastPaymentAmount)} • TrxID: ${commerceState.lastTransactionId}\nঅ্যাডমিন পেমেন্ট এপ্রুভ করলেই কোর্সটি আনলক হয়ে যাবে।",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    CourseAccessState.PAYMENT_REJECTED -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF450A0A).copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFFF87171)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(13.dp)) {
                                Text(
                                    text = "❌ আপনার পেমেন্ট রিজেক্ট করা হয়েছে!",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "কারণ: ${commerceState.rejectionReason.ifBlank { "প্রদত্ত ট্রানজেকশন আইডি বা টাকার পরিমাণ সঠিক নয়।" }}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    CourseAccessState.INSTALLMENT_OVERDUE -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF450A0A).copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(13.dp)) {
                                Text(
                                    text = "⚠️ কিস্তি পরিশোধ না করায় এক্সেস অফ হয়ে গেছে!",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDE68A)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "পরিশোধিত: ${CourseCommerceItemState.formatTakaBn(commerceState.paidAmount)} • বাকি বকেয়া: ${CourseCommerceItemState.formatTakaBn(commerceState.dueAmount)}\nনির্ধারিত সময়ের মধ্যে কিস্তি পরিশোধ না করায় কোর্সটি লক হয়েছে। এখন পরবর্তী কিস্তি পরিশোধ করলেই আবার চালু হবে।",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    lineHeight = 17.sp
                                )
                                if (commerceState.rejectionReason.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "❌ সর্বশেষ পেমেন্ট বাতিলের কারণ: ${commerceState.rejectionReason}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFCA5A5)
                                    )
                                }
                            }
                        }
                    }

                    CourseAccessState.ACTIVE -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action Button depending on state
                when (state) {
                    CourseAccessState.LOCKED_BLURRED -> {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color.White,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(enabled = !isRequestingAccess, onClick = onRequestAccess)
                                .testTag("request_course_access_button_${program.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isRequestingAccess) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF0F172A),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "রিকোয়েস্ট পাঠানো হচ্ছে...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = Color(0xFF5648F9),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "কোর্সে এক্সেস পান",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                        }
                    }

                    CourseAccessState.ACCESS_REQUESTED, CourseAccessState.PAYMENT_PENDING -> {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(onClick = onRefreshStatus)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state == CourseAccessState.ACCESS_REQUESTED) "অ্যাডমিন অনুমোদন চেক করুন" else "পেমেন্ট স্ট্যাটাস রিফ্রেশ করুন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    CourseAccessState.APPROVED_TO_BUY -> {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color(0xFF4ADE80),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(onClick = onOpenPaymentSheet)
                                .testTag("buy_course_button_${program.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCartCheckout,
                                    contentDescription = null,
                                    tint = Color(0xFF064E3B),
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (commerceState.allowInstallments) {
                                        "কোর্সটি কিনুন / কিস্তিতে ভর্তি হোন (${CourseCommerceItemState.formatTakaBn(commerceState.totalPrice)})"
                                    } else {
                                        "কোর্সটি কিনুন (${CourseCommerceItemState.formatTakaBn(commerceState.totalPrice)})"
                                    },
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF064E3B)
                                )
                            }
                        }
                    }

                    CourseAccessState.PAYMENT_REJECTED -> {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color.White,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(onClick = onOpenPaymentSheet)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "সঠিক তথ্য দিয়ে আবার পেমেন্ট জমা দিন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }

                    CourseAccessState.INSTALLMENT_OVERDUE -> {
                        val nextDueAmt = if (commerceState.nextInstallmentAmount > 0) commerceState.nextInstallmentAmount else commerceState.dueAmount
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color(0xFFFBBF24),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(onClick = onOpenPaymentSheet)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "বকেয়া কিস্তি (${CourseCommerceItemState.formatTakaBn(nextDueAmt)}) দিয়ে আনলক করুন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }

                    CourseAccessState.ACTIVE -> {}
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.2.dp,
                color = parsedColor.copy(alpha = 0.32f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            parsedColor.copy(alpha = 0.08f),
                            parsedColor.copy(alpha = 0.02f)
                        )
                    )
                )
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
                        color = parsedColor.copy(alpha = 0.14f),
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
                                text = "${toBengaliDigits(item.completedChapters)}/${toBengaliDigits(item.totalChapters)} মিশন",
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
                            fontWeight = FontWeight.ExtraBold
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
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = parsedColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = parsedColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "অধ্যায় মিশন দেখুন",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = parsedColor
                            )
                        }
                    }
                }
            }
        }
    }
}
