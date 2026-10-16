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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.ui.AspectRatioFrameLayout
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
    val subtitle: String
)

val LIVE_RESOLUTIONS = listOf(
    LiveResolutionOption("auto", "Auto (স্বয়ংক্রিয়)", "নেটওয়ার্ক স্পিড অনুযায়ী এডাপ্টিভ"),
    LiveResolutionOption("1080p", "1080p Full HD", "সর্বোচ্চ রেজুলেশন ও স্পষ্টতা"),
    LiveResolutionOption("720p", "720p HD", "স্ট্যান্ডার্ড হাই ডেফিনিশন (রিকমেন্ডেড)"),
    LiveResolutionOption("480p", "480p SD", "ব্যালেন্সড ডেটা ও কোয়ালিটি"),
    LiveResolutionOption("360p", "360p Data Saver", "সাশ্রয়ী ডেটা খরচ"),
    LiveResolutionOption("240p", "240p Low", "দুর্বল ইন্টারনেটের জন্য")
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
    var showJoinNameDialog by rememberSaveable {
        mutableStateOf(sessionManager.getLiveCustomStudentName().isNullOrBlank())
    }

    // Live Delay state & Sync Trigger
    var liveDelaySeconds by remember { mutableIntStateOf(0) }
    var syncToLiveTrigger by remember { mutableLongStateOf(0L) }

    // Video aspect ratio, zoom scale, and pan
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var videoScale by remember { mutableFloatStateOf(1.0f) }
    var videoPan by remember { mutableStateOf(Offset.Zero) }

    var selectedRole by remember { mutableStateOf("student") } // "teacher", "student", "all"

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

    val quickReplyChips = remember {
        listOf("হ্যালো স্যার 👋", "জি বুঝতে পেরেছি ✅", "আরেকবার বুঝিয়ে দিন 🙏", "ক্লিয়ার দেখা যাচ্ছে ✨")
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

            var directPlaybackSet = false
            if (!academicInfo?.playbackUrl.isNullOrBlank()) {
                wsManager.setMasterUrl(academicInfo!!.playbackUrl!!)
                uiState = LiveClassState.Playing
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
            android.widget.Toast.makeText(context, "লাইভ সার্ভারের সাথে সংযোগ নেই! বার্তা পাঠানো সম্ভব হয়নি।", android.widget.Toast.LENGTH_SHORT).show()
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
                    android.widget.Toast.makeText(context, "বার্তা পাঠানো যায়নি! অনুগ্রহ করে পুনরায় চেষ্টা করুন।", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun retryChatMessage(msgId: String) {
        val idx = chatMessages.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val msg = chatMessages[idx]
        if (!isConnected) {
            android.widget.Toast.makeText(context, "লাইভ সার্ভারের সাথে সংযোগ নেই! পুনরায় সংযোগের চেষ্টা করা হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
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
        containerColor = Color(0xFF0F172A),
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
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(52.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "লাইভ ক্লাসে যোগ দেওয়া হচ্ছে...",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = effectiveLessonTitle,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
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

                    Column(modifier = Modifier.fillMaxSize()) {
                        // ========================================================
                        // VIDEO PLAYER AREA (16:9 in Portrait, Fullscreen in Landscape)
                        // Touch on video toggles controls: click once -> show, click again -> hide!
                        // Auto-hides after 4s.
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
                            // TOP CONTROLS OVERLAY (Back, LIVE, Viewers, Resolution, Poll)
                            // Clean, responsive: never overflows or breaks!
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
                                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                            )
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
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
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // 2. LIVE / RECORDING Badge (Clickable to sync to live edge!)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isLiveOnGoing) {
                                                if (liveDelaySeconds > 3) Color(0xFFE11D48) else Color(0xFF059669)
                                            } else Color(0xFF10B981),
                                            modifier = Modifier.clickable {
                                                if (isLiveOnGoing) {
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                    syncToLiveTrigger = System.currentTimeMillis()
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = if (isLiveOnGoing) {
                                                    if (liveDelaySeconds > 3) "🔴 LIVE (-${liveDelaySeconds}s)" else "🔴 LIVE"
                                                } else "📺 RECORDING",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // 3. Viewer Count Badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.55f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "${if (viewerCount > 0) viewerCount else 1} জন",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        // Student Display Name Chip in Player Top Bar (Click to change name anytime)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.6f),
                                            border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                            modifier = Modifier.clickable {
                                                lastUserInteractionTime = System.currentTimeMillis()
                                                showNameChangeDialog = true
                                            }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = "Student Name",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = studentName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF38BDF8),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Name",
                                                    tint = Color.White.copy(alpha = 0.65f),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // 4. Resolution Switcher Button (Clean Pill)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.6f),
                                            border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                            modifier = Modifier.clickable {
                                                lastUserInteractionTime = System.currentTimeMillis()
                                                showResolutionDialog = true
                                            }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = "Resolution",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = when (selectedResolution.lowercase()) {
                                                        "auto" -> "Auto"
                                                        else -> selectedResolution.uppercase()
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        // 5. Active Poll shortcut if live
                                        if (activePoll?.isLive == true) {
                                            Spacer(modifier = Modifier.width(6.dp))
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
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Quiz,
                                                        contentDescription = "Live Poll",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "পোল",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ----------------------------------------------------
                            // BOTTOM CONTROLS OVERLAY (Live Sync, Fit/Zoom, PiP, Slides, Fullscreen)
                            // Positioned at Bottom of video
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
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                            )
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isLandscape) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                // Clickable LIVE Sync Button in Landscape
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (liveDelaySeconds > 3) Color(0xFFE11D48).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.55f),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (liveDelaySeconds > 3) Color(0xFFF43F5E).copy(alpha = 0.85f) else Color(0xFF10B981).copy(alpha = 0.6f)
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
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(if (liveDelaySeconds > 3) Color(0xFFF43F5E) else Color(0xFF10B981))
                                                        )
                                                        Text(
                                                            text = if (liveDelaySeconds > 3) "🔴 লাইভ (${liveDelaySeconds}s)" else "🔴 লাইভ",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = effectiveLessonTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            // Clickable LIVE Sync Button in Portrait (Tapping reduces delay to 0)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (liveDelaySeconds > 3) Color(0xFFE11D48).copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.55f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (liveDelaySeconds > 3) Color(0xFFF43F5E).copy(alpha = 0.85f) else Color(0xFF10B981).copy(alpha = 0.6f)
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
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(if (liveDelaySeconds > 3) Color(0xFFF43F5E) else Color(0xFF10B981))
                                                    )
                                                    Text(
                                                        text = if (liveDelaySeconds > 3) "🔴 লাইভ (${liveDelaySeconds}s পেছনে • ট্যাপ করুন)" else "🔴 লাইভ",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

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
                                                    // Fit Option
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
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }

                                                    // Zoom Option (Fill / Crop)
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
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }

                                                    // Full Stretch Option
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
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

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
                                                    // Zoom Out Button
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
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (videoScale > 1.05f) Color(0xFF38BDF8) else Color.White,
                                                        modifier = Modifier.padding(horizontal = 3.dp)
                                                    )

                                                    // Zoom In Button
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
                                                    if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT || videoScale > 1.05f) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.35f)
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
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = when (resizeMode) {
                                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "জুম"
                                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ফুল"
                                                            else -> "ফিট"
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT || videoScale > 1.05f) Color(0xFF38BDF8) else Color.White
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

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
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = "Lecture Notes",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        // Picture in Picture
                                        IconButton(
                                            onClick = {
                                                lastUserInteractionTime = System.currentTimeMillis()
                                                PipHelper.enterPipMode(activity, isPlaying = true)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PictureInPictureAlt,
                                                contentDescription = "Picture in Picture",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Landscape Chat Toggle Button
                                        if (isLandscape) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = {
                                                    lastUserInteractionTime = System.currentTimeMillis()
                                                    showLandscapeChat = !showLandscapeChat
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(if (showLandscapeChat) Color(0xFF0284C7) else Color.Black.copy(alpha = 0.5f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Chat,
                                                    contentDescription = "Toggle Chat in Landscape",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Fullscreen / Screen Rotation Button (Bottom-Right corner)
                                        IconButton(
                                            onClick = {
                                                lastUserInteractionTime = System.currentTimeMillis()
                                                toggleScreenOrientation()
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                        ) {
                                            Icon(
                                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                contentDescription = if (isLandscape) "Exit Fullscreen" else "Rotate Fullscreen",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
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
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
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
                                                modifier = Modifier.clickable { showNameChangeDialog = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(10.dp))
                                                    Text(studentName, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), maxLines = 1)
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
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
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
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                        // BELOW VIDEO: CLEAN SHIKHO UI (Portrait Mode)
                        // ========================================================
                        if (!isLandscape) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                // 1. Title
                                Text(
                                    text = effectiveLessonTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // 2. Subtitle (Subject)
                                Text(
                                    text = subjectName,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                )

                                HorizontalDivider(color = Color(0xFF334155))

                                // 3. Live Chat Header Row with Hand Raise, Poll and Connection status
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "লাইভ চ্যাট",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Hand Raise button (হাত তুলুন / হাত নামান)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isHandRaised) Color(0xFFF59E0B) else Color(0xFF1E293B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isHandRaised) Color(0xFFFBBF24) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clickable { wsManager.toggleHandRaise(studentName) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FrontHand,
                                                contentDescription = "Hand Raise",
                                                tint = if (isHandRaised) Color(0xFF090D16) else Color(0xFFFBBF24),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = if (isHandRaised) "হাত নামান" else "হাত তুলুন",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isHandRaised) Color(0xFF090D16) else Color.White
                                            )
                                        }
                                    }

                                    // Poll / Quiz button
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (activePoll?.isLive == true) Color(0xFFDC2626) else Color(0xFF1E293B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (activePoll?.isLive == true) Color(0xFFEF4444) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clickable {
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
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Quiz,
                                                contentDescription = null,
                                                tint = if (activePoll?.isLive == true) Color.White else Color(0xFFF59E0B),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = if (activePoll?.isLive == true) "🔴 পোল চলছে" else "পোল/কুইজ",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // Real-time connection status
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isConnected) Color(0xFF065F46) else Color(0xFF334155)
                                    ) {
                                        Text(
                                            text = if (isConnected) "🟢 কানেক্টেড" else "🔴 অফলাইন",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isConnected) Color(0xFF34D399) else Color(0xFF94A3B8),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // 4. Role selector chips: Teacher, Student, * All
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "রোল:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    val rolesList = listOf(
                                        Triple("teacher", "Teacher", Icons.Default.School),
                                        Triple("student", "Student", Icons.Default.Person),
                                        Triple("all", "* All", Icons.Default.Public)
                                    )
                                    rolesList.forEach { (roleKey, roleLabel, roleIcon) ->
                                        val isSelected = selectedRole == roleKey
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                            ),
                                            modifier = Modifier.clickable { selectedRole = roleKey }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Custom Student Name Chip & Edit Trigger
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.65f)),
                                        modifier = Modifier.clickable { showNameChangeDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // 5. Pinned Teacher Message Announcement Banner (if any)
                                val pinnedTeacherMsg = chatMessages.lastOrNull { it.isTeacher && (it.isPinned || it.id.startsWith("pin_")) }
                                if (pinnedTeacherMsg != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0C2440),
                                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Campaign,
                                                contentDescription = "Pinned Announcement",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "📌 শিক্ষকের পিন করা ঘোষণা (${pinnedTeacherMsg.senderName}):",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF38BDF8)
                                                )
                                                Text(
                                                    text = pinnedTeacherMsg.message,
                                                    fontSize = 11.5.sp,
                                                    color = Color.White,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // 6. Chat messages list
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
                                                    .padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "কোনো চ্যাট বার্তা নেই",
                                                    fontSize = 12.sp,
                                                    color = Color.White.copy(alpha = 0.4f)
                                                )
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

                                // 7. Quick Comment Chips
                                if (isChatEnabled) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        quickReplyChips.forEach { chipText ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF1E293B),
                                                border = BorderStroke(0.7.dp, Color(0xFF334155)),
                                                modifier = Modifier.clickable { sendUserChat(chipText) }
                                            ) {
                                                Text(
                                                    text = chipText,
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFFE2E8F0),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 8. Chat Input or Disabled banner
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
                                            onValueChange = { chatMessageInput = it },
                                            placeholder = {
                                                Text(
                                                    "প্রশ্ন বা বার্তা লিখুন...",
                                                    fontSize = 12.sp,
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(20.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF38BDF8),
                                                unfocusedBorderColor = Color(0xFF334155),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = Color(0xFF1E293B),
                                                unfocusedContainerColor = Color(0xFF1E293B)
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

    // Video Resolution Picker Dialog
    if (showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { showResolutionDialog = false },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ভিডিও রেজুলেশন (Resolution)",
                        fontSize = 16.sp,
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
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF0F172A),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
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
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF38BDF8) else Color.White
                                    )
                                    Text(
                                        text = option.subtitle,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
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
                    Text("বাতিল", color = Color(0xFF94A3B8))
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
            containerColor = Color(0xFF1E293B),
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
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
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
                            color = Color(0xFF0F172A),
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

    // 1. Real Poll Question Dialog (Screenshots 2, 4, 5)
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

    // 2. Real Poll Participation Summary & Leaderboard Dialog (Screenshot 1)
    currentResultDialogPoll?.let { poll ->
        LivePollResultDialog(
            poll = poll,
            currentPeerId = wsManager.getCurrentPeerId(),
            currentUserId = wsManager.getCurrentUserId(),
            currentUserName = studentName,
            onDismiss = { selectedPollIdForResult = null }
        )
    }

    // 3. Real Previous Polls/Quizzes Dialog (Screenshot 3)
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
                // Left Accent Stripe in Cyan
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
                    // Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Official Teacher Badge
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
                            color = Color(0xFF38BDF8)
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

                    // Message Body
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
        // STUDENT MESSAGE (WITH CLEAR DELIVERY STATUS)
        // ==========================================
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isMyMsg) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.6f),
            border = BorderStroke(
                if (isMyMsg) 1.dp else 0.5.dp,
                if (isMyMsg) Color(0xFF0284C7).copy(alpha = 0.7f) else Color(0xFF334155)
            ),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
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
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Text(
                        text = item.senderName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMyMsg) Color(0xFF38BDF8) else Color(0xFFF1F5F9)
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
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
