package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.dialogs.GeminiApiSettingsDialog
import com.example.utils.NotebookLMHelper
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Universal Dialog for In-App Gemini 3.8 Flash Slide Study Tutor, PDF/Slide Analyzer (`analyzeSlidePDF`),
 * Interactive Live Voice Tutor (`startVoiceSession` via `gemini-3.8-live` / `gemini-2.5-flash-native-audio`),
 * & Google NotebookLM Integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLMStudyDialog(
    title: String,
    currentPage: Int = 1,
    totalPageCount: Int = 1,
    targetFile: File?,
    remoteUrl: String?,
    renderer: PdfPageRenderer? = null,
    initialTab: Int = 1, // Default to 1: Gemini 3.8 Flash AI Study Tutor
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val safeTotalPages = totalPageCount.coerceAtLeast(1)
    var activeSlidePage by remember(currentPage, safeTotalPages) {
        mutableIntStateOf(currentPage.coerceIn(1, safeTotalPages))
    }
    var includeAdjacentSlides by remember { mutableStateOf(false) }

    var currentSlideBitmap by remember(activeSlidePage, renderer) {
        mutableStateOf(renderer?.getCachedBitmap(activeSlidePage - 1))
    }

    LaunchedEffect(activeSlidePage, renderer) {
        if (renderer != null && currentSlideBitmap == null) {
            currentSlideBitmap = renderer.renderPage(activeSlidePage - 1, 1600)
        }
    }

    LaunchedEffect(Unit) {
        GeminiAiStudyRepository.initialize(context)
        GeminiAiStudyRepository.syncApiKeyFromCloud(context, forceRefresh = false)
    }

    val geminiConfig by GeminiAiStudyRepository.configState.collectAsState()
    var showApiSettingsDialog by remember { mutableStateOf(false) }

    // Multi-turn AI Tutor Conversation State
    var questionInput by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    var loadingStatusText by remember { mutableStateOf("") }
    var analysisProgress by remember { mutableFloatStateOf(0f) }
    var showProgressBar by remember { mutableStateOf(false) }

    val chatMessages = remember { mutableStateListOf<AiChatMessage>() }
    val chatListState = rememberLazyListState()

    // Interactive Voice Session & Playback State
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var isVoiceRecording by remember { mutableStateOf(false) }
    var voiceRecordingSeconds by remember { mutableIntStateOf(0) }

    val voiceController = remember(context) {
        GeminiVoiceSessionController(context) { activeId ->
            speakingMessageId = activeId
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceController.release()
        }
    }

    LaunchedEffect(isVoiceRecording) {
        if (isVoiceRecording) {
            voiceRecordingSeconds = 0
            while (isVoiceRecording) {
                delay(1000L)
                voiceRecordingSeconds += 1
            }
        } else {
            voiceRecordingSeconds = 0
        }
    }

    suspend fun collectActiveSlideBitmaps(): List<Pair<Int, Bitmap>> {
        val slideBitmaps = mutableListOf<Pair<Int, Bitmap>>()
        if (renderer != null && renderer.isInitialized && renderer.pageCount > 0) {
            val pagesToRender = if (includeAdjacentSlides && safeTotalPages > 1) {
                listOf(activeSlidePage - 1, activeSlidePage, activeSlidePage + 1)
                    .filter { it in 1..safeTotalPages }
                    .distinct()
            } else {
                listOf(activeSlidePage)
            }

            for (pageNum in pagesToRender) {
                val bmp = renderer.getCachedBitmap(pageNum - 1)
                    ?: renderer.renderPage(pageNum - 1, 1600)
                if (bmp != null) {
                    slideBitmaps.add(pageNum to bmp)
                    if (pageNum == activeSlidePage && currentSlideBitmap == null) {
                        currentSlideBitmap = bmp
                    }
                }
            }
        } else if (currentSlideBitmap != null) {
            slideBitmaps.add(activeSlidePage to currentSlideBitmap!!)
        }
        return slideBitmaps
    }

    fun sendQuestionToGemini(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isBlank() || isAiLoading) return

        val pagesCount = if (includeAdjacentSlides && safeTotalPages > 1) {
            listOf(activeSlidePage - 1, activeSlidePage, activeSlidePage + 1)
                .count { it in 1..safeTotalPages }
        } else 1

        val userMsg = AiChatMessage(
            isUser = true,
            text = cleanPrompt,
            attachedSlidePage = activeSlidePage,
            attachedSlideCount = pagesCount
        )
        chatMessages.add(userMsg)
        isAiLoading = true
        showProgressBar = false
        loadingStatusText = "${GeminiAiStudyRepository.DISPLAY_MODEL_NAME} স্লাইড পৃষ্ঠা ${toBengaliDigits(activeSlidePage)} বিশ্লেষণ করছে..."

        coroutineScope.launch {
            try {
                if (chatMessages.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatMessages.size - 1)
                }
            } catch (_: Exception) {}

            val slideBitmaps = collectActiveSlideBitmaps()
            val historySnapshot = chatMessages.dropLast(1).toList()

            val result = GeminiAiStudyRepository.askSlideQuestion(
                context = context,
                slideTitle = title.ifBlank { "লেকচার স্লাইড" },
                currentPage = activeSlidePage,
                totalPages = safeTotalPages,
                userQuestion = cleanPrompt,
                slideBitmaps = slideBitmaps,
                pdfFileFallback = targetFile,
                chatHistory = historySnapshot
            )

            isAiLoading = false
            when (result) {
                is GeminiTutorResult.Success -> {
                    chatMessages.add(
                        AiChatMessage(
                            isUser = false,
                            text = result.explanation,
                            attachedSlidePage = activeSlidePage
                        )
                    )
                }
                is GeminiTutorResult.Error -> {
                    chatMessages.add(
                        AiChatMessage(
                            isUser = false,
                            text = result.message,
                            attachedSlidePage = activeSlidePage,
                            isError = true
                        )
                    )
                    if (result.isMissingKey) {
                        showApiSettingsDialog = true
                    }
                }
            }

            try {
                if (chatMessages.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatMessages.size - 1)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Triggers `analyzeSlidePDF()` either on a user-uploaded PDF/Image file
     * or on the currently open lecture slide PDF.
     */
    fun triggerSlidePdfAnalysis(
        uploadedBytes: ByteArray? = null,
        uploadedMimeType: String = "application/pdf",
        uploadedFileName: String? = null
    ) {
        if (isAiLoading) return

        val labelText = if (uploadedFileName != null) {
            "📤 আপলোড করা ফাইল বিশ্লেষণ করুন: $uploadedFileName (মূল টপিক, সারাংশ ও রিভিশন প্রশ্ন)"
        } else {
            "📄 এই লেকচার স্লাইডের মূল টপিকসমূহ, বুলেট সারাংশ এবং রিভিশন প্রশ্নোত্তর তৈরি করে দাও।"
        }

        chatMessages.add(
            AiChatMessage(
                isUser = true,
                text = labelText,
                attachedSlidePage = if (uploadedFileName == null) activeSlidePage else null,
                uploadedFileName = uploadedFileName
            )
        )

        isAiLoading = true
        showProgressBar = true
        analysisProgress = 0.08f
        loadingStatusText = "স্লাইড / PDF ফাইল প্রস্তুত করা হচ্ছে..."

        coroutineScope.launch {
            try {
                if (chatMessages.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatMessages.size - 1)
                }
            } catch (_: Exception) {}

            val slideBitmaps = if (uploadedBytes == null) collectActiveSlideBitmaps() else emptyList()

            val result = GeminiAiStudyRepository.analyzeSlidePDF(
                context = context,
                slideTitle = uploadedFileName ?: title.ifBlank { "লেকচার স্লাইড" },
                uploadedBytes = uploadedBytes,
                uploadedMimeType = uploadedMimeType,
                uploadedFileName = uploadedFileName,
                slideBitmaps = slideBitmaps,
                pdfFileFallback = targetFile,
                onProgress = { progress, status ->
                    analysisProgress = progress
                    loadingStatusText = status
                }
            )

            isAiLoading = false
            showProgressBar = false
            when (result) {
                is GeminiTutorResult.Success -> {
                    chatMessages.add(
                        AiChatMessage(
                            isUser = false,
                            text = result.explanation,
                            attachedSlidePage = if (uploadedFileName == null) activeSlidePage else null,
                            uploadedFileName = uploadedFileName
                        )
                    )
                }
                is GeminiTutorResult.Error -> {
                    chatMessages.add(
                        AiChatMessage(
                            isUser = false,
                            text = result.message,
                            isError = true
                        )
                    )
                    if (result.isMissingKey) {
                        showApiSettingsDialog = true
                    }
                }
            }

            try {
                if (chatMessages.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatMessages.size - 1)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Completes recording and sends the student's spoken audio query to `startVoiceSession()`
     * (`gemini-3.8-live` / `gemini-2.5-flash-native-audio`).
     */
    fun finishAndSendVoiceSession() {
        val audioBytes = voiceController.stopAudioRecording()
        isVoiceRecording = false

        if (audioBytes == null || audioBytes.isEmpty()) {
            Toast.makeText(context, "ভয়েস রেকর্ড খুব ছোট বা অস্পষ্ট হয়েছে, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show()
            return
        }

        val userVoiceMsg = AiChatMessage(
            isUser = true,
            text = "🎙️ ভয়েস প্রশ্ন (${toBengaliDigits(voiceRecordingSeconds.coerceAtLeast(1))} সেকেন্ড অডিও)",
            attachedSlidePage = activeSlidePage,
            isVoiceQuery = true
        )
        chatMessages.add(userVoiceMsg)
        isAiLoading = true
        showProgressBar = false
        loadingStatusText = "${GeminiAiStudyRepository.LIVE_VOICE_MODEL} ভয়েস প্রশ্ন ও স্লাইড বিশ্লেষণ করছে..."

        coroutineScope.launch {
            try {
                if (chatMessages.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatMessages.size - 1)
                }
            } catch (_: Exception) {}

            val slideBitmaps = collectActiveSlideBitmaps()
            val historySnapshot = chatMessages.dropLast(1).toList()

            val result = GeminiAiStudyRepository.startVoiceSession(
                context = context,
                slideTitle = title.ifBlank { "লেকচার স্লাইড" },
                currentPage = activeSlidePage,
                totalPages = safeTotalPages,
                recordedAudioBytes = audioBytes,
                audioMimeType = "audio/mp4",
                spokenTranscriptHint = questionInput.trim(),
                slideBitmaps = slideBitmaps,
                chatHistory = historySnapshot,
                onStatusUpdate = { status ->
                    loadingStatusText = status
                }
            )

            questionInput = ""
            isAiLoading = false

            when (result) {
                is GeminiTutorResult.Success -> {
                    val replyMsg = AiChatMessage(
                        isUser = false,
                        text = result.explanation,
                        attachedSlidePage = activeSlidePage,
                        isVoiceQuery = true,
                        audioBytesWav = result.audioBytesWav
                    )
                    chatMessages.add(replyMsg)
                    // Automatically play back the voice tutor response
                    voiceController.toggleSpeakMessage(
                        messageId = replyMsg.id,
                        rawText = replyMsg.text,
                        audioBytes = replyMsg.audioBytesWav
                    )
                }
                is GeminiTutorResult.Error -> {
                    chatMessages.add(
                        AiChatMessage(
                            isUser = false,
                            text = result.message,
                            attachedSlidePage = activeSlidePage,
                            isError = true
                        )
                    )
                    if (result.isMissingKey) {
                        showApiSettingsDialog = true
                    }
                }
            }

            try {
                if (chatMessages.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatMessages.size - 1)
                }
            } catch (_: Exception) {}
        }
    }

    // File Picker for uploading PDF or Slide Images (`analyzeSlidePDF`)
    val slideFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val readRes = GeminiVoiceSessionController.readUploadedSlideUri(context, uri)
            readRes.onSuccess { doc ->
                triggerSlidePdfAnalysis(
                    uploadedBytes = doc.bytes,
                    uploadedMimeType = doc.mimeType,
                    uploadedFileName = doc.fileName
                )
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    err.localizedMessage ?: "ফাইল আপলোড করা যায়নি",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Runtime Permission Launcher for Microphone (`RECORD_AUDIO`)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startAudioRecording().onSuccess {
                isVoiceRecording = true
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "মাইক্রোফোন ত্রুটি", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                context,
                "ভয়েস প্রশ্ন করার জন্য মাইক্রোফোন (Audio) পারমিশন প্রয়োজন",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun onToggleVoiceButton() {
        if (isVoiceRecording) {
            finishAndSendVoiceSession()
            return
        }
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            voiceController.startAudioRecording().onSuccess {
                isVoiceRecording = true
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "মাইক্রোফোন ত্রুটি", Toast.LENGTH_SHORT).show()
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Dialog(
        onDismissRequest = {
            voiceController.release()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(26.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Gradient Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF312E81),
                                    Color(0xFF4F46E5),
                                    Color(0xFF7C3AED)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFFDE68A),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "AI স্লাইড ও ভয়েস টিউটর",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981)
                                ) {
                                    Text(
                                        text = "3.8 Flash + Live",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "পৃষ্ঠা ${toBengaliDigits(activeSlidePage)} / ${toBengaliDigits(safeTotalPages)} • ${title.ifBlank { "লেকচার স্লাইড" }}",
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Quick API Key Settings Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (geminiConfig.isConfigured) {
                                Color(0xFF10B981).copy(alpha = 0.25f)
                            } else {
                                Color(0xFFF59E0B).copy(alpha = 0.3f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (geminiConfig.isConfigured) Color(0xFF34D399) else Color(0xFFFBBF24)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showApiSettingsDialog = true }
                                .testTag("open_gemini_api_settings_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (geminiConfig.isConfigured) Icons.Default.Key else Icons.Default.VpnKey,
                                    contentDescription = "API Key",
                                    tint = if (geminiConfig.isConfigured) Color(0xFFA7F3D0) else Color(0xFFFDE68A),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (geminiConfig.isConfigured) "API ✓" else "API সেট",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                voiceController.release()
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // Tab Selector (AI Slide Tutor vs NotebookLM Export)
                TabRow(
                    selectedTabIndex = if (selectedTab == 1) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("✨ এআই স্লাইড ও ভয়েস টিউটর", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("NotebookLM এ পাঠান", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    )
                }

                // Tab Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (selectedTab) {
                        1 -> InAppGeminiSlideTutorTab(
                            title = title,
                            activeSlidePage = activeSlidePage,
                            totalPageCount = safeTotalPages,
                            currentSlideBitmap = currentSlideBitmap,
                            includeAdjacentSlides = includeAdjacentSlides,
                            onToggleAdjacentSlides = { includeAdjacentSlides = !includeAdjacentSlides },
                            onChangeSlidePage = { newPage ->
                                activeSlidePage = newPage.coerceIn(1, safeTotalPages)
                            },
                            isApiKeyConfigured = geminiConfig.isConfigured,
                            onOpenApiKeySettings = { showApiSettingsDialog = true },
                            questionInput = questionInput,
                            onQuestionChange = { questionInput = it },
                            isAiLoading = isAiLoading,
                            loadingStatusText = loadingStatusText,
                            showProgressBar = showProgressBar,
                            analysisProgress = analysisProgress,
                            chatMessages = chatMessages,
                            chatListState = chatListState,
                            onAskQuestion = { prompt ->
                                sendQuestionToGemini(prompt)
                            },
                            onAnalyzeCurrentSlidePdf = {
                                triggerSlidePdfAnalysis()
                            },
                            onUploadExternalSlideOrPdf = {
                                slideFilePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                            isVoiceRecording = isVoiceRecording,
                            voiceRecordingSeconds = voiceRecordingSeconds,
                            onToggleVoiceRecording = { onToggleVoiceButton() },
                            onCancelVoiceRecording = {
                                voiceController.cancelAudioRecording()
                                isVoiceRecording = false
                            },
                            speakingMessageId = speakingMessageId,
                            onToggleSpeakMessage = { msg ->
                                voiceController.toggleSpeakMessage(
                                    messageId = msg.id,
                                    rawText = msg.text,
                                    audioBytes = msg.audioBytesWav
                                )
                            },
                            onClearChat = {
                                voiceController.stopSpeaking()
                                chatMessages.clear()
                            },
                            context = context
                        )
                        0 -> NotebookLMTabContent(
                            title = title,
                            targetFile = targetFile,
                            remoteUrl = remoteUrl,
                            context = context
                        )
                    }
                }
            }
        }
    }

    if (showApiSettingsDialog) {
        GeminiApiSettingsDialog(
            onDismiss = { showApiSettingsDialog = false },
            onKeySavedSuccessfully = {
                showApiSettingsDialog = false
            }
        )
    }
}

@Composable
private fun InAppGeminiSlideTutorTab(
    title: String,
    activeSlidePage: Int,
    totalPageCount: Int,
    currentSlideBitmap: Bitmap?,
    includeAdjacentSlides: Boolean,
    onToggleAdjacentSlides: () -> Unit,
    onChangeSlidePage: (Int) -> Unit,
    isApiKeyConfigured: Boolean,
    onOpenApiKeySettings: () -> Unit,
    questionInput: String,
    onQuestionChange: (String) -> Unit,
    isAiLoading: Boolean,
    loadingStatusText: String,
    showProgressBar: Boolean,
    analysisProgress: Float,
    chatMessages: List<AiChatMessage>,
    chatListState: androidx.compose.foundation.lazy.LazyListState,
    onAskQuestion: (String) -> Unit,
    onAnalyzeCurrentSlidePdf: () -> Unit,
    onUploadExternalSlideOrPdf: () -> Unit,
    isVoiceRecording: Boolean,
    voiceRecordingSeconds: Int,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    speakingMessageId: String?,
    onToggleSpeakMessage: (AiChatMessage) -> Unit,
    onClearChat: () -> Unit,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Attached Slide Visual Context Bar + PDF/Image Upload Analyzer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Slide Thumbnail Preview
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .width(72.dp)
                            .height(48.dp)
                    ) {
                        if (currentSlideBitmap != null) {
                            Image(
                                bitmap = currentSlideBitmap.asImageBitmap(),
                                contentDescription = "স্লাইড পৃষ্ঠা $activeSlidePage",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Slide Source Info & Page Switcher
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF4F46E5).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "📌 স্লাইড সোর্স সংযুক্ত",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5),
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (totalPageCount > 1) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (includeAdjacentSlides) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onToggleAdjacentSlides() }
                                ) {
                                    Text(
                                        text = if (includeAdjacentSlides) "✓ ৩ পৃষ্ঠা একত্রে" else "+ আশেপাশের স্লাইড",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (includeAdjacentSlides) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "পৃষ্ঠা ${toBengaliDigits(activeSlidePage)} এর ছবি ও লেখা সরাসরি AI দেখবে",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Prev / Next Slide Page Stepper
                    if (totalPageCount > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { if (activeSlidePage > 1) onChangeSlidePage(activeSlidePage - 1) },
                                enabled = activeSlidePage > 1,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "আগের স্লাইড",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${toBengaliDigits(activeSlidePage)}/${toBengaliDigits(totalPageCount)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            IconButton(
                                onClick = { if (activeSlidePage < totalPageCount) onChangeSlidePage(activeSlidePage + 1) },
                                enabled = activeSlidePage < totalPageCount,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "পরের স্লাইড",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Action Row: `analyzeSlidePDF()` on Current Slide OR Upload PDF / Image
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onAnalyzeCurrentSlidePdf,
                        enabled = !isAiLoading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF4F46E5).copy(alpha = 0.12f),
                            contentColor = Color(0xFF4F46E5)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("analyze_slide_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Summarize,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "স্লাইড সামারি ও প্রশ্ন",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onUploadExternalSlideOrPdf,
                        enabled = !isAiLoading,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF7C3AED)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("upload_slide_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "PDF / ছবি আপলোড",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Missing API Key Setup Banner
                if (!isApiKeyConfigured) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenApiKeySettings() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "AI ও ভয়েস টিউটর ব্যবহার করতে Gemini API Key সেট করুন",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFD97706)
                            ) {
                                Text(
                                    text = "সেট করুন",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Main Scrollable Chat & Smart Prompts Area
        LazyColumn(
            state = chatListState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "ক্লাসে বা স্লাইডে কোন অংশটি বুঝতে সমস্যা হচ্ছে?",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (chatMessages.isNotEmpty()) {
                                TextButton(
                                    onClick = onClearChat,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("নতুন চ্যাট", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "নিচের যেকোনো অপশনে ট্যাপ করুন, 🎤 মাইক্রোফোন বাটনে চাপ দিয়ে মুখে প্রশ্ন করুন, অথবা লিখে পাঠান:",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val quickPrompts = listOf(
                            "🧠 একদম জিরো থেকে সহজ ভাষায় বুঝিয়ে দাও" to "আমি ক্লাসের লেকচার ও স্লাইড দেখেও এই পৃষ্ঠার মূল বিষয়টি বুঝতে পারছি না। একদম জিরো থেকে বাস্তব জীবনের উদাহরণ দিয়ে সহজ ভাষায় ধাপে ধাপে বুঝিয়ে দাও।",
                            "🔍 স্লাইডের চিত্র/গেইট ও সমীকরণ ভেঙে বোঝাও" to "এই স্লাইডে যে চিত্র, ডায়াগ্রাম/লজিক গেইট বা সমীকরণ ও ছক দেওয়া আছে, তার প্রতিটি অংশ ও ইনপুট-আউটপুট কীভাবে কাজ করছে তা লাইন বাই লাইন ভেঙে বুঝিয়ে দাও।",
                            "⚡ মনে রাখার শর্টকাট ট্রিক ও বাস্তব উদাহরণ" to "এই স্লাইডের নিয়ম ও সূত্রগুলো যেন পরীক্ষায় কখনোই ভুল না হয়, সেজন্য মনে রাখার শর্টকাট টেকনিক এবং একটি বাস্তব উদাহরণ দাও।",
                            "🎯 বোর্ড ও ভর্তি পরীক্ষার প্রশ্ন (CQ + MCQ) টিপস" to "এই স্লাইডের টপিক থেকে পরীক্ষায় (MCQ এবং সৃজনশীল গ/ঘ) কী ধরনের প্রশ্ন আসে এবং কীভাবে উত্তর করলে পূর্ণ নম্বর পাওয়া যাবে তা উদাহরণসহ দেখাও।",
                            "📝 মূল লেকচার টপিক, সারাংশ ও রিভিশন প্রশ্ন" to "এই স্লাইডটির মূল লেকচার টপিকসমূহ, সহজ বুলেট সারাংশ এবং বাংলায় রিভিশন প্রশ্নোত্তর তৈরি করে দাও।",
                            "❓ আমাকে ছোট কুইজ ধরে যাচাই করো" to "এই স্লাইডের ওপর ভিত্তি করে আমাকে ২টি ছোট MCQ ও ১টি অনুধাবনমূলক প্রশ্ন করো এবং উত্তরসহ ব্যাখ্যা দাও যাতে আমি যাচাই করতে পারি আমি বুঝেছি কি না।"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            quickPrompts.forEach { (label, fullPrompt) ->
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(11.dp))
                                        .clickable(enabled = !isAiLoading) {
                                            onAskQuestion(fullPrompt)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Render Conversation Messages
            items(chatMessages, key = { it.id }) { msg ->
                AiChatMessageBubble(
                    message = msg,
                    isSpeaking = speakingMessageId == msg.id,
                    onToggleSpeak = { onToggleSpeakMessage(msg) },
                    context = context,
                    onRetryOrSetupKey = {
                        if (!isApiKeyConfigured) {
                            onOpenApiKeySettings()
                        }
                    }
                )
            }

            // Loading & Progress Bar Indicator when Gemini 3.8 Flash / Live is processing
            if (isAiLoading) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4F46E5).copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.25f)),
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color(0xFF4F46E5)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loadingStatusText.ifBlank {
                                            "Gemini 3.8 Flash স্লাইড পৃষ্ঠা ${toBengaliDigits(activeSlidePage)} বিশ্লেষণ করছে..."
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "স্লাইডের চিত্র, সমীকরণ ও অডিও দেখে সহজ ব্যাখ্যা তৈরি করা হচ্ছে",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (showProgressBar) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { analysisProgress.coerceIn(0f, 1f) },
                                        color = Color(0xFF4F46E5),
                                        trackColor = Color(0xFF4F46E5).copy(alpha = 0.16f),
                                        drawStopIndicator = {},
                                        gapSize = 0.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                    Text(
                                        text = "অগ্রগতি: ${toBengaliDigits((analysisProgress * 100).toInt().coerceIn(5, 100))}%",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Bottom Question Input & Live Voice Interaction Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Active Live Voice Recording Banner (`gemini-3.8-live`)
                AnimatedVisibility(visible = isVoiceRecording) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDC2626))
                                )
                                Column {
                                    Text(
                                        text = "🎙️ লাইভ ভয়েস রেকর্ড হচ্ছে (${toBengaliDigits(voiceRecordingSeconds)}s)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                    Text(
                                        text = "স্লাইডটি নিয়ে আপনার প্রশ্ন বলুন, বলা শেষ হলে 'পাঠান' চাপুন",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = onCancelVoiceRecording,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("বাতিল", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                                }
                                Button(
                                    onClick = onToggleVoiceRecording,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("পাঠান", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Quick Follow-up chips if conversation already started
                if (chatMessages.isNotEmpty() && !isAiLoading && !isVoiceRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val followUps = listOf(
                            "আরেকটু সহজ উদাহরণ দিয়ে বোঝাও",
                            "এর সত্যক সারণি (Truth Table) বা ধাপগুলো দেখাও",
                            "পরীক্ষায় এখান থেকে কী প্রশ্ন আসে?",
                            "পরের স্লাইডটি বুঝিয়ে দাও"
                        )
                        followUps.forEach { chipText ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF4F46E5).copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (chipText == "পরের স্লাইডটি বুঝিয়ে দাও" && activeSlidePage < totalPageCount) {
                                            onChangeSlidePage(activeSlidePage + 1)
                                        }
                                        onAskQuestion(chipText)
                                    }
                            ) {
                                Text(
                                    text = chipText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Microphone Voice Input Button (`startVoiceSession`)
                    FilledTonalIconButton(
                        onClick = onToggleVoiceRecording,
                        enabled = !isAiLoading,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isVoiceRecording) Color(0xFFDC2626) else Color(0xFF4F46E5).copy(alpha = 0.12f),
                            contentColor = if (isVoiceRecording) Color.White else Color(0xFF4F46E5)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_slide_voice_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isVoiceRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isVoiceRecording) "ভয়েস প্রশ্ন পাঠান" else "ভয়েস দিয়ে প্রশ্ন করুন",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = onQuestionChange,
                        placeholder = {
                            Text(
                                "স্লাইডের যা বুঝতে পারছেন না লিখুন বা 🎤 চাপুন...",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_slide_question_input"),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 3
                    )

                    FilledIconButton(
                        onClick = {
                            if (questionInput.isNotBlank() && !isAiLoading) {
                                val q = questionInput.trim()
                                onQuestionChange("")
                                onAskQuestion(q)
                            }
                        },
                        enabled = questionInput.isNotBlank() && !isAiLoading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_slide_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "প্রশ্ন পাঠান",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatMessageBubble(
    message: AiChatMessage,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    context: Context,
    onRetryOrSetupKey: () -> Unit
) {
    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = Color(0xFF4F46E5),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.uploadedFileName != null || message.attachedSlidePage != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.padding(bottom = 5.dp)
                        ) {
                            Text(
                                text = when {
                                    message.uploadedFileName != null -> "📎 ফাইল: ${message.uploadedFileName}"
                                    message.attachedSlideCount > 1 && message.attachedSlidePage != null ->
                                        "📄 স্লাইড পৃষ্ঠা ${toBengaliDigits(message.attachedSlidePage)} (+পাশাপাশি স্লাইড)"
                                    message.attachedSlidePage != null ->
                                        "📄 স্লাইড পৃষ্ঠা ${toBengaliDigits(message.attachedSlidePage)}"
                                    else -> ""
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = message.text,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = Color.White
                    )
                }
            }
        }
    } else {
        Card(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isError) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                1.dp,
                if (message.isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                } else {
                    Color(0xFF4F46E5).copy(alpha = 0.3f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (message.isError) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            } else {
                                Color(0xFF4F46E5).copy(alpha = 0.12f)
                            }
                        ) {
                            Text(
                                text = when {
                                    message.isError -> "⚠️ সতর্কবার্তা"
                                    message.isVoiceQuery -> "🎙️ Gemini 3.8 Live ভয়েস টিউটর"
                                    else -> "✨ Gemini 3.8 Flash টিউটর"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (message.isError) MaterialTheme.colorScheme.error else Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        if (message.attachedSlidePage != null && !message.isError) {
                            Text(
                                text = "• পৃষ্ঠা ${toBengaliDigits(message.attachedSlidePage)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!message.isError) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Voice Playback Button (`🔊 শুনুন` / `⏹ থামান`)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSpeaking) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFF4F46E5).copy(alpha = 0.1f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleSpeak() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "শুনুন",
                                        tint = if (isSpeaking) Color(0xFF059669) else Color(0xFF4F46E5),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (isSpeaking) "থামান" else "শুনুন",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSpeaking) Color(0xFF059669) else Color(0xFF4F46E5)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("AI Study Note", message.text)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "ব্যাখ্যা ক্লিপবোর্ডে কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "কপি করুন",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message.text,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                )

                if (message.isError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRetryOrSetupKey,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gemini API Key সেটআপ করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookLMTabContent(
    title: String,
    targetFile: File?,
    remoteUrl: String?,
    context: Context
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Google NotebookLM এর বিশেষ সুবিধা",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• অডিও পডকাস্ট ওভারভিউ: পুরো স্লাইডকে বাস্তবসম্মত ২ জন এআই হোস্টের আলোচনায় রূপান্তর করে শুনতে পারবেন।\n• নির্ভুল রেফারেন্সভিত্তিক প্রশ্নোত্তর ও রিভিশন গাইড তৈরি।\n• যেকোনো কঠিন সমীকরণ বা ডায়াগ্রামের গভীর বিশ্লেষণ।",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "সরাসরি অ্যাকশন",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                NotebookLMHelper.sharePdfToNotebookLM(context, targetFile, remoteUrl, title)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F46E5)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("নোটবুক এলএম-এ ফাইল পাঠান", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        FilledTonalButton(
            onClick = {
                NotebookLMHelper.openNotebookLMWeb(context, remoteUrl)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("NotebookLM ওয়েবসাইট খুলুন", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        if (!remoteUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    NotebookLMHelper.copyPdfLink(context, remoteUrl, title)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("পিডিএফ ডাউনলোড লিংক কপি করুন", fontSize = 13.sp)
            }
        }
    }
}
