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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Service to handle incoming Firebase Cloud Messaging (FCM) notifications and token refreshes.
 */
class ShikhoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ShikhoFCMService"
        const val EXTRA_NOTIFICATION_URL = "extra_notification_url"
        const val EXTRA_NOTIFICATION_DEEPLINK = "extra_notification_deeplink"
        const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        const val EXTRA_CONTENT_ID = "extra_content_id"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Token: $token")
        val sessionManager = SessionManager(applicationContext)
        sessionManager.setFcmToken(token)

        // Sync all subscriptions with the new token
        ShikhoNotificationManager.syncAllTopicSubscriptions(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")
        ShikhoNotificationManager.logTerminal("PUSH", "📩 Real FCM Message Received from: ${remoteMessage.from}")

        val sessionManager = SessionManager(applicationContext)
        if (!sessionManager.isAllNotificationsEnabled()) {
            Log.d(TAG, "Push notifications are disabled in user settings. Skipping display.")
            return
        }

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = notification?.title
            ?: data["title"]
            ?: data["heading"]
            ?: "শিখো লাইভ নোটিফিকেশন"

        val body = notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: data["description"]
            ?: ""

        val imageUrl = notification?.imageUrl?.toString()
            ?: data["image"]
            ?: data["image_url"]
            ?: data["banner"]

        val deeplink = data["deeplink"]
            ?: data["url"]
            ?: data["link"]
            ?: data["click_action"]
            ?: ""

        val type = data["type"] ?: data["content_type"] ?: ""
        val contentId = data["content_id"] ?: data["id"] ?: data["lesson_id"] ?: ""

        // Process and display notification in background scope
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = if (!imageUrl.isNullOrBlank()) {
                downloadBitmap(imageUrl)
            } else null

            displayNotification(
                title = title,
                body = body,
                imageUrl = imageUrl,
                largeBitmap = bitmap,
                deeplink = deeplink,
                type = type,
                contentId = contentId
            )
        }
    }

    private fun displayNotification(
        title: String,
        body: String,
        imageUrl: String?,
        largeBitmap: Bitmap?,
        deeplink: String,
        type: String,
        contentId: String
    ) {
        val channelId = if (type.contains("live", ignoreCase = true) || deeplink.contains("live", ignoreCase = true)) {
            ShikhoNotificationManager.CHANNEL_LIVE_CLASSES
        } else {
            ShikhoNotificationManager.CHANNEL_PUSH_NOTIFICATIONS
        }

        // Create PendingIntent pointing to MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_URL, deeplink)
            putExtra(EXTRA_NOTIFICATION_DEEPLINK, deeplink)
            putExtra(EXTRA_NOTIFICATION_TYPE, type)
            putExtra(EXTRA_CONTENT_ID, contentId)
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

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)

        // If an image was downloaded, use BigPictureStyle; otherwise, use BigTextStyle
        if (largeBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(largeBitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
            builder.setLargeIcon(largeBitmap)
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
        Log.d(TAG, "Displayed notification (id: $notificationId): $title")
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
