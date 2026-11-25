package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.library.TtsModelPackInfo
import com.example.tts.OfflineBookTtsManager
import com.example.tts.TtsVoicePackManager
import com.example.tts.VoicePackDownloadProgress
import kotlinx.coroutines.launch

/**
 * Dialog for managing Offline Bangla + English Human-Like TTS Voice Packs
 * (Cloudflare R2 Sherpa-ONNX neural packs + Android system offline neural voice setup).
 */
@Composable
fun LibraryVoiceSettingsDialog(
    ttsModels: Map<String, TtsModelPackInfo>,
    onDismiss: () -> Unit,
    onVoiceChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadStates by TtsVoicePackManager.downloadStates.collectAsStateWithLifecycle()
    val isDark = com.example.ui.theme.isAppInDarkTheme()

    var refreshTick by remember { mutableIntStateOf(0) }
    val hasSherpaRuntime = remember { TtsVoicePackManager.isSherpaRuntimeBundled() }
    val bnInstalled = remember(refreshTick, downloadStates) {
        TtsVoicePackManager.getInstalledModelPaths(context, "bn")
    }
    val enInstalled = remember(refreshTick, downloadStates) {
        TtsVoicePackManager.getInstalledModelPaths(context, "en")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF0F172A) else Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "অফলাইন বাংলা ও ইংরেজি ভয়েস",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "বই পড়ে শোনানোর অফলাইন TTS সেটিংস",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10B981).copy(alpha = if (isDark) 0.12f else 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "বাংলা ও ইংরেজি লেখা স্বয়ংক্রিয়ভাবে শনাক্ত করে প্রাকৃতিক কণ্ঠে অফলাইনে পড়ে শোনাবে।",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Cloudflare R2 Neural Voice Packs (when configured in library_index.json)
                val bnPack = ttsModels["bn"]
                val enPack = ttsModels["en"]
                if (bnPack != null || enPack != null) {
                    Text(
                        text = "নিউরাল অফলাইন ভয়েস প্যাক (Cloudflare R2)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    bnPack?.let { pack ->
                        VoicePackRowCard(
                            pack = pack,
                            isInstalled = bnInstalled != null,
                            installedBytes = bnInstalled?.totalSizeBytes ?: 0L,
                            progress = downloadStates["bn"],
                            isDark = isDark,
                            onDownload = {
                                scope.launch {
                                    val res = TtsVoicePackManager.downloadAndInstallVoicePack(context, pack)
                                    refreshTick++
                                    onVoiceChanged()
                                    res.onSuccess {
                                        Toast.makeText(context, "বাংলা অফলাইন ভয়েস প্যাক প্রস্তুত!", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.localizedMessage ?: "ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onDelete = {
                                TtsVoicePackManager.deleteVoicePack(context, "bn")
                                refreshTick++
                                onVoiceChanged()
                                Toast.makeText(context, "বাংলা ভয়েস প্যাক মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    enPack?.let { pack ->
                        VoicePackRowCard(
                            pack = pack,
                            isInstalled = enInstalled != null,
                            installedBytes = enInstalled?.totalSizeBytes ?: 0L,
                            progress = downloadStates["en"],
                            isDark = isDark,
                            onDownload = {
                                scope.launch {
                                    val res = TtsVoicePackManager.downloadAndInstallVoicePack(context, pack)
                                    refreshTick++
                                    onVoiceChanged()
                                    res.onSuccess {
                                        Toast.makeText(context, "ইংরেজি অফলাইন ভয়েস প্যাক প্রস্তুত!", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.localizedMessage ?: "ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onDelete = {
                                TtsVoicePackManager.deleteVoicePack(context, "en")
                                refreshTick++
                                onVoiceChanged()
                                Toast.makeText(context, "ইংরেজি ভয়েস প্যাক মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (!hasSherpaRuntime && (bnInstalled != null || enInstalled != null)) {
                        Text(
                            text = "নোট: ভয়েস প্যাক সেভ করা হয়েছে। বর্তমানে ডিভাইসের হাই-কোয়ালিটি অফলাইন ভয়েস ব্যাকআপ হিসেবে সক্রিয় আছে।",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Built-in Android Google Offline Neural Voice Pack Quick Setup
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ফোনের অফলাইন বাংলা/ইংরেজি ভয়েস",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "অফলাইনে আরও পরিষ্কার বাংলা (bn-BD) ও ইংরেজি কণ্ঠ পেতে ফোনের Google Speech Services থেকে অফলাইন বাংলা ভয়েস ডাউনলোড করা আছে কিনা দেখে নিন।",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { OfflineBookTtsManager.openSystemTtsVoiceSettings(context) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("open_system_tts_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ফোনের অফলাইন ভয়েস সেটিংস খুলুন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.testTag("close_voice_settings_button")
            ) {
                Text("ঠিক আছে", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun VoicePackRowCard(
    pack: TtsModelPackInfo,
    isInstalled: Boolean,
    installedBytes: Long,
    progress: VoicePackDownloadProgress?,
    isDark: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val isDownloading = progress?.isDownloading == true
    val sizeLabel = if (installedBytes > 0L) {
        String.format("%.1f MB সেভড", installedBytes / (1024.0 * 1024.0))
    } else {
        "প্রায় ${pack.sizeMb} MB"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
        border = BorderStroke(
            1.dp,
            if (isInstalled) Color(0xFF10B981).copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isInstalled) Color(0xFF10B981).copy(alpha = 0.16f)
                            else Color(0xFF0284C7).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isInstalled) Color(0xFF10B981) else Color(0xFF0284C7),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isDownloading) progress?.statusTextBn.orEmpty() else sizeLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF10B981)
                    )
                } else if (isInstalled) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete voice pack",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onDownload,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text("ডাউনলোড", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (progress?.progressFraction ?: 0.05f).coerceIn(0.02f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}
