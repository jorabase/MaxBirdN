package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.GeminiAiStudyRepository
import com.example.auth.SessionManager
import kotlinx.coroutines.launch

@Composable
fun GeminiApiSettingsDialog(
    sessionManager: SessionManager? = null,
    onDismiss: () -> Unit,
    onKeySavedSuccessfully: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        GeminiAiStudyRepository.initialize(context, sessionManager)
        GeminiAiStudyRepository.syncApiKeyFromCloud(context, sessionManager, forceRefresh = false)
    }

    val configState by GeminiAiStudyRepository.configState.collectAsState()
    var apiKeyInput by remember { mutableStateOf(configState.apiKey) }
    LaunchedEffect(configState.apiKey) {
        if (apiKeyInput.isBlank() && configState.apiKey.isNotBlank()) {
            apiKeyInput = configState.apiKey
        }
    }
    var isKeyVisible by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isSavingKey by remember { mutableStateOf(false) }
    var isManualCloudSyncing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF4F46E5),
                                    Color(0xFF7C3AED),
                                    Color(0xFF2563EB)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini AI",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Gemini API সেটিংস",
                                    fontSize = 16.sp,
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
                                        text = "3.8 Flash",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "এআই দিয়ে লেকচার স্লাইড ও কঠিন টপিক বুঝুন",
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "বন্ধ করুন",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Cloud Sync & Model Status Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (configState.isConfigured) {
                            Color(0xFF10B981).copy(alpha = 0.1f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (configState.isConfigured) {
                                Color(0xFF10B981).copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (configState.isConfigured) Color(0xFF10B981).copy(alpha = 0.2f)
                                        else Color(0xFF6366F1).copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (configState.isConfigured) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = if (configState.isConfigured) Color(0xFF10B981) else Color(0xFF6366F1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (configState.isConfigured) {
                                        "AI টিউটর সক্রিয় আছে (${configState.displayModelName})"
                                    } else {
                                        "আপনার ব্যক্তিগত Gemini API Key যুক্ত করুন"
                                    },
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = configState.syncStatusText,
                                    fontSize = 11.5.sp,
                                    color = if (configState.isConfigured) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Manual Cloud Sync Button
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isManualCloudSyncing = true
                                        feedbackMessage = null
                                        try {
                                            val restored = GeminiAiStudyRepository.syncApiKeyFromCloud(
                                                context = context,
                                                sessionManager = sessionManager,
                                                forceRefresh = true
                                            )
                                            if (!restored.isNullOrBlank()) {
                                                apiKeyInput = restored
                                                isErrorFeedback = false
                                                feedbackMessage = "☁️ ক্লাউড ডাটাবেজ থেকে আপনার API Key সফলভাবে সিঙ্ক হয়েছে!"
                                            } else {
                                                isErrorFeedback = true
                                                feedbackMessage = "ক্লাউড ডাটাবেজে আপনার একাউন্টের কোনো সংরক্ষিত API Key পাওয়া যায়নি।"
                                            }
                                        } finally {
                                            isManualCloudSyncing = false
                                        }
                                    }
                                },
                                enabled = !isManualCloudSyncing && !isSavingKey,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (isManualCloudSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "ক্লাউড থেকে রিস্টোর করুন",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // API Key Input Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "আপনার Gemini API Key:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                feedbackMessage = null
                            },
                            placeholder = {
                                Text("AIzaSy... এখানে আপনার API Key পেস্ট করুন", fontSize = 13.sp)
                            },
                            singleLine = true,
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input"),
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { isKeyVisible = !isKeyVisible },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Show/Hide Key",
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val pasteText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                            if (pasteText.isNotBlank()) {
                                                apiKeyInput = pasteText
                                                feedbackMessage = null
                                                Toast.makeText(context, "ক্লিপবোর্ড থেকে পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "ক্লিপবোর্ডে কোনো টেক্সট পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // Feedback Banner
                    AnimatedVisibility(visible = !feedbackMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isErrorFeedback) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            } else {
                                Color(0xFF10B981).copy(alpha = 0.14f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isErrorFeedback) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isErrorFeedback) MaterialTheme.colorScheme.error else Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = feedbackMessage.orEmpty(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isErrorFeedback) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF065F46)
                                )
                            }
                        }
                    }

                    // Save & Cloud Sync Button
                    Button(
                        onClick = {
                            val trimmed = apiKeyInput.trim()
                            if (trimmed.isBlank()) {
                                isErrorFeedback = true
                                feedbackMessage = "অনুগ্রহ করে আপনার Gemini API Key লিখুন বা পেস্ট করুন।"
                                return@Button
                            }
                            scope.launch {
                                isSavingKey = true
                                feedbackMessage = null
                                try {
                                    val result = GeminiAiStudyRepository.saveAndSyncApiKey(
                                        context = context,
                                        apiKeyInput = trimmed,
                                        sessionManager = sessionManager,
                                        verifyFirst = true
                                    )
                                    result.onSuccess { msg ->
                                        isErrorFeedback = false
                                        feedbackMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        onKeySavedSuccessfully?.invoke()
                                    }.onFailure { err ->
                                        isErrorFeedback = true
                                        feedbackMessage = err.localizedMessage ?: "API Key যাচাই করা যায়নি"
                                    }
                                } finally {
                                    isSavingKey = false
                                }
                            }
                        },
                        enabled = !isSavingKey && !isManualCloudSyncing && !isDeleting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_gemini_api_key_button")
                    ) {
                        if (isSavingKey) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("যাচাই ও ডাটাবেজে সেভ হচ্ছে...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("API Key যাচাই ও ডাটাবেজে সেভ করুন", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Delete button if already configured
                    if (configState.isConfigured) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isDeleting = true
                                    try {
                                        GeminiAiStudyRepository.deleteApiKey(context, sessionManager)
                                        apiKeyInput = ""
                                        isErrorFeedback = false
                                        feedbackMessage = "API Key ডিভাইস ও ক্লাউড থেকে মুছে ফেলা হয়েছে।"
                                    } finally {
                                        isDeleting = false
                                    }
                                }
                            },
                            enabled = !isSavingKey && !isManualCloudSyncing && !isDeleting,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সংরক্ষিত API Key মুছে ফেলুন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Cloud Backup Benefit Info Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "অটোমেটিক ক্লাউড ব্যাকআপ সুবিধা",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "আপনার দেওয়া Gemini API Key আপনার একাউন্টের বিপরীতে ক্লাউড ডাটাবেজে সুরক্ষিত থাকে। ফলে অ্যাপ ডিলিট হয়ে গেলে বা নতুন কোনো মোবাইলে একাউন্ট লগইন করার সাথে সাথেই আপনার API Key স্বয়ংক্রিয়ভাবে চলে আসবে!",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // How to get a free Gemini API Key card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔑 কীভাবে ফ্রি Gemini API Key পাবেন?",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "১. নিচের বাটনে ক্লিক করে Google AI Studio ওয়েবসাইটে যান ও জিমেইল দিয়ে সাইন ইন করুন।\n২. 'Get API key' বা 'Create API key' বাটনে ট্যাপ করুন।\n৩. তৈরি হওয়া Key-টি কপি করে উপরের বক্সে পেস্ট করে সেভ করুন!",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "ব্রাউজার ওপেন করা যায়নি", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google AI Studio থেকে ফ্রি API Key নিন", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
