package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.auth.SessionManager
import com.example.notification.ShikhoNotificationManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

@Composable
fun FcmLiveTerminalDialog(
    sessionManager: SessionManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val logs by ShikhoNotificationManager.liveLogs.collectAsState()
    val listState = rememberLazyListState()

    var fcmToken by remember { mutableStateOf(sessionManager.getFcmToken() ?: "") }
    var isSyncing by remember { mutableStateOf(false) }

    val userId = sessionManager.getUserId() ?: ""
    val programId = sessionManager.getSubscribedFcmProgramId() ?: sessionManager.getActiveProgramId() ?: ""
    val phaseId = sessionManager.getSubscribedFcmPhaseId() ?: sessionManager.getActiveProgramPhaseId() ?: ""

    // Auto-scroll to latest log
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // Refresh token on launch
    LaunchedEffect(Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                fcmToken = task.result
                sessionManager.setFcmToken(task.result)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)) // Dark Slate Terminal Theme
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 1. Top Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FCM লাইভ টার্মিনাল ও ক্লাউড সিঙ্ক",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Active Session & Topics Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🔴 ক্লাউড সাবস্ক্রিপশন স্ট্যাটাস (শিখো ফায়ারবেস):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Global Topic
                        TopicStatusRow(
                            label = "1. Global Broadcast:",
                            topic = ShikhoNotificationManager.TOPIC_SHIKHO_ALL,
                            isActive = true
                        )

                        // User Specific Topic
                        TopicStatusRow(
                            label = "2. User Topic:",
                            topic = if (userId.isNotBlank()) userId else "লগইন করা নেই",
                            isActive = userId.isNotBlank()
                        )

                        // User + Program Topic
                        val userProgTopic = if (userId.isNotBlank() && programId.isNotBlank()) {
                            "LIVE_ShikhoNotification_UserID_${userId}_Program_${programId}"
                        } else "অপেক্ষমাণ"
                        TopicStatusRow(
                            label = "3. Program Alert:",
                            topic = userProgTopic,
                            isActive = userId.isNotBlank() && programId.isNotBlank()
                        )

                        // Program + Phase Topic
                        val progPhaseTopic = if (programId.isNotBlank() && phaseId.isNotBlank()) {
                            "LIVE_ShikhoNotification_Program_${programId}_Phase_${phaseId}"
                        } else "অপেক্ষমাণ"
                        TopicStatusRow(
                            label = "4. Phase Live Alert:",
                            topic = progPhaseTopic,
                            isActive = programId.isNotBlank() && phaseId.isNotBlank()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. FCM Device Token Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device FCM Token:",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = if (fcmToken.isNotBlank()) fcmToken.take(28) + "..." else "টোকেন লোড হচ্ছে...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                if (fcmToken.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", fcmToken))
                                    Toast.makeText(context, "FCM টোকেন কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "কপি করুন",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Terminal Log Console
                Text(
                    text = "💻 লাইভ ইভেন্ট লগ (টার্মিনাল):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF030712)) // Pure terminal dark
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "কোনো লগ এন্ট্রি নেই। নিচে 'সিঙ্ক রিফ্রেশ করুন' বাটনে ট্যাপ করুন।",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(logs) { log ->
                                val textColor = when (log.type) {
                                    "SUCCESS" -> Color(0xFF4ADE80) // Green
                                    "ERROR" -> Color(0xFFF87171)   // Red
                                    "WARN" -> Color(0xFFFBBF24)    // Amber
                                    "PUSH" -> Color(0xFFF43F5E)    // Pink/Rose
                                    else -> Color(0xFF38BDF8)      // Cyan
                                }

                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "[${log.timestamp}] ",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = log.message,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Action Buttons (Re-Sync & Clear Logs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { ShikhoNotificationManager.clearLogs() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ক্লিয়ার", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            isSyncing = true
                            coroutineScope.launch {
                                ShikhoNotificationManager.syncAllTopicSubscriptions(context)
                                kotlinx.coroutines.delay(1000)
                                isSyncing = false
                            }
                        },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "সিঙ্ক হচ্ছে...", fontSize = 13.sp, color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "টপিক সিঙ্ক যাচাই করুন", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicStatusRow(
    label: String,
    topic: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isActive) Color(0xFF22C55E) else Color(0xFFEF4444))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF94A3B8),
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = topic,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isActive) Color(0xFFE2E8F0) else Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
