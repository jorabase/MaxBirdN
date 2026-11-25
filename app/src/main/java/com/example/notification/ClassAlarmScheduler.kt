package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.api.LiveClassDetails
import com.example.api.StudentLessonItem
import com.example.utils.RoutineDateUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScheduledClassAlarmInfo(
    val lessonId: String,
    val title: String,
    val subjectName: String,
    val startTimeMs: Long,
    val alarmTriggerMs: Long,
    val isExam: Boolean,
    val isEnabled: Boolean
)

object ClassAlarmScheduler {
    private const val TAG = "ClassAlarmScheduler"
    private const val PREFS_NAME = "shikho_alarm_scheduled_prefs"
    private const val KEY_SAVED_ROUTINE_JSON = "saved_routine_lessons_json"
    private const val KEY_SAVED_PROGRAM_ID = "saved_routine_program_id"
    private const val KEY_NOTIFIED_PRE_IDS = "notified_pre_class_ids"
    private const val KEY_NOTIFIED_START_IDS = "notified_start_class_ids"

    const val CHANNEL_ID = "shikho_class_alarms_channel"
    val CHANNEL_NAME: String
        get() = com.example.utils.AppLang.tr("Class Reminders & Alarms", "ক্লাস রিমাইন্ডার ও এলার্ম")

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = com.example.utils.AppLang.tr(
                    "Timely notifications for live classes and exams",
                    "নির্দিষ্ট সময়ে শিখোর লাইভ ক্লাস ও পরীক্ষা শুরুর নোটিফিকেশন"
                )
                enableLights(true)
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Resolves the accurate class start timestamp in milliseconds (Asia/Dhaka aware)
     * from either `lesson.live_class.start_time` or `lesson.start_time`.
     */
    private fun resolveLessonStartMillis(lesson: StudentLessonItem): Long? {
        val liveStart = lesson.live_class?.start_time?.trim()?.takeIf { it.isNotBlank() }
        val lessonStart = lesson.start_time?.trim()?.takeIf { it.isNotBlank() }

        // Prefer live_class.start_time when lesson.start_time is date-only or midnight placeholder
        val primaryCandidate = if (
            liveStart != null &&
            (lessonStart == null || lessonStart.length <= 10 || lessonStart.contains("T00:00:00"))
        ) {
            liveStart
        } else {
            lessonStart ?: liveStart
        } ?: return null

        RoutineDateUtils.parseIsoToDhakaCalendar(primaryCandidate)?.timeInMillis?.let {
            if (it > 0L) return it
        }
        if (liveStart != null && liveStart != primaryCandidate) {
            RoutineDateUtils.parseIsoToDhakaCalendar(liveStart)?.timeInMillis?.let {
                if (it > 0L) return it
            }
        }
        return null
    }

    fun extractScheduledAlarms(
        context: Context,
        lessons: List<StudentLessonItem>
    ): List<ScheduledClassAlarmInfo> {
        val sessionManager = com.example.auth.SessionManager(context)
        val leadTimeMillis = sessionManager.getClassNotificationLeadTimeMinutes() * 60 * 1000L
        val disabledAlarmIds = sessionManager.getDisabledAlarmIds()
        val isAllEnabled = sessionManager.isAllNotificationsEnabled()
        val isLiveEnabled = sessionManager.isLiveClassNotificationEnabled()
        val isExamEnabled = sessionManager.isExamNotificationEnabled()

        val now = System.currentTimeMillis()
        val result = mutableListOf<ScheduledClassAlarmInfo>()

        for ((index, lesson) in lessons.withIndex()) {
            val startMs = resolveLessonStartMillis(lesson) ?: continue
            if (startMs <= now) continue

            val lessonTitle = lesson.title ?: lesson.live_class?.chapter_name ?: com.example.utils.AppLang.tr("Live Class", "লাইভ ক্লাস")
            val subjectName = lesson.subject_name ?: lesson.live_class?.subject_name ?: com.example.utils.AppLang.tr("Course", "কোর্স")
            val lessonId = lesson.id.ifBlank { lesson.live_class?.id ?: "lesson_$index" }
            val isExam = lesson.isExam || lesson.content_type?.contains("Exam", ignoreCase = true) == true

            val categoryEnabled = if (isExam) isExamEnabled else isLiveEnabled
            val enabled = isAllEnabled && categoryEnabled && !disabledAlarmIds.contains(lessonId)
            val preTriggerMs = startMs - leadTimeMillis
            // If pre-class reminder time is still in the future, show preTriggerMs;
            // if we are already inside the lead-time window before class starts, show exact class startMs
            val effectiveDisplayTriggerMs = if (preTriggerMs > now) preTriggerMs else startMs

            result.add(
                ScheduledClassAlarmInfo(
                    lessonId = lessonId,
                    title = lessonTitle,
                    subjectName = subjectName,
                    startTimeMs = startMs,
                    alarmTriggerMs = effectiveDisplayTriggerMs,
                    isExam = isExam,
                    isEnabled = enabled
                )
            )
        }
        return result.distinctBy { it.lessonId }.sortedBy { it.startTimeMs }
    }

    fun scheduleUpcomingClassAlarms(context: Context, lessons: List<StudentLessonItem>) {
        val programId = com.example.auth.SessionManager(context).getActiveProgramId() ?: "default"
        val effectiveLessons = if (lessons.isNotEmpty()) {
            lessons
        } else {
            com.example.course.LessonCacheManager.getAllLessons()
        }
        schedule7DayClassAlarms(context, programId, effectiveLessons)
    }

    fun scheduleCustomAlarm(context: Context, alarm: com.example.auth.CustomAlarmData) {
        if (alarm.isEnabled && alarm.triggerTimeMs > System.currentTimeMillis()) {
            scheduleCustomAlarm(context, alarm.id, alarm.title, alarm.message, alarm.triggerTimeMs)
        } else {
            cancelCustomAlarm(context, alarm.id)
        }
    }

    private fun scheduleExactAlarmCompat(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) {
        try {
            // Primary: setAlarmClock guarantees exact delivery even under Xiaomi MIUI/HyperOS/ColorOS battery saver
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showPendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val showPendingIntent = PendingIntent.getActivity(context, 0, showIntent, showPendingFlags)
            val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(clockInfo, operation)
            return
        } catch (e: Throwable) {
            Log.w(TAG, "setAlarmClock fallback to setExactAndAllowWhileIdle: ${e.message}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to schedule alarm: ${e.message}")
        }
    }

    fun schedule7DayClassAlarms(context: Context, programId: String, lessons: List<StudentLessonItem>) {
        createNotificationChannel(context)
        if (lessons.isNotEmpty()) {
            saveRoutineSnapshotForReboot(context, programId, lessons)
        }

        // 1. Cancel previous scheduled routine alarms before re-registering
        cancelAllAlarms(context, clearSavedRoutine = false)

        val sessionManager = com.example.auth.SessionManager(context)
        if (!sessionManager.isAllNotificationsEnabled()) {
            Log.d(TAG, "All notifications are turned off by user. Skipping alarms.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notifiedPreIds = (prefs.getStringSet(KEY_NOTIFIED_PRE_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val notifiedStartIds = (prefs.getStringSet(KEY_NOTIFIED_START_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val scheduledIds = mutableSetOf<Int>()

        val currentTime = System.currentTimeMillis()
        val leadTimeMinutes = sessionManager.getClassNotificationLeadTimeMinutes()
        val leadTimeMillis = leadTimeMinutes * 60 * 1000L
        val disabledAlarmIds = sessionManager.getDisabledAlarmIds()
        val isLiveEnabled = sessionManager.isLiveClassNotificationEnabled()
        val isExamEnabled = sessionManager.isExamNotificationEnabled()

        val timeFormat12Hr = SimpleDateFormat("hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        for ((index, lesson) in lessons.withIndex()) {
            val classStartTimeMillis = resolveLessonStartMillis(lesson) ?: continue
            val lessonTitle = lesson.title ?: lesson.live_class?.chapter_name ?: com.example.utils.AppLang.tr("Live Class", "লাইভ ক্লাস")
            val subjectName = lesson.subject_name ?: lesson.live_class?.subject_name ?: com.example.utils.AppLang.tr("Course", "কোর্স")
            val lessonId = lesson.id.ifBlank { lesson.live_class?.id ?: "lesson_$index" }

            val isExam = lesson.isExam || lesson.content_type?.contains("Exam", ignoreCase = true) == true
            if (isExam && !isExamEnabled) continue
            if (!isExam && !isLiveEnabled) continue
            if (disabledAlarmIds.contains(lessonId)) continue

            val mentorName = lesson.live_class?.teacher?.displayName
                ?: lesson.live_class?.instructor?.displayName
                ?: ""

            val mentorAvatar = lesson.live_class?.teacher?.displayAvatar
                ?: lesson.live_class?.instructor?.displayAvatar
                ?: ""

            try {
                val alarmTriggerTime = classStartTimeMillis - leadTimeMillis
                val classStartTimeDisplay = try {
                    timeFormat12Hr.format(Date(classStartTimeMillis))
                } catch (_: Exception) {
                    com.example.utils.AppLang.tr("scheduled time", "নির্দিষ্ট সময়ে")
                }

                // 0. If Shikho server reports this class is LIVE RIGHT NOW (`is_on_going == true`)
                // or it started within the last 15 minutes, fire the Shikho Live Class notification immediately once!
                val isCurrentlyLiveOnShikho = lesson.isLiveNow ||
                    (classStartTimeMillis in (currentTime - 15 * 60 * 1000L)..currentTime)
                val startKey = "${lessonId}_start"
                if (isCurrentlyLiveOnShikho && !notifiedStartIds.contains(startKey)) {
                    notifiedStartIds.add(startKey)
                    val immediateStartIntent = Intent(context, ClassAlarmReceiver::class.java).apply {
                        setPackage(context.packageName)
                        action = "com.maxbird.ff.app.ACTION_CLASS_ALARM_NOW_$lessonId"
                        putExtra("subject_name", subjectName)
                        putExtra("lesson_title", lessonTitle)
                        putExtra("lesson_id", lessonId)
                        putExtra("class_start_time_str", classStartTimeDisplay)
                        putExtra("mentor_name", mentorName)
                        putExtra("mentor_avatar", mentorAvatar)
                        putExtra("lead_time_minutes", 0)
                        putExtra("is_start_now", true)
                    }
                    context.sendBroadcast(immediateStartIntent)
                }

                // 1. Alarm: Pre-class Reminder (e.g. 30 mins before)
                val preKey = "${lessonId}_pre_${leadTimeMinutes}"
                if (alarmTriggerTime > currentTime) {
                    val requestCode = kotlin.math.abs((lessonId.hashCode() + index) * 31 + 1) % 1000000
                    scheduledIds.add(requestCode)

                    val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
                        setPackage(context.packageName)
                        action = "com.maxbird.ff.app.ACTION_CLASS_ALARM_$requestCode"
                        putExtra("subject_name", subjectName)
                        putExtra("lesson_title", lessonTitle)
                        putExtra("lesson_id", lessonId)
                        putExtra("class_start_time_str", classStartTimeDisplay)
                        putExtra("mentor_name", mentorName)
                        putExtra("mentor_avatar", mentorAvatar)
                        putExtra("lead_time_minutes", leadTimeMinutes)
                        putExtra("is_start_now", false)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        pendingIntentFlags
                    )

                    scheduleExactAlarmCompat(context, alarmManager, alarmTriggerTime, pendingIntent)
                    Log.d(TAG, "⏰ Scheduled $leadTimeMinutes-min reminder for $subjectName at ${Date(alarmTriggerTime)}")
                } else if (classStartTimeMillis > currentTime && !notifiedPreIds.contains(preKey)) {
                    // We are already inside the lead-time window (e.g., 4:46 PM for a 5:00 PM class with 30m lead time)!
                    // Fire the pre-class reminder once right away so the student never misses it.
                    notifiedPreIds.add(preKey)
                    val remainingMinutes = ((classStartTimeMillis - currentTime) / 60000L).toInt().coerceAtLeast(1)
                    val immediatePreIntent = Intent(context, ClassAlarmReceiver::class.java).apply {
                        setPackage(context.packageName)
                        action = "com.maxbird.ff.app.ACTION_CLASS_PRE_NOW_$lessonId"
                        putExtra("subject_name", subjectName)
                        putExtra("lesson_title", lessonTitle)
                        putExtra("lesson_id", lessonId)
                        putExtra("class_start_time_str", classStartTimeDisplay)
                        putExtra("mentor_name", mentorName)
                        putExtra("mentor_avatar", mentorAvatar)
                        putExtra("lead_time_minutes", remainingMinutes)
                        putExtra("is_start_now", false)
                    }
                    context.sendBroadcast(immediatePreIntent)
                }

                // 2. Alarm: Class Start Notification (at exact start time on Shikho routine)
                if (classStartTimeMillis > currentTime) {
                    val requestCodeStart = kotlin.math.abs((lessonId.hashCode() + index) * 31 + 2) % 1000000
                    scheduledIds.add(requestCodeStart)

                    val intentStart = Intent(context, ClassAlarmReceiver::class.java).apply {
                        setPackage(context.packageName)
                        action = "com.maxbird.ff.app.ACTION_CLASS_ALARM_$requestCodeStart"
                        putExtra("subject_name", subjectName)
                        putExtra("lesson_title", lessonTitle)
                        putExtra("lesson_id", lessonId)
                        putExtra("class_start_time_str", classStartTimeDisplay)
                        putExtra("mentor_name", mentorName)
                        putExtra("mentor_avatar", mentorAvatar)
                        putExtra("lead_time_minutes", 0)
                        putExtra("is_start_now", true)
                    }

                    val pendingIntentStart = PendingIntent.getBroadcast(
                        context,
                        requestCodeStart,
                        intentStart,
                        pendingIntentFlags
                    )

                    scheduleExactAlarmCompat(context, alarmManager, classStartTimeMillis, pendingIntentStart)
                    Log.d(TAG, "🔴 Scheduled exact class start notification for $subjectName at ${Date(classStartTimeMillis)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling class alarm for lesson $lessonId: ${e.message}")
            }
        }

        prefs.edit()
            .putStringSet("scheduled_alarm_ids", scheduledIds.map { it.toString() }.toSet())
            .putStringSet(KEY_NOTIFIED_PRE_IDS, notifiedPreIds.toSet())
            .putStringSet(KEY_NOTIFIED_START_IDS, notifiedStartIds.toSet())
            .apply()
        Log.d(TAG, "✅ Scheduled ${scheduledIds.size} class alarms for program $programId")
    }

    private fun saveRoutineSnapshotForReboot(context: Context, programId: String, lessons: List<StudentLessonItem>) {
        try {
            val arr = JSONArray()
            lessons.take(60).forEach { item ->
                val startStr = item.live_class?.start_time?.takeIf { it.isNotBlank() } ?: item.start_time ?: return@forEach
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title ?: item.live_class?.chapter_name ?: "")
                    put("subject_name", item.subject_name ?: item.live_class?.subject_name ?: "")
                    put("start_time", startStr)
                    put("content_type", item.content_type ?: "")
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_SAVED_PROGRAM_ID, programId)
                .putString(KEY_SAVED_ROUTINE_JSON, arr.toString())
                .apply()
        } catch (_: Throwable) {}
    }

    fun rescheduleSavedRoutineAlarms(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val programId = prefs.getString(KEY_SAVED_PROGRAM_ID, "default") ?: "default"
            val rawJson = prefs.getString(KEY_SAVED_ROUTINE_JSON, null) ?: return
            val arr = JSONArray(rawJson)
            val restored = mutableListOf<StudentLessonItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val startStr = obj.optString("start_time", "")
                if (startStr.isBlank()) continue
                restored.add(
                    StudentLessonItem(
                        id = obj.optString("id", "restored_$i"),
                        title = obj.optString("title", com.example.utils.AppLang.tr("Live Class", "লাইভ ক্লাস")),
                        subject_name = obj.optString("subject_name", com.example.utils.AppLang.tr("Course", "কোর্স")),
                        start_time = startStr,
                        content_type = obj.optString("content_type", "LiveClass"),
                        live_class = LiveClassDetails(
                            id = obj.optString("id", "restored_$i"),
                            chapter_name = obj.optString("title", com.example.utils.AppLang.tr("Live Class", "লাইভ ক্লাস")),
                            subject_name = obj.optString("subject_name", com.example.utils.AppLang.tr("Course", "কোর্স")),
                            start_time = startStr
                        )
                    )
                )
            }
            if (restored.isNotEmpty()) {
                schedule7DayClassAlarms(context, programId, restored)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to reschedule saved routine alarms after boot: ${e.message}")
        }
    }

    fun scheduleCustomAlarm(context: Context, alarmId: String, title: String, body: String, triggerTimeMs: Long) {
        createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val requestCode = alarmId.hashCode()
        val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
            setPackage(context.packageName)
            action = "com.maxbird.ff.app.ACTION_CUSTOM_ALARM_$requestCode"
            putExtra("custom_title", title)
            putExtra("custom_body", body)
            putExtra("lesson_id", alarmId)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags
        )

        scheduleExactAlarmCompat(context, alarmManager, triggerTimeMs, pendingIntent)
        Log.d(TAG, "⏰ Scheduled custom alarm '$title' at ${Date(triggerTimeMs)}")
    }

    fun cancelCustomAlarm(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = alarmId.hashCode()
        val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
            setPackage(context.packageName)
            action = "com.maxbird.ff.app.ACTION_CUSTOM_ALARM_$requestCode"
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)
        try {
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }

    fun triggerInstantTestNotification(context: Context, title: String, body: String) {
        createNotificationChannel(context)
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)

        val mainIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            mainIntent,
            pendingIntentFlags
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setColor(0xFF0072EC.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending instant test notification: ${e.message}")
        }
    }

    fun cancelAllAlarms(context: Context, clearSavedRoutine: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idsStr = prefs.getStringSet("scheduled_alarm_ids", emptySet()) ?: emptySet()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        for (idStr in idsStr) {
            val requestCode = idStr.toIntOrNull() ?: continue
            val intentWithAction = Intent(context, ClassAlarmReceiver::class.java).apply {
                setPackage(context.packageName)
                action = "com.maxbird.ff.app.ACTION_CLASS_ALARM_$requestCode"
            }
            val pendingWithAction = PendingIntent.getBroadcast(context, requestCode, intentWithAction, pendingIntentFlags)
            try {
                alarmManager.cancel(pendingWithAction)
            } catch (_: Exception) {}

            val legacyIntent = Intent(context, ClassAlarmReceiver::class.java)
            val legacyPending = PendingIntent.getBroadcast(context, requestCode, legacyIntent, pendingIntentFlags)
            try {
                alarmManager.cancel(legacyPending)
            } catch (_: Exception) {}
        }

        if (clearSavedRoutine) {
            prefs.edit().clear().apply()
        } else {
            prefs.edit().remove("scheduled_alarm_ids").apply()
        }
        Log.d(TAG, "🗑️ Cancelled previous class alarms")
    }
}
