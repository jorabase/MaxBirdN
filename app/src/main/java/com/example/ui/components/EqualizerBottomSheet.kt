package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.player.EqualizerManager
import com.example.player.ExternalEqualizerAppInfo
import com.example.ui.theme.isAppInDarkTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = isAppInDarkTheme()

    var enabled by remember { mutableStateOf(EqualizerManager.isEnabled(context)) }
    var selectedPreset by remember { mutableIntStateOf(EqualizerManager.getSelectedPresetIndex(context)) }
    var bassStrength by remember { mutableFloatStateOf(EqualizerManager.getBassStrength(context) / 10f) }
    var loudnessGain by remember { mutableFloatStateOf(EqualizerManager.getLoudnessGain(context) / 15f) }

    var selectedExtPkg by remember { mutableStateOf(EqualizerManager.getSelectedExternalAppPackage(context)) }
    var selectedExtName by remember { mutableStateOf(EqualizerManager.getSelectedExternalAppName(context)) }

    var band0 by remember { mutableFloatStateOf(EqualizerManager.getCustomBandPercent(context, 0).toFloat()) }
    var band1 by remember { mutableFloatStateOf(EqualizerManager.getCustomBandPercent(context, 1).toFloat()) }
    var band2 by remember { mutableFloatStateOf(EqualizerManager.getCustomBandPercent(context, 2).toFloat()) }
    var band3 by remember { mutableFloatStateOf(EqualizerManager.getCustomBandPercent(context, 3).toFloat()) }
    var band4 by remember { mutableFloatStateOf(EqualizerManager.getCustomBandPercent(context, 4).toFloat()) }

    val installedEqualizerApps = remember {
        EqualizerManager.scanInstalledEqualizerApps(context)
    }

    // Open directly on the user's last active mode ("mobile" vs "builtin")
    var activeTab by remember {
        val savedMode = EqualizerManager.getActiveMode(context)
        mutableIntStateOf(if (savedMode == EqualizerManager.MODE_BUILTIN && selectedExtPkg == null) 1 else 0)
    }

    fun syncBandsFromManager() {
        band0 = EqualizerManager.getCustomBandPercent(context, 0).toFloat()
        band1 = EqualizerManager.getCustomBandPercent(context, 1).toFloat()
        band2 = EqualizerManager.getCustomBandPercent(context, 2).toFloat()
        band3 = EqualizerManager.getCustomBandPercent(context, 3).toFloat()
        band4 = EqualizerManager.getCustomBandPercent(context, 4).toFloat()
    }

    val sheetBg = if (isDark) Color(0xFF0B1322) else Color.White
    val cardBg = if (isDark) Color(0xFF131F33) else Color(0xFFF8FAFC)
    val cardBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Studio Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF0284C7))
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "অডিও ইকুলাইজার স্টুডিও",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            val activeLabel = when {
                                !selectedExtName.isNullOrBlank() -> "সংযুক্ত: $selectedExtName"
                                enabled -> "বিল্ট-ইন ইকুলাইজার সক্রিয়"
                                else -> "পছন্দের ইকুলাইজার সিলেক্ট করুন"
                            }
                            Text(
                                text = activeLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7DD3FC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF10B981).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Auto-Save",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF6EE7B7)
                            )
                        }
                    }
                }
            }

            // 2. Mode Switcher Tabs (Mobile Equalizer vs Built-in Control)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    "মোবাইল ইকুলাইজার" to Icons.Default.PhoneAndroid,
                    "বিল্ট-ইন কন্ট্রোল" to Icons.Default.Tune
                )
                tabs.forEachIndexed { idx, (label, icon) ->
                    val isSelected = activeTab == idx
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF0284C7) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                activeTab = idx
                                EqualizerManager.setActiveMode(
                                    context,
                                    if (idx == 0) EqualizerManager.MODE_MOBILE else EqualizerManager.MODE_BUILTIN
                                )
                            }
                            .testTag("eq_tab_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 11.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else secondaryText,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.White else secondaryText
                            )
                        }
                    }
                }
            }

            if (activeTab == 0) {
                // =========================================================================
                // TAB 0: MOBILE EQUALIZER (PERMANENT SELECTION & ONE-TAP PANEL LAUNCH)
                // =========================================================================

                // Active Persistent Connection Banner
                if (!selectedExtName.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF10B981).copy(alpha = if (isDark) 0.14f else 0.08f)
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$selectedExtName সিলেক্ট করা আছে",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText
                                )
                                Text(
                                    text = "প্রতিবার ক্লাস প্লে করলে এটি স্বয়ংক্রিয়ভাবে কাজ করবে",
                                    fontSize = 11.5.sp,
                                    color = secondaryText
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "আপনার মোবাইলে থাকা ইকুলাইজার নির্বাচন করুন:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    installedEqualizerApps.forEach { appInfo ->
                        val isSelected = selectedExtPkg == appInfo.packageName
                        MobileEqualizerSelectCard(
                            appInfo = appInfo,
                            isSelected = isSelected,
                            isDark = isDark,
                            onSelect = {
                                EqualizerManager.selectMobileEqualizerApp(
                                    context = context,
                                    appInfo = appInfo,
                                    openPanelImmediately = false
                                )
                                selectedExtPkg = appInfo.packageName
                                selectedExtName = appInfo.appName
                                Toast.makeText(
                                    context,
                                    "'${appInfo.appName}' স্থায়ীভাবে সিলেক্ট করা হয়েছে!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onOpenControlPanel = {
                                EqualizerManager.selectMobileEqualizerApp(
                                    context = context,
                                    appInfo = appInfo,
                                    openPanelImmediately = true
                                )
                                selectedExtPkg = appInfo.packageName
                                selectedExtName = appInfo.appName
                            }
                        )
                    }
                }

                // System Sound Control Panel Quick Action Button
                OutlinedButton(
                    onClick = {
                        EqualizerManager.openSystemEqualizerPanel(context)
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_system_equalizer_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsVoice,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ডিভাইসের সাউন্ড কন্ট্রোল প্যানেল খুলুন",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                }
            } else {
                // =========================================================================
                // TAB 1: BUILT-IN STUDIO EQUALIZER (5-BAND GRAPHIC EQ + BASS + LOUDNESS)
                // =========================================================================

                // Built-in EQ Master Switch Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled) Color(0xFF0284C7).copy(alpha = 0.12f) else cardBg
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (enabled) Color(0xFF0284C7).copy(alpha = 0.45f) else cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0284C7).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "বিল্ট-ইন স্টুডিও ইকুলাইজার",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText
                                )
                                Text(
                                    text = if (enabled) "কাস্টম সাউন্ড বুস্টার সক্রিয় আছে" else "চালু করতে সুইচটি অন করুন",
                                    fontSize = 11.5.sp,
                                    color = secondaryText
                                )
                            }
                        }

                        Switch(
                            checked = enabled,
                            onCheckedChange = { isChecked ->
                                enabled = isChecked
                                EqualizerManager.setEnabled(context, isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7)
                            ),
                            modifier = Modifier.testTag("equalizer_master_switch")
                        )
                    }
                }

                // Sound Presets Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "সাউন্ড প্রিসেট প্রোফাইল",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        EqualizerManager.presets.forEach { preset ->
                            val isSelected = selectedPreset == preset.index
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFF0284C7) else cardBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF0284C7) else cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (!enabled) {
                                            enabled = true
                                            EqualizerManager.setEnabled(context, true)
                                        }
                                        selectedPreset = preset.index
                                        EqualizerManager.setSelectedPresetIndex(context, preset.index)
                                        syncBandsFromManager()
                                    }
                                    .testTag("eq_preset_${preset.index}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = preset.nameBn,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.White else primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = preset.subtitleBn,
                                        fontSize = 10.5.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Bass Boost & Vocal Loudness Boost Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Loudness / Voice Clarity Booster
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ভয়েস ও লাউডনেস বুস্টার",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "${loudnessGain.toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0284C7),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Slider(
                                value = loudnessGain,
                                onValueChange = {
                                    if (!enabled) {
                                        enabled = true
                                        EqualizerManager.setEnabled(context, true)
                                    }
                                    loudnessGain = it
                                    EqualizerManager.setLoudnessGain(context, (it * 15).toInt())
                                },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF0284C7),
                                    activeTrackColor = Color(0xFF0284C7)
                                )
                            )
                        }

                        HorizontalDivider(color = cardBorder)

                        // Deep Bass Booster
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Speaker,
                                        contentDescription = null,
                                        tint = Color(0xFF7C3AED),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ডিপ বেস বুস্টার (Bass Boost)",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF7C3AED).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "${bassStrength.toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF7C3AED),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Slider(
                                value = bassStrength,
                                onValueChange = {
                                    if (!enabled) {
                                        enabled = true
                                        EqualizerManager.setEnabled(context, true)
                                    }
                                    bassStrength = it
                                    EqualizerManager.setBassStrength(context, (it * 10).toInt())
                                },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF7C3AED),
                                    activeTrackColor = Color(0xFF7C3AED)
                                )
                            )
                        }
                    }
                }

                // 5-Band Frequency Studio Sliders
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "৫-ব্যান্ড ফ্রিকোয়েন্সি টিউনার",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                            TextButton(
                                onClick = {
                                    selectedPreset = 1
                                    EqualizerManager.setSelectedPresetIndex(context, 1)
                                    syncBandsFromManager()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("রিসেট (0 dB)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val bands = listOf(
                            Triple(0, "60 Hz (Sub-Bass)", band0) to { v: Float ->
                                band0 = v
                                selectedPreset = 5
                                EqualizerManager.setCustomBandPercent(context, 0, v.toInt())
                            },
                            Triple(1, "230 Hz (Bass)", band1) to { v: Float ->
                                band1 = v
                                selectedPreset = 5
                                EqualizerManager.setCustomBandPercent(context, 1, v.toInt())
                            },
                            Triple(2, "910 Hz (Vocal Mid)", band2) to { v: Float ->
                                band2 = v
                                selectedPreset = 5
                                EqualizerManager.setCustomBandPercent(context, 2, v.toInt())
                            },
                            Triple(3, "3.6 kHz (Presence)", band3) to { v: Float ->
                                band3 = v
                                selectedPreset = 5
                                EqualizerManager.setCustomBandPercent(context, 3, v.toInt())
                            },
                            Triple(4, "14 kHz (Air Treble)", band4) to { v: Float ->
                                band4 = v
                                selectedPreset = 5
                                EqualizerManager.setCustomBandPercent(context, 4, v.toInt())
                            }
                        )

                        bands.forEach { (info, onUpdate) ->
                            val (_, label, value) = info
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    val sign = if (value > 0) "+" else ""
                                    Text(
                                        text = "$sign${(value / 10f).toInt()} dB",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (value != 0f) Color(0xFF0284C7) else secondaryText
                                    )
                                }
                                Slider(
                                    value = value,
                                    onValueChange = {
                                        if (!enabled) {
                                            enabled = true
                                            EqualizerManager.setEnabled(context, true)
                                        }
                                        onUpdate(it)
                                    },
                                    valueRange = -100f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF0284C7),
                                        activeTrackColor = Color(0xFF0284C7)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileEqualizerSelectCard(
    appInfo: ExternalEqualizerAppInfo,
    isSelected: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
    onOpenControlPanel: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF131F33) else Color(0xFFF8FAFC)
    val borderCol = if (isSelected) Color(0xFF10B981) else if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            Color(0xFF10B981).copy(alpha = if (isDark) 0.14f else 0.08f)
        } else {
            cardBg
        },
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderCol),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .testTag("ext_eq_app_${appInfo.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            if (appInfo.icon != null) {
                AsyncImage(
                    model = appInfo.icon,
                    contentDescription = appInfo.appName,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(24.dp)
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
                        text = appInfo.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isSelected) {
                        "স্থায়ীভাবে সিলেক্টেড • অটো-কানেক্ট চালু"
                    } else {
                        "সিলেক্ট করতে ট্যাপ করুন"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF10B981) else secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                FilledTonalIconButton(
                    onClick = onOpenControlPanel,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF0284C7).copy(alpha = 0.16f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open Equalizer Panel",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF10B981),
                    unselectedColor = secondaryText
                )
            )
        }
    }
}
