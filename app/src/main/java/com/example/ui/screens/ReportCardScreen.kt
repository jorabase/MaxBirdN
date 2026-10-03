package com.example.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.api.*
import com.example.reportcard.ReportCardUiState
import com.example.reportcard.ReportCardViewModel
import com.example.reportcard.ReportTab
import com.example.utils.AvatarUtils
import com.example.utils.SubjectColorUtils

// Bengali digit conversion helpers
private fun Number?.toBn(): String {
    if (this == null) return "০"
    val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val str = this.toString()
    val sb = StringBuilder()
    for (ch in str) {
        if (ch in '0'..'9') sb.append(digits[ch - '0']) else sb.append(ch)
    }
    return sb.toString()
}

private fun String?.toBn(): String {
    if (this == null) return ""
    val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder()
    for (ch in this) {
        if (ch in '0'..'9') sb.append(digits[ch - '0']) else sb.append(ch)
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCardScreen(
    viewModel: ReportCardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    var selectedStudentForProfile by remember { mutableStateOf<LeaderboardUserItem?>(null) }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value &&
            uiState.selectedTab == ReportTab.LEADERBOARD &&
            uiState.hasMoreLeaderboardPages &&
            !uiState.isPaginationLoading &&
            !uiState.isLeaderboardLoading &&
            uiState.searchQuery.isBlank()
        ) {
            viewModel.loadNextLeaderboardPage()
        }
    }

    Scaffold(
        topBar = {
            ReportCardTopBar(
                title = if (uiState.selectedTab == ReportTab.RESULT_DETAILS) "রিপোর্ট কার্ড" else "বিষয়ভিত্তিক লিডারবোর্ড",
                subtitle = uiState.programTitle,
                onBack = onBack,
                onShare = {
                    val shareText = if (uiState.selectedTab == ReportTab.RESULT_DETAILS) {
                        val score = uiState.reportData?.performance_report?.total_score?.percentage ?: 0
                        val rank = uiState.reportData?.performance_report?.class_rank?.rank ?: 0
                        "আমার কোয়ার্টার রিপোর্ট কার্ড:\nমোট স্কোর: ${score}%\nর‍্যাঙ্ক: ${rank}তম\nশিখো অ্যাপে আমার সাথে যুক্ত হও!"
                    } else {
                        val rank = uiState.leaderboardData?.user_rank ?: 0
                        val marks = uiState.leaderboardData?.user_marks ?: 0
                        val subName = uiState.selectedLeaderboardSubject?.display_bn ?: "সকল বিষয়"
                        "বিষয়: $subName | আমার মেধা র‍্যাঙ্ক: ${rank}তম (প্রাপ্ত নম্বর: ${marks}%)\nশিখো অ্যাপ থেকে।"
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "রিপোর্ট কার্ড শেয়ার করো")
                    context.startActivity(shareIntent)
                }
            )
        },
        bottomBar = {
            if (uiState.selectedTab == ReportTab.LEADERBOARD && uiState.leaderboardData != null) {
                StickyMyRankBar(
                    userRank = uiState.leaderboardData?.user_rank,
                    userScore = uiState.leaderboardData?.user_marks,
                    userName = uiState.userName,
                    userAvatar = uiState.userAvatar,
                    onClick = {
                        val myUserId = viewModel.currentUserId
                        val myUserItem = LeaderboardUserItem(
                            user_id = myUserId,
                            rank = uiState.leaderboardData?.user_rank,
                            score = uiState.leaderboardData?.user_marks,
                            user = LeaderboardUserInfo(
                                id = myUserId,
                                user_id = myUserId,
                                name = uiState.userName,
                                avatar = uiState.userAvatar,
                                school = uiState.userSchool,
                                phone = uiState.userPhone
                            )
                        )
                        selectedStudentForProfile = myUserItem
                        viewModel.fetchStudentFullProfile(myUserId, isCurrentUser = true, studentItem = myUserItem)
                    },
                    onShare = {
                        val rank = uiState.leaderboardData?.user_rank ?: 0
                        val marks = uiState.leaderboardData?.user_marks ?: 0
                        val subName = uiState.selectedLeaderboardSubject?.display_bn ?: "সকল বিষয়"
                        val shareText = "বিষয়: $subName\nআমার জাতীয় মেধা র‍্যাঙ্ক: ${rank}তম\nপ্রাপ্ত নম্বর: ${marks}%\nশিখো লাইভ ক্লাসে অংশ নাও!"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "লিডারবোর্ড শেয়ার করো")
                        context.startActivity(shareIntent)
                    }
                )
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshData() },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (uiState.selectedTab == ReportTab.LEADERBOARD) 90.dp else 24.dp)
            ) {
                // 1. Quarters Horizontal Switcher
                item {
                    QuarterSwitcherSection(
                        phases = uiState.phases,
                        selectedPhase = uiState.selectedPhase,
                        onSelectPhase = { viewModel.switchQuarter(it) }
                    )
                }

                // 2. Tab Switcher (Result Details vs Leaderboard)
                item {
                    ReportTabSwitcher(
                        selectedTab = uiState.selectedTab,
                        onTabSelected = { viewModel.switchTab(it) }
                    )
                }

                // 3. Tab Content
                if (uiState.isLoading && uiState.reportData == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                } else if (uiState.selectedTab == ReportTab.RESULT_DETAILS) {
                    // TAB 1: RESULT DETAILS
                    item {
                        PerformanceReportBanner(
                            totalScore = uiState.reportData?.performance_report?.total_score?.percentage,
                            rank = uiState.reportData?.performance_report?.class_rank?.rank,
                            totalStudents = uiState.reportData?.performance_report?.class_rank?.total_students
                        )
                    }

                    item {
                        LearningProgressCards(
                            progress = uiState.reportData?.learning_progress
                        )
                    }

                    item {
                        LearningActivitySummarySection(
                            summary = uiState.reportData?.learning_activity_summary
                        )
                    }

                    item {
                        PerformanceTrendCard(
                            trendData = uiState.trendData,
                            activeMetric = uiState.trendMetric,
                            onMetricChange = { viewModel.switchMetric(it) }
                        )
                    }

                    item {
                        SubjectWisePerformanceSection(
                            container = uiState.reportData?.subject_wise_performance
                        )
                    }
                } else {
                    // TAB 2: SUBJECT-WISE LEADERBOARD
                    item {
                        SubjectPickerChips(
                            subjects = uiState.subjects,
                            selectedSubject = uiState.selectedLeaderboardSubject,
                            onSelectSubject = { viewModel.selectLeaderboardSubject(it) }
                        )
                    }

                    // Mobile Number, Name, College Database Search Bar
                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            placeholder = {
                                Text(
                                    text = "নাম, মোবাইল বা কলেজ দিয়ে ডাটাবেজে খুঁজুন...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (uiState.searchQuery.isNotEmpty()) Color(0xFF2563EB) else Color(0xFF64748B)
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF64748B)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    if (uiState.isLeaderboardLoading && uiState.leaderboardData == null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF3B82F6),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        val leaderboard = uiState.leaderboardData
                        val rawUsers = leaderboard?.data ?: emptyList()

                        // If searching, prioritize direct database search results (with fallback to filtered raw users)
                        val isSearching = uiState.searchQuery.isNotBlank()
                        val filteredUsers = if (!isSearching) {
                            rawUsers
                        } else {
                            if (uiState.dbSearchResults.isNotEmpty()) {
                                uiState.dbSearchResults
                            } else {
                                val q = uiState.searchQuery.trim().lowercase()
                                rawUsers.filter { item ->
                                    val u = item.user
                                    val nameMatch = u?.name?.lowercase()?.contains(q) == true || item.name?.lowercase()?.contains(q) == true
                                    val phoneMatch = u?.phone?.replace("-", "")?.contains(q) == true || item.phone?.replace("-", "")?.contains(q) == true
                                    val collegeMatch = u?.effectiveCollege?.lowercase()?.contains(q) == true ||
                                            u?.school?.lowercase()?.contains(q) == true ||
                                            item.effectiveCollege?.lowercase()?.contains(q) == true
                                    val rollMatch = u?.roll?.lowercase()?.contains(q) == true || item.roll_no?.lowercase()?.contains(q) == true
                                    nameMatch || phoneMatch || collegeMatch || rollMatch
                                }
                            }
                        }

                        if (isSearching) {
                            // Direct Database Search Header
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0xFFEFF6FF),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                                        ) {
                                            Text(
                                                text = "সরাসরি ডাটাবেজ সার্চ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1D4ED8),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ফলাফল (${filteredUsers.size.toBn()} জন)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF334155)
                                        )
                                    }

                                    if (uiState.isSearchingDb) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color(0xFF2563EB),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }

                            if (filteredUsers.isNotEmpty()) {
                                itemsIndexed(filteredUsers) { _, userItem ->
                                    LeaderboardUserRowItem(
                                        userItem = userItem,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        onClick = {
                                            selectedStudentForProfile = userItem
                                            val effectiveId = userItem.effectiveUserId ?: ""
                                            val myUserId = viewModel.currentUserId
                                            val isSelf = (effectiveId.isNotBlank() && effectiveId == myUserId) ||
                                                    (userItem.rank != null && userItem.rank == uiState.leaderboardData?.user_rank)
                                            val targetId = if (isSelf) myUserId else effectiveId
                                            viewModel.fetchStudentFullProfile(targetId, isCurrentUser = isSelf, studentItem = userItem)
                                        }
                                    )
                                }
                            } else if (!uiState.isSearchingDb) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.SearchOff,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "\"${uiState.searchQuery}\" দিয়ে ডাটাবেজে কোনো অ্যাকাউন্ট পাওয়া যায়নি",
                                                color = Color(0xFF475569),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "নামের বানান সঠিক কিনা যাচাই করুন অথবা মোবাইল নম্বর দিয়ে চেষ্টা করুন",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Normal Leaderboard view (Podium + 4th+ list)
                            if (filteredUsers.isNotEmpty()) {
                                // Top 3 Podium
                                item {
                                    LeaderboardPodiumView(
                                        topUsers = filteredUsers.take(3),
                                        onUserClick = { userItem ->
                                            selectedStudentForProfile = userItem
                                            val effectiveId = userItem.effectiveUserId ?: ""
                                            val myUserId = viewModel.currentUserId
                                            val isSelf = (effectiveId.isNotBlank() && effectiveId == myUserId) ||
                                                    (userItem.rank != null && userItem.rank == uiState.leaderboardData?.user_rank)
                                            val targetId = if (isSelf) myUserId else effectiveId
                                            viewModel.fetchStudentFullProfile(targetId, isCurrentUser = isSelf, studentItem = userItem)
                                        }
                                    )
                                }

                                // 4th to 10th+ list
                                val restUsers = if (filteredUsers.size > 3) filteredUsers.drop(3) else emptyList()
                                if (restUsers.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "অন্যান্য শীর্ষ মেধাবী শিক্ষার্থী (${filteredUsers.size.toBn()} জন)",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }

                                    itemsIndexed(restUsers) { _, userItem ->
                                        LeaderboardUserRowItem(
                                            userItem = userItem,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            onClick = {
                                                selectedStudentForProfile = userItem
                                                val effectiveId = userItem.effectiveUserId ?: ""
                                                val myUserId = viewModel.currentUserId
                                                val isSelf = (effectiveId.isNotBlank() && effectiveId == myUserId) ||
                                                        (userItem.rank != null && userItem.rank == uiState.leaderboardData?.user_rank)
                                                val targetId = if (isSelf) myUserId else effectiveId
                                                viewModel.fetchStudentFullProfile(targetId, isCurrentUser = isSelf, studentItem = userItem)
                                            }
                                        )
                                    }
                                }

                                // Pagination Loading Footer Indicator
                                if (uiState.isPaginationLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = Color(0xFF2563EB),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "আরও অ্যাকাউন্ট লোড করা হচ্ছে...",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.SearchOff,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "এই বিষয়ের জন্য এখনো লিডারবোর্ড ডেটা পাওয়া যায়নি",
                                                color = Color(0xFF64748B),
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
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

    selectedStudentForProfile?.let { userItem ->
        val effectiveId = userItem.effectiveUserId ?: ""
        val myUserId = viewModel.currentUserId
        val isSelf = (effectiveId.isNotBlank() && effectiveId == myUserId) ||
                (userItem.rank != null && userItem.rank == uiState.leaderboardData?.user_rank)
        StudentProfileDetailDialog(
            userItem = userItem,
            fullProfile = uiState.selectedStudentFullProfile,
            isSelf = isSelf,
            isLoadingProfile = uiState.isFetchingStudentProfile,
            errorMessage = uiState.profileFetchError,
            onRetry = {
                val targetId = if (isSelf) myUserId else effectiveId
                viewModel.fetchStudentFullProfile(targetId, isCurrentUser = isSelf, studentItem = userItem)
            },
            onDismiss = {
                selectedStudentForProfile = null
                viewModel.clearSelectedStudentProfile()
            }
        )
    }
}

// =======================================================
// TOP BAR & COMMON COMPONENTS
// =======================================================
@Composable
fun ReportCardTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF))
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun QuarterSwitcherSection(
    phases: List<PhaseItem>,
    selectedPhase: PhaseItem?,
    onSelectPhase: (PhaseItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(phases) { phase ->
            val isSelected = phase.id == selectedPhase?.id
            val isCompleted = phase.status.equals("COMPLETED", true)
            val isActive = phase.is_current == true || phase.status.equals("ACTIVE", true)

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectPhase(phase) },
                color = if (isSelected) Color(0xFF1E3A8A) else Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF1E3A8A) else Color(0xFFE2E8F0)
                ),
                shadowElevation = if (isSelected) 3.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive -> Color(0xFF10B981)
                                    isCompleted -> Color(0xFF3B82F6)
                                    else -> Color(0xFF94A3B8)
                                }
                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = phase.title ?: "কোয়ার্টার",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else Color(0xFF1E293B)
                        )
                        if (phase.course_progress_percentage != null && phase.course_progress_percentage > 0) {
                            Text(
                                text = "অগ্রগতি: ${phase.course_progress_percentage.toInt().toBn()}%",
                                fontSize = 11.sp,
                                color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportTabSwitcher(
    selectedTab: ReportTab,
    onTabSelected: (ReportTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE2E8F0).copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Tab 1: রেজাল্টের বিস্তারিত
            val isTab1 = selectedTab == ReportTab.RESULT_DETAILS
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isTab1) Color.White else Color.Transparent)
                    .clickable { onTabSelected(ReportTab.RESULT_DETAILS) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = if (isTab1) Color(0xFF1E3A8A) else Color(0xFF64748B),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "রেজাল্টের বিস্তারিত",
                        fontSize = 13.sp,
                        fontWeight = if (isTab1) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTab1) Color(0xFF1E3A8A) else Color(0xFF64748B)
                    )
                }
            }

            // Tab 2: বিষয়ভিত্তিক লিডারবোর্ড
            val isTab2 = selectedTab == ReportTab.LEADERBOARD
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isTab2) Color.White else Color.Transparent)
                    .clickable { onTabSelected(ReportTab.LEADERBOARD) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = if (isTab2) Color(0xFF1E3A8A) else Color(0xFF64748B),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "বিষয়ভিত্তিক লিডারবোর্ড",
                        fontSize = 13.sp,
                        fontWeight = if (isTab2) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTab2) Color(0xFF1E3A8A) else Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

// =======================================================
// TAB 1: RESULT DETAILS VIEW COMPONENTS
// =======================================================
@Composable
fun PerformanceReportBanner(
    totalScore: Int?,
    rank: Int?,
    totalStudents: Int?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF1E3A8A))
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = Color(0xFFFCD34D),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "পারফরম্যান্স রিপোর্ট",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCD34D)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "সর্বমোট স্কোর",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Text(
                        text = "${(totalScore ?: 0).toBn()}%",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    if (rank != null && rank > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "মেধা র‍্যাঙ্ক: ${rank.toBn()}তম ${if (totalStudents != null) "(${totalStudents.toBn()} জনের মধ্যে)" else ""}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }

                // Golden Star / Medal Illustration
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.35f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                )
                            )
                            .border(2.dp, Color(0xFFFCD34D), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LearningProgressCards(
    progress: LearningProgressContainer?
) {
    val classComp = progress?.class_completion
    val examProg = progress?.chapter_exam_score

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: ক্লাস কমপ্লিশন
        val classAttended = classComp?.attended_classes ?: 0
        val classTotal = (classComp?.total_classes ?: 1).coerceAtLeast(1)
        val classPct = classComp?.percentage ?: ((classAttended.toDouble() / classTotal) * 100).toInt()

        ProgressCircularCard(
            title = "ক্লাস কমপ্লিশন",
            valueText = "${classAttended.toBn()}/${classTotal.toBn()}",
            subText = "উপস্থিতি সংখ্যা",
            percentage = classPct,
            primaryColor = Color(0xFF3B82F6),
            secondaryColor = Color(0xFFDBEAFE),
            icon = Icons.Default.VideoLibrary,
            modifier = Modifier.weight(1f)
        )

        // Card 2: চ্যাপ্টার এক্সাম স্কোর
        val examObtained = examProg?.obtained ?: 0
        val examTotal = (examProg?.total ?: 100).coerceAtLeast(1)
        val examPct = examProg?.percentage ?: ((examObtained.toDouble() / examTotal) * 100).toInt()

        ProgressCircularCard(
            title = "চ্যাপ্টার এক্সাম স্কোর",
            valueText = "${examObtained.toBn()}/${examTotal.toBn()}",
            subText = "প্রাপ্ত মোট নম্বর",
            percentage = examPct,
            primaryColor = Color(0xFF10B981),
            secondaryColor = Color(0xFFD1FAE5),
            icon = Icons.Default.Quiz,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ProgressCircularCard(
    title: String,
    valueText: String,
    subText: String,
    percentage: Int,
    primaryColor: Color,
    secondaryColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(secondaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Circular Arc Gauge
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 8.dp.toPx()
                    // Background Track
                    drawArc(
                        color = secondaryColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Progress Arc
                    val sweep = 270f * (percentage.coerceIn(0, 100) / 100f)
                    drawArc(
                        color = primaryColor,
                        startAngle = 135f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                Text(
                    text = "${percentage.toBn()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = valueText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = subText,
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun LearningActivitySummarySection(
    summary: LearningActivitySummary?
) {
    val quizCount = summary?.practice_quiz_attempted ?: 0
    val resourceCount = summary?.learning_resource_viewed ?: 0
    val animatedCount = summary?.animated_lessons_watched ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "লার্নিং এক্টিভিটির হিসাব",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActivityChip(
                count = quizCount,
                label = "প্র্যাকটিস কুইজ দিয়েছো",
                icon = Icons.Default.EditNote,
                iconBg = Color(0xFFFEF3C7),
                iconColor = Color(0xFFD97706),
                modifier = Modifier.weight(1f)
            )

            ActivityChip(
                count = resourceCount,
                label = "লার্নিং রিসোর্স দেখেছো",
                icon = Icons.Default.MenuBook,
                iconBg = Color(0xFFEDE9FE),
                iconColor = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f)
            )

            if (animatedCount > 0) {
                ActivityChip(
                    count = animatedCount,
                    label = "অ্যানিমেটেড লেসন দেখেছো",
                    icon = Icons.Default.PlayCircle,
                    iconBg = Color(0xFFFCE7F3),
                    iconColor = Color(0xFFDB2777),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ActivityChip(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${count.toBn()}টি",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                lineHeight = 14.sp
            )
        }
    }
}

// =======================================================
// PERFORMANCE TREND LINE CHART
// =======================================================
@Composable
fun PerformanceTrendCard(
    trendData: PerformanceTrendResponse?,
    activeMetric: String,
    onMetricChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "পারফরম্যান্স ট্রেন্ড",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    val avg = trendData?.summary?.current_average ?: 0
                    Text(
                        text = "বর্তমান গড়: ${avg.toBn()}%",
                        fontSize = 12.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Metric Switcher Segmented Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        val isClass = activeMetric == "class_completion"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isClass) Color(0xFF1E3A8A) else Color.Transparent)
                                .clickable { onMetricChange("class_completion") }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "ক্লাস",
                                fontSize = 11.sp,
                                fontWeight = if (isClass) FontWeight.Bold else FontWeight.Medium,
                                color = if (isClass) Color.White else Color(0xFF64748B)
                            )
                        }

                        val isExam = activeMetric == "chapter_exam_score"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isExam) Color(0xFF1E3A8A) else Color.Transparent)
                                .clickable { onMetricChange("chapter_exam_score") }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "এক্সাম",
                                fontSize = 11.sp,
                                fontWeight = if (isExam) FontWeight.Bold else FontWeight.Medium,
                                color = if (isExam) Color.White else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Chart
            val dataPoints = trendData?.current_phase?.data_points ?: listOf(10f, 20f, 35f, 50f, 65f, 75f, 85f)
            val labels = trendData?.x_labels ?: listOf("W1", "W2", "W3", "W4", "W5", "W6", "W7")

            var selectedIndex by remember { mutableStateOf<Int?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(dataPoints) {
                        // Detect tap on point
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 20.dp, top = 10.dp, start = 8.dp, end = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val n = dataPoints.size
                    if (n < 2) return@Canvas

                    // Draw subtle grid lines (0%, 50%, 100%)
                    val gridY = listOf(0f, 0.5f, 1f)
                    for (ratio in gridY) {
                        val y = h * (1f - ratio)
                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }

                    // Build Cubic Bézier Path
                    val path = Path()
                    val fillPath = Path()
                    val stepX = w / (n - 1)

                    val coords = dataPoints.mapIndexed { idx, value ->
                        val clampedVal = value.coerceIn(0f, 100f)
                        val x = idx * stepX
                        val y = h * (1f - (clampedVal / 100f))
                        Offset(x, y)
                    }

                    path.moveTo(coords[0].x, coords[0].y)
                    fillPath.moveTo(coords[0].x, h)
                    fillPath.lineTo(coords[0].x, coords[0].y)

                    for (i in 0 until coords.size - 1) {
                        val p0 = coords[i]
                        val p1 = coords[i + 1]
                        val cx1 = p0.x + (p1.x - p0.x) / 2
                        val cy1 = p0.y
                        val cx2 = p0.x + (p1.x - p0.x) / 2
                        val cy2 = p1.y
                        path.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    }

                    fillPath.lineTo(coords.last().x, h)
                    fillPath.close()

                    // Draw Gradient Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.35f), Color(0xFF38BDF8).copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw Line Stroke
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0284C7), Color(0xFF2563EB))
                        ),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Draw Points
                    coords.forEachIndexed { idx, point ->
                        val isSelected = selectedIndex == idx
                        val radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx()
                        drawCircle(
                            color = Color.White,
                            radius = radius + 2.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF2563EB),
                            radius = radius,
                            center = point
                        )
                    }
                }

                // X-Axis Labels Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayLabels = if (labels.size > 8) {
                        labels.filterIndexed { index, _ -> index % 2 == 0 || index == labels.size - 1 }
                    } else labels

                    displayLabels.forEach { label ->
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// =======================================================
// SUBJECT-WISE PERFORMANCE ACCORDION SECTION
// =======================================================
@Composable
fun SubjectWisePerformanceSection(
    container: SubjectWisePerformanceContainer?
) {
    val groups = container?.groups ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "আমার বিষয়ভিত্তিক পারফরম্যান্স",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "কোনো বিষয়ভিত্তিক তথ্য পাওয়া যায়নি",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp
                )
            }
        } else {
            groups.forEach { group ->
                PerformanceGroupBlock(group = group)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun PerformanceGroupBlock(
    group: PerformanceGroupItem
) {
    val level = group.performance_level?.lowercase() ?: "moderate"
    val (badgeBg, badgeTextColor, headerBorder) = when {
        level.contains("need") || level.contains("improvement") -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), Color(0xFFFECACA))
        level.contains("good") || level.contains("excellent") -> Triple(Color(0xFFD1FAE5), Color(0xFF059669), Color(0xFFA7F3D0))
        else -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Color(0xFFFDE68A))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, headerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Group Header Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = group.label ?: "পারফরম্যান্স",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                val count = group.subjects?.size ?: 0
                Text(
                    text = "${count.toBn()}টি বিষয়",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            group.subjects?.forEachIndexed { index, item ->
                SubjectPerformanceItemRow(item = item)
                if (index < (group.subjects.size - 1)) {
                    HorizontalDivider(
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectPerformanceItemRow(
    item: SubjectPerformanceItem
) {
    var isExpanded by remember { mutableStateOf(false) }
    val studentScore = item.student_avg_score ?: 0
    val topperScore = item.topper_score ?: 90
    val isTopper = item.is_topper == true

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject Icon / Color Indicator
            val subjColor = SubjectColorUtils.getColorScheme(item.title).textColor
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(subjColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.icon.isNullOrBlank()) {
                    AsyncImage(
                        model = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = item.title?.take(1) ?: "ব",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = subjColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title ?: "বিষয়",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    if (isTopper) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "টপার 👑",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Score comparison bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "তোমার: ${studentScore.toBn()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (studentScore >= 70) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "টপার: ${topperScore.toBn()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Expand / Collapse Icon
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF64748B)
                )
            }
        }

        // Animated Expanded Details
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(12.dp)
            ) {
                // Live class completed
                val completedLive = item.completed_live_class ?: 0
                val totalLive = item.total_live_class ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "লাইভ ক্লাস সম্পন্ন", fontSize = 12.sp, color = Color(0xFF64748B))
                    Text(
                        text = "${completedLive.toBn()}/${totalLive.toBn()}টি",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Exam score breakdown
                val obtainedExam = item.total_obtained_score ?: 0
                val totalExam = item.total_exam_score ?: 100
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "চ্যাপ্টার এক্সাম নম্বর", fontSize = 12.sp, color = Color(0xFF64748B))
                    Text(
                        text = "${obtainedExam.toBn()}/${totalExam.toBn()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}

// =======================================================
// TAB 2: SUBJECT-WISE LEADERBOARD COMPONENTS
// =======================================================
@Composable
fun SubjectPickerChips(
    subjects: List<AcademicSubjectItem>,
    selectedSubject: AcademicSubjectItem?,
    onSelectSubject: (AcademicSubjectItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subjects) { subject ->
            val isSelected = subject.code == selectedSubject?.code
            val chipColor = SubjectColorUtils.getColorScheme(subject.display_bn ?: subject.code).textColor

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelectSubject(subject) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) chipColor else Color.White,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) chipColor else Color(0xFFCBD5E1)
                ),
                shadowElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subject.code == "ALL") {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = subject.display_bn ?: subject.code ?: "বিষয়",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF334155)
                    )
                }
            }
        }
    }
}

@Composable
fun LeaderboardPodiumView(
    topUsers: List<LeaderboardUserItem>,
    onUserClick: (LeaderboardUserItem) -> Unit = {}
) {
    if (topUsers.isEmpty()) return

    val first = topUsers.getOrNull(0)
    val second = topUsers.getOrNull(1)
    val third = topUsers.getOrNull(2)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏆 শীর্ষ ৩ মেধাবী শিক্ষার্থী",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd Place (Silver)
                if (second != null) {
                    PodiumColumn(
                        userItem = second,
                        rank = 2,
                        pedestalHeight = 90.dp,
                        podiumColor = Color(0xFF94A3B8),
                        badgeText = "২য়",
                        onClick = { onUserClick(second) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 1st Place (Gold)
                if (first != null) {
                    PodiumColumn(
                        userItem = first,
                        rank = 1,
                        pedestalHeight = 120.dp,
                        podiumColor = Color(0xFFF59E0B),
                        badgeText = "১ম",
                        isWinner = true,
                        onClick = { onUserClick(first) },
                        modifier = Modifier.weight(1.15f)
                    )
                }

                // 3rd Place (Bronze)
                if (third != null) {
                    PodiumColumn(
                        userItem = third,
                        rank = 3,
                        pedestalHeight = 75.dp,
                        podiumColor = Color(0xFFD97706),
                        badgeText = "৩য়",
                        onClick = { onUserClick(third) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PodiumColumn(
    userItem: LeaderboardUserItem,
    rank: Int,
    pedestalHeight: androidx.compose.ui.unit.Dp,
    podiumColor: Color,
    badgeText: String,
    isWinner: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val user = userItem.user
    val score = userItem.effectiveScore
    val avatarUrl = user?.effectiveAvatar
    val name = user?.effectiveName ?: "শিক্ষার্থী"
    val school = user?.effectiveCollege ?: ""

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Crown for Winner
        if (isWinner) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Winner Crown",
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Avatar with border
        Box(
            modifier = Modifier
                .size(if (isWinner) 56.dp else 46.dp)
                .clip(CircleShape)
                .border(2.dp, podiumColor, CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            val formattedAvatar = AvatarUtils.formatAvatarUrl(avatarUrl, name)
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(formattedAvatar)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = podiumColor, strokeWidth = 1.5.dp)
                    }
                },
                error = {
                    UserInitialBadge(
                        name = name,
                        textStyle = TextStyle(fontSize = if (isWinner) 20.sp else 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Student Name
        Text(
            text = name,
            fontSize = if (isWinner) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // School Name
        if (school.isNotBlank()) {
            Text(
                text = school,
                fontSize = 9.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Score Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = podiumColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = "${score.toBn()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = podiumColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Pedestal Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(podiumColor, podiumColor.copy(alpha = 0.7f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                fontSize = if (isWinner) 22.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun UserInitialBadge(
    name: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
) {
    val cleanName = name.trim()
    val initial = cleanName.take(1).ifBlank { "শ" }
    val hash = kotlin.math.abs(cleanName.hashCode())
    val gradientPairs = listOf(
        Pair(Color(0xFF2563EB), Color(0xFF60A5FA)),
        Pair(Color(0xFF059669), Color(0xFF34D399)),
        Pair(Color(0xFFD97706), Color(0xFFFBBF24)),
        Pair(Color(0xFF7C3AED), Color(0xFFA78BFA)),
        Pair(Color(0xFFE11D48), Color(0xFFFB7185)),
        Pair(Color(0xFF0284C7), Color(0xFF38BDF8))
    )
    val colorPair = gradientPairs[hash % gradientPairs.size]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(colorPair.first, colorPair.second)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = textStyle
        )
    }
}

@Composable
fun LeaderboardUserRowItem(
    userItem: LeaderboardUserItem,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rank = userItem.rank ?: 0
    val score = userItem.effectiveScore
    val user = userItem.user
    val name = user?.effectiveName ?: "শিক্ষার্থী"
    val avatar = user?.effectiveAvatar
    val school = user?.effectiveCollege ?: ""

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number Badge
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${rank.toBn()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                val formattedAvatar = AvatarUtils.formatAvatarUrl(avatar, name)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(formattedAvatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE2E8F0)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        }
                    },
                    error = {
                        UserInitialBadge(
                            name = name,
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name & School
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (school.isNotBlank()) {
                    Text(
                        text = school,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Score Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF)
            ) {
                Text(
                    text = "${score.toBn()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2563EB),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StickyMyRankBar(
    userRank: Int?,
    userScore: Int?,
    userName: String,
    userAvatar: String?,
    onClick: () -> Unit = {},
    onShare: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color(0xFF0F172A),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF38BDF8), CircleShape)
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                val formattedAvatar = AvatarUtils.formatAvatarUrl(userAvatar, userName)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(formattedAvatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = userName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E293B)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF38BDF8), strokeWidth = 1.5.dp)
                        }
                    },
                    error = {
                        UserInitialBadge(
                            name = userName,
                            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "তোমার র‍্যাঙ্ক: ${(userRank ?: 0).toBn()}তম (প্রোফাইল দেখুন)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "প্রাপ্ত মোট নম্বর: ${(userScore ?: 0).toBn()}%",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            // Share Button
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "শেয়ার",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

fun downloadProfileImage(context: Context, imageUrl: String, studentName: String) {
    if (imageUrl.isBlank()) {
        Toast.makeText(context, "ডাউনলোড করার মত ছবি নেই", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val request = DownloadManager.Request(android.net.Uri.parse(imageUrl)).apply {
            setTitle("$studentName - প্রোফাইল ছবি")
            setDescription("ছবি ডাউনলোড হচ্ছে...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "profile_${studentName.replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager != null) {
            manager.enqueue(request)
            Toast.makeText(context, "ডাউনলোড শুরু হয়েছে! নোটিফিকেশন বার চেক করুন", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "ডাউনলোড ম্যানেজার পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "ডাউনলোড করতে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenImageViewerDialog(
    imageUrl: String?,
    studentName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formattedUrl = AvatarUtils.formatAvatarUrl(imageUrl, studentName)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = studentName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "বন্ধ করুন",
                        tint = Color.White
                    )
                }
            }

            // Image Center Display
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(formattedUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = studentName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    error = {
                        UserInitialBadge(
                            name = studentName,
                            textStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                )
            }

            // Bottom Action Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            downloadProfileImage(context, formattedUrl, studentName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "ডাউনলোড",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ছবি ডাউনলোড",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Profile Image URL", formattedUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "ছবির লিংক কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "কপি লিংক",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "লিংক কপি",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialContactSection(
    phone: String,
    context: Context
) {
    if (phone.isBlank()) return

    val rawDigits = phone.filter { it.isDigit() }
    val formattedPhone = when {
        rawDigits.startsWith("880") -> rawDigits
        rawDigits.startsWith("0") -> "88$rawDigits"
        rawDigits.length == 10 -> "880$rawDigits"
        else -> rawDigits
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContactPhone,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "সরাসরি যোগাযোগ করুন",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WhatsApp Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            try {
                                val waIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone")
                                }
                                context.startActivity(waIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp খোলা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "💬", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "WhatsApp",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                // Telegram Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            try {
                                val tgIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://t.me/+$formattedPhone")
                                }
                                context.startActivity(tgIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Telegram খোলা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE0F2FE),
                    border = BorderStroke(1.dp, Color(0xFF7DD3FC))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✈️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Telegram",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                    }
                }

                // Phone Call Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            try {
                                val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                    data = android.net.Uri.parse("tel:$phone")
                                }
                                context.startActivity(callIntent)
                            } catch (_: Exception) {}
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "কল",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "কল করুন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileDetailDialog(
    userItem: LeaderboardUserItem,
    fullProfile: UserProfile?,
    isSelf: Boolean = false,
    isLoadingProfile: Boolean,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val user = userItem.user

    val name = run {
        val fName = fullProfile?.first_name?.trim() ?: ""
        val lName = fullProfile?.last_name?.trim() ?: ""
        val combined = "$fName $lName".trim()
        if (combined.isNotBlank()) combined else userItem.effectiveName
    }

    val rawAvatar = fullProfile?.avatar?.takeIf { it.isNotBlank() && it != "null" } ?: userItem.effectiveAvatar
    val formattedAvatar = AvatarUtils.formatAvatarUrl(rawAvatar, name)

    val phone = (fullProfile?.user?.phone ?: userItem.effectivePhone)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val email = fullProfile?.user?.email?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    val gender = run {
        val raw = (fullProfile?.gender ?: userItem.gender ?: userItem.user?.gender)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        when (raw?.lowercase()) {
            "female", "f", "নারী", "মহিলা" -> "মহিলা"
            "male", "m", "পুরুষ" -> "পুরুষ"
            else -> raw
        }
    }

    val dob = run {
        val raw = (fullProfile?.dob ?: userItem.dob ?: userItem.user?.dob)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (raw != null) {
            try {
                val parseFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                val date = parseFormat.parse(raw)
                if (date != null) {
                    java.text.SimpleDateFormat("dd MMMM, yyyy", java.util.Locale.US).format(date)
                } else raw
            } catch (_: Exception) {
                raw
            }
        } else null
    }

    val guardianName = fullProfile?.guardian_name?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val guardianPhone = fullProfile?.guardian_mobile?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    val studyGroup = (fullProfile?.study_group ?: userItem.group ?: userItem.study_group ?: userItem.user?.effectiveGroup)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val passingYear = (fullProfile?.passing_year ?: userItem.passing_year ?: userItem.user?.passing_year)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val className = (fullProfile?.`class`?.display ?: fullProfile?.`class`?.code ?: userItem.batch ?: userItem.user?.batch)?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    val shift = fullProfile?.shift?.trim()?.takeIf { it.isNotBlank() && it != "null" }?.let {
        when (it.lowercase()) {
            "morning" -> "মর্নিং"
            "day" -> "ডে"
            else -> it
        }
    }

    val district = (fullProfile?.school?.address?.district?.display ?: userItem.district ?: userItem.user?.district)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val division = (fullProfile?.school?.address?.division?.display ?: userItem.division ?: userItem.user?.division)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val college = (fullProfile?.school?.name ?: userItem.effectiveCollege)?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    val sscBoard = (fullProfile?.ssc_board_name ?: division?.let { "$it শিক্ষা বোর্ড" })?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val hscBoard = (fullProfile?.hsc_board_name ?: division?.let { "$it শিক্ষা বোর্ড" })?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    val sscRoll = (fullProfile?.board_roll_number ?: userItem.effectiveRoll)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val hscRoll = fullProfile?.hsc_board_roll_number?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val regNo = fullProfile?.board_reg_number?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    val rank = userItem.rank
    val score = userItem.effectiveScore

    val context = LocalContext.current
    var showFullScreenAvatar by remember { mutableStateOf(false) }

    if (showFullScreenAvatar) {
        FullScreenImageViewerDialog(
            imageUrl = formattedAvatar,
            studentName = name,
            onDismiss = { showFullScreenAvatar = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCBD5E1))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "শিক্ষার্থীর সম্পূর্ণ প্রোফাইল",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "বন্ধ করুন",
                        tint = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingProfile) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF2563EB),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "সার্ভার থেকে সম্পূর্ণ প্রোফাইল তথ্য লোড হচ্ছে...",
                            fontSize = 12.sp,
                            color = Color(0xFF1D4ED8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (fullProfile == null && !errorMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "সম্পূর্ণ বিবরণ লোড করা যায়নি",
                            fontSize = 12.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(onClick = onRetry) {
                            Text("পুনরায় চেষ্টা করুন", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Header Card (Avatar + Name + Rank Badge)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color(0xFF3B82F6), CircleShape)
                                .background(Color(0xFFE0F2FE))
                                .clickable { showFullScreenAvatar = true },
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(formattedAvatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFFE0F2FE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                },
                                error = {
                                    UserInitialBadge(
                                        name = name,
                                        textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            )

                            // Zoom Badge Overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB))
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "বড় করে দেখুন",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "🔍 ছবিতে চাপ দিয়ে বড় করুন ও ডাউনলোড করুন",
                            fontSize = 11.sp,
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Rank & Score Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (rank != null) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, Color(0xFFFCD34D))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🏆 র‍্যাংক: #${rank.toBn()}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
                            }

                            if (score != null) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFDCFCE7),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                ) {
                                    Text(
                                        text = "📊 স্কোয়ার: ${score.toBn()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // WhatsApp, Telegram, Direct Call Quick Connect (Only if real phone exists)
                if (!phone.isNullOrBlank()) {
                    SocialContactSection(phone = phone, context = context)
                }

                // Section 1: 👤 ব্যক্তিগত তথ্য (Personal Information)
                ProfileSectionCard(
                    sectionTitle = "ব্যক্তিগত তথ্য",
                    sectionIcon = Icons.Default.Person,
                    sectionColor = Color(0xFF2563EB)
                ) {
                    ProfileInfoRow(
                        icon = Icons.Default.Badge,
                        iconTint = Color(0xFF2563EB),
                        label = "পূর্ণ নাম",
                        value = name
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Phone,
                        iconTint = Color(0xFF16A34A),
                        label = "ফোন নম্বর",
                        value = phone,
                        missingText = "তথ্য দেয়া নেই",
                        isClickable = !phone.isNullOrBlank(),
                        onClick = {
                            if (!phone.isNullOrBlank()) {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = android.net.Uri.parse("tel:${phone.replace("-", "").replace(" ", "")}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Wc,
                        iconTint = Color(0xFF8B5CF6),
                        label = "লিঙ্গ",
                        value = gender
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Cake,
                        iconTint = Color(0xFFEC4899),
                        label = "জন্ম তারিখ",
                        value = dob,
                        missingText = "তথ্য দেয়া নেই"
                    )
                    if (!email.isNullOrBlank()) {
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ProfileInfoRow(
                            icon = Icons.Default.Email,
                            iconTint = Color(0xFF2563EB),
                            label = "ইমেইল",
                            value = email
                        )
                    }
                }

                // Section 2: 👪 অভিভাবকের তথ্য (Guardian Information)
                ProfileSectionCard(
                    sectionTitle = "অভিভাবকের তথ্য",
                    sectionIcon = Icons.Default.FamilyRestroom,
                    sectionColor = Color(0xFF7C3AED)
                ) {
                    ProfileInfoRow(
                        icon = Icons.Default.Person,
                        iconTint = Color(0xFF7C3AED),
                        label = "অভিভাবকের নাম",
                        value = guardianName,
                        missingText = "তথ্য দেয়া নেই"
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Phone,
                        iconTint = Color(0xFF16A34A),
                        label = "অভিভাবকের মোবাইল নম্বর",
                        value = guardianPhone,
                        missingText = "তথ্য দেয়া নেই",
                        isClickable = !guardianPhone.isNullOrBlank(),
                        onClick = {
                            if (!guardianPhone.isNullOrBlank()) {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = android.net.Uri.parse("tel:${guardianPhone.replace("-", "").replace(" ", "")}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }

                // Section 3: 🎓 শিক্ষা প্রতিষ্ঠান (Educational Institution)
                ProfileSectionCard(
                    sectionTitle = "শিক্ষা প্রতিষ্ঠান",
                    sectionIcon = Icons.Default.School,
                    sectionColor = Color(0xFFD97706)
                ) {
                    val classDisplay = if (!className.isNullOrBlank() && !studyGroup.isNullOrBlank()) {
                        "$className | $studyGroup"
                    } else className ?: studyGroup
                    ProfileInfoRow(
                        icon = Icons.Default.Class,
                        iconTint = Color(0xFFD97706),
                        label = "শ্রেণী ও বিভাগ",
                        value = classDisplay
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Schedule,
                        iconTint = Color(0xFF0284C7),
                        label = "শিফট (Shift)",
                        value = shift
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Map,
                        iconTint = Color(0xFF059669),
                        label = "বিভাগ (Division)",
                        value = division
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.LocationOn,
                        iconTint = Color(0xFFE11D48),
                        label = "জেলা (District)",
                        value = district
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.School,
                        iconTint = Color(0xFF2563EB),
                        label = "প্রতিষ্ঠান (স্কুল / কলেজ)",
                        value = college
                    )
                    if (!passingYear.isNullOrBlank()) {
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ProfileInfoRow(
                            icon = Icons.Default.CalendarToday,
                            iconTint = Color(0xFF8B5CF6),
                            label = "শিক্ষাবর্ষ / ব্যাচ",
                            value = "এইচএসসি $passingYear"
                        )
                    }
                }

                // Section 4: 📋 বোর্ড পরীক্ষার তথ্য (Board Exam Information)
                ProfileSectionCard(
                    sectionTitle = "বোর্ড পরীক্ষার তথ্য",
                    sectionIcon = Icons.Default.Assignment,
                    sectionColor = Color(0xFF059669)
                ) {
                    ProfileInfoRow(
                        icon = Icons.Default.AccountBalance,
                        iconTint = Color(0xFF059669),
                        label = "এসএসসি বোর্ড",
                        value = sscBoard,
                        missingText = "তথ্য দেয়া নেই"
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Numbers,
                        iconTint = Color(0xFF6366F1),
                        label = "এসএসসি রোল নম্বর",
                        value = sscRoll,
                        missingText = "তথ্য দেয়া নেই"
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Pin,
                        iconTint = Color(0xFF8B5CF6),
                        label = "বোর্ড রেজিস্ট্রেশন নম্বর",
                        value = regNo,
                        missingText = "তথ্য দেয়া নেই"
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.AccountBalance,
                        iconTint = Color(0xFF0284C7),
                        label = "এইচএসসি বোর্ড",
                        value = hscBoard,
                        missingText = "তথ্য দেয়া নেই"
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ProfileInfoRow(
                        icon = Icons.Default.Numbers,
                        iconTint = Color(0xFFD97706),
                        label = "এইচএসসি রোল নম্বর",
                        value = hscRoll,
                        missingText = "তথ্য দেয়া নেই"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val shareText = "🏆 লিডারবোর্ড স্থান অর্জন করেছেন $name!\n" +
                                (college?.let { "🏛️ কলেজ: $it\n" } ?: "") +
                                (score?.let { "📊 স্কোর: ${it}% " } ?: "") +
                                (rank?.let { "(র‍্যাংক #${it})\n" } ?: "\n") +
                                (phone?.let { "📱 মোবাইল: $it" } ?: "")
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText.trim())
                        }
                        context.startActivity(Intent.createChooser(intent, "প্রোফাইল শেয়ার করুন"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF2563EB))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "শেয়ার",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "শেয়ার করুন",
                        color = Color(0xFF2563EB),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Text(
                        text = "বন্ধ করুন",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionCard(
    sectionTitle: String,
    sectionIcon: ImageVector,
    sectionColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = sectionIcon,
                    contentDescription = null,
                    tint = sectionColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sectionTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

            content()
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String?,
    missingText: String = "তথ্য দেয়া নেই",
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val displayValue = if (!value.isNullOrBlank() && value != "null") value else missingText
    val isMissing = value.isNullOrBlank() || value == "null"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isClickable && onClick != null) {
                    Modifier.clickable { onClick() }
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
            Text(
                text = displayValue,
                fontSize = 14.sp,
                fontWeight = if (isMissing) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isMissing) Color(0xFF94A3B8) else Color(0xFF0F172A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isClickable) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
