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
import com.example.ui.components.CourseAccessRequestConfirmDialog
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

    // State for Confirmation Dialog before sending Course Access Request
    var confirmAccessProgram by remember { mutableStateOf<OtherProgram?>(null) }

    // Always refresh programs & course commerce/installment status when screen opens
    LaunchedEffect(Unit) {
        viewModel.fetchEnrolledPrograms()
    }

    // Show Confirmation Dialog when user clicks "কোর্সে এক্সেস পান"
    confirmAccessProgram?.let { targetProgram ->
        val isReq = uiState.requestingAccessProgramId == targetProgram.id
        CourseAccessRequestConfirmDialog(
            courseTitle = targetProgram.title_bn ?: "কোর্স",
            isRequesting = isReq,
            onDismiss = {
                if (!isReq) confirmAccessProgram = null
            },
            onConfirmRequest = {
                val progToRequest = targetProgram
                confirmAccessProgram = null
                viewModel.requestCoursePurchaseAccess(
                    programId = progToRequest.id,
                    programTitle = progToRequest.title_bn ?: "কোর্স",
                    bannerUrl = progToRequest.banner_url,
                    context = context
                )
            }
        )
    }

    val activePaymentProgId = uiState.activePaymentSheetProgramId
    if (!activePaymentProgId.isNullOrBlank()) {
        val activePaymentCourseTitle = uiState.activePaymentSheetCourseTitle.ifBlank { "কোর্স" }
        val commerceItem = uiState.commerceSnapshot.resolveCourseItemState(
            programId = activePaymentProgId,
            titleBn = activePaymentCourseTitle
        )
        CoursePurchasePaymentBottomSheet(
            courseTitle = activePaymentCourseTitle,
            commerceState = commerceItem,
            paymentSettings = uiState.commerceSnapshot.paymentSettings,
            defaultUserPhone = sessionManager.getUserPhone() ?: "",
            isSubmitting = uiState.submittingPaymentProgramId == activePaymentProgId,
            isStartingTrial = uiState.startingTrialProgramId == activePaymentProgId,
            onDismiss = { viewModel.dismissPaymentSheet() },
            onStartFreeTrial = if (commerceItem.canStartFreeTrial) {
                {
                    viewModel.startCourseFreeTrial(
                        programId = activePaymentProgId,
                        programTitle = activePaymentCourseTitle,
                        context = context
                    )
                }
            } else null,
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
                            confirmAccessProgram = otherProgram
                        },
                        onStartFreeTrial = { otherProgram ->
                            viewModel.startCourseFreeTrial(
                                programId = otherProgram.id,
                                programTitle = otherProgram.title_bn ?: "কোর্স",
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
 * Course List View showing active enrolled courses and locked or purchasable courses
 */
@Composable
private fun MyCoursesListView(
    uiState: CourseUiState,
    onOpenEnrolledCourse: (EnrolledProgram) -> Unit,
    onPayInstallment: (programId: String, titleBn: String) -> Unit,
    onRequestCourseAccess: (OtherProgram) -> Unit,
    onStartFreeTrial: (OtherProgram) -> Unit,
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "আমার কোর্স ও মিশন",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (enrolled.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${toBengaliDigits(enrolled.size)}টি সক্রিয়",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (enrolled.isEmpty()) "নিচের কোর্সগুলো থেকে এক্সেস নিয়ে এককালীন বা কিস্তিতে ভর্তি হও" else "চলমান শিক্ষাবর্ষ ও ভর্তি হওয়া কোর্সসমূহ",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                            label = "কোর্স কিনুন (${toBengaliDigits(other.size)})",
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
                            titleBn = program.title_bn
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

                // Locked / Purchasable Courses Section (Clear, unblurred cards)
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
                                    text = "পছন্দের কোর্সে 'কোর্সে এক্সেস পান' ক্লিক করলে অ্যাডমিন দাম, কিস্তি ও ফ্রি ট্রায়াল সেট করে দিবেন",
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
                            titleBn = program.title_bn
                        )
                        OtherCourseBannerCard(
                            program = program,
                            commerceState = commerceItem,
                            isRequestingAccess = uiState.requestingAccessProgramId == program.id,
                            isStartingTrial = uiState.startingTrialProgramId == program.id,
                            onRequestAccess = { onRequestCourseAccess(program) },
                            onStartFreeTrial = { onStartFreeTrial(program) },
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
    val isTrialActive = commerceState.isTrialCurrentlyActive
    val hasActiveInstallmentDue = !isTrialActive && commerceState.dueAmount > 0.0

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
                // Top Row: Status badge on left + Trial or Installment badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isTrialActive) Color(0xFF7C3AED).copy(alpha = 0.35f) else Color(0xFF10B981).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, if (isTrialActive) Color(0xFFC4B5FD).copy(alpha = 0.6f) else Color(0xFF34D399).copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (isTrialActive) Icons.Default.CardGiftcard else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isTrialActive) Color(0xFFDDD6FE) else Color(0xFF34D399),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isTrialActive) "ফ্রি ট্রায়াল চলমান" else "ভর্তি হয়েছো (আনলকড)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFECFDF5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isTrialActive) {
                        val remTrialText = commerceState.getRemainingTrialTimeTextBn()
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFF59E0B).copy(alpha = 0.28f),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.65f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFFFDE68A),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (remTrialText.isNotBlank()) "$remTrialText বাকি" else "ট্রায়াল সক্রিয়",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFEF3C7),
                                    maxLines = 1
                                )
                            }
                        }
                    } else if (hasActiveInstallmentDue) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.65f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFFFDE68A),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "কিস্তি চলমান",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFEF3C7),
                                    maxLines = 1
                                )
                            }
                        }
                    } else if (commerceState.totalPrice > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "সম্পূর্ণ পরিশোধিত",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE0F2FE),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Layered Hero Banner Box (Ambient blurred backdrop + sharp uncropped foreground)
                if (!program.banner_url.isNullOrBlank()) {
                    val bannerRequest = remember(program.banner_url, context) {
                        ImageRequest.Builder(context)
                            .data(program.banner_url)
                            .crossfade(true)
                            .build()
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = bannerRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(20.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.32f))
                            )
                            AsyncImage(
                                model = bannerRequest,
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 6.dp, horizontal = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
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

                // If currently on Free Trial, show Trial countdown and option to buy full course
                if (isTrialActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color(0xFFC4B5FD).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val remTrial = commerceState.getRemainingTrialTimeTextBn()
                            Text(
                                text = if (remTrial.isNotBlank()) "🎁 ফ্রি ট্রায়ালে কোর্সটি আনলক আছে • বাকি সময়: $remTrial" else "🎁 ফ্রি ট্রায়ালে কোর্সটি আনলক আছে",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDDD6FE)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ট্রায়াল শেষ হওয়ার আগে বা পরে যেকোনো সময় পূর্ণ মেয়াদে কোর্সটি কিনে নিতে পারবেন।",
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                            if (commerceState.totalPrice > 0.0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF4ADE80),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onPayNextInstallment)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 9.dp, horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCartCheckout,
                                            contentDescription = null,
                                            tint = Color(0xFF064E3B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "পূর্ণ মেয়াদে কোর্সটি কিনুন (${CourseCommerceItemState.formatTakaBn(commerceState.totalPrice)})",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF064E3B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // If bought on installments and still has remaining balance, show installment tracker box
                if (hasActiveInstallmentDue) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "পরিশোধিত: ${CourseCommerceItemState.formatTakaBn(commerceState.paidAmount)} / ${CourseCommerceItemState.formatTakaBn(commerceState.totalPrice)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4ADE80),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "বকেয়া: ${CourseCommerceItemState.formatTakaBn(commerceState.dueAmount)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5),
                                    maxLines = 1
                                )
                            }

                            val progress = if (commerceState.totalPrice > 0) {
                                (commerceState.paidAmount / commerceState.totalPrice).toFloat().coerceIn(0.04f, 1f)
                            } else 0.5f

                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.18f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF10B981), Color(0xFF34D399))
                                            )
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            val nextAmt = if (commerceState.nextInstallmentAmount > 0) commerceState.nextInstallmentAmount else commerceState.dueAmount
                            val dueDateText = commerceState.getFormattedNextDueDateBn()
                            val remainingTimeText = commerceState.getRemainingDaysTextBn()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildString {
                                        append("পরবর্তী কিস্তি: ${CourseCommerceItemState.formatTakaBn(nextAmt)}")
                                        if (dueDateText.isNotBlank()) {
                                            append(" • শেষ তারিখ: $dueDateText")
                                        }
                                    },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFEF3C7),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (remainingTimeText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                    border = BorderStroke(0.8.dp, Color(0xFFFBBF24).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = Color(0xFFFDE68A),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "সময় বাকি: $remainingTimeText",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFDE68A),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if (commerceState.rejectionReason.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "❌ কিস্তি পেমেন্ট রিজেক্ট হয়েছে: ${commerceState.rejectionReason}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
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
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
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
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
    isStartingTrial: Boolean,
    onRequestAccess: () -> Unit,
    onStartFreeTrial: () -> Unit,
    onOpenPaymentSheet: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn ?: "কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }
    val state = commerceState.effectiveState
    val isLockedPriorToApproval = state == CourseAccessState.LOCKED_BLURRED || state == CourseAccessState.ACCESS_REQUESTED

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (badgeText, badgeBg, badgeBorder, badgeIcon) = when (state) {
                        CourseAccessState.LOCKED_BLURRED -> Quadruple(
                            "কোর্সে এক্সেস নিন",
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
                        CourseAccessState.APPROVED_TO_BUY -> {
                            if (commerceState.isTrialExpiredNow) {
                                Quadruple(
                                    "ফ্রি ট্রায়াল শেষ • এখন কিনুন",
                                    Color(0xFFF59E0B).copy(alpha = 0.32f),
                                    Color(0xFFFBBF24).copy(alpha = 0.75f),
                                    Icons.Default.TimerOff
                                )
                            } else if (commerceState.canStartFreeTrial) {
                                Quadruple(
                                    "🎁 ${commerceState.getTrialDurationLabelBn()} ফ্রি ট্রায়ালসহ অনুমোদিত",
                                    Color(0xFF7C3AED).copy(alpha = 0.35f),
                                    Color(0xFFC4B5FD).copy(alpha = 0.75f),
                                    Icons.Default.CardGiftcard
                                )
                            } else {
                                Quadruple(
                                    "কেনার এক্সেস অনুমোদিত",
                                    Color(0xFF10B981).copy(alpha = 0.25f),
                                    Color(0xFF34D399).copy(alpha = 0.65f),
                                    Icons.Default.LockOpen
                                )
                            }
                        }
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
                        shape = RoundedCornerShape(50),
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeBorder),
                        modifier = Modifier.weight(1f, fill = false)
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
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Show Admin-set Custom Price Badge when Admin has approved access
                    if (!isLockedPriorToApproval && commerceState.totalPrice > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFEF08A),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "মূল্য: ${CourseCommerceItemState.formatTakaBn(commerceState.totalPrice)}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Course Banner Box: Always sharp and crystal-clear so users can clearly see what course it is!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!program.banner_url.isNullOrBlank()) {
                        val bannerRequest = remember(program.banner_url, context) {
                            ImageRequest.Builder(context)
                                .data(program.banner_url)
                                .crossfade(true)
                                .build()
                        }
                        // Ambient blurred backdrop to eliminate empty dark bars on left/right
                        AsyncImage(
                            model = bannerRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(20.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f))
                        )
                        // Sharp unblurred course graphic for ALL states
                        AsyncImage(
                            model = bannerRequest,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Course Title (Always bright and clearly readable)
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // State-specific Details Box
                when (state) {
                    CourseAccessState.LOCKED_BLURRED -> {
                        Text(
                            text = "এই কোর্সটি কিনতে, ফ্রি ট্রায়াল নিতে বা কিস্তির দাম জানতে নিচের 'কোর্সে এক্সেস পান' বাটনে ক্লিক করুন।",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.88f),
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
                                text = "⏳ আপনার রিকোয়েস্ট অ্যাডমিন অ্যাপে পাঠানো হয়েছে! অ্যাডমিন এই কোর্সের দাম, কিস্তি ও ফ্রি ট্রায়াল সেট করে এপ্রুভ করলেই আপনি কোর্সটি শুরু করতে পারবেন।",
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
                                if (commerceState.isTrialExpiredNow) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                                        border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.55f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "⏰ আপনার ফ্রি ট্রায়ালের সময় শেষ হয়েছে! এখন এককালীন বা কিস্তিতে ভর্তি হয়ে পুরো কোর্সটি আনলক করুন।",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFEF3C7),
                                            lineHeight = 17.sp,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                } else if (commerceState.canStartFreeTrial) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF7C3AED).copy(alpha = 0.28f),
                                        border = BorderStroke(1.dp, Color(0xFFC4B5FD).copy(alpha = 0.55f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "🎁 কেনার আগে এই কোর্সটি ${commerceState.getTrialDurationLabelBn()} সম্পূর্ণ ফ্রিতে ট্রায়াল হিসেবে পড়ে দেখতে পারবেন!",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEDE9FE),
                                            lineHeight = 17.sp,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

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
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Free Trial Button if available before buying
                            if (commerceState.canStartFreeTrial) {
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = Color(0xFFFEF08A),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable(enabled = !isStartingTrial, onClick = onStartFreeTrial)
                                        .testTag("start_free_trial_card_button_${program.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isStartingTrial) {
                                            CircularProgressIndicator(
                                                color = Color(0xFF0F172A),
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "ফ্রি ট্রায়াল চালু হচ্ছে...",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF0F172A)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CardGiftcard,
                                                contentDescription = null,
                                                tint = Color(0xFF7C3AED),
                                                modifier = Modifier.size(19.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "🎁 ${commerceState.getTrialDurationLabelBn()} ফ্রি ট্রায়াল শুরু করুন",
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }
                            }

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(parsedColor.copy(alpha = 0.15f))
                    ) {
                        if (progress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress.coerceAtLeast(0.05f))
                                    .clip(RoundedCornerShape(50))
                                    .background(parsedColor)
                            )
                        }
                    }
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
