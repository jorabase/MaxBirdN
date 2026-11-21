package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.auth.SessionManager
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Service to handle incoming Firebase Cloud Messaging (FCM) notifications and token refreshes
 * from BOTH notification backends:
 * 1. Shikho Official Server (`projects/shikho-tech`):
 *    - Live Class Alerts (`LIVE_ShikhoNotification_...`)
 *    - Model Test / Live Exam / Quiz / Result Alerts
 *    - Daily Routine & Study Guideline Alerts
 *    - Orientation Live & Global Broadcast Campaigns (`SHIKHO_ALL` & `{userId}`)
 * 2. MaxBird Database (`projects/maxbird-notification` + OneSignal + Supabase):
 *    - Device Approval / Block / Unblock
 *    - Course Access Approval & Pricing Ready
 *    - Payment / Installment Verification & Due Reminders
 *    - Free Trial Activation
 */
class ShikhoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ShikhoFCMService"
        const val EXTRA_NOTIFICATION_URL = "extra_notification_url"
        const val EXTRA_NOTIFICATION_DEEPLINK = "extra_notification_deeplink"
        const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        const val EXTRA_CONTENT_ID = "extra_content_id"

        private val PROGRAM_TOPIC_REGEX = Regex("Program_([A-Za-z0-9]+)")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Token: $token")
        val sessionManager = SessionManager.getInstance(applicationContext)
        sessionManager.setFcmToken(token)
        ShikhoNotificationManager.logTerminal("INFO", "🔄 FCM Token Refreshed: ${token.take(20)}...")

        // Sync all subscriptions with the new token (Shikho FCM topics + OneSignal Player + Supabase)
        ShikhoNotificationManager.syncAllTopicSubscriptions(applicationContext)
        OneSignalPushManager.triggerSyncAsync(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val fromTopicOrSender = remoteMessage.from ?: ""
        Log.d(TAG, "FCM Message received from: $fromTopicOrSender")
        ShikhoNotificationManager.logTerminal("PUSH", "📩 Push Notification Received from: $fromTopicOrSender")

        val sessionManager = SessionManager(applicationContext)
        val data = remoteMessage.data
        val notification = remoteMessage.notification

        // 1. If this notification came from a course-specific Shikho topic (`LIVE_ShikhoNotification_...`),
        // allow it for ANY of the student's active (non-expired) enrolled courses.
        // Only auto-unsubscribe and ignore if the course is expired or no longer enrolled.
        if (fromTopicOrSender.startsWith("/topics/LIVE_ShikhoNotification_")) {
            val rawTopicName = fromTopicOrSender.removePrefix("/topics/")
            val topicProgramId = PROGRAM_TOPIC_REGEX.find(rawTopicName)?.groupValues?.getOrNull(1)
            val activeEnrolledIds = sessionManager.getActiveEnrolledProgramIds()

            if (!topicProgramId.isNullOrBlank() &&
                activeEnrolledIds.isNotEmpty() &&
                activeEnrolledIds.none { it.equals(topicProgramId, ignoreCase = true) }
            ) {
                Log.d(TAG, "Ignoring notification from expired/un-enrolled course topic: $rawTopicName (activeCourses=$activeEnrolledIds)")
                ShikhoNotificationManager.logTerminal(
                    "WARN",
                    "🗑️ Auto-unsubscribing expired/un-enrolled course push topic: $rawTopicName"
                )
                try {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(rawTopicName)
                } catch (_: Throwable) {}
                return
            }
        }

        // 2. Parse OneSignal's custom JSON payload (`{"i": "notif_id", "a": {...}}`) if present
        val oneSignalAdditionalData = try {
            val customRaw = data["custom"]
            if (!customRaw.isNullOrBlank() && customRaw.trim().startsWith("{")) {
                org.json.JSONObject(customRaw).optJSONObject("a")
            } else null
        } catch (_: Throwable) {
            null
        }

        val eventType = (
            data["notification_type"]
                ?: data["event_type"]
                ?: data["type"]
                ?: data["content_type"]
                ?: data["category"]
                ?: oneSignalAdditionalData?.optString("notification_type", "")?.takeIf { it.isNotBlank() }
                ?: oneSignalAdditionalData?.optString("event_type", "")?.takeIf { it.isNotBlank() }
                ?: oneSignalAdditionalData?.optString("type", "")?.takeIf { it.isNotBlank() }
                ?: if (fromTopicOrSender.contains("LIVE_ShikhoNotification", ignoreCase = true)) "LIVE_CLASS" else ""
        ).trim()

        // 3. Classify the 5 Notification Types:
        // Type A: Critical Admin / Database Updates (Supabase + OneSignal)
        val isCriticalAdminUpdate = eventType.contains("DEVICE", ignoreCase = true) ||
            eventType.contains("COURSE", ignoreCase = true) ||
            eventType.contains("PAYMENT", ignoreCase = true) ||
            eventType.contains("TRIAL", ignoreCase = true) ||
            eventType.contains("INSTALLMENT", ignoreCase = true) ||
            eventType.contains("APPROV", ignoreCase = true) ||
            eventType.contains("REJECT", ignoreCase = true) ||
            eventType.contains("BLOCK", ignoreCase = true) ||
            eventType.contains("UNBLOCK", ignoreCase = true) ||
            data.containsKey("alert") ||
            data.containsKey("custom")

        // Type B: Live Class Notifications (Shikho Server)
        val isLiveClassNotification = !isCriticalAdminUpdate && (
            eventType.contains("live", ignoreCase = true) ||
                eventType.contains("class", ignoreCase = true) ||
                fromTopicOrSender.contains("LIVE_ShikhoNotification", ignoreCase = true)
            )

        // Type C: Model Test / Exam / Quiz Notifications (Shikho Server)
        val isExamNotification = !isCriticalAdminUpdate && (
            eventType.contains("exam", ignoreCase = true) ||
                eventType.contains("model_test", ignoreCase = true) ||
                eventType.contains("quiz", ignoreCase = true) ||
                eventType.contains("test", ignoreCase = true) ||
                eventType.contains("result", ignoreCase = true)
            )

        // Respect user notification toggles for non-admin notifications
        if (!isCriticalAdminUpdate) {
            if (!sessionManager.isAllNotificationsEnabled()) {
                Log.d(TAG, "Master notifications disabled. Skipping non-admin push.")
                return
            }
            if (isLiveClassNotification && !sessionManager.isLiveClassNotificationEnabled()) {
                Log.d(TAG, "Live class notifications disabled. Skipping.")
                return
            }
            if (isExamNotification && !sessionManager.isExamNotificationEnabled()) {
                Log.d(TAG, "Exam notifications disabled. Skipping.")
                return
            }
        }

        val title = (
            notification?.title
                ?: data["title"]
                ?: data["heading"]
                ?: data["notification_title"]
                ?: oneSignalAdditionalData?.optString("title", "")?.takeIf { it.isNotBlank() }
                ?: "শিখো নোটিফিকেশন"
            ).trim()

        ShikhoNotificationManager.recordIncomingPush(
            from = fromTopicOrSender.ifBlank { "shikho-tech" },
            title = title,
            eventType = eventType
        )

        val body = (
            notification?.body
                ?: data["alert"]
                ?: data["body"]
                ?: data["message"]
                ?: data["description"]
                ?: data["subtitle"]
                ?: data["text"]
                ?: oneSignalAdditionalData?.optString("body", "")?.takeIf { it.isNotBlank() }
                ?: oneSignalAdditionalData?.optString("message", "")?.takeIf { it.isNotBlank() }
                ?: ""
            ).trim()

        val imageUrl = (
            notification?.imageUrl?.toString()
                ?: data["bicon"]
                ?: data["big_picture"]
                ?: data["image"]
                ?: data["image_url"]
                ?: data["banner"]
                ?: data["banner_url"]
                ?: data["picture"]
                ?: data["media_url"]
                ?: oneSignalAdditionalData?.optString("image_url", "")?.takeIf { it.isNotBlank() }
            )?.trim()

        val deeplink = (
            data["deeplink"]
                ?: data["deep_link"]
                ?: data["url"]
                ?: data["link"]
                ?: data["click_action"]
                ?: data["action_url"]
                ?: oneSignalAdditionalData?.optString("deeplink", "")?.takeIf { it.isNotBlank() }
                ?: ""
            ).trim()

        val contentId = (
            data["content_id"]
                ?: data["lesson_id"]
                ?: data["live_class_id"]
                ?: data["model_test_id"]
                ?: data["program_id"]
                ?: data["id"]
                ?: oneSignalAdditionalData?.optString("program_id", "")
                ?: ""
            ).trim()

        // Process and display notification + refresh device/course status in background scope
        CoroutineScope(Dispatchers.IO).launch {
            if (isCriticalAdminUpdate) {
                runCatching {
                    com.example.security.DeviceActivationRepository.checkStudentVerificationStatus(applicationContext)
                }
                runCatching {
                    com.example.course.CourseCommerceRepository.fetchCourseCommerceStatus(applicationContext)
                }
            }

            val bitmap = if (!imageUrl.isNullOrBlank()) {
                downloadBitmap(imageUrl)
            } else null

            displayNotification(
                sessionManager = sessionManager,
                title = title,
                body = body,
                largeBitmap = bitmap,
                deeplink = deeplink,
                type = eventType,
                contentId = contentId,
                useLiveChannel = isLiveClassNotification || isExamNotification
            )
        }
    }

    private fun displayNotification(
        sessionManager: SessionManager,
        title: String,
        body: String,
        largeBitmap: Bitmap?,
        deeplink: String,
        type: String,
        contentId: String,
        useLiveChannel: Boolean
    ) {
        ShikhoNotificationManager.createNotificationChannels(applicationContext)

        val channelId = if (useLiveChannel || type.contains("live", ignoreCase = true) || deeplink.contains("live", ignoreCase = true)) {
            ShikhoNotificationManager.CHANNEL_LIVE_CLASSES
        } else {
            ShikhoNotificationManager.CHANNEL_PUSH_NOTIFICATIONS
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_URL, deeplink)
            putExtra(EXTRA_NOTIFICATION_DEEPLINK, deeplink)
            putExtra(EXTRA_NOTIFICATION_TYPE, type)
            putExtra(EXTRA_CONTENT_ID, contentId)
            if (contentId.isNotBlank() && (useLiveChannel || type.contains("live", ignoreCase = true))) {
                putExtra("lesson_id", contentId)
            }
            if (deeplink.isNotBlank()) {
                data = Uri.parse(deeplink)
            }
        }

        val requestCode = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = if (sessionManager.isNotificationSoundEnabled()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else null

        val appLogoBitmap = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_shikho_notification_small)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (useLiveChannel) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)

        if (soundUri != null) {
            builder.setSound(soundUri)
        }
        if (sessionManager.isNotificationVibrateEnabled()) {
            builder.setVibrate(longArrayOf(0, 350, 200, 350))
        }

        if (appLogoBitmap != null) {
            builder.setLargeIcon(appLogoBitmap)
        }

        if (largeBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(largeBitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = Random.nextInt(1000, 999999)
        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "Displayed notification (id: $notificationId, type: $type): $title")
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to download notification image: ${e.message}")
            null
        }
    }
}
