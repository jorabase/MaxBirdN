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
import androidx.compose.foundation.horizontalScroll
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
    val targetRole: String = "student",
    val senderPeerId: String = "",
    val status: ChatDeliveryStatus = ChatDeliveryStatus.SENT,
    val isPinned: Boolean = false,
    val isMine: Boolean = false
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
    var canViewProfiles by remember {
        mutableStateOf(com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context))
    }
    val defaultProfileName = remember {
        sessionManager.getUserFullName()?.takeIf { it.isNotBlank() } ?: "শিক্ষার্থী"
    }
    var studentName by remember {
        mutableStateOf(
            if (com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context)) {
                sessionManager.getLiveCustomStudentName()?.takeIf { it.isNotBlank() } ?: defaultProfileName
            } else {
                defaultProfileName
            }
        )
    }
    var showNameChangeDialog by remember { mutableStateOf(false) }
    var showJoinNameDialog by remember { mutableStateOf(false) }
    var isUpdatingNameInLiveClass by remember { mutableStateOf(false) }

    // Pre-Join Lobby & Teacher Identity Join States (Strictly false by default so normal join is 100% student mode)
    var hasUserClickedJoin by rememberSaveable(classId, lessonId) { mutableStateOf(false) }
    var isJoiningClass by remember { mutableStateOf(false) }
    var isJoiningAsTeacher by remember { mutableStateOf(false) }
    var cachedRoomId by remember { mutableStateOf<String?>(null) }
    var cachedPrimaryToken by remember { mutableStateOf<String?>(null) }
    var cachedTeacherToken by remember { mutableStateOf<String?>(null) }
    var joinWithTeacherIdMode by rememberSaveable(classId, lessonId) { mutableStateOf(false) }
    var selectedTeacherForJoin by remember { mutableStateOf<DiscoveredRoomTeacher?>(null) }
    var showTeacherIdentityDialog by remember { mutableStateOf(false) }
    var showAutoPollDialog by remember { mutableStateOf(false) }

    // Live Delay state, Play/Pause state & Sync Trigger
    var liveDelaySeconds by remember { mutableIntStateOf(0) }
    var syncToLiveTrigger by remember { mutableLongStateOf(0L) }
    var isPausedByUser by remember { mutableStateOf(false) }
    var isLiveVideoPlaying by remember { mutableStateOf(true) }
    var isLiveVideoBuffering by remember { mutableStateOf(false) }

    // Video aspect ratio, zoom scale, and pan
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var videoScale by remember { mutableFloatStateOf(1.0f) }
    var videoPan by remember { mutableStateOf(Offset.Zero) }

    var selectedRole by remember { mutableStateOf("teacher") } // "teacher", "student", "all"

    var lectureSlideUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeLiveSlideUrl by remember { mutableStateOf<String?>(null) }
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
    val activeRoomTeacher by wsManager.activeRoomTeacher.collectAsState()
    val discoveredTeachers by wsManager.discoveredTeachers.collectAsState()
    val isJoinedAsTeacherIdentity by wsManager.isJoinedAsTeacherIdentity.collectAsState()

    LaunchedEffect(activeRoomTeacher) {
        if (selectedTeacherForJoin == null && activeRoomTeacher != null) {
            selectedTeacherForJoin = activeRoomTeacher
        }
    }

    val effectiveLessonTitle = serverRoomTitle.ifBlank { lessonTitle }

    if (!activeLiveSlideUrl.isNullOrBlank()) {
        com.example.ui.components.SlideViewerDialog(
            slideUrl = activeLiveSlideUrl!!,
            title = "$effectiveLessonTitle - লেকচার স্লাইড",
            onDismiss = { activeLiveSlideUrl = null }
        )
    }

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
    val visibleChatMessages = remember(chatMessages.size, chatMessages.toList(), canViewProfiles) {
        if (canViewProfiles) {
            chatMessages.toList()
        } else {
            chatMessages.filter { it.isMine || it.isTeacher }
        }
    }
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

    // 100ms Real Live Poll / Quiz State & Auto Poll Engine State
    val activePoll by wsManager.activePoll.collectAsState()
    val previousPolls by wsManager.previousPolls.collectAsState()
    val autoPollEnabled by wsManager.autoPollEnabled.collectAsState()
    val autoPollDelayMs by wsManager.autoPollDelayMs.collectAsState()
    val autoPollToastEvent by wsManager.autoPollToastEvent.collectAsState()
    var selectedPollIdForQuestion by remember { mutableStateOf<String?>(null) }
    var selectedPollIdForResult by remember { mutableStateOf<String?>(null) }
    var showPreviousPollsDialog by remember { mutableStateOf(false) }
    var showViewMessagesDialog by remember { mutableStateOf(false) }

    fun updateAutoPollConfig(enabled: Boolean, delayMs: Long) {
        if (!canViewProfiles) return
        val safeMs = delayMs.coerceIn(10L, 120_000L)
        sessionManager.setLiveAutoPollEnabled(enabled)
        sessionManager.setLiveAutoPollDelayMs(safeMs)
        wsManager.setAutoPollConfigurationMs(enabled = enabled, delayMs = safeMs)
    }

    LaunchedEffect(autoPollToastEvent) {
        val msg = autoPollToastEvent
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            wsManager.consumeAutoPollToastEvent()
        }
    }

    LaunchedEffect(canViewProfiles) {
        wsManager.setCanViewAllMessages(canViewProfiles)
        if (canViewProfiles) {
            wsManager.setAutoPollConfigurationMs(
                enabled = sessionManager.getLiveAutoPollEnabled(),
                delayMs = sessionManager.getLiveAutoPollDelayMs()
            )
        } else {
            selectedRole = "teacher"
            joinWithTeacherIdMode = false
            showViewMessagesDialog = false
            showNameChangeDialog = false
            showJoinNameDialog = false
            showTeacherIdentityDialog = false
            showAutoPollDialog = false
            wsManager.setAutoPollConfigurationMs(enabled = false, delayMs = 670L)
            studentName = defaultProfileName
            chatMessages.removeAll { !it.isMine && !it.isTeacher }
        }
    }

    LaunchedEffect(classId, lessonId) {
        wsManager.setCanViewAllMessages(canViewProfiles)
        val freshPerm = com.example.security.DeviceActivationRepository.checkProfileViewPermission(
            context = context,
            forceRefresh = true
        )
        canViewProfiles = freshPerm
        wsManager.setCanViewAllMessages(freshPerm)
        if (freshPerm) {
            wsManager.setAutoPollConfigurationMs(
                enabled = sessionManager.getLiveAutoPollEnabled(),
                delayMs = sessionManager.getLiveAutoPollDelayMs()
            )
        } else {
            selectedRole = "teacher"
            joinWithTeacherIdMode = false
            showViewMessagesDialog = false
            showNameChangeDialog = false
            showJoinNameDialog = false
            showTeacherIdentityDialog = false
            showAutoPollDialog = false
            wsManager.setAutoPollConfigurationMs(enabled = false, delayMs = 670L)
            studentName = defaultProfileName
            chatMessages.removeAll { !it.isMine && !it.isTeacher }
        }
    }

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
    // (When `canViewProfiles && autoPollEnabled` is true, do NOT force-open the poll screen—
    // the background Auto Poll Engine automatically submits the correct answer after `autoPollDelaySeconds`!)
    LaunchedEffect(activePoll?.pollId, activePoll?.state, canViewProfiles, autoPollEnabled) {
        val poll = activePoll
        if (uiState is LiveClassState.Playing &&
            poll != null &&
            poll.isLive &&
            poll.userResponse == null &&
            selectedPollIdForQuestion == null &&
            !(canViewProfiles && autoPollEnabled)
        ) {
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
            val permDeferred = async {
                com.example.security.DeviceActivationRepository.checkProfileViewPermission(
                    context = context,
                    forceRefresh = true
                )
            }

            val academicInfo = academicDeferred.await().getOrNull()
            val roomResult = roomDeferred.await()
            val roomId = roomResult.getOrNull()
            val latestPerm = permDeferred.await()
            canViewProfiles = latestPerm
            wsManager.setCanViewAllMessages(latestPerm)
            if (latestPerm) {
                wsManager.setAutoPollConfigurationMs(
                    enabled = sessionManager.getLiveAutoPollEnabled(),
                    delayMs = sessionManager.getLiveAutoPollDelayMs()
                )
            } else {
                selectedRole = "teacher"
                joinWithTeacherIdMode = false
                showViewMessagesDialog = false
                studentName = defaultProfileName
                wsManager.setAutoPollConfigurationMs(enabled = false, delayMs = 670L)
            }

            // IMPORTANT: Do NOT extract teacher identity, do NOT call setInitialTeacherHint,
            // and do NOT probe the room before the user explicitly clicks "শিক্ষকের আইডি দিয়ে জয়েন করুন"!
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
            cachedRoomId = roomId

            if (!roomId.isNullOrBlank()) {
                // Fetch ONLY the student's normal primary token on initial load (includeTeacherListener = false)
                val tokensResult = service.getHmsRoomTokens(
                    roomId = roomId,
                    userToken = userToken,
                    includeTeacherListener = false,
                    teacherUserId = null,
                    teacherName = null
                )
                val roomTokens = tokensResult.getOrNull()
                val hmsToken = roomTokens?.primaryToken
                cachedPrimaryToken = hmsToken
                cachedTeacherToken = null

                if (!hmsToken.isNullOrBlank()) {
                    if (!hasUserClickedJoin) {
                        if (academicInfo?.isOnGoing == false && !academicInfo.playbackUrl.isNullOrBlank()) {
                            wsManager.setMasterUrl(academicInfo.playbackUrl)
                        }
                        uiState = LiveClassState.Preview(viewerCount)
                        return@launch
                    } else {
                        wsManager.connect(
                            token = hmsToken,
                            studentName = studentName,
                            teacherToken = null,
                            joinWithTeacherIdentity = false,
                            targetTeacher = null
                        )
                        if (academicInfo?.isOnGoing == false && !academicInfo.playbackUrl.isNullOrBlank()) {
                            wsManager.setMasterUrl(academicInfo.playbackUrl)
                            uiState = LiveClassState.Playing
                        } else {
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
            }

            // Fallback when 100ms room/token is not active (e.g. recorded/fallback stream)
            val fallbackUrl = academicInfo?.playbackUrl?.takeIf { it.isNotBlank() }
                ?: cleanDiscovered.firstOrNull()
            if (!fallbackUrl.isNullOrBlank()) {
                wsManager.setMasterUrl(fallbackUrl)
                uiState = if (hasUserClickedJoin) LiveClassState.Playing else LiveClassState.Preview(viewerCount)
                return@launch
            }

            val errMsg = roomResult.exceptionOrNull()?.message
                ?: "লাইভ ক্লাসের ব্রডকাস্ট সংযোগ পাওয়া যায়নি। ক্লাস শুরু হতে বিলম্ব হলে অনুগ্রহ করে একটু অপেক্ষা করে রিফ্রেশ করুন।"
            uiState = LiveClassState.Error(errMsg)
        }
    }

    /**
     * Normal Student Join:
     * Joins the live class strictly as a normal student using the user's own student ID and name,
     * even if `canViewProfiles == true`. Does NOT extract or use any teacher ID or teacher token.
     */
    fun joinLiveClassAsStudent(requestedDisplayName: String) {
        if (isJoiningClass || isJoiningAsTeacher) return
        isJoiningClass = true
        joinWithTeacherIdMode = false
        selectedTeacherForJoin = null
        coroutineScope.launch {
            val userToken = sessionManager.getAccessToken()
            val effectiveName = if (canViewProfiles) {
                requestedDisplayName.trim().ifBlank { defaultProfileName }
            } else {
                defaultProfileName
            }
            studentName = effectiveName
            if (canViewProfiles) {
                sessionManager.setLiveCustomStudentName(effectiveName)
            }

            var primaryTokenToUse = cachedPrimaryToken
            val roomId = cachedRoomId

            if (!roomId.isNullOrBlank() && !userToken.isNullOrBlank()) {
                if (canViewProfiles && effectiveName != defaultProfileName) {
                    runCatching { service.updateShikhoProfileName(effectiveName, userToken) }
                }
                val freshTokens = service.getHmsRoomTokens(
                    roomId = roomId,
                    userToken = userToken,
                    includeTeacherListener = false,
                    teacherUserId = null,
                    teacherName = null
                ).getOrNull()
                if (freshTokens != null) {
                    primaryTokenToUse = freshTokens.primaryToken
                    cachedPrimaryToken = freshTokens.primaryToken
                    cachedTeacherToken = null
                }
            }

            hasUserClickedJoin = true

            if (!primaryTokenToUse.isNullOrBlank()) {
                wsManager.connect(
                    token = primaryTokenToUse,
                    studentName = effectiveName,
                    teacherToken = null,
                    joinWithTeacherIdentity = false,
                    targetTeacher = null
                )
            }

            isJoiningClass = false
            uiState = LiveClassState.Playing

            coroutineScope.launch {
                kotlinx.coroutines.delay(5000L)
                if (wsManager.masterUrl.value.isNullOrBlank()) {
                    val fallback = candidateUrls.firstOrNull { it.isNotBlank() }
                    if (!fallback.isNullOrBlank()) {
                        wsManager.setMasterUrl(fallback)
                    }
                }
            }
        }
    }

    /**
     * Explicit Opt-In Teacher ID Join (ONLY for `canViewProfiles == true`):
     * Triggered ONLY when the user explicitly clicks "শিক্ষকের আইডি দিয়ে জয়েন করুন".
     * Extracts the active class teacher's ID and name on demand, fetches the teacher token,
     * and joins the live class using the teacher's identity.
     */
    fun joinLiveClassWithTeacherId() {
        if (!canViewProfiles || isJoiningClass || isJoiningAsTeacher) return
        isJoiningAsTeacher = true
        joinWithTeacherIdMode = true
        coroutineScope.launch {
            val userToken = sessionManager.getAccessToken()
            val roomId = cachedRoomId
            var primaryTokenToUse = cachedPrimaryToken
            var teacherTokenToUse: String? = null
            var extractedTeacher = selectedTeacherForJoin ?: activeRoomTeacher

            if (!userToken.isNullOrBlank()) {
                val academicInfo = service.getAcademicLiveClassInfo(classId, lessonId, userToken).getOrNull()
                if (primaryTokenToUse.isNullOrBlank() && !roomId.isNullOrBlank()) {
                    primaryTokenToUse = service.getHmsToken(roomId, userToken).getOrNull()
                    cachedPrimaryToken = primaryTokenToUse
                }
                extractedTeacher = wsManager.extractRoomTeacherIdentityOnDemand(
                    token = primaryTokenToUse.orEmpty(),
                    fallbackTeacherId = academicInfo?.teacherId,
                    fallbackTeacherName = academicInfo?.teacherName,
                    fallbackTeacherAvatar = academicInfo?.teacherAvatar
                )
                selectedTeacherForJoin = extractedTeacher

                if (!roomId.isNullOrBlank()) {
                    val freshTokens = service.getHmsRoomTokens(
                        roomId = roomId,
                        userToken = userToken,
                        includeTeacherListener = true,
                        teacherUserId = extractedTeacher.userId,
                        teacherName = extractedTeacher.name
                    ).getOrNull()
                    if (freshTokens != null) {
                        primaryTokenToUse = freshTokens.primaryToken
                        teacherTokenToUse = freshTokens.teacherToken
                        cachedPrimaryToken = freshTokens.primaryToken
                        cachedTeacherToken = freshTokens.teacherToken
                    }
                }
            }

            val teacherDisplayName = extractedTeacher?.name?.trim()?.takeIf { it.isNotBlank() }
                ?: roomTeacherName.ifBlank { studentName }
            studentName = teacherDisplayName
            hasUserClickedJoin = true

            if (!primaryTokenToUse.isNullOrBlank()) {
                wsManager.connect(
                    token = primaryTokenToUse,
                    studentName = teacherDisplayName,
                    teacherToken = teacherTokenToUse,
                    joinWithTeacherIdentity = true,
                    targetTeacher = extractedTeacher
                )
            }

            isJoiningAsTeacher = false
            uiState = LiveClassState.Playing
            Toast.makeText(
                context,
                "শিক্ষক '$teacherDisplayName' এর আইডি দিয়ে ক্লাসে জয়েন করা হয়েছে ✨",
                Toast.LENGTH_SHORT
            ).show()

            coroutineScope.launch {
                kotlinx.coroutines.delay(5000L)
                if (wsManager.masterUrl.value.isNullOrBlank()) {
                    val fallback = candidateUrls.firstOrNull { it.isNotBlank() }
                    if (!fallback.isNullOrBlank()) {
                        wsManager.setMasterUrl(fallback)
                    }
                }
            }
        }
    }

    LaunchedEffect(classId, lessonId) {
        startLiveClassConnection()
    }

    LaunchedEffect(masterUrl, wsError, hasUserClickedJoin) {
        if (!hasUserClickedJoin) return@LaunchedEffect
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

    val incomingHistory by wsManager.receivedMessagesHistory.collectAsState()
    LaunchedEffect(incomingHistory, canViewProfiles) {
        if (!canViewProfiles) {
            chatMessages.removeAll { !it.isMine && !it.isTeacher }
        }
        var addedNew = false
        for (msg in incomingHistory) {
            if (!canViewProfiles && !msg.isTeacher && !msg.isMine) continue
            val existingIdx = chatMessages.indexOfFirst { it.id == msg.id }
            if (existingIdx < 0) {
                chatMessages.add(msg)
                addedNew = true
            }
        }
        if (addedNew && visibleChatMessages.isNotEmpty()) {
            coroutineScope.launch {
                chatListState.animateScrollToItem((visibleChatMessages.size - 1).coerceAtLeast(0))
            }
        }
    }

    fun sendUserChat(textToSend: String) {
        if (!canViewProfiles) {
            Toast.makeText(context, "শুধুমাত্র প্রোফাইল ভিউ পারমিশন অন থাকলে কমেন্ট করা যাবে।", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanText = textToSend.trim()
        if (cleanText.isBlank() || !isChatEnabled) return
        if (!isConnected) {
            Toast.makeText(context, "লাইভ সার্ভারের সাথে সংযোগ নেই! বার্তা পাঠানো সম্ভব হয়নি।", Toast.LENGTH_SHORT).show()
            return
        }
        val myName = studentName.ifBlank { "আপনি" }
        val effectiveTargetRole = selectedRole
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
                isTeacher = false,
                role = "student",
                targetRole = effectiveTargetRole,
                senderPeerId = wsManager.getCurrentPeerId(),
                status = ChatDeliveryStatus.SENDING,
                isMine = true
            )
        )
        chatMessageInput = ""
        keyboardController?.hide()
        coroutineScope.launch {
            val targetIdx = (if (canViewProfiles) chatMessages.size else chatMessages.count { it.isMine || it.isTeacher }) - 1
            if (targetIdx >= 0) {
                chatListState.animateScrollToItem(targetIdx)
            }
        }

        wsManager.sendChatMessage(
            text = cleanText,
            studentName = myName,
            role = "student",
            targetRole = effectiveTargetRole
        ) { isSuccess ->
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val idx = chatMessages.indexOfFirst { it.id == msgId }
                if (idx >= 0) {
                    chatMessages[idx] = chatMessages[idx].copy(
                        status = if (isSuccess) ChatDeliveryStatus.SENT else ChatDeliveryStatus.FAILED
                    )
                }
                if (!isSuccess) {
                    Toast.makeText(context, "বার্তা পাঠানো যায়নি! অনুগ্রহ করে পুনরায় চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun retryChatMessage(msgId: String) {
        if (!canViewProfiles) return
        val idx = chatMessages.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val msg = chatMessages[idx]
        if (!isConnected) {
            Toast.makeText(context, "লাইভ সার্ভারের সাথে সংযোগ নেই! পুনরায় সংযোগের চেষ্টা করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            return
        }
        val effectiveTargetRole = msg.targetRole.ifBlank { selectedRole }
        chatMessages[idx] = msg.copy(status = ChatDeliveryStatus.SENDING, targetRole = effectiveTargetRole)
        wsManager.sendChatMessage(
            text = msg.message,
            studentName = msg.senderName,
            role = msg.role,
            targetRole = effectiveTargetRole
        ) { isSuccess ->
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val currentIdx = chatMessages.indexOfFirst { it.id == msgId }
                if (currentIdx >= 0) {
                    chatMessages[currentIdx] = chatMessages[currentIdx].copy(
                        status = if (isSuccess) ChatDeliveryStatus.SENT else ChatDeliveryStatus.FAILED
                    )
                }
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
                            text = "লাইভ ক্লাসের তথ্য লোড হচ্ছে...",
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
                    var lobbyNameInput by remember(studentName, canViewProfiles) {
                        mutableStateOf(if (canViewProfiles) studentName else defaultProfileName)
                    }
                    val displayTeacherName = selectedTeacherForJoin?.name?.takeIf { it.isNotBlank() }
                        ?: activeRoomTeacher?.name?.takeIf { it.isNotBlank() }
                        ?: roomTeacherName.ifBlank { "নির্ধারিত শিক্ষক" }
                    val displayTeacherId = selectedTeacherForJoin?.userId?.takeIf { it.isNotBlank() }
                        ?: activeRoomTeacher?.userId?.takeIf { it.isNotBlank() }
                        ?: selectedTeacherForJoin?.peerId?.takeIf { it.isNotBlank() }
                        ?: activeRoomTeacher?.peerId.orEmpty()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0B1120))
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Navigation Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    wsManager.disconnect()
                                    onBack()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF162032))
                                    .border(1.dp, Color(0xFF26354D), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isLiveOnGoing) Color(0xFFDC2626).copy(alpha = 0.18f) else Color(0xFF059669).copy(alpha = 0.18f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isLiveOnGoing) Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isLiveOnGoing) Color(0xFFEF4444) else Color(0xFF10B981))
                                    )
                                    Text(
                                        text = if (viewerCount > 0) {
                                            "LIVE • $viewerCount জন ক্লাসে আছেন"
                                        } else if (isLiveOnGoing) {
                                            "LIVE • ক্লাস চলছে"
                                        } else {
                                            "রেকর্ডেড / প্রিভিউ"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Class Banner Card
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF111C33),
                            border = BorderStroke(1.dp, Color(0xFF26354D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier.size(58.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                    border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = subjectName,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = effectiveLessonTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Teacher Info Badge
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF162032),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "ক্লাস নিচ্ছেন: $displayTeacherName",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE2E8F0)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // User Name Card (Editable ONLY if canViewProfiles == true, strictly read-only otherwise)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF111C33),
                            border = BorderStroke(
                                1.dp,
                                if (canViewProfiles) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color(0xFF26354D)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Badge,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "আপনার প্রদর্শিত নাম",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (canViewProfiles) Color(0xFF064E3B) else Color(0xFF1E293B),
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (canViewProfiles) Color(0xFF10B981) else Color(0xFF475569)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (canViewProfiles) Icons.Default.VerifiedUser else Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = if (canViewProfiles) Color(0xFF34D399) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = if (canViewProfiles) "নাম পরিবর্তন অনুমোদিত" else "নির্ধারিত নাম",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (canViewProfiles) Color(0xFF6EE7B7) else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (canViewProfiles) {
                                    OutlinedTextField(
                                        value = lobbyNameInput,
                                        onValueChange = { lobbyNameInput = it },
                                        singleLine = true,
                                        placeholder = {
                                            Text("ক্লাসে যে নামে জয়েন করতে চান লিখুন...", color = Color(0xFF64748B), fontSize = 12.5.sp)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = {
                                            if (lobbyNameInput.isNotBlank()) {
                                                IconButton(onClick = { lobbyNameInput = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
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

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "কুইক সেট:",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF162032),
                                            border = BorderStroke(0.8.dp, Color(0xFF334155)),
                                            modifier = Modifier.clickable { lobbyNameInput = defaultProfileName }
                                        ) {
                                            Text(
                                                text = "নিজের নাম ($defaultProfileName)",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF38BDF8),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        if (displayTeacherName.isNotBlank() && displayTeacherName != "নির্ধারিত শিক্ষক") {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF162032),
                                                border = BorderStroke(0.8.dp, Color(0xFFFBBF24).copy(alpha = 0.5f)),
                                                modifier = Modifier.clickable { lobbyNameInput = displayTeacherName }
                                            ) {
                                                Text(
                                                    text = "স্যারের নাম ($displayTeacherName)",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFFFBBF24),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Strictly read-only name display for regular students without can_view_profiles
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0B1120),
                                        border = BorderStroke(1.dp, Color(0xFF26354D)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = defaultProfileName,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Locked",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 1. Primary Normal Student Join Button (Joins strictly with user's own student info)
                        Button(
                            onClick = { joinLiveClassAsStudent(lobbyNameInput) },
                            enabled = !isJoiningClass && !isJoiningAsTeacher,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                disabledContainerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (isJoiningClass) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "আপনার আইডি দিয়ে জয়েন হচ্ছে...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ক্লাসে জয়েন করুন",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        // 2. Extra Options ONLY for `canViewProfiles == true`:
                        //    (A) Separate Opt-In Button: "শিক্ষকের আইডি দিয়ে জয়েন করুন" (Only extracts teacher info when clicked!)
                        //    (B) Auto Poll ("অটো") Configuration Card
                        if (canViewProfiles) {
                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF111C33),
                                border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.65f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.School,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFBBF24),
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "শিক্ষকের আইডি দিয়ে প্রবেশ (ঐচ্ছিক)",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "উপরের বাটনে ক্লিক করলে সাধারণ শিক্ষার্থীর মতো আপনার নিজের আইডিতে জয়েন হবে। শুধুমাত্র নিচের বাটনে ক্লিক করলেই শিক্ষকের তথ্য বের করে শিক্ষকের আইডিতে জয়েন করবে।",
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { joinLiveClassWithTeacherId() },
                                        enabled = !isJoiningClass && !isJoiningAsTeacher,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD97706),
                                            disabledContainerColor = Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        if (isJoiningAsTeacher) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "শিক্ষকের তথ্য বের করে জয়েন হচ্ছে...",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(19.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "শিক্ষকের আইডি দিয়ে জয়েন করুন",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            com.example.liveclass.poll.AutoPollControlCard(
                                enabled = autoPollEnabled,
                                delayMs = autoPollDelayMs,
                                onConfigChanged = { enabled, ms ->
                                    updateAutoPollConfig(enabled, ms)
                                }
                            )
                        }
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
                                    isPausedByUser = isPausedByUser,
                                    onIsPlayingChanged = { isLiveVideoPlaying = it },
                                    onBufferingChanged = { isLiveVideoBuffering = it },
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

                            // Center Play / Pause Button Overlay (Visible when controls are shown or when paused)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = (showPlayerControls || isPausedByUser) && videoScale <= 1.05f && !isLiveVideoBuffering,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.68f),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isPausedByUser) Color(0xFF10B981) else Color(0xFF38BDF8).copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            lastUserInteractionTime = System.currentTimeMillis()
                                            isPausedByUser = !isPausedByUser
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPausedByUser) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = if (isPausedByUser) "Play Live Class" else "Pause Live Class",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

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
                            val isBehindLiveEdge = isPausedByUser || liveDelaySeconds > 15

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
                                                    if (isBehindLiveEdge) Color(0xFFE11D48) else Color(0xFFDC2626)
                                                } else Color(0xFF059669),
                                                modifier = Modifier.clickable {
                                                    if (effectiveIsLive) {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        isPausedByUser = false
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
                                                            when {
                                                                isPausedByUser -> "LIVE (PAUSED)"
                                                                isBehindLiveEdge -> "LIVE (-${liveDelaySeconds}s)"
                                                                else -> "LIVE"
                                                            }
                                                        } else "REC",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        softWrap = false
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
                                                        maxLines = 1,
                                                        softWrap = false
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
                                                    .then(
                                                        if (canViewProfiles) {
                                                            Modifier.clickable {
                                                                lastUserInteractionTime = System.currentTimeMillis()
                                                                showNameChangeDialog = true
                                                            }
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
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
                                                    if (canViewProfiles) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit Name",
                                                            tint = Color(0xFF38BDF8),
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                    }
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
                                                        maxLines = 1,
                                                        softWrap = false
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
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ----------------------------------------------------
                            // BOTTOM CONTROLS OVERLAY (Play/Pause, Live Sync, Fit/Zoom, PiP, Slides, Fullscreen)
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
                                        // Left Play/Pause + Live Edge Sync Indicator
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .padding(end = 6.dp)
                                        ) {
                                            // Live Play / Pause Pill Button
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isPausedByUser) Color(0xFF10B981).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.65f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isPausedByUser) Color(0xFF10B981) else Color(0xFF38BDF8).copy(alpha = 0.7f)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        isPausedByUser = !isPausedByUser
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPausedByUser) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                        contentDescription = if (isPausedByUser) "Play" else "Pause",
                                                        tint = if (isPausedByUser) Color(0xFF34D399) else Color(0xFF38BDF8),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = if (isPausedByUser) "প্লে" else "পজ",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            // Live Edge Sync Pill
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isBehindLiveEdge) Color(0xFFE11D48).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isBehindLiveEdge) Color(0xFFF43F5E) else Color(0xFF10B981).copy(alpha = 0.7f)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        lastUserInteractionTime = System.currentTimeMillis()
                                                        isPausedByUser = false
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
                                                            .background(if (isBehindLiveEdge) Color(0xFFF43F5E) else Color(0xFF10B981))
                                                    )
                                                    Text(
                                                        text = when {
                                                            isPausedByUser -> "⚡ লাইভে ফিরুন"
                                                            isBehindLiveEdge -> "⚡ ${liveDelaySeconds}s পিছিয়ে • সিঙ্ক করুন"
                                                            else -> "লাইভ সিঙ্কড"
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
                                                        lectureSlideUrls.firstOrNull()?.takeIf { it.isNotBlank() }?.let { url ->
                                                            activeLiveSlideUrl = url
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
                                            Icon(
                                                imageVector = if (canViewProfiles) Icons.Default.Chat else Icons.Default.Campaign,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (canViewProfiles) "লাইভ চ্যাট" else "শিক্ষকের ঘোষণা",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(Modifier.weight(1f))
                                            // View Messages button in landscape drawer (ONLY for Admin-permitted devices)
                                            if (canViewProfiles) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                                    border = BorderStroke(0.8.dp, Color(0xFF38BDF8)),
                                                    modifier = Modifier.clickable { showViewMessagesDialog = true }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Forum, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(10.dp))
                                                        Text(
                                                            text = "ভিউ (${visibleChatMessages.size})",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF38BDF8)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            // Student name chip in landscape drawer (Editable ONLY if canViewProfiles == true)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF1E293B),
                                                border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .then(
                                                        if (canViewProfiles) {
                                                            Modifier.clickable { showNameChangeDialog = true }
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (canViewProfiles) Icons.Default.Edit else Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(10.dp)
                                                    )
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
                                            items(visibleChatMessages, key = { it.id }) { item ->
                                                ChatMessageCard(
                                                    item = item,
                                                    myName = studentName,
                                                    onRetry = { retryChatMessage(item.id) }
                                                )
                                            }
                                        }

                                        // Input (Strictly ONLY for canViewProfiles == true)
                                        if (canViewProfiles) {
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
                                                    placeholder = {
                                                        Text(
                                                            "বার্তা লিখুন...",
                                                            fontSize = 11.sp,
                                                            color = Color.White.copy(0.5f)
                                                        )
                                                    },
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
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF162032),
                                                border = BorderStroke(0.8.dp, Color(0xFF26354D)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = null,
                                                        tint = Color(0xFFF59E0B),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "শুধুমাত্র প্রোফাইল ভিউ পারমিশন থাকলে কমেন্ট করা যাবে",
                                                        fontSize = 10.5.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
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

                                        // Interactive Action Bar: Hand Raise | Poll/Quiz | View Messages | Resolution | Student Name
                                        // Horizontally scrollable so nothing ever gets cut off on any screen width!
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
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

                                            // 2b. Auto Poll ("অটো") Pill (Strictly ONLY for Admin-permitted devices with can_view_profiles)
                                            if (canViewProfiles) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (autoPollEnabled) Color(0xFF064E3B) else Color(0xFF1E293B),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (autoPollEnabled) Color(0xFF10B981) else Color(0xFF334155)
                                                    ),
                                                    modifier = Modifier.clickable { showAutoPollDialog = true }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Bolt,
                                                            contentDescription = "Auto Poll",
                                                            tint = if (autoPollEnabled) Color(0xFF34D399) else Color(0xFFFBBF24),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = if (autoPollEnabled) {
                                                                "অটো (${com.example.liveclass.poll.formatPollDelayMsToSeconds(autoPollDelayMs)}s)"
                                                            } else {
                                                                "অটো পোল"
                                                            },
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (autoPollEnabled) Color(0xFF6EE7B7) else Color.White,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }

                                            // 3. View Messages Pill ("ভিউ ম্যাসেজ" — ONLY for Admin-permitted devices with can_view_profiles)
                                            if (canViewProfiles) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF0284C7).copy(alpha = 0.22f),
                                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                    modifier = Modifier.clickable { showViewMessagesDialog = true }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MarkChatRead,
                                                            contentDescription = "View Messages",
                                                            tint = Color(0xFF38BDF8),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = if (visibleChatMessages.isNotEmpty()) {
                                                                "ভিউ ম্যাসেজ (${visibleChatMessages.size})"
                                                            } else {
                                                                "ভিউ ম্যাসেজ"
                                                            },
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFE0F2FE),
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                // Teacher ID Join / Switch Pill (Only active as teacher when explicitly selected)
                                                val activeTeacherLabel = if (isJoinedAsTeacherIdentity) {
                                                    selectedTeacherForJoin?.name?.takeIf { it.isNotBlank() }
                                                        ?: activeRoomTeacher?.name?.takeIf { it.isNotBlank() }
                                                        ?: roomTeacherName.ifBlank { "শিক্ষক আইডি" }
                                                } else {
                                                    "শিক্ষক আইডি জয়েন"
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isJoinedAsTeacherIdentity) Color(0xFF064E3B) else Color(0xFF1E293B),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isJoinedAsTeacherIdentity) Color(0xFF10B981) else Color(0xFFFBBF24).copy(alpha = 0.7f)
                                                    ),
                                                    modifier = Modifier.clickable { showTeacherIdentityDialog = true }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.School,
                                                            contentDescription = "Teacher Identity",
                                                            tint = if (isJoinedAsTeacherIdentity) Color(0xFF34D399) else Color(0xFFFBBF24),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = if (isJoinedAsTeacherIdentity) "শিক্ষক: $activeTeacherLabel" else activeTeacherLabel,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isJoinedAsTeacherIdentity) Color(0xFF6EE7B7) else Color(0xFFFDE68A),
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }

                                            // 4. Always-Accessible Resolution Pill (Right in the Action Bar too!)
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

                                            // 5. Student Display Name Chip (Editable ONLY if canViewProfiles == true)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF1E293B),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (canViewProfiles) Color(0xFF38BDF8).copy(alpha = 0.7f) else Color(0xFF334155)
                                                ),
                                                modifier = Modifier
                                                    .widthIn(max = 160.dp)
                                                    .then(
                                                        if (canViewProfiles) {
                                                            Modifier.clickable { showNameChangeDialog = true }
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (canViewProfiles) Icons.Default.Edit else Icons.Default.Person,
                                                        contentDescription = "Student Name",
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
                                    if (canViewProfiles) {
                                        // Permitted Device Mode: Full Role Target Selector Bar + "ভিউ ম্যাসেজ" Button
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Forum,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "রোল:",
                                                fontSize = 11.sp,
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
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = roleIcon,
                                                            contentDescription = null,
                                                            tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Text(
                                                            text = roleLabel,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            // Prominent "ভিউ ম্যাসেজ" (View Messages) Button
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF0C2440),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                modifier = Modifier.clickable { showViewMessagesDialog = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Visibility,
                                                        contentDescription = "ভিউ ম্যাসেজ",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = if (visibleChatMessages.isNotEmpty()) {
                                                            "ভিউ ম্যাসেজ (${visibleChatMessages.size})"
                                                        } else {
                                                            "ভিউ ম্যাসেজ"
                                                        },
                                                        fontSize = 10.5.sp,
                                                        color = Color(0xFF38BDF8),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Regular Student Mode: Read-Only Teacher Announcements Header
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF162032),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Campaign,
                                                        contentDescription = null,
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "শিক্ষকের মেসেজ ও নোটিশ",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "শিক্ষকের সকল গুরুত্বপূর্ণ বার্তা এখানে দেখা যাবে",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    // Pinned Teacher Message Announcement Banner (if any)
                                    val pinnedTeacherMsg = visibleChatMessages.lastOrNull { it.isTeacher && (it.isPinned || it.id.startsWith("pin_")) }
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
                                        if (visibleChatMessages.isEmpty()) {
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
                                                                    imageVector = if (canViewProfiles) Icons.Default.ChatBubbleOutline else Icons.Default.Campaign,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF38BDF8).copy(alpha = 0.7f),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = if (canViewProfiles) "লাইভ চ্যাটে স্বাগতম!" else "শিক্ষকের ঘোষণা ও মেসেজ বোর্ড",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFCBD5E1)
                                                        )
                                                        Text(
                                                            text = if (canViewProfiles) {
                                                                "সকল শিক্ষার্থীর কমেন্ট এবং শিক্ষকের মেসেজ এখানে রিয়েল-টাইমে দেখা যাবে"
                                                            } else {
                                                                "ক্লাস চলাকালীন শিক্ষকের পাঠানো সকল মেসেজ ও লিংক এখানে প্রদর্শিত হবে"
                                                            },
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF64748B),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            items(visibleChatMessages, key = { it.id }) { item ->
                                                ChatMessageCard(
                                                    item = item,
                                                    myName = studentName,
                                                    onRetry = { retryChatMessage(item.id) }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (canViewProfiles) {
                                        // Quick Comment Chips (ONLY for canViewProfiles == true)
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

                                        // Chat Input or Disabled banner for permitted users
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
                                                        val roleHint = when (selectedRole) {
                                                            "teacher" -> "শুধুমাত্র শিক্ষককে মেসেজ লিখুন..."
                                                            "student" -> "সকল স্টুডেন্টকে মেসেজ লিখুন..."
                                                            else -> "সকলকে (All) মেসেজ লিখুন..."
                                                        }
                                                        Text(
                                                            roleHint,
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
                                    } else {
                                        // Regular Student Mode: Commenting Disabled Notice Card
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF162032),
                                            border = BorderStroke(1.dp, Color(0xFF26354D)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF59E0B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "শুধুমাত্র প্রোফাইল ভিউ পারমিশন অন থাকা ব্যবহারকারীরা লাইভ ক্লাসে কমেন্ট করতে পারবেন।",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFCBD5E1),
                                                    lineHeight = 16.sp
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

    // Custom Display Name Dialog (Strictly ONLY for users with can_view_profiles == true)
    if (canViewProfiles && (showNameChangeDialog || showJoinNameDialog)) {
        val isFirstJoin = showJoinNameDialog
        var nameInput by remember(studentName) { mutableStateOf(studentName) }

        AlertDialog(
            onDismissRequest = {
                if (!isUpdatingNameInLiveClass) {
                    showNameChangeDialog = false
                    showJoinNameDialog = false
                }
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
                        text = "লাইভ চ্যাটে বার্তা পাঠানোর সময় এবং শিক্ষক ও অন্য শিক্ষার্থীদের কাছে আপনার এই নতুন নামটি ১০০% বাস্তবে আপডেট হবে:",
                        fontSize = 12.5.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        enabled = !isUpdatingNameInLiveClass,
                        placeholder = { Text("আপনার নাম লিখুন...", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (nameInput.isNotBlank() && !isUpdatingNameInLiveClass) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "কুইক সেট:",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0B1120),
                            border = BorderStroke(0.8.dp, Color(0xFF334155)),
                            modifier = Modifier.clickable(enabled = !isUpdatingNameInLiveClass) { nameInput = defaultProfileName }
                        ) {
                            Text(
                                text = "প্রোফাইল নাম",
                                fontSize = 10.5.sp,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        val currentTeacherDisplayName = activeRoomTeacher?.name?.takeIf { it.isNotBlank() } ?: roomTeacherName
                        if (currentTeacherDisplayName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0B1120),
                                border = BorderStroke(0.8.dp, Color(0xFFFBBF24).copy(alpha = 0.5f)),
                                modifier = Modifier.clickable(enabled = !isUpdatingNameInLiveClass) { nameInput = currentTeacherDisplayName }
                            ) {
                                Text(
                                    text = "স্যারের নাম ($currentTeacherDisplayName)",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isUpdatingNameInLiveClass) return@Button
                        val clean = nameInput.trim().ifBlank { defaultProfileName }
                        isUpdatingNameInLiveClass = true
                        coroutineScope.launch {
                            studentName = clean
                            sessionManager.setLiveCustomStudentName(clean)
                            val userToken = sessionManager.getAccessToken()
                            val roomId = cachedRoomId
                            var freshPrimaryToken: String? = null
                            if (!userToken.isNullOrBlank()) {
                                runCatching { service.updateShikhoProfileName(clean, userToken) }
                                if (!roomId.isNullOrBlank()) {
                                    val targetTeacher = if (joinWithTeacherIdMode) (selectedTeacherForJoin ?: activeRoomTeacher) else null
                                    val freshTokens = service.getHmsRoomTokens(
                                        roomId = roomId,
                                        userToken = userToken,
                                        includeTeacherListener = joinWithTeacherIdMode,
                                        teacherUserId = targetTeacher?.userId,
                                        teacherName = targetTeacher?.name
                                    ).getOrNull()
                                    if (freshTokens != null) {
                                        freshPrimaryToken = freshTokens.primaryToken
                                        cachedPrimaryToken = freshTokens.primaryToken
                                        cachedTeacherToken = if (joinWithTeacherIdMode) freshTokens.teacherToken else null
                                    }
                                }
                            }
                            wsManager.updateStudentName(clean, freshPrimaryToken)
                            isUpdatingNameInLiveClass = false
                            showNameChangeDialog = false
                            showJoinNameDialog = false
                            Toast.makeText(context, "লাইভ ক্লাসে আপনার নাম '$clean' হিসেবে আপডেট হয়েছে ✨", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isUpdatingNameInLiveClass,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isUpdatingNameInLiveClass) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "আপডেট হচ্ছে...",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.5.sp
                        )
                    } else {
                        Text(
                            text = if (isFirstJoin) "এই নামে যোগ দিন" else "সংরক্ষণ করুন",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.5.sp
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isUpdatingNameInLiveClass,
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

    // Auto Poll Configuration Dialog (Strictly ONLY for canViewProfiles == true)
    if (canViewProfiles && showAutoPollDialog) {
        AlertDialog(
            onDismissRequest = { showAutoPollDialog = false },
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "অটো পোল লাইভ এক্সাম সেটিংস",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                com.example.liveclass.poll.AutoPollControlCard(
                    enabled = autoPollEnabled,
                    delayMs = autoPollDelayMs,
                    onConfigChanged = { enabled, ms ->
                        updateAutoPollConfig(enabled, ms)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showAutoPollDialog = false }) {
                    Text("সম্পন্ন", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Teacher Identity Switcher / Re-Sync Dialog inside Live Class (ONLY for canViewProfiles == true)
    if (canViewProfiles && showTeacherIdentityDialog) {
        AlertDialog(
            onDismissRequest = { if (!isJoiningAsTeacher) showTeacherIdentityDialog = false },
            containerColor = Color(0xFF111C33),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "শিক্ষক আইডি জয়েন কন্ট্রোল (কো-সেশন)",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isJoinedAsTeacherIdentity) {
                            "আপনি বর্তমানে কো-সেশন প্রযুক্তিতে শিক্ষকের আইডি দিয়ে কানেক্টেড আছেন (এতে আসল শিক্ষক ক্লাস থেকে বের হবেন না, দুজনেই একসাথে সেশনে থাকতে পারবেন)।"
                        } else {
                            "আপনি বর্তমানে সাধারণ শিক্ষার্থীর মতো নিজের আইডি দিয়ে ক্লাসে আছেন। আসল শিক্ষককে ক্লাস থেকে বের না করেই (Co-Session Mode) শিক্ষকের আইডি দিয়ে একসাথে জয়েন করতে নিচের বাটনে ক্লিক করুন:"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 17.sp
                    )

                    Button(
                        onClick = {
                            showTeacherIdentityDialog = false
                            joinLiveClassWithTeacherId()
                        },
                        enabled = !isJoiningAsTeacher && !isJoiningClass,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "শিক্ষকের আইডি দিয়ে জয়েন করুন",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (isJoinedAsTeacherIdentity) {
                        OutlinedButton(
                            onClick = {
                                showTeacherIdentityDialog = false
                                val myNormalName = sessionManager.getLiveCustomStudentName()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: defaultProfileName
                                joinLiveClassAsStudent(myNormalName)
                            },
                            enabled = !isJoiningAsTeacher && !isJoiningClass,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "নিজের স্টুডেন্ট আইডিতে ফিরে যান",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTeacherIdentityDialog = false }) {
                    Text("বন্ধ করুন", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
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
            },
            canUseAutoPoll = canViewProfiles,
            autoPollEnabled = autoPollEnabled,
            autoPollDelayMs = autoPollDelayMs,
            onAutoPollConfigChanged = { enabled, ms ->
                updateAutoPollConfig(enabled, ms)
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
            },
            canUseAutoPoll = canViewProfiles,
            autoPollEnabled = autoPollEnabled,
            autoPollDelayMs = autoPollDelayMs,
            onAutoPollConfigChanged = { enabled, ms ->
                updateAutoPollConfig(enabled, ms)
            }
        )
    }

    // 4. "ভিউ ম্যাসেজ" (View All Messages & Who Messaged What) Dialog — ONLY for Admin-permitted devices
    if (canViewProfiles && showViewMessagesDialog) {
        LiveViewMessagesDialog(
            messages = visibleChatMessages,
            myName = studentName,
            selectedRole = selectedRole,
            isChatEnabled = isChatEnabled,
            onSelectRole = { selectedRole = it },
            onSendMessage = { text -> sendUserChat(text) },
            onRetryMessage = { msgId -> retryChatMessage(msgId) },
            onDismiss = { showViewMessagesDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveViewMessagesDialog(
    messages: List<LiveChatMessage>,
    myName: String,
    selectedRole: String,
    isChatEnabled: Boolean,
    onSelectRole: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("all") } // "all", "student", "teacher", "mine"
    var selectedSenderFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var dialogMessageInput by remember { mutableStateOf("") }

    // Compute unique senders and how many messages each person sent
    val senderSummaryList = remember(messages.size, messages.toList()) {
        messages
            .groupBy { it.senderName.trim().ifBlank { "শিক্ষার্থী" } }
            .map { (name, list) ->
                val isTeacherSender = list.any { it.isTeacher && !it.isMine }
                Triple(name, list.size, isTeacherSender)
            }
            .sortedByDescending { it.second }
    }

    val filteredMessages = remember(
        messages.size,
        messages.toList(),
        selectedCategory,
        selectedSenderFilter,
        searchQuery,
        myName
    ) {
        messages.filter { msg ->
            val isMyMsg = msg.isMine || msg.senderName == "আপনি"
            val isTeacherMsg = msg.isTeacher && !isMyMsg
            val matchesCategory = when (selectedCategory) {
                "student" -> !isTeacherMsg
                "teacher" -> isTeacherMsg || msg.targetRole == "teacher"
                "mine" -> isMyMsg
                else -> true
            }
            val matchesSender = selectedSenderFilter == null ||
                msg.senderName.trim().equals(selectedSenderFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                msg.senderName.contains(searchQuery.trim(), ignoreCase = true) ||
                msg.message.contains(searchQuery.trim(), ignoreCase = true)

            matchesCategory && matchesSender && matchesSearch
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0B1120),
            border = BorderStroke(1.2.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "ভিউ ম্যাসেজ (কে কী মেসেজ করছে)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "মোট ${messages.size}টি মেসেজ • ${senderSummaryList.size} জন অংশগ্রহণকারী",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Tabs: সব | স্টুডেন্ট | শিক্ষক | আমার মেসেজ
                val categoryTabs = listOf(
                    Triple("all", "সব (${messages.size})", Icons.Default.AllInbox),
                    Triple(
                        "student",
                        "স্টুডেন্ট (${messages.count { !(it.isTeacher && !it.isMine) }})",
                        Icons.Default.Groups
                    ),
                    Triple(
                        "teacher",
                        "শিক্ষক (${messages.count { (it.isTeacher && !it.isMine) || it.targetRole == "teacher" }})",
                        Icons.Default.School
                    ),
                    Triple(
                        "mine",
                        "আমার (${messages.count { it.isMine }})",
                        Icons.Default.Person
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categoryTabs.forEach { (key, label, icon) ->
                        val isSelected = selectedCategory == key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF162032),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF26354D)
                            ),
                            modifier = Modifier.clickable { selectedCategory = key }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                // Active Senders Breakdown Strip ("কে কয়টি মেসেজ করেছে — নামের ওপর ক্লিক করে ফিল্টার করুন")
                if (senderSummaryList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "কে মেসেজ করেছে (নামের ওপর ট্যাপ করে তার সব মেসেজ দেখুন):",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            val allSendersSelected = selectedSenderFilter == null
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (allSendersSelected) Color(0xFF0284C7).copy(alpha = 0.3f) else Color(0xFF162032),
                                border = BorderStroke(
                                    1.dp,
                                    if (allSendersSelected) Color(0xFF38BDF8) else Color(0xFF26354D)
                                ),
                                modifier = Modifier.clickable { selectedSenderFilter = null }
                            ) {
                                Text(
                                    text = "সকল প্রেরক (${senderSummaryList.size})",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (allSendersSelected) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.5.dp)
                                )
                            }
                        }

                        items(senderSummaryList) { (sender, count, isTeacherSender) ->
                            val isSelected = selectedSenderFilter?.equals(sender, ignoreCase = true) == true
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) {
                                    Color(0xFF0284C7)
                                } else if (isTeacherSender) {
                                    Color(0xFF0C2440)
                                } else {
                                    Color(0xFF162032)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected || isTeacherSender) Color(0xFF38BDF8) else Color(0xFF26354D)
                                ),
                                modifier = Modifier.clickable {
                                    selectedSenderFilter = if (isSelected) null else sender
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTeacherSender) Icons.Default.School else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color(0xFF38BDF8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "$sender ($count)",
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

                Spacer(modifier = Modifier.height(8.dp))

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "নাম বা মেসেজ লিখে খুঁজুন...",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(17.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF26354D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF111C33),
                        unfocusedContainerColor = Color(0xFF111C33)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Messages Feed inside Dialog
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredMessages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = "কোনো মেসেজ পাওয়া যায়নি",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = "ক্লাসে কেউ মেসেজ পাঠালে তা সাথে সাথে এখানে দেখা যাবে",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredMessages, key = { "view_${it.id}" }) { item ->
                            ChatMessageCard(
                                item = item,
                                myName = myName,
                                onRetry = { onRetryMessage(item.id) }
                            )
                        }
                    }
                }

                // Quick Send Bar inside View Messages Dialog
                if (isChatEnabled) {
                    HorizontalDivider(
                        color = Color(0xFF1E293B),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "পাঠান:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        val roles = listOf(
                            Pair("teacher", "Teacher"),
                            Pair("student", "Student"),
                            Pair("all", "* All")
                        )
                        roles.forEach { (rKey, rLabel) ->
                            val active = selectedRole == rKey
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) Color(0xFF0284C7) else Color(0xFF162032),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (active) Color(0xFF38BDF8) else Color(0xFF26354D)
                                ),
                                modifier = Modifier.clickable { onSelectRole(rKey) }
                            ) {
                                Text(
                                    text = rLabel,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dialogMessageInput,
                            onValueChange = { dialogMessageInput = it },
                            placeholder = {
                                Text(
                                    "মেসেজ লিখুন...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF26354D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF162032),
                                unfocusedContainerColor = Color(0xFF162032)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                if (dialogMessageInput.isNotBlank()) {
                                    onSendMessage(dialogMessageInput)
                                    dialogMessageInput = ""
                                }
                            },
                            enabled = dialogMessageInput.isNotBlank(),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (dialogMessageInput.isNotBlank()) Color(0xFF0284C7) else Color(0xFF162032)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (dialogMessageInput.isNotBlank()) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageCard(
    item: LiveChatMessage,
    myName: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMyMsg = item.isMine || item.senderName == "আপনি"
    val isActualTeacherMsg = (item.isTeacher && !isMyMsg) || (item.role == "teacher" && !isMyMsg && item.isPinned)

    val targetAudienceLabel = when (item.targetRole.lowercase().trim()) {
        "teacher" -> "➔ শিক্ষক"
        "all", "*" -> "➔ সবাইকে (* All)"
        else -> "➔ সকল স্টুডেন্ট"
    }

    if (isActualTeacherMsg) {
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
        // STUDENT / PARTICIPANT MESSAGE (WITH SENDER, TARGET AUDIENCE & DELIVERY STATUS)
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
                            else Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981)))
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
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
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

                        // Target Audience Chip (shows whether sent to Teacher, Students, or All)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(0.6.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = targetAudienceLabel,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Text(
                            text = "• ${item.timestamp}",
                            fontSize = 9.5.sp,
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
