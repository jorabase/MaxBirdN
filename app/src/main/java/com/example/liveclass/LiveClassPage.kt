package com.example.liveclass

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.LocalPictureInPictureMode
import com.example.auth.SessionManager
import com.example.liveclass.poll.*
import com.example.utils.PipHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val TAG = "LiveClassPage"

sealed class LiveClassState {
    data object Loading : LiveClassState()
    data class Preview(val viewerCount: Int) : LiveClassState()
    data object Playing : LiveClassState()
    data class Error(val message: String) : LiveClassState()
}

enum class ChatDeliveryStatus {
    SENDING,
    SENT,
    FAILED
}

data class LiveChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val message: String,
    val timestamp: String = "এখন",
    val isTeacher: Boolean = false,
    val role: String = "student",
    val status: ChatDeliveryStatus = ChatDeliveryStatus.SENT,
    val isPinned: Boolean = false
)

data class LiveResolutionOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    val badge: String = ""
)

val DEFAULT_AUTO_RESOLUTION = listOf(
    LiveResolutionOption(
        id = "auto",
        title = "Auto (স্বয়ংক্রিয়)",
        subtitle = "নেটওয়ার্ক স্পিড অনুযায়ী স্বয়ংক্রিয় রেজুলেশন",
        width = 0,
        height = Int.MAX_VALUE,
        bitrate = 0,
        badge = "AUTO"
    )
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
    var availableResolutions by remember { mutableStateOf(DEFAULT_AUTO_RESOLUTION) }
    var currentPlayingResolution by remember { mutableStateOf("") }
    var showResolutionDialog by remember { mutableStateOf(false) }

    // If the stream's available resolutions change and the previously selected resolution is not in the stream, reset to "auto"
    LaunchedEffect(availableResolutions) {
        if (availableResolutions.size > 1 &&
            !selectedResolution.equals("auto", ignoreCase = true) &&
            availableResolutions.none { it.id.equals(selectedResolution, ignoreCase = true) }
        ) {
            selectedResolution = "auto"
        }
    }

    // Click on video toggles player overlay controls: click once -> show, click again -> hide!
    var showPlayerControls by remember { mutableStateOf(true) }
    var lastUserInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showLandscapeChat by remember { mutableStateOf(false) }

    // Auto-hides video controls after 4 seconds of inactivity
    LaunchedEffect(showPlayerControls, lastUserInteractionTime) {
        if (showPlayerControls) {
            kotlinx.coroutines.delay(4000L)
            showPlayerControls = false
        }
    }

    var uiState by remember { mutableStateOf<LiveClassState>(LiveClassState.Loading) }
    var studentName by remember {
        mutableStateOf(sessionManager.getLiveCustomStudentName() ?: sessionManager.getUserFullName() ?: "শিক্ষার্থী")
    }
    var showNameChangeDialog by remember { mutableStateOf(false) }
    var showJoinNameDialog by rememberSaveable(classId, lessonId) {
        mutableStateOf(sessionManager.getLiveCustomStudentName().isNullOrBlank())
    }

    // Live Delay state & Sync Trigger
    var liveDelaySeconds by remember { mutableIntStateOf(0) }
    var syncToLiveTrigger by remember { mutableLongStateOf(0L) }

    // Video aspect ratio, zoom scale, and pan
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var videoScale by remember { mutableFloatStateOf(1.0f) }
    var videoPan by remember { mutableStateOf(Offset.Zero) }

    var selectedRole by remember { mutableStateOf("teacher") } // "teacher", "student", "all"

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

    // Computed resolution pill label (shows real playing resolution when Auto is selected!)
    val resolutionBadgeText = remember(selectedResolution, currentPlayingResolution) {
        if (selectedResolution.equals("auto", ignoreCase = true)) {
            if (currentPlayingResolution.isNotBlank()) "Auto • $currentPlayingResolution" else "Auto"
        } else {
            selectedResolution
        }
    }

    var chatMessageInput by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<LiveChatMessage>() }
    val chatListState = rememberLazyListState()

    val quickReplyChips = remember {
        listOf(
            "হ্যালো স্যার 👋",
            "জি বুঝতে পেরেছি ✅",
            "আরেকবার বুঝিয়ে দিন 🙏",
            "ক্লিয়ার দেখা যাচ্ছে ✨",
            "সাউন্ড ও ভিডিও ঠিক আছে 👍"
        )
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

    // Keep screen on while playing Live Class
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

    fun toggleScreenOrientation() {
        val act = activity ?: return
        if (isLandscape) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            coroutineScope.launch {
                kotlinx.coroutines.delay(1200)
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

            // 1. Prioritize 100ms WebSocket Live Room Connection so no fallback/animated video plays before live stream!
            if (!roomId.isNullOrBlank()) {
                val tokenResult = service.getHmsToken(roomId, userToken)
                val hmsToken = tokenResult.getOrNull()
                if (!hmsToken.isNullOrBlank()) {
                    wsManager.connect(hmsToken, studentName)
                    if (academicInfo?.isOnGoing == false && !academicInfo.playbackUrl.isNullOrBlank()) {
                        wsManager.setMasterUrl(academicInfo.playbackUrl)
                        uiState = LiveClassState.Playing
                    } else {
                        // Safety fallback if WebSocket does not deliver a stream URL within 5 seconds
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(5000L)
                            if (wsManager.masterUrl.value.isNullOrBlank()) {
                                val fallback = academicInfo?.playbackUrl?.takeIf { it.isNotBlank() }
                                    ?: cleanDiscovered.firstOrNull()
                                if (!fallback.isNullOrBlank()) {
                                    wsManager.setMasterUrl(fallback)
                                    uiState = LiveClassState.Playing
                                }
                            }
                        }
                    }
                    return@launch
                }
            }

            // 2. If 100ms room/token is not active, use direct live/recorded stream URL if available
            val fallbackUrl = academicInfo?.playbackUrl?.takeIf { it.isNotBlank() }
                ?: cleanDiscovered.firstOrNull()
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
        if (!masterUrl.isNullOrBlank()) {
            uiState = LiveClassState.Playing
        } else if (uiState is LiveClassState.Loading && !wsError.isNullOrBlank()) {
            val fallback = candidateUrls.firstOrNull { it.isNotBlank() }
            if (!fallback.isNullOrBlank()) {
                wsManager.setMasterUrl(fallback)
                uiState = LiveClassState.Playing
            } else {
                uiState = LiveClassState.Error("কানেকশন ত্রুটি: $wsError")
            }
        }
    }

    val incomingMsg by wsManager.incomingChatMessages.collectAsState()
    LaunchedEffect(incomingMsg) {
        incomingMsg?.let { msg ->
            if (chatMessages.none { it.id == msg.id || (it.message == msg.message && it.senderName == msg.senderName) }) {
                chatMessages.add(msg)
                coroutineScope.launch {
                    if (chatMessages.isNotEmpty()) {
                        chatListState.animateScrollToItem(chatMessages.size - 1)
                    }
                }
            }
        }
    }

    fun sendUserChat(textToSend: String) {
        val cleanText = textToSend.trim()
        if (cleanText.isBlank() || !isChatEnabled) return
        if (!isConnected) {
            Toast.makeText(context, "লাইভ সার্ভারের সাথে সংযোগ নেই! বার্তা পাঠানো সম্ভব হয়নি।", Toast.LENGTH_SHORT).show()
            return
        }
        val myName = studentName.ifBlank { "আপনি" }
        val isTeacher = selectedRole == "teacher"
        val formattedTime = try {
            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        } catch (_: Exception) {
            "এখন"
        }
        val msgId = java.util.UUID.randomUUID().toString()

        chatMessages.add(
            LiveChatMessage(
                id = msgId,
                senderName = myName,
                message = cleanText,
                timestamp = formattedTime,
                isTeacher = isTeacher,
                role = selectedRole,
                status = ChatDeliveryStatus.SENDING
            )
        )
        chatMessageInput = ""
        keyboardController?.hide()
        coroutineScope.launch {
            if (chatMessages.isNotEmpty()) {
                chatListState.animateScrollToItem(chatMessages.size - 1)
            }
        }

        wsManager.sendChatMessage(
            text = cleanText,
            studentName = myName,
            role = selectedRole,
            targetRole = selectedRole
        ) { isSuccess ->
            val idx = chatMessages.indexOfFirst { it.id == msgId }
            if (idx >= 0) {
                chatMessages[idx] = chatMessages[idx].copy(
                    status = if (isSuccess) ChatDeliveryStatus.SENT else ChatDeliveryStatus.FAILED
                )
            }
            if (!isSuccess) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "বার্তা পাঠানো যায়নি! অনুগ্রহ করে পুনরায় চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun retryChatMessage(msgId: String) {
        val idx = chatMessages.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val msg = chatMessages[idx]
        if (!isConnected) {
            Toast.makeText(context, "লাইভ সার্ভারের সাথে সংযোগ নেই! পুনরায় সংযোগের চেষ্টা করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            return
        }
        chatMessages[idx] = msg.copy(status = ChatDeliveryStatus.SENDING)
        wsManager.sendChatMessage(
            text = msg.message,
            studentName = msg.senderName,
            role = msg.role,
            targetRole = msg.role
        ) { isSuccess ->
            val currentIdx = chatMessages.indexOfFirst { it.id == msgId }
            if (currentIdx >= 0) {
                chatMessages[currentIdx] = chatMessages[currentIdx].copy(
                    status = if (isSuccess) ChatDeliveryStatus.SENT else ChatDeliveryStatus.FAILED
                )
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

    if (isPipMode) {
        val activePlaybackUrl = masterUrl ?: candidateUrls.firstOrNull { it.isNotBlank() }
        if (!activePlaybackUrl.isNullOrBlank()) {
            LivePlayer(
                masterUrl = activePlaybackUrl,
                candidateUrls = candidateUrls,
                selectedResolution = selectedResolution,
                isPipActive = true,
                resizeMode = resizeMode,
                videoScale = 1.0f,
                videoPan = Offset.Zero,
                syncToLiveTrigger = syncToLiveTrigger,
                onLiveDelayChanged = { liveDelaySeconds = it },
                onAvailableResolutionsChanged = { availableResolutions = it },
                onCurrentPlayingResolutionChanged = { currentPlayingResolution = it },
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
        containerColor = Color(0xFF0B1120),
        contentWindowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLandscape) PaddingValues(0.dp) else paddingValues)
        ) {
            when (val state = uiState) {
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
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = effectiveLessonTitle,
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is LiveClassState.Preview -> {
                    LaunchedEffect(Unit) {
                        uiState = LiveClassState.Playing
                    }
                }

                is LiveClassState.Playing -> {
                    val activePlaybackUrl = masterUrl ?: candidateUrls.firstOrNull { it.isNotBlank() }
                    val effectiveIsLive = isLiveOnGoing ||
                        isConnected ||
                        (activePlaybackUrl?.contains("100ms.live", ignoreCase = true) == true) ||
                        liveDelaySeconds > 0

                    Column(modifier = Modifier.fillMaxSize()) {
                        // ========================================================
                        // VIDEO PLAYER AREA (16:9 in Portrait, Fullscreen in Landscape)
                        // ========================================================
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isLandscape) Modifier.fillMaxSize()
                                    else Modifier.aspectRatio(16f / 9f)
                                )
                                .background(Color.Black)
                        ) {
                            if (!activePlaybackUrl.isNullOrBlank()) {
                                LivePlayer(
                                    masterUrl = activePlaybackUrl,
                                    candidateUrls = candidateUrls,
                                    selectedResolution = selectedResolution,
                                    isPipActive = false,
                                    resizeMode = resizeMode,
                                    videoScale = videoScale,
                                    videoPan = videoPan,
                                    syncToLiveTrigger = syncToLiveTrigger,
                                    onLiveDelayChanged = { liveDelaySeconds = it },
                                    onAvailableResolutionsChanged = { availableResolutions = it },
                                    onCurrentPlayingResolutionChanged = { currentPlayingResolution = it },
                                    modifier = Modifier.fillMaxSize(),
                                    onBack = {
                                        if (isLandscape) toggleScreenOrientation()
                                        else {
                                            wsManager.disconnect()
                                            onBack()
                                        }
                                    }
                                )
                            }

                            val currentScaleState = rememberUpdatedState(videoScale)

                            // Touch & Gesture Interceptor covering the video surface (Pinch to zoom, pan, double tap, single tap)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (currentScaleState.value * zoom).coerceIn(1.0f, 4.0f)
                                            videoScale = newScale
                                            if (newScale > 1.02f) {
                                                val maxPanX = (newScale - 1f) * 600f
                                                val maxPanY = (newScale - 1f) * 400f
                                                videoPan = Offset(
                                                    x = (videoPan.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (videoPan.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                            } else {
                                                videoScale = 1.0f
                                                videoPan = Offset.Zero
                                            }
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (videoScale > 1.05f) {
                                                    videoScale = 1.0f
                                                    videoPan = Offset.Zero
                                                    Toast.makeText(context, "জুম রিসেট (১.০x)", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    videoScale = 1.5f
                                                    Toast.makeText(context, "১.৫x জুম করা হয়েছে ✨", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onTap = {
                                                showPlayerControls = !showPlayerControls
                                                if (showPlayerControls) {
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                }
                                            }
                                        )
                                    }
                            )

                            // Quick Reset Pill when video is zoomed in
                            if (videoScale > 1.05f) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.75f),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .clickable {
                                            videoScale = 1.0f
                                            videoPan = Offset.Zero
                                            Toast.makeText(context, "জুম রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "${(videoScale * 100).toInt()}% জুম • রিসেট",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // ----------------------------------------------------
                            // TOP CONTROLS OVERLAY
                            // Uses weighted Left Container + Unweighted Fixed Right Container
                            // so Resolution Switcher is 100% GUARANTEED to stay visible even if studentName is 200+ chars!
                            // ----------------------------------------------------
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showPlayerControls,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)
                                            )
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // LEFT FLEXIBLE GROUP (weight = 1f -> shrinks/truncates name so Right Group is never pushed off!)
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // 1. Back button
                                            IconButton(
                                                onClick = {
                                                    if (isLandscape) toggleScreenOrientation()
                                                    else {
                                                        wsManager.disconnect()
                                                        onBack()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.55f))
                                                    .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Back",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }

                                            // 2. LIVE / REC Badge (Clickable to sync to live edge)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (effectiveIsLive) {
                                                    if (liveDelaySeconds > 3) Color(0xFFE11D48) else Color(0xFFDC2626)
                                                } else Color(0xFF059669),
                                                modifier = Modifier.clickable {
                                                    if (effectiveIsLive) {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        syncToLiveTrigger = System.currentTimeMillis()
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White)
                                                    )
                                                    Text(
                                                        text = if (effectiveIsLive) {
                                                            if (liveDelaySeconds > 3) "LIVE (-${liveDelaySeconds}s)" else "LIVE"
                                                        } else "REC",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // 3. Viewer Count Badge
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Black.copy(alpha = 0.6f),
                                                border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.15f))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Visibility,
                                                        contentDescription = null,
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "${if (viewerCount > 0) viewerCount else 1}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // 4. Flexible Student Name Chip (weight(1f, fill = false) so long names truncate with "..." without pushing right buttons!)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Black.copy(alpha = 0.6f),
                                                border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.55f)),
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        showNameChangeDialog = true
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = "Student Name",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = studentName,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFFE0F2FE),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Name",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // RIGHT FIXED-PRIORITY GROUP (Unweighted -> ALWAYS measured first and 100% visible!)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Resolution Switcher Button (Shows real resolution e.g. "Auto • 720p" or "480p")
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                modifier = Modifier.clickable {
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                    showResolutionDialog = true
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.HighQuality,
                                                        contentDescription = "Video Resolution",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = resolutionBadgeText,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // Active Poll shortcut if live
                                            if (activePoll?.isLive == true) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFDC2626),
                                                    modifier = Modifier.clickable {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        wsManager.loadFullPollDetails(activePoll!!.pollId)
                                                        selectedPollIdForQuestion = activePoll!!.pollId
                                                    }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Quiz,
                                                            contentDescription = "Live Poll",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = "পোল",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ----------------------------------------------------
                            // BOTTOM CONTROLS OVERLAY (Live Sync, Fit/Zoom, PiP, Slides, Fullscreen)
                            // ----------------------------------------------------
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showPlayerControls,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                            )
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Left Live Edge Sync Indicator
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .padding(end = 6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (liveDelaySeconds > 3) Color(0xFFE11D48).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (liveDelaySeconds > 3) Color(0xFFF43F5E) else Color(0xFF10B981).copy(alpha = 0.7f)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        syncToLiveTrigger = System.currentTimeMillis()
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.5.dp)
                                                            .clip(CircleShape)
                                                            .background(if (liveDelaySeconds > 3) Color(0xFFF43F5E) else Color(0xFF10B981))
                                                    )
                                                    Text(
                                                        text = if (liveDelaySeconds > 3) {
                                                            "⚡ ${liveDelaySeconds}s পিছিয়ে • সিঙ্ক করুন"
                                                        } else {
                                                            "লাইভ সিঙ্কড"
                                                        },
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            if (isLandscape) {
                                                Text(
                                                    text = effectiveLessonTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                            }
                                        }

                                        // Right Video Controls Group
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isLandscape) {
                                                // Landscape Aspect Ratio Segmented Bar (Fit | Zoom | Full)
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    border = BorderStroke(0.8.dp, Color(0xFF334155))
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(2.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT && videoScale <= 1.05f) Color(0xFF0284C7) else Color.Transparent,
                                                            modifier = Modifier.clickable {
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                                videoScale = 1.0f
                                                                videoPan = Offset.Zero
                                                                Toast.makeText(context, "ভিডিও: ফিট স্ক্রিন (Fit)", Toast.LENGTH_SHORT).show()
                                                            }
                                                        ) {
                                                            Text(
                                                                text = "ফিট",
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                                            )
                                                        }

                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) Color(0xFF0284C7) else Color.Transparent,
                                                            modifier = Modifier.clickable {
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                                videoScale = 1.0f
                                                                videoPan = Offset.Zero
                                                                Toast.makeText(context, "ভিডিও: জুম ও ফিল স্ক্রিন (Zoom & Fill)", Toast.LENGTH_SHORT).show()
                                                            }
                                                        ) {
                                                            Text(
                                                                text = "জুম",
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                                            )
                                                        }

                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) Color(0xFF0284C7) else Color.Transparent,
                                                            modifier = Modifier.clickable {
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                                videoScale = 1.0f
                                                                videoPan = Offset.Zero
                                                                Toast.makeText(context, "ভিডিও: পূর্ণ পর্দা (Stretch Full)", Toast.LENGTH_SHORT).show()
                                                            }
                                                        ) {
                                                            Text(
                                                                text = "ফুল",
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                // Landscape Zoom Stepper Controls: [-] 100% [+]
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    border = BorderStroke(0.8.dp, if (videoScale > 1.05f) Color(0xFF38BDF8) else Color(0xFF334155))
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                val newScale = (videoScale - 0.25f).coerceAtLeast(1.0f)
                                                                videoScale = newScale
                                                                if (newScale <= 1.02f) {
                                                                    videoPan = Offset.Zero
                                                                }
                                                            },
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ZoomOut,
                                                                contentDescription = "Zoom Out",
                                                                tint = if (videoScale > 1.02f) Color(0xFF38BDF8) else Color.Gray,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }

                                                        Text(
                                                            text = "${(videoScale * 100).toInt()}%",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (videoScale > 1.05f) Color(0xFF38BDF8) else Color.White,
                                                            modifier = Modifier.padding(horizontal = 2.dp)
                                                        )

                                                        IconButton(
                                                            onClick = {
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                val newScale = (videoScale + 0.25f).coerceAtMost(4.0f)
                                                                videoScale = newScale
                                                            },
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ZoomIn,
                                                                contentDescription = "Zoom In",
                                                                tint = if (videoScale < 4.0f) Color(0xFF38BDF8) else Color.Gray,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Portrait Aspect Ratio / Fit / Zoom / Fill button
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    border = BorderStroke(
                                                        0.8.dp,
                                                        if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT || videoScale > 1.05f) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.3f)
                                                    ),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            lastUserInteractionTime = System.currentTimeMillis()
                                                            videoScale = 1.0f
                                                            videoPan = Offset.Zero
                                                            resizeMode = when (resizeMode) {
                                                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                            }
                                                            val label = when (resizeMode) {
                                                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ভিডিও সাইজ: ফিট স্ক্রিন (Fit)"
                                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ভিডিও সাইজ: জুম ও ফিল স্ক্রিন (Zoom & Fill)"
                                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ভিডিও সাইজ: ফুল স্ক্রিন (Stretch)"
                                                                else -> "ফিট স্ক্রিন"
                                                            }
                                                            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                                        }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = when (resizeMode) {
                                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Default.ZoomOutMap
                                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.FitScreen
                                                                else -> Icons.Default.AspectRatio
                                                            },
                                                            contentDescription = "Aspect Ratio",
                                                            tint = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT || videoScale > 1.05f) Color(0xFF38BDF8) else Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = when (resizeMode) {
                                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "জুম"
                                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ফুল"
                                                                else -> "ফিট"
                                                            },
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT || videoScale > 1.05f) Color(0xFF38BDF8) else Color.White
                                                        )
                                                    }
                                                }
                                            }

                                            // Slide Notes
                                            if (lectureSlideUrls.isNotEmpty()) {
                                                IconButton(
                                                    onClick = {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        lectureSlideUrls.firstOrNull()?.let { url ->
                                                            try {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                            } catch (e: Exception) {
                                                                Log.w(TAG, "Cannot open slide URL: ${e.message}")
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Description,
                                                        contentDescription = "Lecture Notes",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }

                                            // Picture in Picture
                                            IconButton(
                                                onClick = {
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                    PipHelper.enterPipMode(activity, isPlaying = true)
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.55f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureInPictureAlt,
                                                    contentDescription = "Picture in Picture",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            // Landscape Chat Toggle Button
                                            if (isLandscape) {
                                                IconButton(
                                                    onClick = {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        showLandscapeChat = !showLandscapeChat
                                                    },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(if (showLandscapeChat) Color(0xFF0284C7) else Color.Black.copy(alpha = 0.55f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Chat,
                                                        contentDescription = "Toggle Chat in Landscape",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // Fullscreen / Screen Rotation Button
                                            IconButton(
                                                onClick = {
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                    toggleScreenOrientation()
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.6f))
                                            ) {
                                                Icon(
                                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                    contentDescription = if (isLandscape) "Exit Fullscreen" else "Rotate Fullscreen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Non-intrusive Live Poll notification pill
                            val activePollItem = activePoll
                            if (activePollItem != null && activePollItem.isLive && selectedPollIdForQuestion == null) {
                                LivePollFloatingNotification(
                                    activePoll = activePollItem,
                                    onOpenPoll = {
                                        wsManager.loadFullPollDetails(it.pollId)
                                        selectedPollIdForQuestion = it.pollId
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 48.dp, end = 10.dp)
                                )
                            }

                            // Landscape Live Chat Side Drawer (when toggled on in Landscape Mode)
                            if (isLandscape && showLandscapeChat) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                                    color = Color(0xF20F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .width(320.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { /* Consume taps inside chat drawer */ }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                    ) {
                                        // Header
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Chat, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("লাইভ চ্যাট", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Spacer(Modifier.weight(1f))
                                            // Student name chip in landscape drawer
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF1E293B),
                                                border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable { showNameChangeDialog = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(10.dp))
                                                    Text(
                                                        text = studentName,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF38BDF8),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { showLandscapeChat = false },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // Messages list
                                        LazyColumn(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(chatMessages, key = { it.id }) { item ->
                                                ChatMessageCard(
                                                    item = item,
                                                    myName = studentName,
                                                    onRetry = { retryChatMessage(item.id) }
                                                )
                                            }
                                        }

                                        // Input
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = chatMessageInput,
                                                onValueChange = { chatMessageInput = it },
                                                placeholder = { Text("বার্তা লিখুন...", fontSize = 11.sp, color = Color.White.copy(0.5f)) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(16.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF38BDF8),
                                                    unfocusedBorderColor = Color(0xFF334155),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = Color(0xFF1E293B),
                                                    unfocusedContainerColor = Color(0xFF1E293B)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (chatMessageInput.isNotBlank()) {
                                                        sendUserChat(chatMessageInput)
                                                    }
                                                },
                                                enabled = chatMessageInput.isNotBlank(),
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(if (chatMessageInput.isNotBlank()) Color(0xFF0284C7) else Color(0xFF1E293B))
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ========================================================
                        // BELOW VIDEO: MODERN PREMIUM EDTECH STUDIO UI (Portrait Mode)
                        // ========================================================
                        if (!isLandscape) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0B1120))
                            ) {
                                // ----------------------------------------------------
                                // 1. ELEVATED CLASS INFO & INTERACTIVE TOOLBAR CARD
                                // ----------------------------------------------------
                                Surface(
                                    color = Color(0xFF111C33),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        // Top Metadata Row: Subject Chip + Teacher Badge + Connection Status
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = subjectName,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF38BDF8),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                )
                                            }

                                            if (roomTeacherName.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF1E293B),
                                                    border = BorderStroke(0.7.dp, Color(0xFF334155)),
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.School,
                                                            contentDescription = null,
                                                            tint = Color(0xFFFBBF24),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Text(
                                                            text = roomTeacherName,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFFE2E8F0),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            // Connection status badge
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isConnected) Color(0xFF064E3B) else Color(0xFF1E293B),
                                                border = BorderStroke(
                                                    0.8.dp,
                                                    if (isConnected) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF475569)
                                                )
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isConnected) Color(0xFF34D399) else Color(0xFF94A3B8))
                                                    )
                                                    Text(
                                                        text = if (isConnected) "কানেক্টেড" else "অফলাইন",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isConnected) Color(0xFF6EE7B7) else Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Lesson Title
                                        Text(
                                            text = effectiveLessonTitle,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Interactive Action Bar: Hand Raise | Poll/Quiz | Resolution | Student Name
                                        // Horizontally scrollable or balanced so nothing ever gets cut off!
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // 1. Hand Raise Pill
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isHandRaised) Color(0xFFF59E0B) else Color(0xFF1E293B),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isHandRaised) Color(0xFFFBBF24) else Color(0xFF334155)
                                                ),
                                                modifier = Modifier.clickable { wsManager.toggleHandRaise(studentName) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FrontHand,
                                                        contentDescription = "Hand Raise",
                                                        tint = if (isHandRaised) Color(0xFF090D16) else Color(0xFFFBBF24),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = if (isHandRaised) "হাত নামান" else "হাত তুলুন",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isHandRaised) Color(0xFF090D16) else Color.White,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // 2. Poll / Quiz Pill
                                            val isPollLive = activePoll?.isLive == true
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isPollLive) Color(0xFFDC2626) else Color(0xFF1E293B),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isPollLive) Color(0xFFEF4444) else Color(0xFF334155)
                                                ),
                                                modifier = Modifier.clickable {
                                                    wsManager.requestPollList("started")
                                                    wsManager.requestPollList("stopped")
                                                    val currActive = activePoll
                                                    if (currActive != null && currActive.isLive) {
                                                        wsManager.loadFullPollDetails(currActive.pollId)
                                                        selectedPollIdForQuestion = currActive.pollId
                                                    } else {
                                                        showPreviousPollsDialog = true
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Quiz,
                                                        contentDescription = "Polls",
                                                        tint = if (isPollLive) Color.White else Color(0xFFF59E0B),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = if (isPollLive) {
                                                            "পোল চলছে"
                                                        } else if (previousPolls.isNotEmpty()) {
                                                            "পোল (${previousPolls.size})"
                                                        } else {
                                                            "পোল/কুইজ"
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // 3. Always-Accessible Resolution Pill (Right in the Action Bar too!)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF1E293B),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                                modifier = Modifier.clickable { showResolutionDialog = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.HighQuality,
                                                        contentDescription = "Resolution",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = resolutionBadgeText,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF38BDF8),
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // 4. Student Display Name Chip (Weighted with Ellipsis so long names never overflow!)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF1E293B),
                                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable { showNameChangeDialog = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Name",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Text(
                                                        text = studentName,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFFE2E8F0),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                                // ----------------------------------------------------
                                // 2. LIVE CHAT SECTION
                                // ----------------------------------------------------
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    // Chat Role Target Selector Bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Forum,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "রোল:",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF94A3B8)
                                        )

                                        val rolesList = listOf(
                                            Triple("teacher", "Teacher", Icons.Default.School),
                                            Triple("student", "Student", Icons.Default.Person),
                                            Triple("all", "* All", Icons.Default.Public)
                                        )
                                        rolesList.forEach { (roleKey, roleLabel, roleIcon) ->
                                            val isSelected = selectedRole == roleKey
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF162032),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF26354D)
                                                ),
                                                modifier = Modifier.clickable { selectedRole = roleKey }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = roleIcon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = roleLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        if (chatMessages.isNotEmpty()) {
                                            Text(
                                                text = "${chatMessages.size} বার্তা",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF64748B),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Pinned Teacher Message Announcement Banner (if any)
                                    val pinnedTeacherMsg = chatMessages.lastOrNull { it.isTeacher && (it.isPinned || it.id.startsWith("pin_")) }
                                    if (pinnedTeacherMsg != null) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF0C2440),
                                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Campaign,
                                                    contentDescription = "Pinned Announcement",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "📌 শিক্ষকের পিন করা ঘোষণা (${pinnedTeacherMsg.senderName}):",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF38BDF8)
                                                    )
                                                    Text(
                                                        text = pinnedTeacherMsg.message,
                                                        fontSize = 12.sp,
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Chat messages list
                                    LazyColumn(
                                        state = chatListState,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        reverseLayout = false
                                    ) {
                                        if (chatMessages.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 28.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color(0xFF162032),
                                                            border = BorderStroke(1.dp, Color(0xFF26354D)),
                                                            modifier = Modifier.size(44.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Default.ChatBubbleOutline,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF38BDF8).copy(alpha = 0.7f),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "লাইভ চ্যাটে স্বাগতম!",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFCBD5E1)
                                                        )
                                                        Text(
                                                            text = "ক্লাস চলাকালীন আপনার যেকোনো প্রশ্ন নিচে লিখুন",
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF64748B),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            items(chatMessages, key = { it.id }) { item ->
                                                ChatMessageCard(
                                                    item = item,
                                                    myName = studentName,
                                                    onRetry = { retryChatMessage(item.id) }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Quick Comment Chips (Horizontally scrollable)
                                    if (isChatEnabled) {
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items(quickReplyChips) { chipText ->
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = Color(0xFF162032),
                                                    border = BorderStroke(0.8.dp, Color(0xFF26354D)),
                                                    modifier = Modifier.clickable { sendUserChat(chipText) }
                                                ) {
                                                    Text(
                                                        text = chipText,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFFE2E8F0),
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Chat Input or Disabled banner
                                    if (!isChatEnabled) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF162032),
                                            border = BorderStroke(1.dp, Color(0xFF26354D)),
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
                                                onValueChange = { chatMessageInput = it },
                                                placeholder = {
                                                    Text(
                                                        "প্রশ্ন বা বার্তা লিখুন...",
                                                        fontSize = 12.5.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(22.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF38BDF8),
                                                    unfocusedBorderColor = Color(0xFF26354D),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = Color(0xFF162032),
                                                    unfocusedContainerColor = Color(0xFF162032)
                                                ),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(onSend = {
                                                    if (chatMessageInput.isNotBlank()) {
                                                        sendUserChat(chatMessageInput)
                                                    }
                                                }),
                                                modifier = Modifier.weight(1f)
                                            )

                                            IconButton(
                                                onClick = {
                                                    if (chatMessageInput.isNotBlank()) {
                                                        sendUserChat(chatMessageInput)
                                                    }
                                                },
                                                enabled = chatMessageInput.isNotBlank(),
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (chatMessageInput.isNotBlank()) Color(0xFF0284C7) else Color(0xFF162032)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Send",
                                                    tint = if (chatMessageInput.isNotBlank()) Color.White else Color(0xFF64748B),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "লাইভ ক্লাসে কানেক্ট করা সম্ভব হয়নি",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    wsManager.disconnect()
                                    onBack()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF475569))
                            ) {
                                Text("ফিরে যান")
                            }

                            Button(
                                onClick = { startLiveClassConnection() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("পুনরায় চেষ্টা করুন")
                            }
                        }
                    }
                }
            }
        }
    }

    // ====================================================================
    // REAL STREAM VIDEO RESOLUTION PICKER DIALOG
    // Dynamically shows exact tracks from the stream (e.g. Auto, 720p, 480p, 360p, 144p)
    // ====================================================================
    if (showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { showResolutionDialog = false },
            containerColor = Color(0xFF111C33),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "ভিডিও রেজুলেশন (Real Stream)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (currentPlayingResolution.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.6f),
                            border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34D399))
                                )
                                Text(
                                    text = "বর্তমানে স্ট্রিমে প্লে হচ্ছে: $currentPlayingResolution",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6EE7B7)
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (availableResolutions.size <= 1) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "লাইভ স্ট্রিম থেকে আসল রেজুলেশন তালিকা স্ক্যান করা হচ্ছে...",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    availableResolutions.forEach { option ->
                        val isSelected = selectedResolution.equals(option.id, ignoreCase = true)
                        val isCurrentlyPlayingTrack = currentPlayingResolution.equals(option.id, ignoreCase = true)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF0B1120),
                            border = BorderStroke(
                                if (isSelected) 1.4.dp else 1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF26354D)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedResolution = option.id
                                    showResolutionDialog = false
                                    val toastLabel = if (option.id == "auto") "Auto (স্বয়ংক্রিয়)" else option.title
                                    Toast.makeText(
                                        context,
                                        "রেজুলেশন '$toastLabel' নির্বাচন করা হয়েছে ✨",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = option.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF38BDF8) else Color.White
                                        )

                                        if (option.badge.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B)
                                            ) {
                                                Text(
                                                    text = option.badge,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) Color.White else Color(0xFF38BDF8),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }

                                        if (isCurrentlyPlayingTrack && !option.id.equals("auto", ignoreCase = true)) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF065F46)
                                            ) {
                                                Text(
                                                    text = "চলছে",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF6EE7B7),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

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
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Unselected",
                                        tint = Color(0xFF475569),
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
                    Text("বন্ধ করুন", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Custom Student Display Name Dialog (for joining or editing anytime)
    if (showNameChangeDialog || showJoinNameDialog) {
        val isFirstJoin = showJoinNameDialog
        var nameInput by remember(studentName) { mutableStateOf(studentName) }
        val defaultProfileName = sessionManager.getUserFullName() ?: "শিক্ষার্থী"

        AlertDialog(
            onDismissRequest = {
                showNameChangeDialog = false
                showJoinNameDialog = false
            },
            containerColor = Color(0xFF111C33),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isFirstJoin) Icons.Default.Badge else Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isFirstJoin) "লাইভ ক্লাসে যোগদানের নাম" else "প্রদর্শন নাম পরিবর্তন করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "লাইভ চ্যাটে বার্তা পাঠানোর সময় এবং শিক্ষক ও অন্য শিক্ষার্থীদের কাছে আপনার এই নামটি প্রদর্শিত হবে:",
                        fontSize = 12.5.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        placeholder = { Text("আপনার নাম লিখুন...", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (nameInput.isNotBlank()) {
                                IconButton(onClick = { nameInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0B1120),
                            unfocusedContainerColor = Color(0xFF0B1120)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick suggestion chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "পরামর্শ:",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0B1120),
                            border = BorderStroke(0.8.dp, Color(0xFF334155)),
                            modifier = Modifier.clickable { nameInput = defaultProfileName }
                        ) {
                            Text(
                                text = "প্রোফাইল নাম",
                                fontSize = 10.5.sp,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = nameInput.trim().ifBlank { defaultProfileName }
                        studentName = clean
                        sessionManager.setLiveCustomStudentName(clean)
                        wsManager.updateStudentName(clean)
                        showNameChangeDialog = false
                        showJoinNameDialog = false
                        Toast.makeText(context, "নাম '$clean' হিসেবে সেট করা হয়েছে ✨", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isFirstJoin) "এই নামে যোগ দিন" else "সংরক্ষণ করুন",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNameChangeDialog = false
                        showJoinNameDialog = false
                    }
                ) {
                    Text(
                        text = if (isFirstJoin) "ডিফল্ট নাম রাখুন" else "বাতিল",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
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

@Composable
fun ChatMessageCard(
    item: LiveChatMessage,
    myName: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTeacherMsg = item.isTeacher || item.role == "teacher"
    val isMyMsg = item.senderName.equals(myName, ignoreCase = true) || item.senderName == "আপনি"

    if (isTeacherMsg) {
        // ==========================================
        // TEACHER MESSAGE: GLOWING, HIGH-CONTRAST CARD
        // ==========================================
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0C2440),
            border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF38BDF8))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0284C7)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "শিক্ষক (Teacher)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = item.senderName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Text(
                            text = "• ${item.timestamp}",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Verified Announcement",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.message,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    } else {
        // ==========================================
        // STUDENT MESSAGE (WITH AVATAR & DELIVERY STATUS)
        // ==========================================
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMyMsg) Color(0xFF162238) else Color(0xFF131C2E),
            border = BorderStroke(
                if (isMyMsg) 1.dp else 0.7.dp,
                if (isMyMsg) Color(0xFF0284C7).copy(alpha = 0.65f) else Color(0xFF26354D)
            ),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Avatar Initial Circle
                val initial = item.senderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMyMsg) Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)))
                            else Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF475569)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (badgeLabel, badgeBg) = if (isMyMsg) {
                            Pair("আপনি", Color(0xFF0284C7))
                        } else if (item.role == "all" || item.role == "*") {
                            Pair("* All", Color(0xFF4F46E5))
                        } else {
                            Pair("Student", Color(0xFF059669))
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = badgeLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                            )
                        }

                        Text(
                            text = item.senderName,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMyMsg) Color(0xFF38BDF8) else Color(0xFFF1F5F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Text(
                            text = "• ${item.timestamp}",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // DELIVERY STATUS INDICATOR (for user's own sent messages)
                        if (isMyMsg) {
                            when (item.status) {
                                ChatDeliveryStatus.SENDING -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 1.5.dp,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "পাঠানো হচ্ছে...",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }
                                ChatDeliveryStatus.SENT -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Delivered",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "গেছে",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                                ChatDeliveryStatus.FAILED -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = "Failed",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "ব্যর্থ",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                            border = BorderStroke(0.7.dp, Color(0xFFEF4444)),
                                            modifier = Modifier.clickable { onRetry() }
                                        ) {
                                            Text(
                                                text = "পুনরায় পাঠান",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF4444),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = item.message,
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
