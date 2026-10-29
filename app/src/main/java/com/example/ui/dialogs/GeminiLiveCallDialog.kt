package com.example.ui.dialogs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ai.AiChatMessage
import com.example.ai.GeminiAiStudyRepository
import com.example.ai.GeminiTutorResult
import com.example.ai.GeminiVoiceSessionController
import com.example.auth.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

enum class GeminiCallState {
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    PAUSED
}

data class LiveCallTranscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Fullscreen Gemini Live Voice Call Experience modeled after the official Google Gemini mobile app.
 * Features:
 * - Fluid animated pulsating glowing multi-color gradient audio orb.
 * - Hands-free continuous back-and-forth speech recognition & Bengali TTS conversation loop.
 * - Complete lecture PDF slide deck context ingestion (gemini-3.8-flash).
 * - Real-time subtitles, mic mute toggle, interrupt controls, and End Call button.
 */
@Composable
fun GeminiLiveCallDialog(
    slideTitle: String,
    currentPage: Int,
    totalPages: Int,
    pdfFile: File? = null,
    slideBitmaps: List<Pair<Int, Bitmap>> = emptyList(),
    sessionManager: SessionManager? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var callState by remember { mutableStateOf(GeminiCallState.CONNECTING) }
    var isMicMuted by remember { mutableStateOf(false) }
    var userPartialTranscript by remember { mutableStateOf("") }
    var geminiLiveSubtitle by remember { mutableStateOf("") }
    var speechAmplitude by remember { mutableStateOf(0.2f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeSpeakingId by remember { mutableStateOf<String?>(null) }

    val transcriptList = remember { mutableStateListOf<LiveCallTranscriptItem>() }
    val transcriptScrollState = rememberScrollState()

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isRecognizerActive by remember { mutableStateOf(false) }

    // TTS & Voice Controller
    val voiceController = remember {
        GeminiVoiceSessionController(context) { speakingMsgId ->
            activeSpeakingId = speakingMsgId
            if (speakingMsgId == null && callState == GeminiCallState.SPEAKING) {
                // Gemini finished speaking! Automatically transition back to LISTENING
                if (!isMicMuted) {
                    callState = GeminiCallState.LISTENING
                } else {
                    callState = GeminiCallState.PAUSED
                }
            }
        }
    }

    // Permission check
    val hasAudioPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission.value = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Gemini Live-এ কথা বলতে মাইক্রোফোন পারমিশন প্রয়োজন", Toast.LENGTH_LONG).show()
        }
    }

    // Function to start speech recognition
    fun startListeningUserSpeech() {
        if (!hasAudioPermission.value || isMicMuted) return
        try {
            voiceController.stopSpeaking()
            userPartialTranscript = ""
            errorMessage = null

            val recognizer = speechRecognizer ?: run {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    val r = SpeechRecognizer.createSpeechRecognizer(context)
                    speechRecognizer = r
                    r
                } else null
            }

            if (recognizer == null) {
                callState = GeminiCallState.PAUSED
                errorMessage = "ডিভাইসে স্পিচ রিকগনাইজার সক্রিয় নেই। নিচে ট্যাপ করে প্রশ্ন করুন।"
                return
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isRecognizerActive = true
                    callState = GeminiCallState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    speechAmplitude = 0.85f
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.15f, 1.0f)
                    speechAmplitude = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isRecognizerActive = false
                    speechAmplitude = 0.2f
                }

                override fun onError(error: Int) {
                    isRecognizerActive = false
                    speechAmplitude = 0.2f
                    // If no speech detected in silence, restart listening if still in LISTENING mode
                    if (callState == GeminiCallState.LISTENING && !isMicMuted) {
                        coroutineScope.launch {
                            delay(500L)
                            if (callState == GeminiCallState.LISTENING && !isMicMuted) {
                                startListeningUserSpeech()
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isRecognizerActive = false
                    speechAmplitude = 0.2f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim().orEmpty()
                    if (spokenText.isNotBlank()) {
                        userPartialTranscript = spokenText
                        transcriptList.add(LiveCallTranscriptItem(isUser = true, text = spokenText))
                        // Send query to Gemini 3.8 Flash with full lecture slide deck
                        coroutineScope.launch {
                            processQueryWithGemini(
                                userQuestion = spokenText,
                                context = context,
                                slideTitle = slideTitle,
                                currentPage = currentPage,
                                totalPages = totalPages,
                                pdfFile = pdfFile,
                                slideBitmaps = slideBitmaps,
                                sessionManager = sessionManager,
                                transcriptHistory = transcriptList.toList(),
                                onStateChange = { callState = it },
                                onGeminiReply = { reply ->
                                    geminiLiveSubtitle = reply
                                    transcriptList.add(LiveCallTranscriptItem(isUser = false, text = reply))
                                    // Speak out loud in Bengali
                                    val msgId = "gemini_call_${System.currentTimeMillis()}"
                                    activeSpeakingId = msgId
                                    voiceController.toggleSpeakMessage(msgId, reply)
                                },
                                onError = { err ->
                                    errorMessage = err
                                    callState = GeminiCallState.LISTENING
                                }
                            )
                        }
                    } else {
                        // Restart listening loop
                        if (callState == GeminiCallState.LISTENING && !isMicMuted) {
                            startListeningUserSpeech()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = partialMatches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        userPartialTranscript = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.w("GeminiLiveCall", "startListening error: ${e.message}")
            isRecognizerActive = false
        }
    }

    // Call Lifecycle & Initial Greeting
    LaunchedEffect(Unit) {
        if (!hasAudioPermission.value) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        delay(800L)
        // Welcome student with Gemini voice greeting
        val greeting = "নমস্কার! আমি Gemini লাইভ টিউটর। সম্পূর্ণ \"$slideTitle\" লেকচার স্লাইডের যেকোনো অংশ বা সূত্র নিয়ে প্রশ্ন করো, আমি বুঝিয়ে দিচ্ছি।"
        geminiLiveSubtitle = greeting
        transcriptList.add(LiveCallTranscriptItem(isUser = false, text = greeting))
        callState = GeminiCallState.SPEAKING
        val initialId = "welcome_greeting"
        activeSpeakingId = initialId
        voiceController.toggleSpeakMessage(initialId, greeting)
    }

    // When state changes to LISTENING, activate speech recognizer
    LaunchedEffect(callState, isMicMuted) {
        if (callState == GeminiCallState.LISTENING && !isMicMuted && !isRecognizerActive) {
            startListeningUserSpeech()
        }
    }

    // Auto-scroll transcripts
    LaunchedEffect(transcriptList.size, geminiLiveSubtitle, userPartialTranscript) {
        try {
            transcriptScrollState.animateScrollTo(transcriptScrollState.maxValue)
        } catch (_: Exception) {}
    }

    // Clean up
    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            voiceController.release()
        }
    }

    Dialog(
        onDismissRequest = {
            voiceController.stopSpeaking()
            onDismissRequest()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080C15))
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("gemini_live_call_dialog")
        ) {
            // Ambient Radial Gradient Aura in background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.42f)
                val auraColor = when (callState) {
                    GeminiCallState.SPEAKING -> Color(0xFF3B82F6).copy(alpha = 0.22f)
                    GeminiCallState.THINKING -> Color(0xFFA855F7).copy(alpha = 0.25f)
                    GeminiCallState.LISTENING -> Color(0xFF06B6D4).copy(alpha = 0.20f)
                    else -> Color(0xFF6366F1).copy(alpha = 0.16f)
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(auraColor, Color.Transparent),
                        center = center,
                        radius = size.width * 0.85f
                    ),
                    center = center,
                    radius = size.width * 0.85f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP BAR: Connection Badge, Slide Title & Minimize Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Gemini Live Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (callState != GeminiCallState.PAUSED) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Text(
                                text = "Gemini 3.8 Flash Live",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Lecture Info & Close Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                voiceController.stopSpeaking()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Call",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // SUBTITLE / CONTEXT BANNER
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = slideTitle,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "📚 সম্পূর্ণ লেকচার স্লাইড ডেক (মোট $totalPages পৃষ্ঠা) সংযুক্ত রয়েছে",
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // CENTER: Animated Gemini Pulsing Glowing Orb
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GeminiLiveOrb(
                        state = callState,
                        amplitude = speechAmplitude,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // CALL STATUS TEXT
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    val statusText = when (callState) {
                        GeminiCallState.CONNECTING -> "Gemini Live-এ সংযোগ হচ্ছে..."
                        GeminiCallState.LISTENING -> if (isMicMuted) "মাইক্রোফোন মিউট করা আছে" else "🎙️ শুনছি, আপনার প্রশ্ন বলুন..."
                        GeminiCallState.THINKING -> "✨ Gemini উত্তর তৈরি করছে..."
                        GeminiCallState.SPEAKING -> "🔊 Gemini কথা বলছে..."
                        GeminiCallState.PAUSED -> "সেশন বিরতিতে আছে। মাইক চালু করুন।"
                    }

                    val statusColor = when (callState) {
                        GeminiCallState.LISTENING -> Color(0xFF38BDF8)
                        GeminiCallState.THINKING -> Color(0xFFC084FC)
                        GeminiCallState.SPEAKING -> Color(0xFF34D399)
                        else -> Color(0xFFE2E8F0)
                    }

                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage.orEmpty(),
                            color = Color(0xFFF87171),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // TRANSCRIPT & LIVE SUBTITLE BOX
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .verticalScroll(transcriptScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Show active user spoken sentence in real time
                        if (userPartialTranscript.isNotBlank() && callState == GeminiCallState.LISTENING) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🗣️ আপনি:",
                                    color = Color(0xFF67E8F9),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userPartialTranscript,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Last transcript items
                        transcriptList.takeLast(4).forEach { item ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (item.isUser) "🗣️ আপনি:" else "✨ Gemini:",
                                    color = if (item.isUser) Color(0xFF67E8F9) else Color(0xFFA7F3D0),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.text,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // BOTTOM CONTROLS DOCK: Mute, Stop/Repeat, and End Call
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Unmute Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                isMicMuted = !isMicMuted
                                if (isMicMuted) {
                                    try {
                                        speechRecognizer?.stopListening()
                                    } catch (_: Exception) {}
                                    callState = GeminiCallState.PAUSED
                                } else {
                                    callState = GeminiCallState.LISTENING
                                    startListeningUserSpeech()
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isMicMuted) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f))
                                .border(1.dp, if (isMicMuted) Color(0xFFEF4444) else Color.White.copy(alpha = 0.2f), CircleShape)
                                .testTag("gemini_call_mute_toggle")
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isMicMuted) "Unmute" else "Mute",
                                tint = if (isMicMuted) Color(0xFFEF4444) else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isMicMuted) "আনমিউট" else "মিউট",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }

                    // BIG RED END CALL BUTTON
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                voiceController.stopSpeaking()
                                try {
                                    speechRecognizer?.destroy()
                                } catch (_: Exception) {}
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                                .border(2.dp, Color(0xFFFCA5A5), CircleShape)
                                .testTag("gemini_call_end_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Live Call",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "কল কাটুন",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Interrupt / Repeat Speaking Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                if (callState == GeminiCallState.SPEAKING) {
                                    // Interrupt Gemini and switch to listening immediately
                                    voiceController.stopSpeaking()
                                    callState = GeminiCallState.LISTENING
                                    startListeningUserSpeech()
                                } else if (geminiLiveSubtitle.isNotBlank()) {
                                    // Repeat last answer
                                    callState = GeminiCallState.SPEAKING
                                    val repeatId = "repeat_${System.currentTimeMillis()}"
                                    activeSpeakingId = repeatId
                                    voiceController.toggleSpeakMessage(repeatId, geminiLiveSubtitle)
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .testTag("gemini_call_action_button")
                        ) {
                            Icon(
                                imageVector = if (callState == GeminiCallState.SPEAKING) Icons.Default.Stop else Icons.Default.Replay,
                                contentDescription = if (callState == GeminiCallState.SPEAKING) "Stop" else "Replay",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (callState == GeminiCallState.SPEAKING) "থামুন" else "আবার শুনুন",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated Gemini Orb matching the Google Gemini mobile app's live calling aesthetic.
 * Pulses and expands fluidly in response to speech amplitudes and conversation state.
 */
@Composable
private fun GeminiLiveOrb(
    state: GeminiCallState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiOrb")

    // Rotation angle for gradient swirl
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val effectiveScale = when (state) {
        GeminiCallState.SPEAKING -> 0.95f + (amplitude * 0.35f)
        GeminiCallState.LISTENING -> (0.92f * breathingPulse) + (amplitude * 0.25f)
        GeminiCallState.THINKING -> 1.0f * breathingPulse
        else -> 0.95f
    }

    Box(
        modifier = modifier.scale(effectiveScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2.7f

            // Outer Ripple Halo 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        when (state) {
                            GeminiCallState.SPEAKING -> Color(0xFF38BDF8).copy(alpha = 0.35f)
                            GeminiCallState.THINKING -> Color(0xFFA855F7).copy(alpha = 0.38f)
                            GeminiCallState.LISTENING -> Color(0xFF00E5FF).copy(alpha = 0.32f)
                            else -> Color(0xFF818CF8).copy(alpha = 0.25f)
                        },
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.55f
                ),
                center = center,
                radius = baseRadius * 1.55f
            )

            // Outer Ripple Halo 2
            drawCircle(
                color = when (state) {
                    GeminiCallState.SPEAKING -> Color(0xFF60A5FA).copy(alpha = 0.22f)
                    GeminiCallState.THINKING -> Color(0xFFC084FC).copy(alpha = 0.24f)
                    GeminiCallState.LISTENING -> Color(0xFF38BDF8).copy(alpha = 0.20f)
                    else -> Color(0xFF4F46E5).copy(alpha = 0.15f)
                },
                center = center,
                radius = baseRadius * 1.28f,
                style = Stroke(width = 2.5f)
            )

            // Inner Vibrant Core with Gemini Signature Multi-Color Gradient
            val gradientColors = when (state) {
                GeminiCallState.SPEAKING -> listOf(
                    Color(0xFF38BDF8),
                    Color(0xFF6366F1),
                    Color(0xFFA855F7),
                    Color(0xFF10B981)
                )
                GeminiCallState.THINKING -> listOf(
                    Color(0xFFA855F7),
                    Color(0xFFEC4899),
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6)
                )
                GeminiCallState.LISTENING -> listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF2563EB),
                    Color(0xFF7C3AED),
                    Color(0xFF06B6D4)
                )
                else -> listOf(
                    Color(0xFF475569),
                    Color(0xFF334155),
                    Color(0xFF1E293B)
                )
            }

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = gradientColors,
                    center = center
                ),
                center = center,
                radius = baseRadius
            )

            // Core Glossy Highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.60f), Color.Transparent),
                    center = Offset(center.x - baseRadius * 0.3f, center.y - baseRadius * 0.3f),
                    radius = baseRadius * 0.8f
                ),
                center = Offset(center.x - baseRadius * 0.25f, center.y - baseRadius * 0.25f),
                radius = baseRadius * 0.7f
            )
        }
    }
}

/**
 * Executes a question directly to Gemini 3.8 Flash, ingesting the full PDF slide deck
 * and returning the conversational Bengali explanation.
 */
private suspend fun processQueryWithGemini(
    userQuestion: String,
    context: Context,
    slideTitle: String,
    currentPage: Int,
    totalPages: Int,
    pdfFile: File?,
    slideBitmaps: List<Pair<Int, Bitmap>>,
    sessionManager: SessionManager?,
    transcriptHistory: List<LiveCallTranscriptItem>,
    onStateChange: (GeminiCallState) -> Unit,
    onGeminiReply: (String) -> Unit,
    onError: (String) -> Unit
) {
    onStateChange(GeminiCallState.THINKING)

    val history = transcriptHistory.map {
        AiChatMessage(isUser = it.isUser, text = it.text)
    }

    val result = GeminiAiStudyRepository.askSlideQuestion(
        context = context,
        slideTitle = slideTitle,
        currentPage = currentPage,
        totalPages = totalPages,
        userQuestion = userQuestion,
        slideBitmaps = slideBitmaps,
        pdfFileFallback = pdfFile,
        chatHistory = history,
        sessionManager = sessionManager
    )

    when (result) {
        is GeminiTutorResult.Success -> {
            onStateChange(GeminiCallState.SPEAKING)
            onGeminiReply(result.explanation)
        }
        is GeminiTutorResult.Error -> {
            onError(result.message)
        }
    }
}
