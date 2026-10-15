package com.example.liveclass

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.LocalPictureInPictureMode
import com.example.auth.SessionManager
import com.example.liveclass.poll.*
import com.example.util.PipHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "LiveClassPage"

sealed class LiveClassState {
    data object Loading : LiveClassState()
    data class Preview(val viewerCount: Int) : LiveClassState()
    data object Playing : LiveClassState()
    data class Error(val message: String) : LiveClassState()
}

data class LiveChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val message: String,
    val timestamp: String = "এখন",
    val isTeacher: Boolean = false,
    val role: String = "student"
)

data class LiveResolutionOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
)

val LIVE_RESOLUTIONS = listOf(
    LiveResolutionOption("auto", "Auto (স্বয়ংক্রিয়)", "নেটওয়ার্ক স্পিড অনুযায়ী এডাপ্টিভ কোয়ালিটি", "AUTO"),
    LiveResolutionOption("720p", "720p HD", "সর্বোচ্চ স্পষ্টতা (বোর্ডের লেখার জন্য সেরা)", "HD"),
    LiveResolutionOption("480p", "480p Standard", "ব্যালেন্সড ডেটা ও স্পষ্ট ভিডিও", "SD"),
    LiveResolutionOption("360p", "360p Data Saver", "কম ইন্টারনেটে নিরবচ্ছিন্ন স্ট্রিমিং", "360P"),
    LiveResolutionOption("240p", "240p Ultra Saver", "দুর্বল নেটওয়ার্কের জন্য বিশেষ মোড", "LOW")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveClassPage(
    classId: String,
    lessonId: String,
    lessonTitle: String = "লাইভ ক্লাস",
    subjectName: String = "বিষয়",
    initialCandidateUrls: List<String> = emptyList(),
    sessionManager: SessionManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val service = remember { LiveClassService() }
    val wsManager = remember { LiveClassWebSocketManager() }

    val isPipMode = LocalPictureInPictureMode.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var selectedResolution by remember { mutableStateOf("auto") }
    var showResolutionDialog by remember { mutableStateOf(false) }

    // Controls visibility & auto-hide timer so nothing blocks the video canvas
    var showPlayerControls by remember { mutableStateOf(true) }
    var showLandscapeChatDrawer by remember { mutableStateOf(false) }

    var uiState by remember { mutableStateOf<LiveClassState>(LiveClassState.Loading) }
    var studentName by remember {
        mutableStateOf(sessionManager.getUserFullName() ?: "শিক্ষার্থী")
    }
    var selectedRole by remember { mutableStateOf("teacher") } // Matches HAR default target: ["teacher"]

    var lectureSlideUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLiveOnGoing by remember { mutableStateOf(true) }

    val masterUrl by wsManager.masterUrl.collectAsState()
    val candidateUrls by wsManager.candidateUrls.collectAsState()
    val viewerCount by wsManager.viewerCount.collectAsState()
    val wsError by wsManager.error.collectAsState()
    val isConnected by wsManager.isConnected.collectAsState()
    val roomTeacherName by wsManager.teacherName.collectAsState()
    val serverRoomTitle by wsManager.roomTitle.collectAsState()
    val isChatEnabled by wsManager.isChatEnabled.collectAsState()
    val isHandRaised by wsManager.isHandRaised.collectAsState()

    val effectiveLessonTitle = serverRoomTitle.ifBlank { lessonTitle }

    var chatMessageInput by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<LiveChatMessage>() }
    val chatListState = rememberLazyListState()

    // Quick comment chips for instant interaction during live class
    val quickReplyChips = remember {
        listOf("হ্যালো স্যার 👋", "জি ভাইয়া বুঝতে পেরেছি ✅", "আরেকবার একটু বুঝিয়ে দিন 🙏", "ক্লিয়ার দেখা যাচ্ছে ✨")
    }

    // 100ms Real Live Poll / Quiz State
    val activePoll by wsManager.activePoll.collectAsState()
    val previousPolls by wsManager.previousPolls.collectAsState()
    var selectedPollIdForQuestion by remember { mutableStateOf<String?>(null) }
    var selectedPollIdForResult by remember { mutableStateOf<String?>(null) }
    var showPreviousPollsDialog by remember { mutableStateOf(false) }

    val currentQuestionDialogPoll = remember(selectedPollIdForQuestion, activePoll, previousPolls) {
        val targetId = selectedPollIdForQuestion ?: return@remember null
        previousPolls.find { it.pollId == targetId }
            ?: activePoll?.takeIf { it.pollId == targetId }
    }

    val currentResultDialogPoll = remember(selectedPollIdForResult, activePoll, previousPolls) {
        val targetId = selectedPollIdForResult ?: return@remember null
        previousPolls.find { it.pollId == targetId }
            ?: activePoll?.takeIf { it.pollId == targetId }
    }

    // Keep screen on while watching Live Class and manage immersive fullscreen in landscape
    DisposableEffect(isLandscape, uiState) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (isLandscape && uiState is LiveClassState.Playing) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-hide top & bottom edge controls after 3.5 seconds so the class video is 100% unobstructed
    LaunchedEffect(showPlayerControls, uiState) {
        if (showPlayerControls && uiState is LiveClassState.Playing) {
            delay(3500)
            showPlayerControls = false
        }
    }

    // Helper function to toggle Mobile Screen Rotation (Portrait <-> Sensor Landscape)
    fun toggleScreenOrientation() {
        val act = activity ?: return
        if (isLandscape) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            showLandscapeChatDrawer = false
            // Unlock to sensor after returning to portrait so normal phone rotation works too
            coroutineScope.launch {
                delay(1500)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Auto-open active poll question dialog when teacher starts a new live poll
    LaunchedEffect(activePoll?.pollId, activePoll?.state) {
        val poll = activePoll
        if (poll != null && poll.isLive && poll.userResponse == null && selectedPollIdForQuestion == null) {
            selectedPollIdForQuestion = poll.pollId
        }
    }

    fun startLiveClassConnection() {
        uiState = LiveClassState.Loading
        coroutineScope.launch {
            val userToken = sessionManager.getAccessToken()
            if (userToken.isNullOrBlank()) {
                uiState = LiveClassState.Error("ব্যবহারকারীর অথেন্টিকেশন পাওয়া যায়নি। অনুগ্রহ করে লগইন করুন।")
                return@launch
            }

            val academicDeferred = async { service.getAcademicLiveClassInfo(classId, lessonId, userToken) }
            val roomDeferred = async { service.getHmsRoomId(classId, lessonId, userToken) }

            val academicInfo = academicDeferred.await().getOrNull()
            val roomResult = roomDeferred.await()
            val roomId = roomResult.getOrNull()

            val discoveredUrls = mutableListOf<String>()
            academicInfo?.playbackUrl?.takeIf { it.isNotBlank() }?.let { discoveredUrls.add(it) }
            discoveredUrls.addAll(academicInfo?.candidateUrls ?: emptyList())
            discoveredUrls.addAll(initialCandidateUrls)
            val cleanDiscovered = discoveredUrls.filter { it.isNotBlank() && it != "null" }.distinct()

            academicInfo?.slideUrls?.let {
                lectureSlideUrls = it
            }
            if (academicInfo != null) {
                isLiveOnGoing = academicInfo.isOnGoing
            }

            wsManager.addCandidateUrls(cleanDiscovered)

            var directPlaybackSet = false
            if (!academicInfo?.playbackUrl.isNullOrBlank()) {
                wsManager.setMasterUrl(academicInfo!!.playbackUrl!!)
                uiState = LiveClassState.Playing // Enter directly into seamless playing mode
                directPlaybackSet = true
            } else if (cleanDiscovered.isNotEmpty()) {
                wsManager.setMasterUrl(cleanDiscovered.first())
                uiState = LiveClassState.Playing
                directPlaybackSet = true
            }

            if (!roomId.isNullOrBlank()) {
                val tokenResult = service.getHmsToken(roomId, userToken)
                val hmsToken = tokenResult.getOrNull()
                if (!hmsToken.isNullOrBlank()) {
                    wsManager.connect(hmsToken, studentName)
                    return@launch
                }
            }

            if (directPlaybackSet) {
                return@launch
            }

            val fallbackUrl = academicInfo?.playbackUrl ?: cleanDiscovered.firstOrNull()
            if (!fallbackUrl.isNullOrBlank()) {
                wsManager.setMasterUrl(fallbackUrl)
                uiState = LiveClassState.Playing
                return@launch
            }

            val errMsg = roomResult.exceptionOrNull()?.message
                ?: "লাইভ ক্লাসের ব্রডকাস্ট সংযোগ পাওয়া যায়নি। ক্লাস শুরু হতে বিলম্ব হলে অনুগ্রহ করে একটু অপেক্ষা করে রিফ্রেশ করুন।"
            uiState = LiveClassState.Error(errMsg)
        }
    }

    LaunchedEffect(classId, lessonId) {
        startLiveClassConnection()
    }

    LaunchedEffect(masterUrl, wsError) {
        if (uiState is LiveClassState.Loading) {
            if (!masterUrl.isNullOrBlank()) {
                uiState = LiveClassState.Playing
            } else if (!wsError.isNullOrBlank()) {
                val fallback = candidateUrls.firstOrNull { it.isNotBlank() }
                if (!fallback.isNullOrBlank()) {
                    wsManager.setMasterUrl(fallback)
                    uiState = LiveClassState.Playing
                } else {
                    uiState = LiveClassState.Error("কানেকশন ত্রুটি: $wsError")
                }
            }
        }
    }

    val incomingMsg by wsManager.incomingChatMessages.collectAsState()
    LaunchedEffect(incomingMsg) {
        incomingMsg?.let { msg ->
            if (chatMessages.none { it.id == msg.id }) {
                chatMessages.add(msg)
                coroutineScope.launch {
                    if (chatMessages.isNotEmpty()) {
                        chatListState.animateScrollToItem(chatMessages.size - 1)
                    }
                }
            }
        }
    }

    fun sendUserChat(messageText: String) {
        val cleanText = messageText.trim()
        if (cleanText.isBlank() || !isChatEnabled) return
        val myName = studentName.ifBlank { "আপনি" }
        val formattedTime = try {
            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        } catch (_: Exception) {
            "এখন"
        }

        chatMessages.add(
            LiveChatMessage(
                senderName = myName,
                message = cleanText,
                timestamp = formattedTime,
                isTeacher = false,
                role = selectedRole
            )
        )
        wsManager.sendChatMessage(
            text = cleanText,
            studentName = myName,
            role = selectedRole,
            targetRole = selectedRole
        )
        chatMessageInput = ""
        keyboardController?.hide()
        coroutineScope.launch {
            if (chatMessages.isNotEmpty()) {
                chatListState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            wsManager.disconnect()
        }
    }

    BackHandler {
        if (isLandscape) {
            toggleScreenOrientation()
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            wsManager.disconnect()
            onBack()
        }
    }

    // Picture-in-Picture (PiP) Mode: Pure video canvas
    if (isPipMode) {
        val activePlaybackUrl = masterUrl ?: candidateUrls.firstOrNull { it.isNotBlank() }
        if (!activePlaybackUrl.isNullOrBlank()) {
            LivePlayer(
                masterUrl = activePlaybackUrl,
                candidateUrls = candidateUrls,
                selectedResolution = selectedResolution,
                isPipActive = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
            }
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFF090D16),
        contentWindowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLandscape) PaddingValues(0.dp) else paddingValues)
        ) {
            when (val state = uiState) {
                // ==========================================
                // 1. LOADING STATE
                // ==========================================
                is LiveClassState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                strokeWidth = 3.5.dp,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.size(64.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "লাইভ ক্লাসে সংযোগ স্থাপন হচ্ছে...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = effectiveLessonTitle,
                            fontSize = 13.5.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }

                // ==========================================
                // 2. PREVIEW STATE
                // ==========================================
                is LiveClassState.Preview -> {
                    LaunchedEffect(Unit) {
                        uiState = LiveClassState.Playing
                    }
                }

                // ==========================================
                // 3. PLAYING STATE (PORTRAIT & FULL LANDSCAPE)
                // ==========================================
                is LiveClassState.Playing -> {
                    val activePlaybackUrl = masterUrl ?: candidateUrls.firstOrNull { it.isNotBlank() }

                    if (isLandscape) {
                        // =====================================================
                        // FULLSCREEN LANDSCAPE MODE (UNOBSTRUCTED + SIDE DRAWER)
                        // =====================================================
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            // Video Canvas Area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color.Black)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showPlayerControls = !showPlayerControls
                                    }
                            ) {
                                if (!activePlaybackUrl.isNullOrBlank()) {
                                    LivePlayer(
                                        masterUrl = activePlaybackUrl,
                                        candidateUrls = candidateUrls,
                                        selectedResolution = selectedResolution,
                                        isPipActive = false,
                                        modifier = Modifier.fillMaxSize(),
                                        onBack = { toggleScreenOrientation() }
                                    )
                                }

                                // Top Edge Bar (Only visible when tapped; auto-hides in 3.5s)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showPlayerControls,
                                    enter = fadeIn() + slideInVertically { -it },
                                    exit = fadeOut() + slideOutVertically { -it },
                                    modifier = Modifier.align(Alignment.TopCenter)
                                ) {
                                    VideoTopControlBar(
                                        isLiveOnGoing = isLiveOnGoing,
                                        viewerCount = viewerCount,
                                        lessonTitle = effectiveLessonTitle,
                                        isLandscape = true,
                                        selectedResolution = selectedResolution,
                                        hasSlides = lectureSlideUrls.isNotEmpty(),
                                        onBackClick = { toggleScreenOrientation() },
                                        onOpenResolution = { showResolutionDialog = true },
                                        onEnterPip = { PipHelper.enterPipMode(activity, isPlaying = true) },
                                        onOpenSlide = {
                                            lectureSlideUrls.firstOrNull()?.let { url ->
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    )
                                }

                                // Bottom Edge Bar (Only visible when tapped; auto-hides in 3.5s)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showPlayerControls,
                                    enter = fadeIn() + slideInVertically { it },
                                    exit = fadeOut() + slideOutVertically { it },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    VideoBottomEdgeBar(
                                        isLandscape = true,
                                        isHandRaised = isHandRaised,
                                        hasActivePoll = activePoll?.isLive == true,
                                        showChatDrawer = showLandscapeChatDrawer,
                                        onToggleHandRaise = { wsManager.toggleHandRaise(studentName) },
                                        onOpenPolls = {
                                            wsManager.requestPollList("started")
                                            wsManager.requestPollList("stopped")
                                            val currActive = activePoll
                                            if (currActive != null && currActive.isLive) {
                                                wsManager.loadFullPollDetails(currActive.pollId)
                                                selectedPollIdForQuestion = currActive.pollId
                                            } else {
                                                showPreviousPollsDialog = true
                                            }
                                        },
                                        onToggleChatDrawer = { showLandscapeChatDrawer = !showLandscapeChatDrawer },
                                        onToggleOrientation = { toggleScreenOrientation() }
                                    )
                                }

                                // Non-intrusive corner Poll notification pill
                                val activePollItem = activePoll
                                if (activePollItem != null && activePollItem.isLive && selectedPollIdForQuestion == null) {
                                    LivePollFloatingNotification(
                                        activePoll = activePollItem,
                                        onOpenPoll = {
                                            wsManager.loadFullPollDetails(it.pollId)
                                            selectedPollIdForQuestion = it.pollId
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 56.dp, end = 16.dp)
                                    )
                                }
                            }

                            // Collapsible Right-Side Live Chat Drawer in Landscape Mode
                            AnimatedVisibility(
                                visible = showLandscapeChatDrawer,
                                enter = slideInHorizontally { it } + expandHorizontally(),
                                exit = slideOutHorizontally { it } + shrinkHorizontally()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .width(330.dp)
                                        .fillMaxHeight(),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                                ) {
                                    LiveChatSection(
                                        lessonTitle = effectiveLessonTitle,
                                        subjectName = subjectName,
                                        teacherName = roomTeacherName,
                                        isCompactLandscape = true,
                                        isConnected = isConnected,
                                        isChatEnabled = isChatEnabled,
                                        isHandRaised = isHandRaised,
                                        activePoll = activePoll,
                                        selectedRole = selectedRole,
                                        onSelectRole = { selectedRole = it },
                                        chatMessages = chatMessages,
                                        chatListState = chatListState,
                                        chatMessageInput = chatMessageInput,
                                        onChatInputChanged = { chatMessageInput = it },
                                        quickReplyChips = quickReplyChips,
                                        onSendMessage = { sendUserChat(it) },
                                        onToggleHandRaise = { wsManager.toggleHandRaise(studentName) },
                                        onOpenPolls = {
                                            wsManager.requestPollList("started")
                                            wsManager.requestPollList("stopped")
                                            val currActive = activePoll
                                            if (currActive != null && currActive.isLive) {
                                                wsManager.loadFullPollDetails(currActive.pollId)
                                                selectedPollIdForQuestion = currActive.pollId
                                            } else {
                                                showPreviousPollsDialog = true
                                            }
                                        },
                                        onCloseDrawer = { showLandscapeChatDrawer = false }
                                    )
                                }
                            }
                        }
                    } else {
                        // =====================================================
                        // PORTRAIT MODE (16:9 UNOBSTRUCTED VIDEO + INTERACTION)
                        // =====================================================
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 16:9 Video Box — Clean center, thin auto-hiding top/bottom edge bars
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(Color.Black)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showPlayerControls = !showPlayerControls
                                    }
                            ) {
                                if (!activePlaybackUrl.isNullOrBlank()) {
                                    LivePlayer(
                                        masterUrl = activePlaybackUrl,
                                        candidateUrls = candidateUrls,
                                        selectedResolution = selectedResolution,
                                        isPipActive = false,
                                        modifier = Modifier.fillMaxSize(),
                                        onBack = {
                                            wsManager.disconnect()
                                            onBack()
                                        }
                                    )
                                }

                                // Top Edge Bar (Auto-hides after 3.5s)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showPlayerControls,
                                    enter = fadeIn() + slideInVertically { -it },
                                    exit = fadeOut() + slideOutVertically { -it },
                                    modifier = Modifier.align(Alignment.TopCenter)
                                ) {
                                    VideoTopControlBar(
                                        isLiveOnGoing = isLiveOnGoing,
                                        viewerCount = viewerCount,
                                        lessonTitle = "",
                                        isLandscape = false,
                                        selectedResolution = selectedResolution,
                                        hasSlides = lectureSlideUrls.isNotEmpty(),
                                        onBackClick = {
                                            wsManager.disconnect()
                                            onBack()
                                        },
                                        onOpenResolution = { showResolutionDialog = true },
                                        onEnterPip = { PipHelper.enterPipMode(activity, isPlaying = true) },
                                        onOpenSlide = {
                                            lectureSlideUrls.firstOrNull()?.let { url ->
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    )
                                }

                                // Bottom Edge Bar with Fullscreen/Rotate toggle (Auto-hides after 3.5s)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showPlayerControls,
                                    enter = fadeIn() + slideInVertically { it },
                                    exit = fadeOut() + slideOutVertically { it },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    VideoBottomEdgeBar(
                                        isLandscape = false,
                                        isHandRaised = isHandRaised,
                                        hasActivePoll = activePoll?.isLive == true,
                                        showChatDrawer = false,
                                        onToggleHandRaise = { wsManager.toggleHandRaise(studentName) },
                                        onOpenPolls = {
                                            wsManager.requestPollList("started")
                                            wsManager.requestPollList("stopped")
                                            val currActive = activePoll
                                            if (currActive != null && currActive.isLive) {
                                                wsManager.loadFullPollDetails(currActive.pollId)
                                                selectedPollIdForQuestion = currActive.pollId
                                            } else {
                                                showPreviousPollsDialog = true
                                            }
                                        },
                                        onToggleChatDrawer = {},
                                        onToggleOrientation = { toggleScreenOrientation() }
                                    )
                                }

                                // Non-intrusive Poll notification pill
                                val activePollItem = activePoll
                                if (activePollItem != null && activePollItem.isLive && selectedPollIdForQuestion == null) {
                                    LivePollFloatingNotification(
                                        activePoll = activePollItem,
                                        onOpenPoll = {
                                            wsManager.loadFullPollDetails(it.pollId)
                                            selectedPollIdForQuestion = it.pollId
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(start = 10.dp, bottom = 42.dp)
                                    )
                                }
                            }

                            // Bottom Live Class Info, Action Bar & Real-time Chat
                            LiveChatSection(
                                lessonTitle = effectiveLessonTitle,
                                subjectName = subjectName,
                                teacherName = roomTeacherName,
                                isCompactLandscape = false,
                                isConnected = isConnected,
                                isChatEnabled = isChatEnabled,
                                isHandRaised = isHandRaised,
                                activePoll = activePoll,
                                selectedRole = selectedRole,
                                onSelectRole = { selectedRole = it },
                                chatMessages = chatMessages,
                                chatListState = chatListState,
                                chatMessageInput = chatMessageInput,
                                onChatInputChanged = { chatMessageInput = it },
                                quickReplyChips = quickReplyChips,
                                onSendMessage = { sendUserChat(it) },
                                onToggleHandRaise = { wsManager.toggleHandRaise(studentName) },
                                onOpenPolls = {
                                    wsManager.requestPollList("started")
                                    wsManager.requestPollList("stopped")
                                    val currActive = activePoll
                                    if (currActive != null && currActive.isLive) {
                                        wsManager.loadFullPollDetails(currActive.pollId)
                                        selectedPollIdForQuestion = currActive.pollId
                                    } else {
                                        showPreviousPollsDialog = true
                                    }
                                },
                                onCloseDrawer = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                // ==========================================
                // 4. ERROR STATE
                // ==========================================
                is LiveClassState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "লাইভ ক্লাসে কানেক্ট করা সম্ভব হয়নি",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = state.message,
                            fontSize = 13.5.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    wsManager.disconnect()
                                    onBack()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text("ফিরে যান")
                            }

                            Button(
                                onClick = { startLiveClassConnection() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("পুনরায় চেষ্টা করুন", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Resolution Switcher Dialog
    if (showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { showResolutionDialog = false },
            containerColor = Color(0xFF131B2E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "ভিডিও রেজুলেশন নির্বাচন করুন",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LIVE_RESOLUTIONS.forEach { option ->
                        val isSelected = selectedResolution.equals(option.id, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.22f) else Color(0xFF0B1120),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedResolution = option.id
                                    showResolutionDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = option.badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color(0xFF090D16) else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF38BDF8) else Color.White
                                    )
                                    Text(
                                        text = option.subtitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResolutionDialog = false }) {
                    Text("বন্ধ করুন", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // 1. Real Poll Question Dialog
    currentQuestionDialogPoll?.let { poll ->
        LivePollQuestionDialog(
            poll = poll,
            teacherName = poll.teacherName.ifBlank { roomTeacherName },
            onDismiss = { selectedPollIdForQuestion = null },
            onSubmitAnswer = { pId, qIdx, optIdx, dur ->
                wsManager.submitPollAnswer(pId, qIdx, optIdx, dur)
            },
            onViewResults = { pollItem ->
                wsManager.fetchPollLeaderboard(pollItem.pollId)
                wsManager.fetchUserPollResponse(pollItem.pollId)
                selectedPollIdForQuestion = null
                selectedPollIdForResult = pollItem.pollId
            }
        )
    }

    // 2. Real Poll Participation Summary & Leaderboard Dialog
    currentResultDialogPoll?.let { poll ->
        LivePollResultDialog(
            poll = poll,
            currentPeerId = wsManager.getCurrentPeerId(),
            currentUserId = wsManager.getCurrentUserId(),
            currentUserName = studentName,
            onDismiss = { selectedPollIdForResult = null }
        )
    }

    // 3. Real Previous Polls/Quizzes Dialog
    if (showPreviousPollsDialog) {
        PreviousPollsDialog(
            polls = previousPolls,
            onDismiss = { showPreviousPollsDialog = false },
            onSelectPoll = { selectedPoll ->
                showPreviousPollsDialog = false
                wsManager.loadFullPollDetails(selectedPoll.pollId)
                selectedPollIdForQuestion = selectedPoll.pollId
            }
        )
    }
}

/**
 * Sleek Top Edge Control Bar over the video player.
 * Never touches the middle of the video screen.
 */
@Composable
private fun VideoTopControlBar(
    isLiveOnGoing: Boolean,
    viewerCount: Int,
    lessonTitle: String,
    isLandscape: Boolean,
    selectedResolution: String,
    hasSlides: Boolean,
    onBackClick: () -> Unit,
    onOpenResolution: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenSlide: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Pulsing LIVE Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isLiveOnGoing) Color(0xFFDC2626) else Color(0xFF059669)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    if (isLiveOnGoing) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = liveDotAlpha))
                        )
                    }
                    Text(
                        text = if (isLiveOnGoing) "LIVE" else "RECORDED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Viewer Count Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${viewerCount.coerceAtLeast(1)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (isLandscape && lessonTitle.isNotBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = lessonTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Slide PDF Button if available
            if (hasSlides) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickable { onOpenSlide() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Slides",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "স্লাইড",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Resolution Switcher Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                modifier = Modifier.clickable { onOpenResolution() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Quality",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (selectedResolution.equals("auto", true)) "Auto" else selectedResolution.uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Picture-in-Picture Button
            IconButton(
                onClick = onEnterPip,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "PiP",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Sleek Bottom Edge Bar on the video player for quick Fullscreen/Rotation toggle,
 * Hand Raise, Poll access, and Landscape Chat Drawer toggle.
 */
@Composable
private fun VideoBottomEdgeBar(
    isLandscape: Boolean,
    isHandRaised: Boolean,
    hasActivePoll: Boolean,
    showChatDrawer: Boolean,
    onToggleHandRaise: () -> Unit,
    onOpenPolls: () -> Unit,
    onToggleChatDrawer: () -> Unit,
    onToggleOrientation: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (isLandscape) {
                // Hand Raise button inside landscape bar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isHandRaised) Color(0xFFF59E0B) else Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(0.8.dp, if (isHandRaised) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onToggleHandRaise() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FrontHand,
                            contentDescription = "Raise Hand",
                            tint = if (isHandRaised) Color(0xFF090D16) else Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isHandRaised) "হাত নামান" else "হাত তুলুন",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHandRaised) Color(0xFF090D16) else Color.White
                        )
                    }
                }

                // Poll/Quiz button inside landscape bar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (hasActivePoll) Color(0xFFDC2626) else Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(0.8.dp, if (hasActivePoll) Color(0xFFEF4444) else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onOpenPolls() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = "Poll",
                            tint = if (hasActivePoll) Color.White else Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (hasActivePoll) "LIVE পোল" else "পোল/কুইজ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Landscape Chat Drawer Toggle Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (showChatDrawer) Color(0xFF0284C7) else Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(0.8.dp, if (showChatDrawer) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onToggleChatDrawer() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Live Chat",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (showChatDrawer) "চ্যাট লুকান" else "লাইভ চ্যাট",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Fullscreen / Rotate Screen Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.clickable { onToggleOrientation() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Rotate / Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isLandscape) "ছোট করুন" else "ফুলস্ক্রিন",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Complete Interactive Section: Class Info, Hand Raise, Polls, Quick Chips & Real-time Chat.
 */
@Composable
private fun LiveChatSection(
    lessonTitle: String,
    subjectName: String,
    teacherName: String,
    isCompactLandscape: Boolean,
    isConnected: Boolean,
    isChatEnabled: Boolean,
    isHandRaised: Boolean,
    activePoll: LivePollItem?,
    selectedRole: String,
    onSelectRole: (String) -> Unit,
    chatMessages: List<LiveChatMessage>,
    chatListState: androidx.compose.foundation.lazy.LazyListState,
    chatMessageInput: String,
    onChatInputChanged: (String) -> Unit,
    quickReplyChips: List<String>,
    onSendMessage: (String) -> Unit,
    onToggleHandRaise: () -> Unit,
    onOpenPolls: () -> Unit,
    onCloseDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF090D16))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (!isCompactLandscape) {
            // Class Header Card in Portrait Mode
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF111827),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lessonTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = subjectName,
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (teacherName.isNotBlank()) {
                                    Text(
                                        text = "• $teacherName",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Action Buttons: Real Hand Raise & Real Poll/Quiz
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Hand Raise Button (_handraise protocol)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isHandRaised) Color(0xFFF59E0B) else Color(0xFF1E293B),
                                border = BorderStroke(
                                    1.dp,
                                    if (isHandRaised) Color(0xFFFBBF24) else Color(0xFF334155)
                                ),
                                modifier = Modifier.clickable { onToggleHandRaise() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FrontHand,
                                        contentDescription = "Raise Hand",
                                        tint = if (isHandRaised) Color(0xFF090D16) else Color(0xFFFBBF24),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isHandRaised) "হাত নামান" else "হাত তুলুন",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHandRaised) Color(0xFF090D16) else Color.White
                                    )
                                }
                            }

                            // Poll / Quiz Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activePoll?.isLive == true) Color(0xFFDC2626) else Color(0xFF1E293B),
                                border = BorderStroke(
                                    1.dp,
                                    if (activePoll?.isLive == true) Color(0xFFEF4444) else Color(0xFF334155)
                                ),
                                modifier = Modifier.clickable { onOpenPolls() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Quiz,
                                        contentDescription = "Poll/Quiz",
                                        tint = if (activePoll?.isLive == true) Color.White else Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (activePoll?.isLive == true) "🔴 পোল চলছে" else "পোল/কুইজ",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Chat Section Header & Role Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "লাইভ কমেন্ট ও প্রশ্নোত্তর",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            // Teacher vs All target selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val targets = listOf(
                    "teacher" to "শিক্ষককে",
                    "all" to "সবাইকে"
                )
                targets.forEach { (key, label) ->
                    val isSelected = selectedRole == key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF0284C7) else Color(0xFF111827),
                        border = BorderStroke(
                            0.8.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                        ),
                        modifier = Modifier.clickable { onSelectRole(key) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (onCloseDrawer != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Chat",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Chat Messages List
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0D1322),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            if (chatMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ক্লাস চলাকালীন যেকোনো প্রশ্ন বা মতামত এখানে লিখুন",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages, key = { it.id }) { item ->
                        val isTeacherMsg = item.isTeacher
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isTeacherMsg) Color(0xFF1E293B) else Color(0xFF131C31),
                            border = BorderStroke(
                                0.8.dp,
                                if (isTeacherMsg) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isTeacherMsg) Color(0xFF0284C7) else Color(0xFF059669)
                                    ) {
                                        Text(
                                            text = if (isTeacherMsg) "শিক্ষক" else "শিক্ষার্থী",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }

                                    Text(
                                        text = item.senderName,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTeacherMsg) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Text(
                                        text = "• ${item.timestamp}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = item.message,
                                    fontSize = 13.sp,
                                    color = Color(0xFFF1F5F9),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Comment Chips Row (Only when chat is enabled)
        if (isChatEnabled && !isCompactLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickReplyChips.take(3).forEach { chipText ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(0.8.dp, Color(0xFF1E293B)),
                        modifier = Modifier.clickable { onSendMessage(chipText) }
                    ) {
                        Text(
                            text = chipText,
                            fontSize = 10.5.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Chat Input Row or Teacher Disabled Banner
        if (!isChatEnabled) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "শিক্ষক এই মুহূর্তে লাইভ চ্যাট বন্ধ রেখেছেন",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatMessageInput,
                    onValueChange = onChatInputChanged,
                    placeholder = {
                        Text(
                            text = if (selectedRole == "teacher") "শিক্ষককে প্রশ্ন করুন..." else "কমেন্ট লিখুন...",
                            fontSize = 12.5.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF111827),
                        unfocusedContainerColor = Color(0xFF111827)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        onSendMessage(chatMessageInput)
                    }),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { onSendMessage(chatMessageInput) },
                    enabled = chatMessageInput.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (chatMessageInput.isNotBlank()) Color(0xFF0284C7) else Color(0xFF1E293B)
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
