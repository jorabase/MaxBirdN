package com.example.ui.screens

import android.widget.Toast
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
import com.example.tts.TtsEnginePreference
import com.example.tts.TtsVoicePackManager
import com.example.tts.VoicePackDownloadProgress
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dialog for switching between Offline Neural TTS (Sherpa-ONNX) and Google Offline TTS,
 * downloading/removing Bangla & English ONNX voice packs, and live-testing voices.
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

    var selectedEngine by remember { mutableStateOf(TtsVoicePackManager.getEnginePreference(context)) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val hasSherpaRuntime = remember { TtsVoicePackManager.isSherpaRuntimeBundled() }

    val effectivePacks = remember(ttsModels) {
        TtsVoicePackManager.resolveEffectiveVoicePacks(ttsModels)
    }
    val bnInstalled = remember(refreshTick, downloadStates) {
        TtsVoicePackManager.getInstalledModelPaths(context, "bn")
    }
    val enInstalled = remember(refreshTick, downloadStates) {
        TtsVoicePackManager.getInstalledModelPaths(context, "en")
    }

    // Dedicated lightweight preview manager for instant 1-tap voice testing inside the dialog
    val previewTts = remember { OfflineBookTtsManager(context.applicationContext) }
    val previewState by previewTts.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose { previewTts.release() }
    }

    AlertDialog(
        onDismissRequest = {
            previewTts.stop()
            onDismiss()
        },
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
                        text = "অফলাইন ভয়েস ও TTS",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sherpa-ONNX নিউরাল ও Google TTS",
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
                // 1. Engine Mode Selector (Keep Google TTS + Sherpa-ONNX Neural TTS side-by-side)
                Text(
                    text = "ভয়েস ইঞ্জিন নির্বাচন করুন",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TtsEnginePreference.entries.forEach { pref ->
                        val isSelected = selectedEngine == pref
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = if (isSelected) {
                                Color(0xFF10B981).copy(alpha = if (isDark) 0.18f else 0.11f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.25f else 0.45f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF10B981)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .clickable {
                                    selectedEngine = pref
                                    TtsVoicePackManager.setEnginePreference(context, pref)
                                    previewTts.refreshVoiceAvailability()
                                    onVoiceChanged()
                                }
                                .testTag("tts_engine_option_${pref.storageKey}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981)),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pref.labelBn,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = pref.subtitleBn,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Quick Live Voice Test Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (previewState.isSpeaking) {
                                previewTts.stop()
                            } else {
                                previewTts.refreshVoiceAvailability()
                                previewTts.speak("স্বাগতম! এটি আপনার অফলাইন বাংলা ভয়েস রিডার।")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                            contentColor = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("test_bangla_voice_button")
                    ) {
                        Icon(
                            imageVector = if (previewState.isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (previewState.isSpeaking) "থামান" else "বাংলা টেস্ট",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (previewState.isSpeaking) {
                                previewTts.stop()
                            } else {
                                previewTts.refreshVoiceAvailability()
                                previewTts.speak("Welcome! This is your offline English neural voice reader.")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF0284C7).copy(alpha = 0.15f),
                            contentColor = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("test_english_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "English Test",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // 3. Offline Neural Voice Packs (Sherpa-ONNX: Bangla Coqui VITS + English Piper Lessac)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "অফলাইন নিউরাল ভয়েস প্যাক",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasSherpaRuntime) {
                            Color(0xFF10B981).copy(alpha = 0.15f)
                        } else {
                            Color(0xFFF59E0B).copy(alpha = 0.16f)
                        }
                    ) {
                        Text(
                            text = if (hasSherpaRuntime) "ONNX সক্রিয় ✓" else "বিল্ডে যুক্ত হবে",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasSherpaRuntime) Color(0xFF10B981) else Color(0xFFD97706),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                effectivePacks["bn"]?.let { pack ->
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
                                previewTts.refreshVoiceAvailability()
                                onVoiceChanged()
                                res.onSuccess {
                                    Toast.makeText(context, "বাংলা অফলাইন নিউরাল ভয়েস প্রস্তুত!", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, err.localizedMessage ?: "ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onDelete = {
                            TtsVoicePackManager.deleteVoicePack(context, "bn")
                            refreshTick++
                            previewTts.refreshVoiceAvailability()
                            onVoiceChanged()
                            Toast.makeText(context, "বাংলা ভয়েস প্যাক মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                effectivePacks["en"]?.let { pack ->
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
                                previewTts.refreshVoiceAvailability()
                                onVoiceChanged()
                                res.onSuccess {
                                    Toast.makeText(context, "ইংরেজি অফলাইন নিউরাল ভয়েস প্রস্তুত!", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, err.localizedMessage ?: "ডাউনলোড ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onDelete = {
                            TtsVoicePackManager.deleteVoicePack(context, "en")
                            refreshTick++
                            previewTts.refreshVoiceAvailability()
                            onVoiceChanged()
                            Toast.makeText(context, "ইংরেজি ভয়েস প্যাক মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (!hasSherpaRuntime) {
                    Text(
                        text = "নোট: GitHub Actions থেকে নতুন Release APK ইনস্টল করলে Sherpa-ONNX নিউরাল ইঞ্জিন স্বয়ংক্রিয়ভাবে সক্রিয় থাকবে। ততক্ষণ Google অফলাইন TTS কাজ করবে।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }

                // 4. Built-in Android Google Offline Voice Quick Setup
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.3f else 0.45f),
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
                                text = "Google অফলাইন ভয়েস সেটিংস",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Google TTS ব্যবহার করলে ফোনের Speech Services থেকে অফলাইন বাংলা (bn-BD) ভয়েস ডাউনলোড করা আছে কিনা দেখে নিন।",
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
                                text = "ফোনের Google ভয়েস সেটিংস খুলুন",
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
                onClick = {
                    previewTts.stop()
                    onDismiss()
                },
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
        String.format(Locale.US, "%.1f MB সেভড • অফলাইনে প্রস্তুত", installedBytes / (1024.0 * 1024.0))
    } else {
        "প্রায় ${pack.sizeMb} MB • একবার ডাউনলোড করলেই অফলাইন"
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
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isDownloading) progress?.statusTextBn.orEmpty() else sizeLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("download_voice_pack_${pack.lang}")
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
            } else if (!progress?.errorTextBn.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = progress?.errorTextBn.orEmpty(),
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}
