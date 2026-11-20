package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.auth.CustomAlarmData
import com.example.auth.SessionManager
import com.example.home.HomeViewModel
import com.example.notification.ClassAlarmScheduler
import com.example.notification.ScheduledClassAlarmInfo
import com.example.ui.theme.isAppInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsScreen(
    sessionManager: SessionManager = SessionManager.getInstance(LocalContext.current),
    homeViewModel: HomeViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isAppInDarkTheme()

    // Permission check for Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // Reactive preferences state
    var allEnabled by remember { mutableStateOf(sessionManager.isAllNotificationsEnabled()) }
    var liveClassEnabled by remember { mutableStateOf(sessionManager.isLiveClassNotificationEnabled()) }
    var examEnabled by remember { mutableStateOf(sessionManager.isExamNotificationEnabled()) }
    var soundEnabled by remember { mutableStateOf(sessionManager.isNotificationSoundEnabled()) }
    var vibrateEnabled by remember { mutableStateOf(sessionManager.isNotificationVibrateEnabled()) }
    val leadTimeMinutes by sessionManager.classNotificationLeadTimeFlow.collectAsStateWithLifecycle()
    var disabledIds by remember { mutableStateOf(sessionManager.getDisabledAlarmIds()) }

    // Clean out any legacy dummy test_* alarms automatically
    var customAlarms by remember {
        val cleaned = sessionManager.getCustomAlarms().filterNot { it.id.startsWith("test_") }
        sessionManager.saveCustomAlarms(cleaned)
        mutableStateOf(cleaned)
    }

    var showAddAlarmDialog by remember { mutableStateOf(false) }

    // Compute upcoming scheduled alarms from routine lessons
    val effectiveLessons = remember(homeUiState.weeklyRoutine) {
        if (homeUiState.weeklyRoutine.isNotEmpty()) {
            homeUiState.weeklyRoutine
        } else {
            com.example.course.LessonCacheManager.getAllLessons()
        }
    }

    val upcomingRoutineAlarms = remember(
        effectiveLessons,
        leadTimeMinutes,
        disabledIds,
        allEnabled,
        liveClassEnabled,
        examEnabled
    ) {
        ClassAlarmScheduler.extractScheduledAlarms(
            context = context,
            lessons = effectiveLessons
        )
    }

    // Reschedule whenever preferences change
    fun syncAllAlarms() {
        ClassAlarmScheduler.scheduleUpcomingClassAlarms(context, effectiveLessons)
    }

    // Automatically sync FCM topics, OneSignal player, and schedule all upcoming routine class alarms
    // in the background (previously triggered by the Terminal component before it was removed from UI)
    LaunchedEffect(Unit) {
        if (homeUiState.weeklyRoutine.isEmpty() && !homeUiState.isLoading) {
            homeViewModel.loadData()
        }
        com.example.notification.ShikhoNotificationManager.syncAllTopicSubscriptions(context.applicationContext)
        com.example.notification.OneSignalPushManager.triggerSyncAsync(context.applicationContext)
    }

    LaunchedEffect(effectiveLessons, leadTimeMinutes, allEnabled, liveClassEnabled, examEnabled) {
        if (effectiveLessons.isNotEmpty()) {
            ClassAlarmScheduler.scheduleUpcomingClassAlarms(context, effectiveLessons)
        }
    }

    val pageBg = if (isDark) Color(0xFF070D19) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val cardBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val activeRoutineAlarmCount = remember(upcomingRoutineAlarms, allEnabled) {
        if (!allEnabled) 0 else upcomingRoutineAlarms.count { it.isEnabled }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "নোটিফিকেশন ও ক্লাস অ্যালার্ম",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = "স্মার্ট রুটিন রিমাইন্ডার ও স্টাডি অ্যালার্ম কন্ট্রোল",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("notification_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cardBg
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddAlarmDialog = true },
                containerColor = Color(0xFF0284C7),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.AlarmAdd, contentDescription = null) },
                text = { Text("নতুন স্টাডি অ্যালার্ম", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_custom_alarm_fab")
            )
        },
        containerColor = pageBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Permission Banner if not granted on Android 13+
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF3B1219) else Color(0xFFFEF2F2)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "নোটিফিকেশন পারমিশন বন্ধ আছে",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                                )
                                Text(
                                    text = "ক্লাসের সঠিক সময়ে অ্যালার্ম পেতে পারমিশন চালু করুন",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFFFECACA) else Color(0xFFB91C1C)
                                )
                            }
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("চালু করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 1. Hero Smart Alarm Status Banner + Master Toggle
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    if (allEnabled) {
                                        listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF0284C7))
                                    } else {
                                        listOf(Color(0xFF1E293B), Color(0xFF334155))
                                    }
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                            imageVector = if (allEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "মাস্টার নোটিফিকেশন ও অ্যালার্ম",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (allEnabled) {
                                                "সকল ক্লাস ও পরীক্ষার স্বয়ংক্রিয় রিমাইন্ডার সক্রিয়"
                                            } else {
                                                "সকল নোটিফিকেশন ও অ্যালার্ম সাময়িকভাবে বন্ধ"
                                            },
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Switch(
                                    checked = allEnabled,
                                    onCheckedChange = { enabled ->
                                        allEnabled = enabled
                                        sessionManager.setAllNotificationsEnabled(enabled)
                                        syncAllAlarms()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF0284C7),
                                        checkedTrackColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("master_notification_switch")
                                )
                            }

                            // Quick Metrics Row inside Hero Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.24f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AlarmStatPill(
                                    icon = Icons.Default.Schedule,
                                    label = "অগ্রিম সময়",
                                    value = "$leadTimeMinutes মিনিট আগে"
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(26.dp)
                                        .background(Color.White.copy(alpha = 0.2f))
                                )
                                AlarmStatPill(
                                    icon = Icons.Default.EventAvailable,
                                    label = "রুটিন অ্যালার্ম",
                                    value = "${activeRoutineAlarmCount}টি সক্রিয়"
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(26.dp)
                                        .background(Color.White.copy(alpha = 0.2f))
                                )
                                AlarmStatPill(
                                    icon = Icons.Default.AlarmOn,
                                    label = "নিজস্ব রিমাইন্ডার",
                                    value = "${customAlarms.count { it.isEnabled }}টি সেট"
                                )
                            }
                        }
                    }
                }
            }

            // 2. Lead Time Selector (5m, 10m, 15m, 20m, 25m, 30m, 45m, 60m)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTimeFilled,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ক্লাসের কতক্ষণ আগে অ্যালার্ম বাজবে?",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText
                                )
                                Text(
                                    text = "নির্ধারিত সময়ের ঠিক এত মিনিট আগে আপনাকে জানানো হবে",
                                    fontSize = 11.5.sp,
                                    color = secondaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val timeOptions = listOf(
                            5 to "৫ মিনিট",
                            10 to "১০ মিনিট",
                            15 to "১৫ মিনিট",
                            20 to "২০ মিনিট",
                            25 to "২৫ মিনিট",
                            30 to "৩০ মিনিট",
                            45 to "৪৫ মিনিট",
                            60 to "১ ঘণ্টা"
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 4
                        ) {
                            timeOptions.forEach { (mins, labelBn) ->
                                val isSelected = leadTimeMinutes == mins
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF0284C7) else if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF0284C7) else cardBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = allEnabled) {
                                            sessionManager.setClassNotificationLeadTimeMinutes(mins)
                                            syncAllAlarms()
                                            Toast.makeText(
                                                context,
                                                "ক্লাসের $labelBn আগে রিমাইন্ডার সেট করা হয়েছে",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .testTag("lead_time_chip_$mins")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = labelBn,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else primaryText,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Category & Sound/Vibration Toggles
            item {
                AnimatedVisibility(visible = allEnabled) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            NotificationToggleRow(
                                icon = Icons.Default.Videocam,
                                iconColor = Color(0xFF2563EB),
                                title = "লাইভ ক্লাস অ্যালার্ম",
                                subtitle = "রুটিনের সকল লাইভ ক্লাস শুরু হওয়ার আগে রিমাইন্ডার",
                                isDark = isDark,
                                checked = liveClassEnabled,
                                onCheckedChange = {
                                    liveClassEnabled = it
                                    sessionManager.setLiveClassNotificationEnabled(it)
                                    syncAllAlarms()
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = cardBorder
                            )
                            NotificationToggleRow(
                                icon = Icons.Default.Quiz,
                                iconColor = Color(0xFF9333EA),
                                title = "মডেল টেস্ট ও লাইভ এক্সাম",
                                subtitle = "পরীক্ষা ও এমসিকিউ কুইজের সময়সূচী নোটিফিকেশন",
                                isDark = isDark,
                                checked = examEnabled,
                                onCheckedChange = {
                                    examEnabled = it
                                    sessionManager.setExamNotificationEnabled(it)
                                    syncAllAlarms()
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = cardBorder
                            )
                            NotificationToggleRow(
                                icon = Icons.Default.VolumeUp,
                                iconColor = Color(0xFF059669),
                                title = "নোটিফিকেশন সাউন্ড",
                                subtitle = "রিমাইন্ডার আসার সময় অ্যালার্ম সাউন্ড বাজবে",
                                isDark = isDark,
                                checked = soundEnabled,
                                onCheckedChange = {
                                    soundEnabled = it
                                    sessionManager.setNotificationSoundEnabled(it)
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = cardBorder
                            )
                            NotificationToggleRow(
                                icon = Icons.Default.Vibration,
                                iconColor = Color(0xFFEA580C),
                                title = "ভাইব্রেশন অ্যালার্ট",
                                subtitle = "নোটিফিকেশন আসার সময় ফোন ভাইব্রেট করবে",
                                isDark = isDark,
                                checked = vibrateEnabled,
                                onCheckedChange = {
                                    vibrateEnabled = it
                                    sessionManager.setNotificationVibrateEnabled(it)
                                }
                            )
                        }
                    }
                }
            }

            // 4. Custom Study Reminders Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AlarmOn,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "নিজস্ব স্টাডি রিমাইন্ডার (${customAlarms.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                    }

                    TextButton(
                        onClick = { showAddAlarmDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("নতুন যোগ করুন", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (customAlarms.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF7C3AED).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlarmAdd,
                                    contentDescription = null,
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "কোনো নিজস্ব স্টাডি রিমাইন্ডার সেট করা নেই",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Text(
                                    text = "নিজের পড়ার রুটিন বা রিভিশনের জন্য নির্দিষ্ট তারিখ ও সময়ে অ্যালার্ম সেট করুন",
                                    fontSize = 11.5.sp,
                                    color = secondaryText
                                )
                            }
                        }
                    }
                }
            } else {
                items(customAlarms, key = { it.id }) { alarm ->
                    CustomAlarmCard(
                        alarm = alarm,
                        isDark = isDark,
                        onToggle = { enabled ->
                            val updated = customAlarms.map {
                                if (it.id == alarm.id) it.copy(isEnabled = enabled) else it
                            }
                            customAlarms = updated
                            sessionManager.saveCustomAlarms(updated)
                            val target = updated.find { it.id == alarm.id }
                            if (target != null) {
                                ClassAlarmScheduler.scheduleCustomAlarm(context, target)
                            }
                        },
                        onDelete = {
                            ClassAlarmScheduler.cancelCustomAlarm(context, alarm.id)
                            val updated = customAlarms.filterNot { it.id == alarm.id }
                            customAlarms = updated
                            sessionManager.saveCustomAlarms(updated)
                            Toast.makeText(context, "রিমাইন্ডার মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // 5. Date-wise Routine Class & Exam Alarms List
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "রুটিন অনুযায়ী আসন্ন ক্লাস ও পরীক্ষা (${upcomingRoutineAlarms.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                    }
                }
            }

            if (upcomingRoutineAlarms.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "বর্তমানে রুটিনে কোনো আসন্ন লাইভ ক্লাস বা পরীক্ষা নেই",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                            Text(
                                text = "নতুন ক্লাস যুক্ত হলে স্বয়ংক্রিয়ভাবে অ্যালার্ম সেট হয়ে যাবে",
                                fontSize = 12.sp,
                                color = secondaryText
                            )
                        }
                    }
                }
            } else {
                items(upcomingRoutineAlarms, key = { it.lessonId }) { item ->
                    ScheduledClassAlarmCard(
                        item = item,
                        leadTimeMinutes = leadTimeMinutes,
                        isDark = isDark,
                        onToggle = { enabled ->
                            sessionManager.setAlarmDisabled(item.lessonId, !enabled)
                            disabledIds = sessionManager.getDisabledAlarmIds()
                            syncAllAlarms()
                        }
                    )
                }
            }
        }
    }

    if (showAddAlarmDialog) {
        AddCustomAlarmDialog(
            isDark = isDark,
            onDismiss = { showAddAlarmDialog = false },
            onSave = { newAlarm ->
                val updated = customAlarms + newAlarm
                customAlarms = updated
                sessionManager.saveCustomAlarms(updated)
                ClassAlarmScheduler.scheduleCustomAlarm(context, newAlarm)
                showAddAlarmDialog = false
                Toast.makeText(context, "নতুন স্টাডি অ্যালার্ম সেট করা হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun AlarmStatPill(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    isDark: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = secondaryText
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iconColor
            )
        )
    }
}

@Composable
private fun ScheduledClassAlarmCard(
    item: ScheduledClassAlarmInfo,
    leadTimeMinutes: Int,
    isDark: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val dhakaTz = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val dateStr = remember(item.startTimeMs) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.forLanguageTag("bn-BD")).apply {
            timeZone = dhakaTz
        }.format(Date(item.startTimeMs))
    }
    val alarmTriggerStr = remember(item.alarmTriggerMs) {
        SimpleDateFormat("hh:mm a", Locale.forLanguageTag("bn-BD")).apply {
            timeZone = dhakaTz
        }.format(Date(item.alarmTriggerMs))
    }

    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val cardBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentColor = if (item.isExam) Color(0xFF9333EA) else Color(0xFF0284C7)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEnabled) cardBg else cardBg.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isEnabled) accentColor.copy(alpha = 0.35f) else cardBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isEnabled) 1.5.dp else 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = item.subjectName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.isEnabled) Color(0xFF10B981).copy(alpha = 0.14f) else cardBorder
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isEnabled) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                            contentDescription = null,
                            tint = if (item.isEnabled) Color(0xFF10B981) else secondaryText,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isEnabled) "অ্যালার্ম: $alarmTriggerStr" else "অ্যালার্ম অফ",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isEnabled) Color(0xFF10B981) else secondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ক্লাসের সময়: $dateStr ($leadTimeMinutes মি. আগে রিমাইন্ডার)",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                }

                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    )
                )
            }
        }
    }
}

@Composable
private fun CustomAlarmCard(
    alarm: CustomAlarmData,
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val dhakaTz = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val formattedDate = remember(alarm.triggerTimeMs) {
        SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.forLanguageTag("bn-BD")).apply {
            timeZone = dhakaTz
        }.format(Date(alarm.triggerTimeMs))
    }
    val isPast = alarm.triggerTimeMs <= System.currentTimeMillis()
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                if (alarm.message.isNotBlank()) {
                    Text(
                        text = alarm.message,
                        fontSize = 12.sp,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isPast) "$formattedDate (সময় পার হয়েছে)" else formattedDate,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPast) Color(0xFFEF4444) else Color(0xFF7C3AED)
                )
            }
            Switch(
                checked = alarm.isEnabled && !isPast,
                onCheckedChange = onToggle,
                enabled = !isPast,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF7C3AED)
                )
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Alarm",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
private fun AddCustomAlarmDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (CustomAlarmData) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember {
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka")).apply {
            add(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
    }

    var title by remember { mutableStateOf("পড়ার ও রিভিশন রিমাইন্ডার") }
    var message by remember { mutableStateOf("নির্ধারিত অধ্যায়ের নোট ও প্র্যাকটিস কুইজ সমাধান করার সময় হয়েছে!") }
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    val formattedDate = remember(selectedYear, selectedMonth, selectedDay) {
        val c = Calendar.getInstance().apply { set(selectedYear, selectedMonth, selectedDay) }
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(c.time)
    }
    val formattedTime = remember(selectedHour, selectedMinute) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(c.time)
    }

    val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AlarmAdd,
                    contentDescription = null,
                    tint = Color(0xFF0284C7)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "নতুন স্টাডি রিমাইন্ডার",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("রিমাইন্ডার শিরোনাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("বিস্তারিত বার্তা") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedYear = y
                                    selectedMonth = m
                                    selectedDay = d
                                },
                                selectedYear,
                                selectedMonth,
                                selectedDay
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(formattedDate, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    selectedHour = h
                                    selectedMinute = min
                                },
                                selectedHour,
                                selectedMinute,
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(formattedTime, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val triggerCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka")).apply {
                        set(Calendar.YEAR, selectedYear)
                        set(Calendar.MONTH, selectedMonth)
                        set(Calendar.DAY_OF_MONTH, selectedDay)
                        set(Calendar.HOUR_OF_DAY, selectedHour)
                        set(Calendar.MINUTE, selectedMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (triggerCal.timeInMillis <= System.currentTimeMillis()) {
                        Toast.makeText(context, "ভবিষ্যতের কোনো সময় নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(
                            CustomAlarmData(
                                title = title.ifBlank { "স্টাডি রিমাইন্ডার" },
                                message = message.ifBlank { "পড়ার সময় হয়েছে!" },
                                triggerTimeMs = triggerCal.timeInMillis,
                                isEnabled = true
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("অ্যালার্ম সেভ করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
