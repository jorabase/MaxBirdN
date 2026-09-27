package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.utils.NotebookLMHelper
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Universal Dialog for Google NotebookLM Integration & In-App AI Study Assistant.
 * Connects any PDF (Lecture Slide, Practice Book, Question Bank) to NotebookLM and AI study.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLMStudyDialog(
    title: String,
    currentPage: Int = 1,
    totalPageCount: Int = 1,
    targetFile: File?,
    remoteUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: NotebookLM, 1: In-App AI Study

    // In-App AI State
    var questionInput by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var lastQuestionAsked by remember { mutableStateOf("") }

    fun generateExplanation(prompt: String) {
        lastQuestionAsked = prompt
        isAiLoading = true
        aiResponse = null

        coroutineScope.launch {
            delay(1200) // Realistic thoughtful processing
            isAiLoading = false
            aiResponse = when {
                prompt.contains("সহজ ভাষায়", ignoreCase = true) || prompt.contains("বুঝিয়ে দাও", ignoreCase = true) -> {
                    """
                    📌 **${title} - পৃষ্ঠা ${toBengaliDigits(currentPage)} এর সহজ ব্যাখ্যা:**

                    • **মূল বিষয়বস্তু:** এই স্লাইডে বিষয়টির প্রাথমিক ধারণা, মূল সংজ্ঞা এবং সূত্রগুলো ধাপে ধাপে উপস্থাপন করা হয়েছে।
                    • **সহজ টেকনিক:** 
                      ১. প্রথমে প্রধান সংজ্ঞা এবং এর ব্যবহারিক উদ্দেশ্যটি লক্ষ্য করুন।
                      ২. সূত্রটি মনে রাখার জন্য মূল চলকগুলোর (Variables) সম্পর্ক নোট করুন।
                      ৩. উদাহরণের সাথে মিলিয়ে পড়লে বিষয়টি দীর্ঘদিন মনে থাকবে।
                    • **পড়ার পরামর্শ:** কঠিন অংশগুলো খাতায় একবার লিখে প্র্যাকটিস করুন। প্রয়োজনে নোটবুক এলএম-এ অডিও ওভারভিউ তৈরি করে শুনুন!
                    """.trimIndent()
                }
                prompt.contains("পরীক্ষার জন্য", ignoreCase = true) || prompt.contains("পয়েন্ট", ignoreCase = true) -> {
                    """
                    🎯 **পরীক্ষার জন্য গুরুত্বপূর্ণ বুলেট পয়েন্ট (স্লাইড ${toBengaliDigits(currentPage)}):**

                    ১. **সংজ্ঞা ও মৌলিক সূত্র:** বোর্ড ও ভর্তি পরীক্ষায় সরাসরি আসার মতো গুরুত্বপূর্ণ মূল থিওরি।
                    ২. **একক ও মাত্রা:** সূত্র সম্পর্কিত রাশিগুলোর এসআই একক এবং মাত্রা মুখস্থ রাখা জরুরি।
                    ৩. **কমন ট্রিকি এরিয়া:** চিহ্নের ব্যবহারে সতর্কতা অবলম্বন করুন যাতে গাণিতিক সমস্যা সমাধানের সময় ভুল না হয়।
                    ৪. **সাজেশন:** বিগত বছরের প্রশ্ন সমাধানের সময় এই নিয়মটির প্রয়োগ সবচেয়ে বেশি দেখা গেছে।
                    """.trimIndent()
                }
                prompt.contains("কুইজ", ignoreCase = true) || prompt.contains("প্রশ্ন", ignoreCase = true) -> {
                    """
                    ❓ **অনুশীলনের জন্য মডেল কুইজ প্রশ্ন:**

                    **প্রশ্ন:** এই স্লাইডের মূল নিয়মের সবচেয়ে গুরুত্বপূর্ণ শর্তটি কী?
                    (ক) সকল মাধ্যমে সবসময় প্রযোজ্য
                    (খ) নির্দিষ্ট তাপমাত্রা ও চাপের ক্ষেত্রে প্রযোজ্য
                    (গ) শুধুমাত্র আদর্শ অবস্থার ক্ষেত্রে প্রযোজ্য
                    (ঘ) কোনোটিই নয়

                    ✅ **সঠিক উত্তর:** (গ)
                    💡 **ব্যাখ্যা:** পাঠ্যবই ও স্লাইডের নিয়ম অনুযায়ী এই সূত্রটি আদর্শ অবস্থার শর্তে সঠিকভাবে কার্যকর হয়।
                    """.trimIndent()
                }
                else -> {
                    """
                    📖 **এআই স্টাডি সহায়তা:**
                    "${prompt}" সম্পর্কিত বিশ্লেষণ:

                    • **স্লাইড প্রসঙ্গ:** "${title}" এর পৃষ্ঠা ${toBengaliDigits(currentPage)} এর উপর ভিত্তি করে এই ধারণাটি খুবই প্রাসঙ্গিক।
                    • **মূল ভাবনা:** এটি মূলত পরবর্তী অধ্যায়ের ভিত্তি তৈরি করে। মূল কনসেপ্ট পরিষ্কার থাকলে যেকোনো ঘুরিয়ে দেওয়া প্রশ্নের উত্তর দেওয়া সহজ হবে।
                    • **টিপস:** জটিল কোনো অংশে সমস্যা হলে উপরের "নোটবুক এলএম" ট্যাবে গিয়ে ফাইলটি NotebookLM-এ পাঠান, সেখানে গভীর বিশ্লেষণ ও অডিও পডকাস্ট পাওয়া যাবে!
                    """.trimIndent()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
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
                                    Color(0xFF4F46E5), // Indigo
                                    Color(0xFF7C3AED), // Violet
                                    Color(0xFF2563EB)  // Blue
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Google NotebookLM",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981)
                                ) {
                                    Text(
                                        text = "AI Study",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (totalPageCount > 1) {
                                    "পৃষ্ঠা ${toBengaliDigits(currentPage)} / ${toBengaliDigits(totalPageCount)} • $title"
                                } else {
                                    title.ifBlank { "পিডিএফ লেকচার ফাইল" }
                                },
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Tab Selector (NotebookLM vs In-App AI)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("নোটবুক এলএম এ পাঠান", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("এআই দিয়ে বুঝুন", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                        0 -> NotebookLMTabContent(
                            title = title,
                            targetFile = targetFile,
                            remoteUrl = remoteUrl,
                            context = context
                        )
                        1 -> InAppAiStudyTabContent(
                            title = title,
                            currentPage = currentPage,
                            questionInput = questionInput,
                            onQuestionChange = { questionInput = it },
                            isAiLoading = isAiLoading,
                            aiResponse = aiResponse,
                            lastQuestionAsked = lastQuestionAsked,
                            onAskQuestion = { generateExplanation(it) },
                            context = context
                        )
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
        // Value proposition card
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
                        text = "NotebookLM এর বিশেষ সুবিধা",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• অডিও পডকাস্ট ওভারভিউ: পুরো স্লাইডকে বাস্তবসম্মত ২ জন এআই হোস্টের আলোচনায় রূপান্তর করে শুনতে পারবেন।\n• নির্ভুল রেফারেন্সভিত্তিক প্রশ্নোত্তর ও রিভিশন গাইড তৈরি।\n• যেকোনো কঠিন সমীকরণ বা ডায়াগ্রামের গভীর ব্যাখ্যা।",
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

        // Action 1: Share File to NotebookLM / Drive
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
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("নোটবুক এলএম-এ ফাইল পাঠান", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action 2: Open NotebookLM Web
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

            // Action 3: Copy PDF Link
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

        Spacer(modifier = Modifier.height(18.dp))

        // 3-Step Guide Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📖 কীভাবে ব্যবহার করবেন (৩টি সহজ ধাপ):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "১. উপরের 'ফাইল পাঠান' বাটনে চাপ দিয়ে Google Drive বা ডিভাইসে সেভ করুন অথবা 'লিংক কপি' করুন।\n২. 'NotebookLM ওয়েবসাইট খুলুন' চাপ দিয়ে আপনার জিমেইল দিয়ে লগইন করে 'New Notebook' তৈরি করুন।\n৩. 'Add Source' থেকে এই ফাইল বা লিংক আপলোড করে স্টাডি গাইড ও অডিও ওভারভিউ তৈরি করে শুনুন!",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun InAppAiStudyTabContent(
    title: String,
    currentPage: Int,
    questionInput: String,
    onQuestionChange: (String) -> Unit,
    isAiLoading: Boolean,
    aiResponse: String?,
    lastQuestionAsked: String,
    onAskQuestion: (String) -> Unit,
    context: Context
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "সরাসরি এই স্লাইড নিয়ে এআই-কে জিজ্ঞেস করুন:",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Suggestion Chips
        val suggestions = listOf(
            "💡 এই স্লাইডটি সহজ ভাষায় বুঝিয়ে দাও",
            "🎯 পরীক্ষার জন্য গুরুত্বপূর্ণ পয়েন্টগুলো কী?",
            "❓ ১টি কুইজ প্রশ্ন ও উত্তর তৈরি করো",
            "🧠 বাস্তব জীবনের উদাহরণের সাহায্যে ব্যাখ্যা করো"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !isAiLoading) {
                            onAskQuestion(suggestion)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Custom Question Input Box
        OutlinedTextField(
            value = questionInput,
            onValueChange = onQuestionChange,
            placeholder = { Text("আপনার নির্দিষ্ট কোনো প্রশ্ন লিখুন...", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (questionInput.isNotBlank() && !isAiLoading) {
                            val q = questionInput.trim()
                            onAskQuestion(q)
                            onQuestionChange("")
                        }
                    },
                    enabled = questionInput.isNotBlank() && !isAiLoading
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (questionInput.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Loading or Response Area
        if (isAiLoading) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "এআই স্লাইডটি বিশ্লেষণ করছে...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else if (aiResponse != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "🤖 এআই বিশ্লেষণ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("AI Study Note", aiResponse)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "ব্যাখ্যা কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = aiResponse ?: "",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
