package com.example.liveclass

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.SessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.app.Activity
import com.example.LocalPictureInPictureMode
import com.example.util.PipHelper

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
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val service = remember { LiveClassService() }
    val wsManager = remember { LiveClassWebSocketManager() }

    val isPipMode = LocalPictureInPictureMode.current
    var selectedResolution by remember { mutableStateOf("auto") }
    var showResolutionDialog by remember { mutableStateOf(false) }

    var uiState by remember { mutableStateOf<LiveClassState>(LiveClassState.Loading) }
    var studentName by remember {
        mutableStateOf(sessionManager.getUserFullName() ?: "শিক্ষার্থী")
    }
    var selectedRole by remember { mutableStateOf("student") } // "teacher", "student", "all"

    var lectureSlideUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLiveOnGoing by remember { mutableStateOf(true) }

    val masterUrl by wsManager.masterUrl.collectAsState()
    val candidateUrls by wsManager.candidateUrls.collectAsState()
    val viewerCount by wsManager.viewerCount.collectAsState()
    val wsError by wsManager.error.collectAsState()
    val isConnected by wsManager.isConnected.collectAsState()

    // Chat Messages State (Starts completely empty - no dummy or mock messages)
    var chatMessageInput by remember { mutableStateOf("") }
    val chatMessages = remember {
        mutableStateListOf<LiveChatMessage>()
    }

    // Function to initialize connection flow: GraphQL -> Token -> WebSocket
    fun startLiveClassConnection() {
        uiState = LiveClassState.Loading
        coroutineScope.launch {
            val userToken = sessionManager.getAccessToken()
            if (userToken.isNullOrBlank()) {
                uiState = LiveClassState.Error("ব্যবহারকারীর অথেন্টিকেশন পাওয়া যায়নি। অনুগ্রহ করে লগইন করুন।")
                return@launch
            }

            // Step 1: Concurrently fetch Academic Live Class info (playback_url, on_going, slides) AND HMS Room ID
            val academicDeferred = async { service.getAcademicLiveClassInfo(classId, lessonId, userToken) }
            val roomDeferred = async { service.getHmsRoomId(classId, lessonId, userToken) }

            val academicInfo = academicDeferred.await().getOrNull()
            val roomResult = roomDeferred.await()
            val roomId = roomResult.getOrNull()

            // Collect all discovered stream and recording URLs
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

            Log.d(TAG, "Fetched details: roomId=$roomId, onGoing=$isLiveOnGoing, directPlayback=${academicInfo?.playbackUrl}, totalCandidates=${cleanDiscovered.size}")
            wsManager.addCandidateUrls(cleanDiscovered)

            // Step 2: Instantly set direct playback URL if available (whether ongoing or recorded) for zero-delay streaming
            if (!academicInfo?.playbackUrl.isNullOrBlank()) {
                Log.d(TAG, "Loading direct playback URL: ${academicInfo?.playbackUrl}")
                wsManager.setMasterUrl(academicInfo!!.playbackUrl!!)
                uiState = LiveClassState.Preview(viewerCount.coerceAtLeast(1))
                return@launch
            } else if (cleanDiscovered.isNotEmpty()) {
                Log.d(TAG, "Loading candidate stream URL: ${cleanDiscovered.first()}")
                wsManager.setMasterUrl(cleanDiscovered.first())
                uiState = LiveClassState.Preview(viewerCount.coerceAtLeast(1))
                return@launch
            }

            // Step 3: Try 100ms HMS Room connection
            if (!roomId.isNullOrBlank()) {
                val tokenResult = service.getHmsToken(roomId, userToken)
                val hmsToken = tokenResult.getOrNull()
                if (!hmsToken.isNullOrBlank()) {
                    wsManager.connect(hmsToken, studentName)
                    return@launch
                }
            }

            // Step 4: Fallback to direct stream/playback URL
            val fallbackUrl = academicInfo?.playbackUrl ?: cleanDiscovered.firstOrNull()
            if (!fallbackUrl.isNullOrBlank()) {
                Log.d(TAG, "Using direct fallback playback URL: $fallbackUrl")
                wsManager.setMasterUrl(fallbackUrl)
                uiState = LiveClassState.Preview(viewerCount.coerceAtLeast(1))
                return@launch
            }

            // Step 5: Only show error if no HMS room AND no direct stream exists
            val errMsg = roomResult.exceptionOrNull()?.message
                ?: "লাইভ ক্লাসের ব্রডকাস্ট সংযোগ পাওয়া যায়নি। ক্লাস শুরু হতে বিলম্ব হলে অনুগ্রহ করে একটু অপেক্ষা করে রিফ্রেশ করুন।"
            uiState = LiveClassState.Error(errMsg)
        }
    }

    // Trigger initial connection on launch
    LaunchedEffect(classId, lessonId) {
        startLiveClassConnection()
    }

    // React to WebSocket updates safely
    LaunchedEffect(masterUrl, wsError) {
        // CRITICAL BUG FIX: Only transition state if we are currently in Loading!
        // NEVER overwrite uiState to Error if the user is already in Preview or Playing!
        if (uiState is LiveClassState.Loading) {
            if (!masterUrl.isNullOrBlank()) {
                uiState = LiveClassState.Preview(viewerCount.coerceAtLeast(1))
            } else if (!wsError.isNullOrBlank()) {
                // If we have any candidate URLs, use them as fallback instead of failing!
                val fallback = candidateUrls.firstOrNull { it.isNotBlank() }
                if (!fallback.isNullOrBlank()) {
                    wsManager.setMasterUrl(fallback)
                    uiState = LiveClassState.Preview(viewerCount.coerceAtLeast(1))
                } else {
                    uiState = LiveClassState.Error("কানেকশন ত্রুটি: $wsError")
                }
            }
        }
    }

    // Listen to incoming chat messages over WebSocket
    val incomingMsg by wsManager.incomingChatMessages.collectAsState()
    LaunchedEffect(incomingMsg) {
        incomingMsg?.let { msg ->
            if (chatMessages.none { it.id == msg.id || (it.message == msg.message && it.senderName == msg.senderName) }) {
                chatMessages.add(msg)
            }
        }
    }

    // Cleanup on Back / Disposal
    DisposableEffect(Unit) {
        onDispose {
            wsManager.disconnect()
        }
    }

    BackHandler {
        wsManager.disconnect()
        onBack()
    }

    // Direct Fullscreen PiP mode handler: when minimized to Picture-in-Picture window,
    // render ONLY the video surface filling the 16:9 box without outer UI or chat!
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
        containerColor = Color(0xFF0F172A),
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                // ==========================================
                // SCREEN 1: LOADING STATE
                // ==========================================
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
                            text = "অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করুন",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // ==========================================
                // SCREEN 2: PREVIEW STATE (GET STARTED)
                // ==========================================
                is LiveClassState.Preview -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Navigation / Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    wsManager.disconnect()
                                    onBack()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Viewer Count Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${if (viewerCount > 0) viewerCount else 1} জন যুক্ত আছেন",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Middle Form Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isLiveOnGoing) Color(0xFFE11D48) else Color(0xFF10B981)
                                ) {
                                    Text(
                                        text = if (isLiveOnGoing) "🔴 LIVE CLASS" else "📺 ক্লাস রেকর্ডিং",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = lessonTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = subjectName,
                                    fontSize = 13.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                 // Name Input Field
                                OutlinedTextField(
                                    value = studentName,
                                    onValueChange = { studentName = it },
                                    label = { Text("আপনার নাম (Name)", color = Color.White.copy(alpha = 0.7f)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF475569),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF0F172A),
                                        unfocusedContainerColor = Color(0xFF0F172A)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Role Selector: teacher, student, all (*)
                                Text(
                                    text = "রোল নির্বাচন করুন (Role):",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val roles = listOf("teacher" to "Teacher", "student" to "Student", "all" to "* All")
                                    roles.forEach { (key, label) ->
                                        val isSelected = selectedRole == key
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedRole = key }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                val hasAvailableStream = !masterUrl.isNullOrBlank() || candidateUrls.isNotEmpty()

                                Button(
                                    onClick = { uiState = LiveClassState.Playing },
                                    enabled = studentName.isNotBlank() && hasAvailableStream,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = if (isLiveOnGoing) "লাইভ ক্লাসে যোগ দিন (Join Now)" else "ক্লাস শুরু করুন (Start Class)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Bottom hint
                        Text(
                            text = "লাইভ স্ট্রিমিং চলাকালীন ভালো অভিজ্ঞতার জন্য Wi-Fi বা 4G ব্যবহার করুন",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ==========================================
                // SCREEN 3: PLAYING STATE (LIVE PLAYER + CHAT)
                // ==========================================
                is LiveClassState.Playing -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        var showControls by remember { mutableStateOf(true) }

                        // Top Video Section with click listener to toggle controls
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                                .clickable {
                                    showControls = !showControls
                                }
                        ) {
                            val activePlaybackUrl = masterUrl ?: candidateUrls.firstOrNull { it.isNotBlank() }

                            if (!activePlaybackUrl.isNullOrBlank()) {
                                LivePlayer(
                                    masterUrl = activePlaybackUrl,
                                    candidateUrls = candidateUrls,
                                    selectedResolution = selectedResolution,
                                    isPipActive = false,
                                    onBack = {
                                        wsManager.disconnect()
                                        onBack()
                                    }
                                )
                            }

                            // Top Controls Overlay with Gradient Scrim
                            if (showControls) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                            )
                                        )
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                wsManager.disconnect()
                                                onBack()
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isLiveOnGoing) Color(0xFFE11D48) else Color(0xFF10B981)
                                        ) {
                                            Text(
                                                text = if (isLiveOnGoing) "🔴 LIVE" else "📺 RECORDING",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = "${if (viewerCount > 0) viewerCount else 1} জন দেখছেন",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.5f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        )

                                        Spacer(modifier = Modifier.weight(1f))

                                        // Video Resolution Switcher Button
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.55f),
                                            border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable { showResolutionDialog = true }
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
                                                    modifier = Modifier.size(14.dp)
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

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Picture in Picture (PiP) Button
                                        IconButton(
                                            onClick = {
                                                val activity = context as? Activity
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
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        // Slide / PDF button if available
                                        if (lectureSlideUrls.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = {
                                                    val firstSlide = lectureSlideUrls.firstOrNull()
                                                    if (!firstSlide.isNullOrBlank()) {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(firstSlide))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Log.w(TAG, "Cannot open slide URL: ${e.message}")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                                    .padding(horizontal = 4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Description,
                                                        contentDescription = "Lecture Notes",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "স্লাইড",
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
                        }

                        // Bottom Section: Title + Live Chat
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = lessonTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = subjectName,
                                fontSize = 12.5.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )

                            HorizontalDivider(color = Color(0xFF334155))

                            // Chat Header
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
                                    text = "লাইভ চ্যাট (Live Chat)",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // Connection indicator badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isConnected) Color(0xFF065F46) else Color(0xFF334155)
                                ) {
                                    Text(
                                        text = if (isConnected) "কানেক্টেড" else "রেকর্ডিং মোড",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isConnected) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 3 Role Options Selection Bar: Teacher, Student, * All
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
                                }
                            }

                            // Chat Messages List
                            LazyColumn(
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
                                        val isTeacherMsg = item.isTeacher || item.role == "teacher"
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isTeacherMsg) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.6f),
                                            border = BorderStroke(
                                                0.5.dp,
                                                if (isTeacherMsg) Color(0xFF38BDF8) else Color(0xFF334155)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    // Role Badge
                                                    val (badgeLabel, badgeBg, badgeTextColor) = when {
                                                        isTeacherMsg -> Triple("Teacher", Color(0xFF0284C7), Color.White)
                                                        item.role == "all" || item.role == "*" -> Triple("* All", Color(0xFF4F46E5), Color.White)
                                                        else -> Triple("Student", Color(0xFF059669), Color.White)
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = badgeBg
                                                    ) {
                                                        Text(
                                                            text = badgeLabel,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = badgeTextColor,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = item.senderName,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isTeacherMsg) Color(0xFF38BDF8) else Color(0xFFF1F5F9)
                                                    )
                                                    Text(
                                                        text = "• ${item.timestamp}",
                                                        fontSize = 10.sp,
                                                        color = Color.White.copy(alpha = 0.5f)
                                                    )
                                                }
                                                Text(
                                                    text = item.message,
                                                    fontSize = 12.5.sp,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Send Message Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = chatMessageInput,
                                    onValueChange = { chatMessageInput = it },
                                    placeholder = { Text("প্রশ্ন বা বার্তা লিখুন...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f)) },
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
                                            val textToSend = chatMessageInput.trim()
                                            val myName = studentName.ifBlank { "আপনি" }
                                            val isTeacher = selectedRole == "teacher"
                                            chatMessages.add(
                                                LiveChatMessage(
                                                    senderName = myName,
                                                    message = textToSend,
                                                    timestamp = "এখন",
                                                    isTeacher = isTeacher,
                                                    role = selectedRole
                                                )
                                            )
                                            wsManager.sendChatMessage(
                                                text = textToSend,
                                                studentName = myName,
                                                role = selectedRole,
                                                targetRole = selectedRole
                                            )
                                            chatMessageInput = ""
                                            keyboardController?.hide()
                                        }
                                    }),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        if (chatMessageInput.isNotBlank()) {
                                            val textToSend = chatMessageInput.trim()
                                            val myName = studentName.ifBlank { "আপনি" }
                                            val isTeacher = selectedRole == "teacher"
                                            chatMessages.add(
                                                LiveChatMessage(
                                                    senderName = myName,
                                                    message = textToSend,
                                                    timestamp = "এখন",
                                                    isTeacher = isTeacher,
                                                    role = selectedRole
                                                )
                                            )
                                            wsManager.sendChatMessage(
                                                text = textToSend,
                                                studentName = myName,
                                                role = selectedRole,
                                                targetRole = selectedRole
                                            )
                                            chatMessageInput = ""
                                            keyboardController?.hide()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7))
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

                // ==========================================
                // SCREEN 4: ERROR STATE
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
}
