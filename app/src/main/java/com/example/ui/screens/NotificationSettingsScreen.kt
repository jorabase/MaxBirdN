package com.example.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.auth.SessionManager
import com.example.home.HomeViewModel
import com.example.notification.ClassAlarmReceiver
import com.example.notification.ClassAlarmScheduler
import com.example.utils.toBengaliDigits

data class ClassAlarmItem(
    val id: String,
    val subjectName: String,
    val lessonTitle: String,
    val dateDisplay: String,
    val startTimeDisplay: String,
    val classTimeHours: Int = 7,
    val classTimeMinutes: Int = 0,
    val isExam: Boolean = false,
    val startMs: Long = 0L,
    var isEnabled: Boolean = true
)

fun triggerClassTestNotification(
    context: Context,
    subjectName: String,
    lessonTitle: String,
    classStartTimeDisplay: String,
    leadTimeMinutes: Int
) {
    val sessionManager = SessionManager(context)
    ClassAlarmScheduler.createNotificationChannel(context)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val title = "⏰ $subjectName ক্লাস রিমাইন্ডার"
    val body = "আপনার $subjectName ($lessonTitle) ক্লাস $classStartTimeDisplay এ শুরু হবে, রেডি হন! 🚀"

    val mainIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        (System.currentTimeMillis() % 10000).toInt(),
        mainIntent,
        pendingIntentFlags
    )

    val soundUri = if (sessionManager.isNotificationSoundEnabled()) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    } else null

    val notificationBuilder = NotificationCompat.Builder(context, ClassAlarmScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(0xFF0072EC.toInt())
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setSound(soundUri)
        .setContentIntent(pendingIntent)

    if (sessionManager.isNotificationVibrateEnabled()) {
        notificationBuilder.setVibrate(longArrayOf(0, 300, 200, 300))
    } else {
        notificationBuilder.setVibrate(longArrayOf(0))
    }

    val notificationId = (System.currentTimeMillis() % 100000).toInt()
    notificationManager.notify(notificationId, notificationBuilder.build())

    Toast.makeText(context, "$subjectName রিমাইন্ডার টেস্ট পাঠানো হয়েছে! 🔔", Toast.LENGTH_SHORT).show()
}

fun triggerGeneralTestNotification(context: Context) {
    val sessionManager = SessionManager(context)
    ClassAlarmScheduler.createNotificationChannel(context)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val leadTime = sessionManager.getClassNotificationLeadTimeMinutes()
    val title = "MaxBird • ক্লাস রিমাইন্ডার টেস্ট 🔔"
    val body = "আপনার ক্লাস অ্যালার্ম ও নোটিফিকেশন সিস্টেম প্রস্তুত! নির্ধারিত লাইভ ক্লাস শুরু হওয়ার ${leadTime.toString().toBengaliDigits()} মিনিট পূর্বে আপনি স্বয়ংক্রিয় অ্যালার্ম পাবেন।"

    val soundUri = if (sessionManager.isNotificationSoundEnabled()) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    } else null

    val builder = NotificationCompat.Builder(context, ClassAlarmScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(0xFF0072EC.toInt())
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setSound(soundUri)

    if (sessionManager.isNotificationVibrateEnabled()) {
        builder.setVibrate(longArrayOf(0, 300, 200, 300))
    } else {
        builder.setVibrate(longArrayOf(0))
    }

    notificationManager.notify(101, builder.build())
    Toast.makeText(context, "টেস্ট নোটিফিকেশন পাঠানো হয়েছে! 🔔", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsScreen(
    sessionManager: SessionManager,
    homeViewModel: HomeViewModel? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAllEnabled by remember { mutableStateOf(sessionManager.isAllNotificationsEnabled()) }
    var isLiveEnabled by remember { mutableStateOf(sessionManager.isLiveClassNotificationEnabled()) }
    var isExamEnabled by remember { mutableStateOf(sessionManager.isExamNotificationEnabled()) }
    var isSoundEnabled by remember { mutableStateOf(sessionManager.isNotificationSoundEnabled()) }
    var isVibrateEnabled by remember { mutableStateOf(sessionManager.isNotificationVibrateEnabled()) }

    var selectedLeadTime by remember { mutableIntStateOf(sessionManager.getClassNotificationLeadTimeMinutes()) }
    var disabledAlarmIds by remember { mutableStateOf(sessionManager.getDisabledAlarmIds()) }
    var customAlarms by remember { mutableStateOf(sessionManager.getCustomAlarms()) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    val homeUiState = homeViewModel?.uiState?.collectAsState()?.value
    val selectedSubjectCodes = homeUiState?.selectedSubjectCodes ?: emptySet()
    val courseSubjects = homeUiState?.courseSubjects ?: emptyList()

    // Dynamic Scheduled Alarms
    val alarmList = remember(
        homeUiState?.weeklyRoutine,
        homeUiState?.filteredWeeklyRoutine,
        selectedSubjectCodes,
        courseSubjects,
        disabledAlarmIds
    ) {
        val rawLessons = homeUiState?.filteredWeeklyRoutine ?: homeUiState?.weeklyRoutine
        val now = System.currentTimeMillis()

        if (!rawLessons.isNullOrEmpty()) {
            rawLessons.mapNotNull { lesson ->
                val startMs = lesson.classStartMs
                val endMs = lesson.classEndMs

                // Filter out past lessons (if start time has already passed)
                if (startMs == Long.MAX_VALUE || startMs <= now) {
                    return@mapNotNull null
                }

                val subject = lesson.subject_name ?: lesson.live_class?.subject_name ?: "ক্লাস"
                val title = lesson.title ?: lesson.live_class?.chapter_name ?: "লাইভ ক্লাস / পরীক্ষা"
                val isExam = lesson.isExam || lesson.content_type?.contains("Exam", ignoreCase = true) == true

                val dateDisplay = com.example.utils.formatLessonDateDetailed(lesson.start_time ?: lesson.live_class?.start_time)
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Dhaka")).apply { timeInMillis = startMs }
                val hours = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val mins = cal.get(java.util.Calendar.MINUTE)

                val period = if (hours < 12) "সকাল" else if (hours < 17) "দুপুর" else "সন্ধ্যা/রাত"
                val displayHour = if (hours % 12 == 0) 12 else hours % 12
                val timeStr = "${period} ${if (displayHour < 10) "০$displayHour" else displayHour}:${if (mins < 10) "০$mins" else mins} টা".toBengaliDigits()

                val lessonId = if (lesson.id.isNotBlank()) lesson.id else "lesson_$startMs"

                ClassAlarmItem(
                    id = lessonId,
                    subjectName = subject,
                    lessonTitle = title,
                    dateDisplay = dateDisplay,
                    startTimeDisplay = timeStr,
                    classTimeHours = hours,
                    classTimeMinutes = mins,
                    isExam = isExam,
                    startMs = startMs,
                    isEnabled = !disabledAlarmIds.contains(lessonId)
                )
            }.distinctBy { it.id }.sortedBy { it.startMs }
        } else {
            emptyList()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerGeneralTestNotification(context)
        } else {
            Toast.makeText(context, "নোটিফিকেশন পারমিশন প্রয়োজন", Toast.LENGTH_SHORT).show()
        }
    }

    val hasNotificationPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "নোটিফিকেশন ও ক্লাস অ্যালার্ম",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "লাইভ ক্লাস ও পরীক্ষার স্মার্ট রিমাইন্ডার",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ==========================================
            // MASTER SWITCH CARD
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = if (isAllEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    1.5.dp,
                    if (isAllEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAllEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAllEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = if (isAllEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "সকল ক্লাস অ্যালার্ম ও নোটিফিকেশন",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isAllEnabled) "🟢 অ্যালার্ম সার্ভিস চালু আছে" else "⚪ অ্যালার্ম সার্ভিস বন্ধ আছে",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isAllEnabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isAllEnabled,
                        onCheckedChange = { checked ->
                            isAllEnabled = checked
                            sessionManager.setAllNotificationsEnabled(checked)
                            if (checked) {
                                val lessons = homeUiState?.filteredWeeklyRoutine ?: homeUiState?.weeklyRoutine ?: emptyList()
                                ClassAlarmScheduler.schedule7DayClassAlarms(context, homeUiState?.activeProgram?.id ?: "", lessons)
                                Toast.makeText(context, "ক্লাস অ্যালার্ম সক্রিয় করা হয়েছে! 🔔", Toast.LENGTH_SHORT).show()
                            } else {
                                ClassAlarmScheduler.cancelAllAlarms(context)
                                Toast.makeText(context, "ক্লাস অ্যালার্ম বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // ==========================================
            // SOUND & CATEGORY PREFERENCES
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "অ্যালার্ম ও রিমাইন্ডার প্রিফারেন্স",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sound Toggle
                    PreferenceSwitchRow(
                        icon = Icons.Default.VolumeUp,
                        iconTint = Color(0xFF0284C7),
                        title = "শব্দ ও রিংটোন (Sound)",
                        subtitle = "নোটিফিকেশন আসার সাথে রিংটোন বাজবে",
                        isChecked = isSoundEnabled && isAllEnabled,
                        enabled = isAllEnabled,
                        onCheckedChange = {
                            isSoundEnabled = it
                            sessionManager.setNotificationSoundEnabled(it)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // Vibration Toggle
                    PreferenceSwitchRow(
                        icon = Icons.Default.Vibration,
                        iconTint = Color(0xFF7C3AED),
                        title = "ভাইব্রেশন (Vibration)",
                        subtitle = "নোটিফিকেশনের সময় মোবাইল কেঁপে উঠবে",
                        isChecked = isVibrateEnabled && isAllEnabled,
                        enabled = isAllEnabled,
                        onCheckedChange = {
                            isVibrateEnabled = it
                            sessionManager.setNotificationVibrateEnabled(it)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // Live Class Reminder Toggle
                    PreferenceSwitchRow(
                        icon = Icons.Default.Videocam,
                        iconTint = Color(0xFFDC2626),
                        title = "লাইভ ক্লাস রিমাইন্ডার",
                        subtitle = "রুটিনের প্রতিটি লাইভ ক্লাসের জন্য অ্যালার্ম",
                        isChecked = isLiveEnabled && isAllEnabled,
                        enabled = isAllEnabled,
                        onCheckedChange = {
                            isLiveEnabled = it
                            sessionManager.setLiveClassNotificationEnabled(it)
                            val lessons = homeUiState?.filteredWeeklyRoutine ?: homeUiState?.weeklyRoutine ?: emptyList()
                            ClassAlarmScheduler.schedule7DayClassAlarms(context, homeUiState?.activeProgram?.id ?: "", lessons)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // Live Exam Reminder Toggle
                    PreferenceSwitchRow(
                        icon = Icons.Default.Assignment,
                        iconTint = Color(0xFFD97706),
                        title = "লাইভ পরীক্ষা ও মডেল টেস্ট রিমাইন্ডার",
                        subtitle = "পরীক্ষা শুরু হওয়ার পূর্বে সতর্কবার্তা",
                        isChecked = isExamEnabled && isAllEnabled,
                        enabled = isAllEnabled,
                        onCheckedChange = {
                            isExamEnabled = it
                            sessionManager.setExamNotificationEnabled(it)
                            val lessons = homeUiState?.filteredWeeklyRoutine ?: homeUiState?.weeklyRoutine ?: emptyList()
                            ClassAlarmScheduler.schedule7DayClassAlarms(context, homeUiState?.activeProgram?.id ?: "", lessons)
                        }
                    )
                }
            }

            // ==========================================
            // LEAD TIME SELECTOR CARD
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ক্লাস শুরুর কত মিনিট আগে রিমাইন্ডার চান?",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "বর্তমানে সেট করা: ${selectedLeadTime.toString().toBengaliDigits()} মিনিট আগে",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    val minuteOptions = listOf(5, 10, 15, 20, 25, 30, 45, 60)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        minuteOptions.forEach { mins ->
                            val isSelected = selectedLeadTime == mins
                            val isDefault = mins == 25

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = isAllEnabled) {
                                        selectedLeadTime = mins
                                        sessionManager.setClassNotificationLeadTimeMinutes(mins)
                                        val lessons = homeUiState?.filteredWeeklyRoutine ?: homeUiState?.weeklyRoutine ?: emptyList()
                                        ClassAlarmScheduler.schedule7DayClassAlarms(context, homeUiState?.activeProgram?.id ?: "", lessons)
                                        Toast.makeText(
                                            context,
                                            "রিমাইন্ডারের সময় ${mins.toString().toBengaliDigits()} মিনিট সেট করা হলো!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    if (isSelected) 1.8.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedLeadTime = mins
                                            sessionManager.setClassNotificationLeadTimeMinutes(mins)
                                        },
                                        enabled = isAllEnabled,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isDefault) "${mins.toString().toBengaliDigits()} মি. (ডিফল্ট)"
                                        else "${mins.toString().toBengaliDigits()} মি.",
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Calculation dynamic info banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            val reminderMinute = (60 - selectedLeadTime) % 60
                            val exampleReminderTime = "০৬:${if (reminderMinute < 10) "0$reminderMinute" else reminderMinute}".toBengaliDigits()
                            Text(
                                text = "উদাহরণ: সন্ধ্যা ০৭:০০ টার ক্লাসের অ্যালার্ম বাজবে ঠিক $exampleReminderTime টায় (${selectedLeadTime.toString().toBengaliDigits()} মিনিট আগে)।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ==========================================
            // REALISTIC NOTIFICATION PREVIEW CARD
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Preview,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "নোটিফিকেশন প্রিভিউ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    triggerGeneralTestNotification(context)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "টেস্ট নোটিফিকেশন",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Realistic Notification Shell
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Top Bar of Notification
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(0.5.dp, Color(0xFFCBD5E1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_notification),
                                        contentDescription = null,
                                        tint = Color(0xFF0072EC),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Text(
                                    text = "MaxBird • এখন 🔔",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // Notification Content
                            Text(
                                text = "⏰ পদার্থবিজ্ঞান ১ম পত্র ক্লাস রিমাইন্ডার",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "আপনার পদার্থবিজ্ঞান (ভেক্টর ও গতিবিদ্যা) ক্লাস সন্ধ্যা ০৭:০০ টা এ শুরু হবে, রেডি হন! 🚀",
                                fontSize = 12.sp,
                                color = Color(0xFF334155),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // CUSTOM ALARMS & TEST NOTIFICATIONS CARD
            // ==========================================
            CustomAlarmSectionCard(
                context = context,
                customAlarms = customAlarms,
                isAllEnabled = isAllEnabled,
                onAddClick = { showAddCustomDialog = true },
                onToggleAlarm = { alarm, enabled ->
                    val updated = customAlarms.map {
                        if (it.id == alarm.id) it.copy(isEnabled = enabled) else it
                    }
                    customAlarms = updated
                    sessionManager.saveCustomAlarms(updated)
                    if (enabled) {
                        if (alarm.triggerTimeMs > System.currentTimeMillis()) {
                            ClassAlarmScheduler.scheduleCustomAlarm(
                                context,
                                alarm.id,
                                alarm.title,
                                alarm.message,
                                alarm.triggerTimeMs
                            )
                            Toast.makeText(context, "কাস্টম রিমাইন্ডার সক্রিয় করা হয়েছে ⏰", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        ClassAlarmScheduler.cancelCustomAlarm(context, alarm.id)
                        Toast.makeText(context, "কাস্টম রিমাইন্ডার বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteAlarm = { alarm ->
                    ClassAlarmScheduler.cancelCustomAlarm(context, alarm.id)
                    val updated = customAlarms.filter { it.id != alarm.id }
                    customAlarms = updated
                    sessionManager.saveCustomAlarms(updated)
                    Toast.makeText(context, "রিমাইন্ডার মুছে ফেলা হয়েছে 🗑️", Toast.LENGTH_SHORT).show()
                },
                onQuickTest = { delaySec ->
                    val triggerTimeMs = System.currentTimeMillis() + (delaySec * 1000L)
                    val newAlarm = com.example.auth.CustomAlarmData(
                        id = "test_${System.currentTimeMillis()}",
                        title = "⏰ টেস্ট রিমাইন্ডার নোটিফিকেশন",
                        message = "অভিনন্দন! আপনার নোটিফিকেশন সার্ভিস চমৎকারভাবে কাজ করছে! 🚀",
                        triggerTimeMs = triggerTimeMs,
                        isEnabled = true
                    )
                    val updated = customAlarms + newAlarm
                    customAlarms = updated
                    sessionManager.saveCustomAlarms(updated)
                    ClassAlarmScheduler.scheduleCustomAlarm(
                        context,
                        newAlarm.id,
                        newAlarm.title,
                        newAlarm.message,
                        triggerTimeMs
                    )
                    val label = if (delaySec < 60) "${delaySec.toString().toBengaliDigits()} সেকেন্ড" else "${(delaySec / 60).toString().toBengaliDigits()} মিনিট"
                    Toast.makeText(context, "⏰ $label পর নোটিফিকেশন আসবে! অপেক্ষা করুন...", Toast.LENGTH_LONG).show()
                }
            )

            // ==========================================
            // SCHEDULED ALARMS LIST CARD
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "আসন্ন ক্লাস ও পরীক্ষা শিডিউল",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${alarmList.size.toString().toBengaliDigits()} টি ক্লাসের অ্যালার্ম তালিকাভুক্ত",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (homeViewModel != null && courseSubjects.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { homeViewModel.openSubjectFilterDialog() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("বিষয় সাজাও", fontSize = 11.5.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    if (alarmList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "আসন্ন কোনো ক্লাস বা পরীক্ষার শিডিউল নেই",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            alarmList.forEach { item ->
                                val isAlarmActive = item.isEnabled && isAllEnabled

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isAlarmActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isAlarmActive) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Left Type Icon
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (item.isExam) Color(0xFFFEF3C7)
                                                    else Color(0xFFFEE2E2)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (item.isExam) Icons.Default.Assignment else Icons.Default.LiveTv,
                                                contentDescription = null,
                                                tint = if (item.isExam) Color(0xFFD97706) else Color(0xFFDC2626),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Center Details
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = item.subjectName,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (item.isExam) Color(0xFFFEF3C7) else Color(0xFFFEE2E2)
                                                ) {
                                                    Text(
                                                        text = if (item.isExam) "পরীক্ষা" else "লাইভ",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (item.isExam) Color(0xFFB45309) else Color(0xFFB91C1C),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = item.lessonTitle,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = "📅 ${item.dateDisplay} • ⏰ ${item.startTimeDisplay}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // Right Actions: Test button + Switch
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Switch(
                                                checked = isAlarmActive,
                                                onCheckedChange = { checked ->
                                                    sessionManager.setAlarmDisabled(item.id, !checked)
                                                    disabledAlarmIds = sessionManager.getDisabledAlarmIds()
                                                    val lessons = homeUiState?.filteredWeeklyRoutine ?: homeUiState?.weeklyRoutine ?: emptyList()
                                                    ClassAlarmScheduler.schedule7DayClassAlarms(context, homeUiState?.activeProgram?.id ?: "", lessons)
                                                },
                                                enabled = isAllEnabled,
                                                modifier = Modifier.height(26.dp)
                                            )

                                            TextButton(
                                                onClick = {
                                                    triggerClassTestNotification(
                                                        context = context,
                                                        subjectName = item.subjectName,
                                                        lessonTitle = item.lessonTitle,
                                                        classStartTimeDisplay = item.startTimeDisplay,
                                                        leadTimeMinutes = selectedLeadTime
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                modifier = Modifier.height(24.dp)
                                            ) {
                                                Text("টেস্ট", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // RELIABILITY & BATTERY OPTIMIZATION GUIDE
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "অ্যালার্ম মিস হওয়া এড়াতে প্রয়োজনীয় টিপস",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Xiaomi/Realme/Vivo ফোনে সময়মতো অ্যালার্ম পেতে অ্যাপটিকে সেটিংস থেকে 'Autostart' চালু রাখুন এবং ব্যাটারি অপ্টিমাইজেশন 'No restrictions' দিন।",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        if (showAddCustomDialog) {
            AddCustomAlarmDialog(
                onDismiss = { showAddCustomDialog = false },
                onSave = { title, message, triggerTimeMs ->
                    val newAlarm = com.example.auth.CustomAlarmData(
                        id = "custom_${System.currentTimeMillis()}",
                        title = title,
                        message = message,
                        triggerTimeMs = triggerTimeMs,
                        isEnabled = true
                    )
                    val updated = customAlarms + newAlarm
                    customAlarms = updated
                    sessionManager.saveCustomAlarms(updated)
                    ClassAlarmScheduler.scheduleCustomAlarm(
                        context,
                        newAlarm.id,
                        newAlarm.title,
                        newAlarm.message,
                        triggerTimeMs
                    )
                    showAddCustomDialog = false
                    Toast.makeText(context, "কাস্টম অ্যালার্ম সেট করা হয়েছে! ⏰", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (homeViewModel != null && homeUiState?.showSubjectFilterDialog == true) {
            com.example.ui.components.SubjectFilterDialog(
                courseTitle = homeUiState.activeProgram?.title_bn ?: "সাবজেক্ট সাজাও",
                subjects = homeUiState.courseSubjects,
                selectedSubjectCodes = homeUiState.selectedSubjectCodes,
                isLoading = homeUiState.isCourseSubjectsLoading,
                isSaving = homeUiState.isSavingSubjectFilter,
                onToggleSubject = { homeViewModel.toggleSubjectSelection(it) },
                onSelectAll = { homeViewModel.selectAllSubjects() },
                onClearAll = { homeViewModel.clearAllSubjectSelection() },
                onSave = { homeViewModel.saveSubjectFilter() },
                onDismiss = { homeViewModel.dismissSubjectFilterDialog() }
            )
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.height(28.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomAlarmSectionCard(
    context: Context,
    customAlarms: List<com.example.auth.CustomAlarmData>,
    isAllEnabled: Boolean,
    onAddClick: () -> Unit,
    onToggleAlarm: (com.example.auth.CustomAlarmData, Boolean) -> Unit,
    onDeleteAlarm: (com.example.auth.CustomAlarmData) -> Unit,
    onQuickTest: (seconds: Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAlert,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "কাস্টম টেস্ট ও রিমাইন্ডার অ্যালার্ম",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "নিজের মত সময় সেট করে নোটিফিকেশন পরীক্ষা করুন",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Test Buttons Row
            Text(
                text = "⚡ কুইক টেস্ট অপশন:",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = { onQuickTest(10) },
                    label = { Text("⚡ ১০ সেকেন্ড পর", fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFFEF3C7),
                        labelColor = Color(0xFFD97706)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFCD34D))
                )

                AssistChip(
                    onClick = { onQuickTest(60) },
                    label = { Text("⏰ ১ মিনিট পর", fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFE0F2FE),
                        labelColor = Color(0xFF0284C7)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                )

                AssistChip(
                    onClick = { onQuickTest(300) },
                    label = { Text("🔔 ৫ মিনিট পর", fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFF3E8FF),
                        labelColor = Color(0xFF7C3AED)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                )

                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("➕ নতুন রিমাইন্ডার", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (customAlarms.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                Text(
                    text = "সেট করা কাস্টম অ্যালার্মসমূহ (${customAlarms.size.toString().toBengaliDigits()} টি)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val now = System.currentTimeMillis()
                    customAlarms.forEach { alarm ->
                        val isExpired = alarm.triggerTimeMs <= now
                        val remainingSec = ((alarm.triggerTimeMs - now) / 1000).coerceAtLeast(0)
                        val remainingText = if (isExpired) "ট্রিগার হয়েছে / অতিবাহিত"
                        else if (remainingSec < 60) "আর ${remainingSec.toString().toBengaliDigits()} সেকেন্ড বাকি"
                        else "আর ${(remainingSec / 60).toString().toBengaliDigits()} মিনিট বাকি"

                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMs }
                        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val min = cal.get(java.util.Calendar.MINUTE)
                        val displayHour = if (hour % 12 == 0) 12 else hour % 12
                        val period = if (hour < 12) "সকাল" else if (hour < 17) "দুপুর" else "রাত"
                        val timeFormatted = "$period ${displayHour.toString().padStart(2, '0').toBengaliDigits()}:${min.toString().padStart(2, '0').toBengaliDigits()}"

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExpired) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else Color(0xFFF0FDF4),
                            border = BorderStroke(
                                1.dp,
                                if (isExpired) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                else Color(0xFFBBF7D0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isExpired) Color.Gray.copy(alpha = 0.2f) else Color(0xFF16A34A).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isExpired) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = if (isExpired) Color.Gray else Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alarm.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = alarm.message,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "⏰ $timeFormatted • $remainingText",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF15803D)
                                    )
                                }

                                Switch(
                                    checked = alarm.isEnabled && !isExpired,
                                    onCheckedChange = { onToggleAlarm(alarm, it) },
                                    enabled = !isExpired && isAllEnabled,
                                    modifier = Modifier.height(26.dp)
                                )

                                IconButton(
                                    onClick = { onDeleteAlarm(alarm) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "মুছে ফেলুন",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCustomAlarmDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, message: String, triggerTimeMs: Long) -> Unit
) {
    var title by remember { mutableStateOf("পদার্থবিজ্ঞান কাস্টম রিমাইন্ডার") }
    var message by remember { mutableStateOf("পড়ার সময় হয়েছে, রিভিশন শুরু করুন! 🚀") }
    var selectedMinutesDelay by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AlarmAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("কাস্টম রিমাইন্ডার যোগ করুন", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("রিমাইন্ডারের শিরোনাম") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("বিবরণ / নোট") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Text(
                    text = "কত মিনিট পর নোটিফিকেশন চান?",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val delayOptions = listOf(1, 2, 5, 10, 15, 30, 60)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    delayOptions.forEach { mins ->
                        val isSelected = selectedMinutesDelay == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMinutesDelay = mins },
                            label = {
                                Text(
                                    if (mins < 60) "${mins.toString().toBengaliDigits()} মি." else "১ ঘণ্টা",
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val triggerTimeMs = System.currentTimeMillis() + (selectedMinutesDelay * 60 * 1000L)
                    onSave(title, message, triggerTimeMs)
                }
            ) {
                Text("অ্যালার্ম সেট করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
