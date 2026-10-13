package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.player.EqualizerAppInfo
import com.example.player.EqualizerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isConnected by EqualizerManager.isConnected.collectAsState()
    val isEnabled by EqualizerManager.isEnabled.collectAsState()
    val currentSessionId by EqualizerManager.currentSessionId.collectAsState()
    val currentPreset by EqualizerManager.currentPreset.collectAsState()
    val bandGains by EqualizerManager.bandGains.collectAsState()
    val bassBoost by EqualizerManager.bassBoost.collectAsState()
    val virtualizer by EqualizerManager.virtualizer.collectAsState()
    val loudnessGainDb by EqualizerManager.loudnessGainDb.collectAsState()
    val installedApps by EqualizerManager.installedEqualizerApps.collectAsState()
    val statusMessage by EqualizerManager.hardwareStatusMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Mobile Apps, 1: Built-in EQ

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.5.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 18.dp)
        ) {
            // Header: Title + Session Badge + Connect Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) Color(0xFF10B981).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "ইকুলাইজার",
                            tint = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "অডিও ইকুলাইজার (Equalizer)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF10B981) else Color.Gray)
                            )
                            Text(
                                text = if (isConnected) {
                                    if (currentSessionId > 0) "কানেক্টেড (সেশন #$currentSessionId)" else "কানেক্টেড (স্ট্যান্ডবাই)"
                                } else {
                                    "ডিসকানেক্টেড"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Master Connect Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isConnected) "কানেক্টেড" else "সংযোগ বন্ধ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = isConnected,
                        onCheckedChange = { checked ->
                            EqualizerManager.setConnected(context, checked)
                            val toastMsg = if (checked) "ইকুলাইজার সফলভাবে কানেক্ট করা হয়েছে!" else "ইকুলাইজার ডিসকানেক্ট করা হয়েছে"
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Navigation Tabs: Mobile Equalizer Apps vs Built-in EQ
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(
                                text = "মোবাইলের ইকুলাইজার (${installedApps.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
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
                            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(
                                text = "বিল্ট-ইন কন্ট্রোল",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content
            when (selectedTab) {
                0 -> MobileEqualizerAppsTab(
                    context = context,
                    installedApps = installedApps,
                    isConnected = isConnected,
                    currentSessionId = currentSessionId,
                    statusMessage = statusMessage,
                    onSwitchToBuiltIn = { selectedTab = 1 }
                )
                1 -> BuiltInEqualizerTab(
                    context = context,
                    isEnabled = isEnabled,
                    isConnected = isConnected,
                    currentPreset = currentPreset,
                    bandGains = bandGains,
                    bassBoost = bassBoost,
                    virtualizer = virtualizer,
                    loudnessGainDb = loudnessGainDb
                )
            }
        }
    }
}

@Composable
private fun MobileEqualizerAppsTab(
    context: android.content.Context,
    installedApps: List<EqualizerAppInfo>,
    isConnected: Boolean,
    currentSessionId: Int,
    statusMessage: String,
    onSwitchToBuiltIn: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // Explanation Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "লাইভ অডিও সিঙ্ক কানেকশন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "মোবাইলে থাকা যেকোনো ইকুলাইজার অ্যাপ দিয়ে আমাদের অডিও সরাসরি নিয়ন্ত্রণ করা যাবে। সেখানে যেকোনো ব্যান্ডের পরিবর্তন করলে এখানেও সাথে সাথে কাজ করবে।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        item {
            // Quick Action: Open System Sound Panel / Chooser
            Button(
                onClick = {
                    val success = EqualizerManager.openSystemEqualizerChooser(context)
                    if (!success) {
                        Toast.makeText(context, "সিস্টেম প্যানেল খুলতে পারছে না, বিল্ট-ইন ইকুলাইজার ব্যবহার করুন", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "মোবাইলের সিস্টেম ইকুলাইজার প্যানেল খুলুন",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ফোনে পাওয়া ইকুলাইজার অ্যাপসমূহ (${installedApps.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = {
                        val updated = EqualizerManager.scanInstalledEqualizerApps(context)
                        Toast.makeText(context, "${updated.size} টি ইকুলাইজার পাওয়া গেছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("রিফ্রেশ", fontSize = 12.sp)
                }
            }
        }

        if (installedApps.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "কোনো বাহ্যিক ইকুলাইজার অ্যাপ ডিটেক্ট হয়নি",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "চিন্তার কিছু নেই! আপনার ফোনে আলাদা ইকুলাইজার অ্যাপ না থাকলেও আমাদের অ্যাপের নিজস্ব 'বিল্ট-ইন কন্ট্রোল' থেকে বেস, ট্রেবল ও ৫-ব্যান্ড ফ্রিকোয়েন্সি নিয়ন্ত্রণ করতে পারবেন।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onSwitchToBuiltIn,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("বিল্ট-ইন ইকুলাইজারে যান", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        } else {
            items(installedApps) { app ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            EqualizerManager.openEqualizerApp(context, app)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (app.icon != null) {
                                val bitmap = remember(app.packageName) {
                                    try {
                                        app.icon.toBitmap(width = 44, height = 44).asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmap,
                                        contentDescription = app.appName,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Column {
                                Text(
                                    text = app.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (app.isSystemDefault) "সিস্টেম ডিফল্ট ইকুলাইজার" else app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = { EqualizerManager.openEqualizerApp(context, app) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text("কানেক্ট ও ওপেন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Technical session info card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "অডিও ব্রডকাস্ট টেকনিক্যাল স্ট্যাটাস",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• অডিও সেশন আইডি: ${if (currentSessionId > 0) "#$currentSessionId" else "অপেক্ষমান"}\n• ব্রডকাস্ট অ্যাকশন: OPEN_AUDIO_EFFECT_CONTROL_SESSION\n• স্ট্যাটাস: $statusMessage",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltInEqualizerTab(
    context: android.content.Context,
    isEnabled: Boolean,
    isConnected: Boolean,
    currentPreset: String,
    bandGains: List<Int>,
    bassBoost: Int,
    virtualizer: Int,
    loudnessGainDb: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // Equalizer Power Switch
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isEnabled) "ইকুলাইজার সক্রিয় (Power ON)" else "ইকুলাইজার বাইপাস (Power OFF)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { EqualizerManager.setEnabled(context, it) }
                    )
                }
            }
        }

        // Live Frequency Visualizer Curve
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f

                        // Draw baseline
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, midY),
                            end = Offset(width, midY),
                            strokeWidth = 1f
                        )

                        // Draw interactive curve based on bandGains
                        val path = Path()
                        val bandStep = width / (bandGains.size + 1)
                        path.moveTo(0f, midY)

                        val points = mutableListOf<Offset>()
                        points.add(Offset(0f, midY))
                        for (i in bandGains.indices) {
                            val x = bandStep * (i + 1)
                            // Map -15..15 dB to Y deviation (-height/2.4 .. +height/2.4)
                            val gain = bandGains[i]
                            val dynamicMod = if (isEnabled) kotlin.math.sin(wavePhase + i * 1.2f) * 3f else 0f
                            val y = midY - (gain / 15f) * (height * 0.35f) + dynamicMod.toFloat()
                            points.add(Offset(x, y))
                        }
                        points.add(Offset(width, midY))

                        // Smooth bezier through points
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            path.quadraticTo(p0.x, p0.y, cx, (p0.y + p1.y) / 2f)
                        }
                        path.lineTo(width, midY)

                        drawPath(
                            path = path,
                            color = Color(0xFF10B981),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
                        )
                    }

                    // Overlay labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("লো (Bass)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        Text("মিড (Voice)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        Text("হাই (Treble)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Sound Presets Carousel
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "সাউন্ড প্রিসেটস (Presets)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(EqualizerManager.PRESETS) { preset ->
                        val isSelected = currentPreset == preset.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { EqualizerManager.applyPreset(context, preset.id) },
                            label = {
                                Text(
                                    text = preset.nameBangla,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // 5-Band Sliders
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "৫-ব্যান্ড ফ্রিকোয়েন্সি লেভেল",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        TextButton(
                            onClick = { EqualizerManager.resetToDefault(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("রিসেট", fontSize = 12.sp)
                        }
                    }

                    val bandLabels = listOf(
                        "60 Hz (সাব-বেস)",
                        "230 Hz (বেস)",
                        "910 Hz (ভয়েস স্পষ্ট)",
                        "3.6 kHz (শার্পনেস)",
                        "14 kHz (ট্রেবল)"
                    )

                    for (i in bandGains.indices) {
                        val gain = bandGains[i]
                        val label = bandLabels.getOrElse(i) { "Band $i" }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${if (gain > 0) "+$gain" else "$gain"} dB",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (gain > 0) Color(0xFF10B981) else if (gain < 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Slider(
                                value = gain.toFloat(),
                                onValueChange = { newVal ->
                                    EqualizerManager.setBandGain(context, i, newVal.toInt())
                                },
                                valueRange = -15f..15f,
                                steps = 29,
                                enabled = isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Bass Boost, Virtualizer & Loudness Enhancers
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "সাউন্ড এফেক্টস ও বুস্টার",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )

                    // 1. Bass Boost
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Speaker, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF6366F1))
                                Text("বেস বুস্টার (Bass Boost)", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("$bassBoost%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                        }
                        Slider(
                            value = bassBoost.toFloat(),
                            onValueChange = { EqualizerManager.setBassBoost(context, it.toInt()) },
                            valueRange = 0f..100f,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6366F1),
                                activeTrackColor = Color(0xFF6366F1)
                            )
                        )
                    }

                    // 2. 3D Virtualizer
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SurroundSound, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0284C7))
                                Text("ভার্চুয়ালাইজার (3D Spatial Audio)", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("$virtualizer%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        }
                        Slider(
                            value = virtualizer.toFloat(),
                            onValueChange = { EqualizerManager.setVirtualizer(context, it.toInt()) },
                            valueRange = 0f..100f,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF0284C7),
                                activeTrackColor = Color(0xFF0284C7)
                            )
                        )
                    }

                    // 3. Loudness Enhancer
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEA580C))
                                Text("লাউডনেস বুস্ট (Volume Gain)", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                text = String.format(java.util.Locale.US, "+%.1f dB", loudnessGainDb),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                        }
                        Slider(
                            value = loudnessGainDb,
                            onValueChange = { EqualizerManager.setLoudnessGain(context, it) },
                            valueRange = 0f..12f,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFEA580C),
                                activeTrackColor = Color(0xFFEA580C)
                            )
                        )
                    }
                }
            }
        }
    }
}
