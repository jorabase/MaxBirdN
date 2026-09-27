package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.example.auth.SessionManager
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manages Firebase Cloud Messaging (FCM) topic subscriptions and notification channels
 * matching the authentic Shikho notification architecture.
 */
object ShikhoNotificationManager {
    private const val TAG = "ShikhoNotificationMgr"

    // Primary Notification Channels
    const val CHANNEL_PUSH_NOTIFICATIONS = "shikho_push_notifications"
    const val CHANNEL_LIVE_CLASSES = "shikho_live_classes"

    // Global topic for all Shikho students
    const val TOPIC_SHIKHO_ALL = "SHIKHO_ALL"

    data class FcmTerminalLog(
        val timestamp: String,
        val type: String, // "INFO", "SUCCESS", "WARN", "ERROR", "PUSH"
        val message: String
    )

    private val _liveLogs = kotlinx.coroutines.flow.MutableStateFlow<List<FcmTerminalLog>>(emptyList())
    val liveLogs: kotlinx.coroutines.flow.StateFlow<List<FcmTerminalLog>> = _liveLogs

    fun logTerminal(type: String, message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())
        val entry = FcmTerminalLog(timestamp = time, type = type, message = message)
        _liveLogs.value = (_liveLogs.value + entry).takeLast(100)
        Log.d(TAG, "[$type] $message")
    }

    fun clearLogs() {
        _liveLogs.value = emptyList()
    }

    /**
     * Initializes high-importance notification channels for Android 8.0+ (API 26+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            // 1. General & Announcement Notifications Channel
            val pushChannel = NotificationChannel(
                CHANNEL_PUSH_NOTIFICATIONS,
                "শিখো নোটিফিকেশন ও ঘোষণা",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "লাইভ ক্লাস, পরীক্ষা এবং গুরুত্বপূর্ণ ঘোষণা সংক্রান্ত নোটিফিকেশন"
                enableLights(true)
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(pushChannel)

            // 2. Real-time Live Class Alerts Channel
            val liveClassChannel = NotificationChannel(
                CHANNEL_LIVE_CLASSES,
                "লাইভ ক্লাস অ্যালার্ট",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "লাইভ ক্লাস শুরুর তাত্ক্ষণিক অ্যালার্ট ও রিমাইন্ডার"
                enableLights(true)
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(liveClassChannel)

            logTerminal("INFO", "✅ Notification channels initialized (shikho_push_notifications, shikho_live_classes)")
        }
    }

    /**
     * Synchronizes all FCM topic subscriptions:
     * 1. SHIKHO_ALL
     * 2. {userId}
     * 3. LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}
     * 4. LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}
     */
    fun syncAllTopicSubscriptions(context: Context) {
        try {
            val sessionManager = SessionManager(context)
            val fcm = FirebaseMessaging.getInstance()

            logTerminal("INFO", "⚡ Firebase Messaging ক্লাউড সিঙ্ক শুরু হচ্ছে...")

            // Fetch & Log real FCM Registration Token
            fcm.token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    sessionManager.setFcmToken(token)
                    logTerminal("INFO", "🔑 Device FCM Token: ${token.take(20)}...${token.takeLast(10)}")
                } else {
                    logTerminal("WARN", "⚠️ Token fetch pending/failed: ${task.exception?.message}")
                }
            }

            // 1. Subscribe to Global Broadcast Topic
            fcm.subscribeToTopic(TOPIC_SHIKHO_ALL)
                .addOnSuccessListener {
                    logTerminal("SUCCESS", "✅ Subscribed to Global Topic: $TOPIC_SHIKHO_ALL")
                }
                .addOnFailureListener { e ->
                    logTerminal("ERROR", "❌ Failed subscribing to $TOPIC_SHIKHO_ALL: ${e.message}")
                }

            // 2. User-specific Direct Topic
            val userId = sessionManager.getUserId()
            if (!userId.isNullOrBlank()) {
                fcm.subscribeToTopic(userId)
                    .addOnSuccessListener {
                        logTerminal("SUCCESS", "✅ Subscribed to User Topic: $userId")
                    }
                    .addOnFailureListener { e ->
                        logTerminal("ERROR", "❌ Failed subscribing to user $userId: ${e.message}")
                    }
            } else {
                logTerminal("WARN", "⚠️ User ID পাওয়া যায়নি (লগইন প্রয়োজন)")
            }

            // 3. Program & Phase Specific Topics
            val activeProgramId = sessionManager.getActiveProgramId()
            val activePhaseId = sessionManager.getActiveProgramPhaseId()

            if (!activeProgramId.isNullOrBlank()) {
                subscribeProgramAndPhase(fcm, sessionManager, userId, activeProgramId, activePhaseId)
            } else {
                logTerminal("WARN", "⚠️ কোনো অ্যাক্টিভ প্রোগ্রাম/কোর্স নির্বাচিত নেই")
            }
        } catch (e: Throwable) {
            logTerminal("ERROR", "🚨 Exception during FCM sync: ${e.message}")
        }
    }

    /**
     * Subscribes to program and phase topics while cleanly unsubscribing from any previous ones.
     */
    fun updateProgramAndPhase(context: Context, newProgramId: String, newPhaseId: String?) {
        try {
            val sessionManager = SessionManager(context)
            val fcm = FirebaseMessaging.getInstance()
            val userId = sessionManager.getUserId()

            subscribeProgramAndPhase(fcm, sessionManager, userId, newProgramId, newPhaseId)
        } catch (e: Throwable) {
            Log.e(TAG, "Error updating program & phase topics: ${e.message}", e)
        }
    }

    private fun subscribeProgramAndPhase(
        fcm: FirebaseMessaging,
        sessionManager: SessionManager,
        userId: String?,
        newProgramId: String,
        newPhaseId: String?
    ) {
        val oldProgramId = sessionManager.getSubscribedFcmProgramId()
        val oldPhaseId = sessionManager.getSubscribedFcmPhaseId()

        // If program or phase changed, unsubscribe from old topics first
        if (!oldProgramId.isNullOrBlank() && oldProgramId != newProgramId) {
            // Unsubscribe old user program topic
            if (!userId.isNullOrBlank()) {
                val oldUserProgramTopic = "LIVE_ShikhoNotification_UserID_${userId}_Program_${oldProgramId}"
                fcm.unsubscribeFromTopic(oldUserProgramTopic)
                    .addOnSuccessListener { logTerminal("INFO", "🔄 Unsubscribed old topic: $oldUserProgramTopic") }
            }

            // Unsubscribe old program phase topic
            if (!oldPhaseId.isNullOrBlank()) {
                val oldPhaseTopic = "LIVE_ShikhoNotification_Program_${oldProgramId}_Phase_${oldPhaseId}"
                fcm.unsubscribeFromTopic(oldPhaseTopic)
                    .addOnSuccessListener { logTerminal("INFO", "🔄 Unsubscribed old phase: $oldPhaseTopic") }
            }
        } else if (!oldPhaseId.isNullOrBlank() && oldPhaseId != newPhaseId) {
            // Only phase changed
            val oldPhaseTopic = "LIVE_ShikhoNotification_Program_${newProgramId}_Phase_${oldPhaseId}"
            fcm.unsubscribeFromTopic(oldPhaseTopic)
                .addOnSuccessListener { logTerminal("INFO", "🔄 Unsubscribed old phase: $oldPhaseTopic") }
        }

        // Subscribe to New User + Program Topic: LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}
        if (!userId.isNullOrBlank()) {
            val userProgramTopic = "LIVE_ShikhoNotification_UserID_${userId}_Program_${newProgramId}"
            fcm.subscribeToTopic(userProgramTopic)
                .addOnSuccessListener {
                    logTerminal("SUCCESS", "✅ Subscribed to Program: $userProgramTopic")
                }
                .addOnFailureListener { e ->
                    logTerminal("ERROR", "❌ Failed subscribing to $userProgramTopic: ${e.message}")
                }
        }

        // Subscribe to New Program + Phase Topic: LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}
        if (!newPhaseId.isNullOrBlank()) {
            val programPhaseTopic = "LIVE_ShikhoNotification_Program_${newProgramId}_Phase_${newPhaseId}"
            fcm.subscribeToTopic(programPhaseTopic)
                .addOnSuccessListener {
                    logTerminal("SUCCESS", "✅ Subscribed to Phase: $programPhaseTopic")
                }
                .addOnFailureListener { e ->
                    logTerminal("ERROR", "❌ Failed subscribing to $programPhaseTopic: ${e.message}")
                }
        }

        // Save current subscriptions in session
        sessionManager.setSubscribedFcmProgramId(newProgramId)
        sessionManager.setSubscribedFcmPhaseId(newPhaseId)
        logTerminal("SUCCESS", "🎯 সব টপিক ক্লাউডে সফলভাবে নিবন্ধিত হয়েছে!")
    }

    /**
     * Unsubscribes from user and course-specific topics upon logout
     */
    fun unsubscribeAll(context: Context) {
        try {
            val sessionManager = SessionManager(context)
            val fcm = FirebaseMessaging.getInstance()
            val userId = sessionManager.getUserId()
            val programId = sessionManager.getSubscribedFcmProgramId()
            val phaseId = sessionManager.getSubscribedFcmPhaseId()

            if (!userId.isNullOrBlank()) {
                fcm.unsubscribeFromTopic(userId)
                if (!programId.isNullOrBlank()) {
                    fcm.unsubscribeFromTopic("LIVE_ShikhoNotification_UserID_${userId}_Program_${programId}")
                }
            }

            if (!programId.isNullOrBlank() && !phaseId.isNullOrBlank()) {
                fcm.unsubscribeFromTopic("LIVE_ShikhoNotification_Program_${programId}_Phase_${phaseId}")
            }

            sessionManager.setSubscribedFcmProgramId(null)
            sessionManager.setSubscribedFcmPhaseId(null)
            Log.d(TAG, "Unsubscribed from personalized topics on logout.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error unsubscribing from topics: ${e.message}", e)
        }
    }
}
