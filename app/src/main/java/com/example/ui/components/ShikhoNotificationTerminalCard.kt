package com.example.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notification.ShikhoNotificationManager
import com.example.notification.ShikhoNotificationManager.TopicConnectionStatus
import com.example.notification.ShikhoNotificationManager.TopicSubscriptionState

/**
 * Real-time Shikho FCM Connection & Diagnostic Terminal.
 * Strictly rendered ONLY on devices where `can_view_profiles == true`.
 * Shows real FirebaseApp, Firebase Installation ID (FID), FIS Auth JWT, FCM Token,
 * live `fcmregistrations.googleapis.com` `:subscribe` HTTP responses, and incoming pushes.
 */
@Composable
fun ShikhoNotificationTerminalCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by ShikhoNotificationManager.connectionState.collectAsStateWithLifecycle()
    val liveLogs by ShikhoNotificationManager.liveLogs.collectAsStateWithLifecycle()

    var topicsExpanded by remember { mutableStateOf(true) }
    var consoleExpanded by remember { mutableStateOf(true) }

    val pulseTransition = rememberInfiniteTransition(label = "terminal_status_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val statusColor = when {
        connectionState.isSyncing -> Color(0xFF38BDF8)
        connectionState.isConnected -> Color(0xFF10B981)
        else -> Color(0xFFEF4444)
    }

    val statusLabel = when {
        connectionState.isSyncing -> "কানেক্ট হচ্ছে..."
        connectionState.isConnected -> "শিখো নোটিফিকেশন কানেক্টেড"
        else -> "কানেক্টেড নয়"
    }

    val statusCodeBadge = when {
        connectionState.isSyncing -> "SYNCING"
        connectionState.isConnected -> "${connectionState.connectedTopicCount}/${connectionState.activeTargetTopicCount} LIVE"
        else -> "OFFLINE"
    }

    val consoleScrollState = rememberScrollState()
    LaunchedEffect(liveLogs.size) {
        if (liveLogs.isNotEmpty()) {
            consoleScrollState.animateScrollTo(consoleScrollState.maxValue)
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF070E1A),
        border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.45f)),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("shikho_fcm_terminal_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Terminal Header Chrome
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF0B1528),
                                Color(0xFF0F1F3A),
                                Color(0xFF0A1426)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(statusColor.copy(alpha = 0.16f))
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Shikho Notification Terminal",
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(if (connectionState.isSyncing || connectionState.isConnected) pulseAlpha else 1f)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SHIKHO FCM টার্মিনাল",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFF8FAFC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = statusLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Compact live status chip (never crowds Bengali title)
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = statusColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusCodeBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 2. Real Connection Telemetry Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0C1629))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TerminalMetaRow(
                        label = "PROJECT",
                        value = connectionState.projectId.ifBlank { "Not Initialized" },
                        valueColor = if (connectionState.projectId.isNotBlank()) Color(0xFF38BDF8) else Color(0xFFF87171)
                    )
                    TerminalMetaRow(
                        label = "FID (REG)",
                        value = connectionState.installationId.ifBlank { "Unavailable" },
                        valueColor = if (connectionState.installationId.isNotBlank()) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                    TerminalMetaRow(
                        label = "FIS AUTH",
                        value = if (connectionState.fisAuthReady) "JWT Verified (Active)" else "Missing / Failed",
                        valueColor = if (connectionState.fisAuthReady) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                    TerminalMetaRow(
                        label = "FCM TOKEN",
                        value = connectionState.fcmTokenPreview.ifBlank { "Not Ready" },
                        valueColor = if (connectionState.fcmTokenPreview.isNotBlank()) Color(0xFFE2E8F0) else Color(0xFFFBBF24)
                    )
                    TerminalMetaRow(
                        label = "USER ID",
                        value = connectionState.activeUserId.ifBlank { "Not Logged In" },
                        valueColor = Color(0xFFCBD5E1)
                    )
                    TerminalMetaRow(
                        label = "COURSES SYNC",
                        value = buildString {
                            append("${connectionState.activeCourseCount} Active")
                            if (connectionState.expiredCourseCount > 0) {
                                append(" • ${connectionState.expiredCourseCount} Expired")
                            }
                            if (connectionState.autoUnsubscribedCount > 0) {
                                append(" • ${connectionState.autoUnsubscribedCount} Unsub")
                            }
                        },
                        valueColor = if (connectionState.activeCourseCount > 0) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                    if (connectionState.expiredCourseNames.isNotEmpty()) {
                        TerminalMetaRow(
                            label = "EXPIRED CLEANED",
                            value = connectionState.expiredCourseNames.joinToString(", "),
                            valueColor = Color(0xFFFBBF24)
                        )
                    }
                    TerminalMetaRow(
                        label = "ONESIGNAL",
                        value = if (connectionState.oneSignalConnected && connectionState.oneSignalPlayerId.isNotBlank()) {
                            "Active (${connectionState.oneSignalPlayerId.take(12)}...)"
                        } else {
                            "Pending / Not Linked"
                        },
                        valueColor = if (connectionState.oneSignalConnected) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                    TerminalMetaRow(
                        label = "SELECTED COURSE",
                        value = buildString {
                            append(connectionState.activeProgramId.ifBlank { "None" })
                            append(" / ")
                            append(connectionState.activePhaseId.ifBlank { "None" })
                        },
                        valueColor = Color(0xFFCBD5E1)
                    )
                    TerminalMetaRow(
                        label = "LAST CHECK",
                        value = buildString {
                            append(connectionState.lastSyncedAt.ifBlank { "—" })
                            append("  •  Push RX: ")
                            append(connectionState.receivedPushCount)
                        },
                        valueColor = Color(0xFF94A3B8)
                    )
                }

                // 3. Action Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            ShikhoNotificationManager.verifyAndSyncNow(
                                context = context.applicationContext,
                                forceRefreshFisToken = true
                            )
                            Toast.makeText(
                                context,
                                "শিখো FCM কানেকশন নতুন করে যাচাই করা হচ্ছে...",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        enabled = !connectionState.isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF0284C7).copy(alpha = 0.16f),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("shikho_terminal_resync_button")
                    ) {
                        if (connectionState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF38BDF8)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-verify Shikho FCM Connection",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (connectionState.isSyncing) "চেক হচ্ছে..." else "আবার চেক ও কানেক্ট",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = {
                            copyTerminalReport(context, connectionState, liveLogs)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF132038))
                            .border(1.dp, Color(0xFF253552), RoundedCornerShape(12.dp))
                            .testTag("shikho_terminal_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Terminal Logs",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            ShikhoNotificationManager.clearLogs()
                            Toast.makeText(context, "টার্মিনাল লগ ক্লিয়ার হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF132038))
                            .border(1.dp, Color(0xFF253552), RoundedCornerShape(12.dp))
                            .testTag("shikho_terminal_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Terminal Logs",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // 4. Per-Topic Real Subscription Table (4 Shikho Topics)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A1221))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { topicsExpanded = !topicsExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TOPIC SUBSCRIPTIONS (${connectionState.connectedTopicCount}/${connectionState.activeTargetTopicCount}) • ${connectionState.activeCourseCount} COURSES",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                        Icon(
                            imageVector = if (topicsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Topic List",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = topicsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (connectionState.topics.isEmpty()) {
                                Text(
                                    text = "কোনো টপিক সিঙ্ক এখনো শুরু হয়নি — 'আবার চেক ও কানেক্ট' চাপুন।",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(4.dp)
                                )
                            } else {
                                connectionState.topics.forEach { topic ->
                                    TopicStatusRow(topic = topic)
                                }
                            }
                        }
                    }
                }

                // 5. Live Scrollable Console Output
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF030712))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { consoleExpanded = !consoleExpanded }
                            .background(Color(0xFF0B1324))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$ live-fcm-console (${liveLogs.size} lines)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF34D399)
                        )
                        Icon(
                            imageVector = if (consoleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Live Console",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = consoleExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 250.dp)
                                .verticalScroll(consoleScrollState)
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                                .testTag("shikho_terminal_console_output")
                        ) {
                            if (liveLogs.isEmpty()) {
                                Text(
                                    text = "$ waiting for real Shikho FCM events...",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    liveLogs.forEach { entry ->
                                        val lineColor = when (entry.type) {
                                            "SUCCESS" -> Color(0xFF34D399)
                                            "HTTP" -> Color(0xFF38BDF8)
                                            "PUSH" -> Color(0xFFC084FC)
                                            "WARN" -> Color(0xFFFBBF24)
                                            "ERROR" -> Color(0xFFF87171)
                                            else -> Color(0xFFCBD5E1)
                                        }
                                        Text(
                                            text = "[${entry.timestamp}] [${entry.type}] ${entry.message}",
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = lineColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalMetaRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopicStatusRow(topic: TopicSubscriptionState) {
    val (tint, icon, badgeText) = when (topic.status) {
        TopicConnectionStatus.CONNECTED -> {
            val codeText = when {
                topic.httpCode == 200 -> "HTTP 200 OK"
                topic.sdkVerified -> "SDK OK"
                else -> "OK"
            }
            Triple(Color(0xFF10B981), Icons.Default.CheckCircle, codeText)
        }
        TopicConnectionStatus.CONNECTING -> Triple(Color(0xFF38BDF8), Icons.Default.HourglassTop, "SYNCING")
        TopicConnectionStatus.FAILED -> Triple(
            Color(0xFFEF4444),
            Icons.Default.ErrorOutline,
            topic.httpCode?.takeIf { it > 0 }?.let { "HTTP $it" } ?: "FAILED"
        )
        TopicConnectionStatus.SKIPPED -> Triple(Color(0xFF94A3B8), Icons.Default.RemoveCircleOutline, "SKIPPED")
        TopicConnectionStatus.IDLE -> Triple(Color(0xFF64748B), Icons.Default.HourglassTop, "IDLE")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F1A2E))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = topic.status.name,
                    tint = tint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = topic.label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1F5F9),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = tint.copy(alpha = 0.16f)
            ) {
                Text(
                    text = buildString {
                        append(badgeText)
                        topic.latencyMs?.takeIf { it > 0 }?.let { append(" • ${it}ms") }
                    },
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = tint,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = topic.topicName,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF94A3B8),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!topic.createTime.isNullOrBlank()) {
            Text(
                text = "createTime: ${topic.createTime}${if (topic.sdkVerified) "  |  FCM SDK ✓" else ""}",
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF34D399)
            )
        } else if (!topic.errorMessage.isNullOrBlank()) {
            Text(
                text = topic.errorMessage,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                color = if (topic.status == TopicConnectionStatus.SKIPPED) Color(0xFF94A3B8) else Color(0xFFFCA5A5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyTerminalReport(
    context: Context,
    state: ShikhoNotificationManager.ShikhoFcmConnectionState,
    logs: List<ShikhoNotificationManager.FcmTerminalLog>
) {
    val report = buildString {
        appendLine("=== SHIKHO FCM CONNECTION REPORT ===")
        appendLine("Status: ${if (state.isConnected) "CONNECTED" else "DISCONNECTED"}")
        appendLine("Project: ${state.projectId}")
        appendLine("FID: ${state.installationId}")
        appendLine("FIS Auth JWT: ${if (state.fisAuthReady) "READY" else "MISSING"}")
        appendLine("FCM Token: ${state.fcmTokenPreview}")
        appendLine("User ID: ${state.activeUserId}")
        appendLine("Courses: ${state.activeCourseCount} Active, ${state.expiredCourseCount} Expired, ${state.autoUnsubscribedCount} Auto-Unsubscribed")
        if (state.expiredCourseNames.isNotEmpty()) {
            appendLine("Expired Cleaned: ${state.expiredCourseNames.joinToString(", ")}")
        }
        appendLine("OneSignal: ${if (state.oneSignalConnected) "ACTIVE (${state.oneSignalPlayerId})" else "NOT LINKED"}")
        appendLine("Selected Program/Phase: ${state.activeProgramId} / ${state.activePhaseId}")
        appendLine("Topics:")
        state.topics.forEach { t ->
            appendLine("  - [${t.status}] ${t.topicName} (http=${t.httpCode ?: "-"}, sdk=${t.sdkVerified}, createTime=${t.createTime ?: "-"}, err=${t.errorMessage ?: "-"})")
        }
        appendLine("--- Console Logs ---")
        logs.forEach { l ->
            appendLine("[${l.timestamp}] [${l.type}] ${l.message}")
        }
    }
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Shikho FCM Terminal Logs", report).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "টার্মিনাল রিপোর্ট কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {}
}
