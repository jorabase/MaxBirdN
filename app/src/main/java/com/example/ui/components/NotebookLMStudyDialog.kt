package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
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
import com.example.ai.AiChatMessage
import com.example.ai.GeminiAiStudyRepository
import com.example.ai.GeminiTutorResult
import com.example.ui.dialogs.GeminiApiSettingsDialog
import com.example.utils.NotebookLMHelper
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.launch
import java.io.File

/**
 * Universal Dialog for In-App Gemini 3.8 Flash Slide Study Tutor & Google NotebookLM Integration.
 * Sends the exact lecture slide page visually to Gemini 3.8 Flash so students can ask any question
 * about diagrams, logic gates, math derivations, or concepts they didn't understand in class.
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
    var selectedTab by remember { mutableIntStateOf(initialTab) } // 1: AI Tutor, 0: NotebookLM

    // Active slide page that the student is asking about (can be changed inside the dialog!)
    val safeTotalPages = totalPageCount.coerceAtLeast(1)
    var activeSlidePage by remember(currentPage, safeTotalPages) {
        mutableIntStateOf(currentPage.coerceIn(1, safeTotalPages))
    }
    var includeAdjacentSlides by remember { mutableStateOf(false) }

    // Rendered bitmap preview of the active slide page
    var currentSlideBitmap by remember(activeSlidePage, renderer) {
        mutableStateOf(renderer?.getCachedBitmap(activeSlidePage - 1))
    }

    LaunchedEffect(activeSlidePage, renderer) {
        if (renderer != null && currentSlideBitmap == null) {
            currentSlideBitmap = renderer.renderPage(activeSlidePage - 1, 1600)
        }
    }

    // Initialize and check Gemini API Key from local + Supabase cloud
    LaunchedEffect(Unit) {
        GeminiAiStudyRepository.initialize(context)
        GeminiAiStudyRepository.syncApiKeyFromCloud(context, forceRefresh = false)
    }

    val geminiConfig by GeminiAiStudyRepository.configState.collectAsState()
    var showApiSettingsDialog by remember { mutableStateOf(false) }

    // Multi-turn AI Tutor Conversation State
    var questionInput by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<AiChatMessage>() }
    val chatListState = rememberLazyListState()

    fun sendQuestionToGemini(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isBlank() || isAiLoading) return

        // Add user message to chat
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

        coroutineScope.launch {
            try {
                if (chatMessages.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatMessages.size - 1)
                }
            } catch (_: Exception) {}

            // Collect slide bitmap(s) for multimodal visual analysis
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

    Dialog(
        onDismissRequest = onDismiss,
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
                                    Color(0xFF312E81), // Deep Indigo
                                    Color(0xFF4F46E5), // Indigo
                                    Color(0xFF7C3AED)  // Violet
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
                                    text = "AI স্লাইড টিউটর",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981)
                                ) {
                                    Text(
                                        text = "Gemini 3.8 Flash",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
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
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = onDismiss,
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
                                Text("✨ এআই দিয়ে স্লাইড বুঝুন", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
                                Text("NotebookLM এ পাঠান", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
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
                            chatMessages = chatMessages,
                            chatListState = chatListState,
                            onAskQuestion = { prompt ->
                                sendQuestionToGemini(prompt)
                            },
                            onClearChat = { chatMessages.clear() },
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
    chatMessages: List<AiChatMessage>,
    chatListState: androidx.compose.foundation.lazy.LazyListState,
    onAskQuestion: (String) -> Unit,
    onClearChat: () -> Unit,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Attached Slide Visual Context Bar (NotebookLM Source Card)
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
                            .width(74.dp)
                            .height(50.dp)
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
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Toggle Single Slide vs Multi-Slide Context
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
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "পৃষ্ঠা ${toBengaliDigits(activeSlidePage)} এর ছবি ও লেখা সরাসরি AI দেখবে",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Prev / Next Slide Page Stepper inside AI Dialog
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
                                fontSize = 11.5.sp,
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

                // Missing API Key Setup Banner (if user hasn't added their key yet)
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
                                    text = "AI দিয়ে স্লাইড বুঝতে আপনার Gemini API Key সেট করুন",
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
            // Welcome & Quick Pedagogical Prompts Card
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = "নিচের যেকোনো অপশনে ট্যাপ করুন অথবা সরাসরি নিচে আপনার প্রশ্ন লিখুন (যেমন: 'আমি এই গেইটটি বুঝতে পারছি না, কীভাবে কাজ করে?'):",
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
                            "📝 এই স্লাইডের মূল সারাংশ ও রিভিশন নোট" to "NotebookLM এর মতো এই স্লাইডটির একটি গোছানো স্টাডি গাইড, মূল পয়েন্টগুলোর সারাংশ এবং কুইক রিভিশন নোট তৈরি করে দাও।",
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
                    context = context,
                    onRetryOrSetupKey = {
                        if (!isApiKeyConfigured) {
                            onOpenApiKeySettings()
                        }
                    }
                )
            }

            // Loading Indicator when Gemini 3.8 Flash is analyzing the slide
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
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF4F46E5)
                            )
                            Column {
                                Text(
                                    text = "Gemini 3.8 Flash স্লাইড পৃষ্ঠা ${toBengaliDigits(activeSlidePage)} বিশ্লেষণ করছে...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "স্লাইডের চিত্র, সমীকরণ ও লেখা দেখে সহজ ব্যাখ্যা তৈরি করা হচ্ছে",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Bottom Question Input Bar
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
                // Quick Follow-up chips if conversation already started
                if (chatMessages.isNotEmpty() && !isAiLoading) {
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
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = onQuestionChange,
                        placeholder = {
                            Text(
                                "স্লাইডের যা বুঝতে পারছেন না প্রশ্ন করুন...",
                                fontSize = 13.sp,
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
                    if (message.attachedSlidePage != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.padding(bottom = 5.dp)
                        ) {
                            Text(
                                text = if (message.attachedSlideCount > 1) {
                                    "📄 স্লাইড পৃষ্ঠা ${toBengaliDigits(message.attachedSlidePage)} (+পাশাপাশি স্লাইড)"
                                } else {
                                    "📄 স্লাইড পৃষ্ঠা ${toBengaliDigits(message.attachedSlidePage)}"
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                text = if (message.isError) "⚠️ সতর্কবার্তা" else "✨ Gemini 3.8 Flash টিউটর",
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
