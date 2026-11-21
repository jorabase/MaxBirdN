package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.auth.SessionManager
import com.example.security.AntiTamperSecurity
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Manages Firebase Cloud Messaging (FCM) topic subscriptions and notification channels
 * matching the authentic Shikho notification architecture (`projects/shikho-tech`):
 * 1. `SHIKHO_ALL` (Global broadcasts, campaigns, orientation live, routine reminders)
 * 2. `{userId}` (Personal account notifications)
 * 3. `LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}` (Selected course notifications)
 * 4. `LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}` (Selected course phase live class & exam alerts)
 *
 * Uses the real Firebase Installations (`FID` + `x-goog-firebase-installations-auth`) +
 * `fcmregistrations.googleapis.com/v1/projects/{projectId}/registrations/{fid}/topicSubscriptions/{topic}:subscribe`
 * protocol alongside Android `FirebaseMessaging.getInstance()` topic subscriptions, and exposes
 * truthful, real-time connection diagnostics for authorized `can_view_profiles` devices.
 */
object ShikhoNotificationManager {
    private const val TAG = "ShikhoNotificationMgr"

    // Primary Notification Channels
    const val CHANNEL_PUSH_NOTIFICATIONS = "shikho_push_notifications"
    const val CHANNEL_LIVE_CLASSES = "shikho_live_classes"

    // Global topic for all Shikho students
    const val TOPIC_SHIKHO_ALL = "SHIKHO_ALL"

    private const val FCM_REGISTRATIONS_BASE_URL = "https://fcmregistrations.googleapis.com/v1"

    enum class TopicConnectionStatus {
        IDLE,
        CONNECTING,
        CONNECTED,
        FAILED,
        SKIPPED
    }

    data class FcmTerminalLog(
        val id: Long,
        val timestamp: String,
        val type: String, // "INFO", "SUCCESS", "WARN", "ERROR", "PUSH", "HTTP"
        val message: String
    )

    data class TopicSubscriptionState(
        val key: String,
        val label: String,
        val topicName: String,
        val status: TopicConnectionStatus = TopicConnectionStatus.IDLE,
        val httpCode: Int? = null,
        val createTime: String? = null,
        val latencyMs: Long? = null,
        val sdkVerified: Boolean = false,
        val errorMessage: String? = null
    )

    data class ShikhoFcmConnectionState(
        val isSyncing: Boolean = false,
        val playServicesAvailable: Boolean = false,
        val firebaseInitialized: Boolean = false,
        val projectId: String = "",
        val appId: String = "",
        val installationId: String = "",
        val fisAuthReady: Boolean = false,
        val fcmTokenPreview: String = "",
        val activeUserId: String = "",
        val activeProgramId: String = "",
        val activePhaseId: String = "",
        val topics: List<TopicSubscriptionState> = emptyList(),
        val lastSyncedAt: String = "",
        val lastError: String? = null,
        val receivedPushCount: Int = 0
    ) {
        val connectedTopicCount: Int
            get() = topics.count { it.status == TopicConnectionStatus.CONNECTED }

        val activeTargetTopicCount: Int
            get() = topics.count { it.status != TopicConnectionStatus.SKIPPED }

        val isConnected: Boolean
            get() = firebaseInitialized &&
                connectedTopicCount > 0 &&
                (fisAuthReady || fcmTokenPreview.isNotBlank())
    }

    private data class FcmRestResult(
        val success: Boolean,
        val httpCode: Int,
        val topicName: String? = null,
        val createTime: String? = null,
        val latencyMs: Long = 0L,
        val errorMessage: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val logIdSeq = AtomicLong(1L)

    private val httpClient: OkHttpClient by lazy {
        AntiTamperSecurity.applyToOkHttpBuilder(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
        ).build()
    }

    private val _liveLogs = MutableStateFlow<List<FcmTerminalLog>>(emptyList())
    val liveLogs: StateFlow<List<FcmTerminalLog>> = _liveLogs.asStateFlow()

    private val _connectionState = MutableStateFlow(ShikhoFcmConnectionState())
    val connectionState: StateFlow<ShikhoFcmConnectionState> = _connectionState.asStateFlow()

    private fun nowTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    fun logTerminal(type: String, message: String) {
        val entry = FcmTerminalLog(
            id = logIdSeq.getAndIncrement(),
            timestamp = nowTimeString(),
            type = type,
            message = message
        )
        _liveLogs.update { current -> (current + entry).takeLast(150) }
        Log.d(TAG, "[$type] $message")
    }

    fun clearLogs() {
        _liveLogs.value = emptyList()
    }

    fun recordIncomingPush(from: String, title: String, eventType: String) {
        _connectionState.update { state ->
            state.copy(receivedPushCount = state.receivedPushCount + 1)
        }
        val cleanType = eventType.ifBlank { "GENERAL" }
        logTerminal("PUSH", "📩 Push [$cleanType] from $from -> \"$title\"")
    }

    /**
     * Initializes high-importance notification channels for Android 8.0+ (API 26+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            // 1. General, Routine, Campaign & Account Notifications Channel
            val pushChannel = NotificationChannel(
                CHANNEL_PUSH_NOTIFICATIONS,
                "শিখো নোটিফিকেশন ও ঘোষণা",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "লাইভ ক্লাস, পরীক্ষা, রুটিন এবং গুরুত্বপূর্ণ ঘোষণা সংক্রান্ত নোটিফিকেশন"
                enableLights(true)
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(pushChannel)

            // 2. Real-time Live Class & Exam Alerts Channel
            val liveClassChannel = NotificationChannel(
                CHANNEL_LIVE_CLASSES,
                "লাইভ ক্লাস ও পরীক্ষা অ্যালার্ট",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "লাইভ ক্লাস ও মডেল টেস্ট শুরুর তাৎক্ষণিক অ্যালার্ট ও রিমাইন্ডার"
                enableLights(true)
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(liveClassChannel)
        }
    }

    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo("com.google.android.gms", 0)
            appInfo.enabled
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Synchronizes all authentic Shikho FCM topic subscriptions on `projects/shikho-tech`:
     * 1. `SHIKHO_ALL`
     * 2. `{userId}`
     * 3. `LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}`
     * 4. `LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}`
     *
     * Performs real Firebase Installations + FCM Token + `fcmregistrations.googleapis.com` `:subscribe`
     * + Android `FirebaseMessaging` SDK subscription and streams real results into [connectionState] and [liveLogs].
     */
    fun syncAllTopicSubscriptions(context: Context) {
        verifyAndSyncNow(context = context, forceRefreshFisToken = false)
    }

    /**
     * Triggers an immediate live diagnostic sync against Shikho's FCM registration backend
     * (`fcmregistrations.googleapis.com/v1/projects/{projectId}/registrations/{fid}/topicSubscriptions/{topic}:subscribe`)
     * and Android `FirebaseMessaging` SDK. Never fakes or simulates success.
     */
    fun verifyAndSyncNow(context: Context, forceRefreshFisToken: Boolean = true) {
        val appContext = context.applicationContext
        scope.launch {
            runFullShikhoFcmSync(appContext, forceRefreshFisToken)
        }
    }

    private suspend fun runFullShikhoFcmSync(
        appContext: Context,
        forceRefreshFisToken: Boolean,
        overrideProgramId: String? = null,
        overridePhaseId: String? = null
    ) = syncMutex.withLock {
        val sessionManager = SessionManager.getInstance(appContext)
        val gmsAvailable = isGooglePlayServicesAvailable(appContext)
        val userId = sessionManager.getUserId()?.trim().orEmpty()
        val activeProgramId = overrideProgramId?.trim()?.takeIf { it.isNotBlank() }
            ?: sessionManager.getActiveProgramId()?.trim().orEmpty()
        val activePhaseId = overridePhaseId?.trim()?.takeIf { it.isNotBlank() }
            ?: sessionManager.getActiveProgramPhaseId()?.trim().orEmpty()

        val initialTopics = buildExpectedTopics(userId, activeProgramId, activePhaseId)

        _connectionState.update { prev ->
            prev.copy(
                isSyncing = true,
                playServicesAvailable = gmsAvailable,
                activeUserId = userId,
                activeProgramId = activeProgramId,
                activePhaseId = activePhaseId,
                topics = initialTopics,
                lastError = null
            )
        }

        logTerminal("INFO", "🚀 Starting real Shikho FCM connection check...")

        // 1. Verify FirebaseApp & options from google-services.json
        val firebaseApp = try {
            FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext)
        } catch (e: Throwable) {
            logTerminal("ERROR", "❌ FirebaseApp init failed: ${e.message}")
            null
        }

        if (firebaseApp == null) {
            markAllActiveTopicsFailed("FirebaseApp not initialized")
            _connectionState.update {
                it.copy(
                    isSyncing = false,
                    firebaseInitialized = false,
                    lastSyncedAt = nowTimeString(),
                    lastError = "FirebaseApp not initialized (check google-services.json)"
                )
            }
            return
        }

        val options = firebaseApp.options
        val projectId = options.projectId?.trim().orEmpty()
        val appId = options.applicationId.trim()
        val apiKey = options.apiKey.trim()

        _connectionState.update {
            it.copy(
                firebaseInitialized = true,
                projectId = projectId,
                appId = appId
            )
        }

        logTerminal(
            "INFO",
            "📦 FirebaseApp ready | project=$projectId | appId=${appId.take(28)}..."
        )

        if (!gmsAvailable) {
            logTerminal("WARN", "⚠️ Google Play Services unavailable; attempting direct FIS + FCM REST check")
        }

        // 2. Acquire real Firebase Installation ID (FID) & FIS Auth JWT
        val installations = try {
            FirebaseInstallations.getInstance(firebaseApp)
        } catch (e: Throwable) {
            logTerminal("ERROR", "❌ FirebaseInstallations unavailable: ${e.message}")
            null
        }

        var fid = ""
        var fisAuthToken = ""
        if (installations != null) {
            val fidResult = awaitTaskResult(installations.id)
            fidResult.onSuccess { id ->
                fid = id?.trim().orEmpty()
                if (fid.isNotBlank()) {
                    logTerminal("SUCCESS", "🆔 Firebase Installation ID (FID): $fid")
                } else {
                    logTerminal("WARN", "⚠️ Firebase Installation ID returned blank")
                }
            }.onFailure { err ->
                logTerminal("ERROR", "❌ FID request failed: ${err.message}")
            }

            val tokenResult = awaitTaskResult(installations.getToken(forceRefreshFisToken))
            tokenResult.onSuccess { authRes ->
                fisAuthToken = authRes?.token?.trim().orEmpty()
                if (fisAuthToken.isNotBlank()) {
                    logTerminal(
                        "SUCCESS",
                        "🔐 FIS Auth JWT ready (len=${fisAuthToken.length}, exp=${authRes?.tokenExpirationTimestamp})"
                    )
                } else {
                    logTerminal("WARN", "⚠️ FIS Auth JWT returned blank")
                }
            }.onFailure { err ->
                logTerminal("ERROR", "❌ FIS Auth JWT failed: ${err.message}")
            }
        }

        _connectionState.update {
            it.copy(
                installationId = fid,
                fisAuthReady = fisAuthToken.isNotBlank()
            )
        }

        // 3. Acquire real FCM Registration Token via FirebaseMessaging SDK
        val fcm = try {
            FirebaseMessaging.getInstance().apply {
                runCatching { isAutoInitEnabled = true }
            }
        } catch (e: Throwable) {
            logTerminal("ERROR", "❌ FirebaseMessaging instance failed: ${e.message}")
            null
        }

        var fcmToken = sessionManager.getFcmToken()?.trim().orEmpty()
        if (fcm != null && gmsAvailable) {
            val fcmTokenResult = awaitTaskResult(fcm.token)
            fcmTokenResult.onSuccess { freshToken ->
                if (!freshToken.isNullOrBlank()) {
                    fcmToken = freshToken.trim()
                    sessionManager.setFcmToken(fcmToken)
                    logTerminal("SUCCESS", "🔑 FCM Registration Token: ${fcmToken.take(20)}...")
                }
            }.onFailure { err ->
                logTerminal("ERROR", "❌ FCM Token fetch failed: ${err.message}")
            }
        }

        _connectionState.update {
            it.copy(
                fcmTokenPreview = if (fcmToken.length > 20) "${fcmToken.take(18)}...${fcmToken.takeLast(6)}" else fcmToken
            )
        }

        // 4. Unsubscribe stale course/phase topics if course changed
        val previouslySubscribed = sessionManager.getSubscribedCourseTopics().toMutableSet()
        val activeCourseTopics = initialTopics
            .filter { it.status != TopicConnectionStatus.SKIPPED && it.key.startsWith("course_") }
            .map { it.topicName }
            .toSet()

        if (activeProgramId.isNotBlank()) {
            val staleTopics = previouslySubscribed.filter { oldTopic ->
                oldTopic !in activeCourseTopics && !oldTopic.contains("_Program_${activeProgramId}")
            }
            for (oldTopic in staleTopics) {
                unsubscribeTopicReal(
                    fcm = fcm,
                    gmsAvailable = gmsAvailable,
                    projectId = projectId,
                    apiKey = apiKey,
                    fid = fid,
                    fisAuthToken = fisAuthToken,
                    topic = oldTopic
                )
                previouslySubscribed.remove(oldTopic)
            }
        }

        // 5. Subscribe & verify every active Shikho topic (exact HAR endpoint + FirebaseMessaging SDK)
        var anyError: String? = null
        val updatedTopics = initialTopics.toMutableList()

        for (i in updatedTopics.indices) {
            val item = updatedTopics[i]
            if (item.status == TopicConnectionStatus.SKIPPED) {
                logTerminal("WARN", "⏭️ Skipped [${item.label}]: ${item.errorMessage}")
                continue
            }

            updatedTopics[i] = item.copy(status = TopicConnectionStatus.CONNECTING)
            _connectionState.update { it.copy(topics = updatedTopics.toList()) }

            // 5a. Android FirebaseMessaging SDK subscription (required for GMS delivery)
            var sdkOk = false
            var sdkErr: String? = null
            if (fcm != null && gmsAvailable) {
                val sdkRes = awaitTaskResult(fcm.subscribeToTopic(item.topicName))
                sdkRes.onSuccess {
                    sdkOk = true
                }.onFailure { err ->
                    sdkErr = err.message ?: err.javaClass.simpleName
                }
            } else if (!gmsAvailable) {
                sdkErr = "Google Play Services unavailable"
            }

            // 5b. Real fcmregistrations.googleapis.com REST :subscribe verification (exact Shikho protocol)
            var restResult: FcmRestResult? = null
            if (projectId.isNotBlank() && apiKey.isNotBlank() && fid.isNotBlank() && fisAuthToken.isNotBlank()) {
                restResult = callFcmRegistrationTopicAction(
                    projectId = projectId,
                    apiKey = apiKey,
                    fid = fid,
                    fisAuthToken = fisAuthToken,
                    topic = item.topicName,
                    action = "subscribe"
                )
            }

            val finalState = when {
                restResult != null && restResult.success -> {
                    val createInfo = restResult.createTime?.let { " | createTime=$it" } ?: ""
                    logTerminal(
                        "HTTP",
                        "POST .../topicSubscriptions/${item.topicName}:subscribe -> 200 OK (${restResult.latencyMs}ms)$createInfo"
                    )
                    if (sdkOk) {
                        logTerminal("SUCCESS", "✅ Connected [${item.label}]: ${item.topicName}")
                    } else if (!sdkErr.isNullOrBlank()) {
                        logTerminal("WARN", "⚠️ REST 200 OK, SDK note for ${item.topicName}: $sdkErr")
                    }
                    item.copy(
                        status = TopicConnectionStatus.CONNECTED,
                        httpCode = restResult.httpCode,
                        createTime = restResult.createTime,
                        latencyMs = restResult.latencyMs,
                        sdkVerified = sdkOk,
                        errorMessage = null
                    )
                }

                sdkOk -> {
                    val restNote = restResult?.let { "REST HTTP ${it.httpCode}: ${it.errorMessage}" }
                    if (restNote != null) {
                        logTerminal("WARN", "⚠️ SDK subscribed ${item.topicName}, but $restNote")
                    } else {
                        logTerminal("SUCCESS", "✅ SDK Subscribed [${item.label}]: ${item.topicName}")
                    }
                    item.copy(
                        status = TopicConnectionStatus.CONNECTED,
                        httpCode = restResult?.httpCode,
                        latencyMs = restResult?.latencyMs,
                        sdkVerified = true,
                        errorMessage = restNote
                    )
                }

                else -> {
                    val reason = buildString {
                        if (restResult != null) {
                            append("HTTP ${restResult.httpCode}")
                            if (!restResult.errorMessage.isNullOrBlank()) {
                                append(": ${restResult.errorMessage}")
                            }
                        }
                        if (!sdkErr.isNullOrBlank()) {
                            if (isNotEmpty()) append(" | ")
                            append("SDK: $sdkErr")
                        }
                        if (isEmpty()) {
                            append("FIS/FCM credentials unavailable")
                        }
                    }
                    anyError = reason
                    logTerminal("ERROR", "❌ Failed [${item.label}] ${item.topicName} -> $reason")
                    item.copy(
                        status = TopicConnectionStatus.FAILED,
                        httpCode = restResult?.httpCode,
                        latencyMs = restResult?.latencyMs,
                        sdkVerified = false,
                        errorMessage = reason
                    )
                }
            }

            updatedTopics[i] = finalState
            if (finalState.status == TopicConnectionStatus.CONNECTED && item.key.startsWith("course_")) {
                previouslySubscribed.add(item.topicName)
            }
            _connectionState.update { it.copy(topics = updatedTopics.toList()) }
        }

        // Persist tracked course subscription state
        if (activeProgramId.isNotBlank()) {
            sessionManager.setSubscribedFcmProgramId(activeProgramId)
            if (activePhaseId.isNotBlank()) {
                sessionManager.setSubscribedFcmPhaseId(activePhaseId)
            }
            sessionManager.setSubscribedCourseTopics(previouslySubscribed)
        }

        val connectedCount = updatedTopics.count { it.status == TopicConnectionStatus.CONNECTED }
        val activeCount = updatedTopics.count { it.status != TopicConnectionStatus.SKIPPED }
        val finishedAt = nowTimeString()

        _connectionState.update {
            it.copy(
                isSyncing = false,
                topics = updatedTopics.toList(),
                lastSyncedAt = finishedAt,
                lastError = anyError
            )
        }

        if (connectedCount > 0 && connectedCount == activeCount) {
            logTerminal("SUCCESS", "🟢 Shikho Notification Connected ($connectedCount/$activeCount active topics verified)")
        } else if (connectedCount > 0) {
            logTerminal("WARN", "🟡 Partial Shikho Connection ($connectedCount/$activeCount topics connected)")
        } else {
            logTerminal("ERROR", "🔴 Shikho Notification Disconnected (0/$activeCount topics connected)")
        }
    }

    private fun buildExpectedTopics(
        userId: String,
        programId: String,
        phaseId: String
    ): List<TopicSubscriptionState> {
        val list = mutableListOf<TopicSubscriptionState>()

        // 1. Global topic: SHIKHO_ALL
        list.add(
            TopicSubscriptionState(
                key = "global_all",
                label = "Global Broadcast",
                topicName = TOPIC_SHIKHO_ALL,
                status = TopicConnectionStatus.IDLE
            )
        )

        // 2. Direct User topic: {userId}
        if (userId.isNotBlank()) {
            list.add(
                TopicSubscriptionState(
                    key = "user_direct",
                    label = "User Direct",
                    topicName = userId,
                    status = TopicConnectionStatus.IDLE
                )
            )
        } else {
            list.add(
                TopicSubscriptionState(
                    key = "user_direct",
                    label = "User Direct",
                    topicName = "{userId}",
                    status = TopicConnectionStatus.SKIPPED,
                    errorMessage = "Shikho User ID পাওয়া যায়নি (লগইন করা নেই)"
                )
            )
        }

        // 3. Course User topic: LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}
        if (userId.isNotBlank() && programId.isNotBlank()) {
            list.add(
                TopicSubscriptionState(
                    key = "course_user",
                    label = "Course Live (User)",
                    topicName = "LIVE_ShikhoNotification_UserID_${userId}_Program_${programId}",
                    status = TopicConnectionStatus.IDLE
                )
            )
        } else {
            list.add(
                TopicSubscriptionState(
                    key = "course_user",
                    label = "Course Live (User)",
                    topicName = "LIVE_ShikhoNotification_UserID_{user}_Program_{prog}",
                    status = TopicConnectionStatus.SKIPPED,
                    errorMessage = "কোর্স বা ইউজার সিলেক্ট করা নেই"
                )
            )
        }

        // 4. Course Phase topic: LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}
        if (programId.isNotBlank() && phaseId.isNotBlank()) {
            list.add(
                TopicSubscriptionState(
                    key = "course_phase",
                    label = "Course Phase Live",
                    topicName = "LIVE_ShikhoNotification_Program_${programId}_Phase_${phaseId}",
                    status = TopicConnectionStatus.IDLE
                )
            )
        } else {
            list.add(
                TopicSubscriptionState(
                    key = "course_phase",
                    label = "Course Phase Live",
                    topicName = "LIVE_ShikhoNotification_Program_{prog}_Phase_{phase}",
                    status = TopicConnectionStatus.SKIPPED,
                    errorMessage = "কোর্সের ফেজ (Phase ID) এখনো লোড হয়নি"
                )
            )
        }

        return list
    }

    private fun markAllActiveTopicsFailed(reason: String) {
        _connectionState.update { state ->
            state.copy(
                topics = state.topics.map { topic ->
                    if (topic.status == TopicConnectionStatus.SKIPPED) topic
                    else topic.copy(status = TopicConnectionStatus.FAILED, errorMessage = reason)
                }
            )
        }
    }

    /**
     * Executes the exact `fcmregistrations.googleapis.com` topic subscription REST endpoint observed
     * in the Shikho Android app:
     * `POST https://fcmregistrations.googleapis.com/v1/projects/{projectId}/registrations/{fid}/topicSubscriptions/{topic}:{subscribe|unsubscribe}`
     */
    private fun callFcmRegistrationTopicAction(
        projectId: String,
        apiKey: String,
        fid: String,
        fisAuthToken: String,
        topic: String,
        action: String
    ): FcmRestResult {
        val encodedTopic = Uri.encode(topic)
        val url = "$FCM_REGISTRATIONS_BASE_URL/projects/$projectId/registrations/$fid/topicSubscriptions/$encodedTopic:$action"
        val startedNs = System.nanoTime()

        return try {
            val emptyBody = "".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("x-goog-firebase-installations-auth", fisAuthToken)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(emptyBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
                val bodyText = response.body?.string().orEmpty().trim()
                if (response.isSuccessful) {
                    val json = runCatching { JSONObject(bodyText) }.getOrNull()
                    FcmRestResult(
                        success = true,
                        httpCode = response.code,
                        topicName = json?.optString("topicName", topic)?.takeIf { it.isNotBlank() } ?: topic,
                        createTime = json?.optString("createTime", "")?.takeIf { it.isNotBlank() },
                        latencyMs = elapsedMs
                    )
                } else {
                    val errMsg = runCatching {
                        val json = JSONObject(bodyText)
                        val errObj = json.optJSONObject("error")
                        val status = errObj?.optString("status", "").orEmpty()
                        val msg = errObj?.optString("message", "").orEmpty()
                        listOf(status, msg).filter { it.isNotBlank() }.joinToString(" - ")
                    }.getOrNull().takeIf { !it.isNullOrBlank() }
                        ?: response.message.ifBlank { bodyText.take(140) }

                    FcmRestResult(
                        success = false,
                        httpCode = response.code,
                        latencyMs = elapsedMs,
                        errorMessage = errMsg
                    )
                }
            }
        } catch (e: Throwable) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
            FcmRestResult(
                success = false,
                httpCode = -1,
                latencyMs = elapsedMs,
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private suspend fun unsubscribeTopicReal(
        fcm: FirebaseMessaging?,
        gmsAvailable: Boolean,
        projectId: String,
        apiKey: String,
        fid: String,
        fisAuthToken: String,
        topic: String
    ) {
        if (fcm != null && gmsAvailable) {
            val res = awaitTaskResult(fcm.unsubscribeFromTopic(topic))
            res.onSuccess {
                logTerminal("INFO", "🔄 SDK Unsubscribed old topic: $topic")
            }.onFailure { err ->
                logTerminal("WARN", "⚠️ SDK Unsubscribe warning ($topic): ${err.message}")
            }
        }
        if (projectId.isNotBlank() && apiKey.isNotBlank() && fid.isNotBlank() && fisAuthToken.isNotBlank()) {
            val rest = callFcmRegistrationTopicAction(
                projectId = projectId,
                apiKey = apiKey,
                fid = fid,
                fisAuthToken = fisAuthToken,
                topic = topic,
                action = "unsubscribe"
            )
            if (rest.success) {
                logTerminal("HTTP", "POST .../topicSubscriptions/$topic:unsubscribe -> ${rest.httpCode} OK (${rest.latencyMs}ms)")
            }
        }
    }

    private suspend fun <T> awaitTaskResult(task: com.google.android.gms.tasks.Task<T>): Result<T?> {
        return suspendCancellableCoroutine { cont ->
            task.addOnCompleteListener { completed ->
                if (!cont.isActive) return@addOnCompleteListener
                if (completed.isSuccessful) {
                    cont.resume(Result.success(completed.result))
                } else {
                    cont.resume(Result.failure(completed.exception ?: IllegalStateException("Task failed")))
                }
            }
        }
    }

    /**
     * Dynamically switches or updates the subscribed course (`newProgramId`) and phase (`newPhaseId`)
     * on Shikho's FCM server (`projects/shikho-tech`).
     * - Immediately unsubscribes (`:unsubscribe`) from any previous course or phase topics.
     * - Immediately subscribes (`:subscribe`) to the newly selected course and phase topics.
     */
    @Synchronized
    fun updateProgramAndPhase(context: Context, newProgramId: String, newPhaseId: String?) {
        val cleanProgramId = newProgramId.trim()
        if (cleanProgramId.isBlank()) return
        val cleanPhaseId = newPhaseId?.trim()?.takeIf { it.isNotBlank() }

        try {
            val appContext = context.applicationContext
            val sessionManager = SessionManager.getInstance(appContext)
            val oldProgramId = sessionManager.getSubscribedFcmProgramId()?.trim()
            val oldPhaseId = sessionManager.getSubscribedFcmPhaseId()?.trim()

            val effectivePhaseId = when {
                !cleanPhaseId.isNullOrBlank() -> cleanPhaseId
                oldProgramId == cleanProgramId && !oldPhaseId.isNullOrBlank() -> oldPhaseId
                else -> null
            }

            if (!isGooglePlayServicesAvailable(appContext)) {
                sessionManager.setSubscribedFcmProgramId(cleanProgramId)
                if (effectivePhaseId != null) {
                    sessionManager.setSubscribedFcmPhaseId(effectivePhaseId)
                }
            }

            scope.launch {
                runFullShikhoFcmSync(
                    appContext = appContext,
                    forceRefreshFisToken = false,
                    overrideProgramId = cleanProgramId,
                    overridePhaseId = effectivePhaseId
                )
            }
        } catch (e: Throwable) {
            Log.i(TAG, "Program topic update warning: ${e.message}")
            logTerminal("WARN", "⚠️ Program topic update warning: ${e.message}")
        }
    }

    /**
     * Unsubscribes only from the active course & phase topics (e.g., when changing syllabus/class
     * or when no course is currently selected) while keeping `SHIKHO_ALL` and `{userId}`.
     */
    @Synchronized
    fun unsubscribeActiveCourseTopics(context: Context) {
        try {
            val appContext = context.applicationContext
            val sessionManager = SessionManager.getInstance(appContext)
            val userId = sessionManager.getUserId()?.trim()
            val oldProgramId = sessionManager.getSubscribedFcmProgramId()?.trim()
            val oldPhaseId = sessionManager.getSubscribedFcmPhaseId()?.trim()
            val topicsToRemove = sessionManager.getSubscribedCourseTopics().toMutableSet()

            if (!oldProgramId.isNullOrBlank()) {
                if (!userId.isNullOrBlank()) {
                    topicsToRemove.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${oldProgramId}")
                }
                if (!oldPhaseId.isNullOrBlank()) {
                    topicsToRemove.add("LIVE_ShikhoNotification_Program_${oldProgramId}_Phase_${oldPhaseId}")
                }
            }

            if (isGooglePlayServicesAvailable(appContext)) {
                val fcm = try { FirebaseMessaging.getInstance() } catch (_: Throwable) { null }
                if (fcm != null) {
                    for (topic in topicsToRemove) {
                        try {
                            fcm.unsubscribeFromTopic(topic)
                                .addOnSuccessListener {
                                    logTerminal("INFO", "🔄 Unsubscribed course topic: $topic")
                                }
                                .addOnFailureListener { err ->
                                    logTerminal("WARN", "⚠️ Unsubscribe failed ($topic): ${err.message}")
                                }
                        } catch (_: Throwable) {}
                    }
                }
            }

            sessionManager.setSubscribedFcmProgramId(null)
            sessionManager.setSubscribedFcmPhaseId(null)
            sessionManager.setSubscribedCourseTopics(emptySet())
        } catch (e: Throwable) {
            Log.i(TAG, "unsubscribeActiveCourseTopics warning: ${e.message}")
        }
    }

    /**
     * Unsubscribes from user and course-specific topics upon logout
     */
    @Synchronized
    fun unsubscribeAll(context: Context) {
        try {
            val appContext = context.applicationContext
            val sessionManager = SessionManager.getInstance(appContext)
            val userId = sessionManager.getUserId()?.trim()

            unsubscribeActiveCourseTopics(appContext)

            if (isGooglePlayServicesAvailable(appContext) && !userId.isNullOrBlank()) {
                val fcm = try { FirebaseMessaging.getInstance() } catch (_: Throwable) { null }
                try {
                    fcm?.unsubscribeFromTopic(userId)
                        ?.addOnSuccessListener {
                            logTerminal("INFO", "🔄 Unsubscribed user topic on logout: $userId")
                        }
                } catch (_: Throwable) {}
            }
            Log.d(TAG, "Unsubscribed from personalized topics on logout.")
        } catch (e: Throwable) {
            Log.i(TAG, "Optional unsubscribeFromTopics: ${e.message}")
        }
    }
}
