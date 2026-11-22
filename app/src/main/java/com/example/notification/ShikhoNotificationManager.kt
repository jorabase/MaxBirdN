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
import com.example.auth.StoredEnrolledFcmCourse
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
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Manages real Firebase Cloud Messaging (FCM) topic subscriptions and notification channels
 * matching the authentic Shikho notification architecture (`projects/shikho-tech`):
 * 1. `SHIKHO_ALL` (Global broadcasts, campaigns, orientation live, routine reminders)
 * 2. `{userId}` (Personal account notifications)
 * 3. `LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}` (Auto-subscribed for ALL active enrolled courses)
 * 4. `LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}` (Auto-subscribed for ALL active courses' current phase)
 *
 * Smart Multi-Course Engine:
 * - Auto-subscribes ALL active (non-expired) enrolled courses & their active phases immediately upon login/enrollment load.
 * - Automatically unsubscribes (`:unsubscribe`) expired courses (`expiry_date` passed or `is_active == false`),
 *   ended/old phases, and all personal/course topics on logout.
 * - Uses real `FirebaseInstallations` (`FID` + `x-goog-firebase-installations-auth`), real `FirebaseMessaging` SDK,
 *   and real `fcmregistrations.googleapis.com` `:subscribe`/`:unsubscribe` calls. Never fakes or simulates success.
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

    data class EnrolledCourseFcmTarget(
        val programId: String,
        val titleBn: String = "",
        val phaseId: String? = null,
        val isActive: Boolean = true,
        val expiryDate: String? = null
    )

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
        val errorMessage: String? = null,
        val courseTitle: String? = null
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
        val activeCourseCount: Int = 0,
        val expiredCourseCount: Int = 0,
        val expiredCourseNames: List<String> = emptyList(),
        val autoUnsubscribedCount: Int = 0,
        val oneSignalConnected: Boolean = false,
        val oneSignalPlayerId: String = "",
        val oneSignalKey: String = "",
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

    private data class FirebaseCredentials(
        val firebaseApp: FirebaseApp?,
        val projectId: String,
        val appId: String,
        val apiKey: String,
        val fid: String,
        val fisAuthToken: String,
        val fcm: FirebaseMessaging?,
        val fcmToken: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val logIdSeq = AtomicLong(1L)

    // Session-scoped smart diff cache so automatic background syncs avoid redundant HTTP calls
    // for topics already verified CONNECTED (manual "আবার চেক ও কানেক্ট" always forces full live re-check).
    private var verifiedCacheIdentity: String = ""
    private val verifiedTopicCache = mutableMapOf<String, TopicSubscriptionState>()
    private val unsubscribedSessionTopics = mutableSetOf<String>()
    private var lastSyncedCredentials: FirebaseCredentials? = null
    private var lastCredentialsResolvedAtMs: Long = 0L

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
        _liveLogs.update { current -> (current + entry).takeLast(200) }
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

    fun updateOneSignalStatus(playerId: String, externalKey: String) {
        val cleanPlayerId = playerId.trim()
        val cleanKey = externalKey.trim()
        if (cleanPlayerId.isBlank()) return
        _connectionState.update { state ->
            state.copy(
                oneSignalConnected = true,
                oneSignalPlayerId = cleanPlayerId,
                oneSignalKey = cleanKey
            )
        }
    }

    /**
     * Parses ISO-8601 expiry timestamps from Shikho's `enrollment_details.expiry_date`
     * (e.g. "2026-09-30T17:59:59Z" or "2027-12-31T17:59:59.000Z") and returns true if expired.
     */
    fun isCourseExpired(
        isActive: Boolean?,
        expiryDate: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (isActive == false) return true
        val expiryMs = parseIsoTimestampMs(expiryDate) ?: return false
        return expiryMs < nowMs
    }

    private fun parseIsoTimestampMs(raw: String?): Long? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" to true,
            "yyyy-MM-dd'T'HH:mm:ss'Z'" to true,
            "yyyy-MM-dd'T'HH:mm:ssXXX" to false,
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" to false,
            "yyyy-MM-dd HH:mm:ss" to true,
            "yyyy-MM-dd" to true
        )
        for ((pattern, forceUtc) in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    if (forceUtc) timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(clean)
                if (parsed != null) return parsed.time
            } catch (_: Throwable) {
            }
        }
        return null
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
     * `SHIKHO_ALL`, `{userId}`, and all currently active enrolled courses & their active phases.
     * Automatically cleans up any course whose `expiry_date` has passed.
     */
    fun syncAllTopicSubscriptions(context: Context) {
        verifyAndSyncNow(context = context, forceRefreshFisToken = false)
    }

    /**
     * Smart Multi-Course Auto-Subscribe + Auto-Expiry Cleanup:
     * Immediately subscribes in the background to ALL active (non-expired) enrolled courses
     * and their active phases, while automatically unsubscribing any expired or un-enrolled courses.
     */
    fun syncEnrolledCourses(
        context: Context,
        enrolledCourses: List<EnrolledCourseFcmTarget>
    ) {
        val appContext = context.applicationContext
        scope.launch {
            runFullShikhoFcmSync(
                appContext = appContext,
                forceRefreshFisToken = false,
                incomingEnrolledCourses = enrolledCourses
            )
        }
    }

    /**
     * Triggers an immediate live diagnostic sync against Shikho's FCM registration backend
     * (`fcmregistrations.googleapis.com/v1/projects/{projectId}/registrations/{fid}/topicSubscriptions/{topic}:subscribe`)
     * and Android `FirebaseMessaging` SDK. Never fakes or simulates success.
     */
    fun verifyAndSyncNow(context: Context, forceRefreshFisToken: Boolean = true) {
        val appContext = context.applicationContext
        scope.launch {
            runFullShikhoFcmSync(
                appContext = appContext,
                forceRefreshFisToken = forceRefreshFisToken
            )
        }
    }

    private suspend fun resolveFirebaseCredentials(
        appContext: Context,
        sessionManager: SessionManager,
        gmsAvailable: Boolean,
        forceRefreshFisToken: Boolean
    ): FirebaseCredentials {
        val firebaseApp = try {
            FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext)
        } catch (e: Throwable) {
            logTerminal("ERROR", "❌ FirebaseApp init failed: ${e.message}")
            null
        }

        if (firebaseApp == null) {
            return FirebaseCredentials(
                firebaseApp = null,
                projectId = "",
                appId = "",
                apiKey = "",
                fid = "",
                fisAuthToken = "",
                fcm = null,
                fcmToken = ""
            )
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
                installationId = fid,
                fisAuthReady = fisAuthToken.isNotBlank(),
                fcmTokenPreview = if (fcmToken.length > 20) "${fcmToken.take(18)}...${fcmToken.takeLast(6)}" else fcmToken
            )
        }

        return FirebaseCredentials(
            firebaseApp = firebaseApp,
            projectId = projectId,
            appId = appId,
            apiKey = apiKey,
            fid = fid,
            fisAuthToken = fisAuthToken,
            fcm = fcm,
            fcmToken = fcmToken
        )
    }

    private suspend fun runFullShikhoFcmSync(
        appContext: Context,
        forceRefreshFisToken: Boolean,
        overrideProgramId: String? = null,
        overridePhaseId: String? = null,
        overrideCourseTitleBn: String? = null,
        incomingEnrolledCourses: List<EnrolledCourseFcmTarget>? = null
    ) = syncMutex.withLock {
        val sessionManager = SessionManager.getInstance(appContext)
        val gmsAvailable = isGooglePlayServicesAvailable(appContext)
        val userId = sessionManager.getUserId()?.trim().orEmpty()
        val selectedProgramId = overrideProgramId?.trim()?.takeIf { it.isNotBlank() }
            ?: sessionManager.getActiveProgramId()?.trim().orEmpty()
        val selectedPhaseId = overridePhaseId?.trim()?.takeIf { it.isNotBlank() }
            ?: sessionManager.getActiveProgramPhaseId()?.trim().orEmpty()

        val nowMs = System.currentTimeMillis()
        val cachedCoursesById = sessionManager.getActiveEnrolledFcmCourses().associateBy { it.programId }

        val activeCourses = mutableListOf<EnrolledCourseFcmTarget>()
        val expiredCourses = mutableListOf<EnrolledCourseFcmTarget>()

        if (incomingEnrolledCourses != null) {
            // Full enrollment refresh from Shikho API: separate active vs expired/inactive courses
            for (course in incomingEnrolledCourses.distinctBy { it.programId }) {
                val pid = course.programId.trim()
                if (pid.isBlank()) continue
                val cached = cachedCoursesById[pid]
                val resolvedPhase = course.phaseId?.trim()?.takeIf { it.isNotBlank() }
                    ?: cached?.phaseId?.takeIf { it.isNotBlank() }
                    ?: if (pid == selectedProgramId) selectedPhaseId.takeIf { it.isNotBlank() } else null
                val resolvedTitle = course.titleBn.trim().ifBlank { cached?.titleBn.orEmpty() }
                val merged = course.copy(
                    programId = pid,
                    titleBn = resolvedTitle,
                    phaseId = resolvedPhase
                )
                if (isCourseExpired(merged.isActive, merged.expiryDate, nowMs)) {
                    expiredCourses.add(merged)
                } else {
                    activeCourses.add(merged)
                }
            }
        } else {
            // Re-evaluate all previously stored enrolled courses against current time for expiry
            val workingMap = LinkedHashMap<String, EnrolledCourseFcmTarget>()
            for (stored in cachedCoursesById.values) {
                val target = EnrolledCourseFcmTarget(
                    programId = stored.programId,
                    titleBn = stored.titleBn,
                    phaseId = stored.phaseId,
                    isActive = stored.isActive,
                    expiryDate = stored.expiryDate
                )
                if (isCourseExpired(target.isActive, target.expiryDate, nowMs)) {
                    expiredCourses.add(target)
                } else {
                    workingMap[target.programId] = target
                }
            }

            // Merge/upsert currently selected program & phase WITHOUT removing other active courses
            if (selectedProgramId.isNotBlank()) {
                val existing = workingMap[selectedProgramId]
                val title = overrideCourseTitleBn?.trim()?.takeIf { it.isNotBlank() }
                    ?: existing?.titleBn?.takeIf { it.isNotBlank() }
                    ?: sessionManager.getActiveProgramTitleBn()?.trim().orEmpty()
                val phase = selectedPhaseId.takeIf { it.isNotBlank() } ?: existing?.phaseId
                workingMap[selectedProgramId] = EnrolledCourseFcmTarget(
                    programId = selectedProgramId,
                    titleBn = title,
                    phaseId = phase,
                    isActive = existing?.isActive ?: true,
                    expiryDate = existing?.expiryDate
                )
            }
            activeCourses.addAll(workingMap.values)
        }

        // Persist updated active course list to SessionManager
        sessionManager.setActiveEnrolledFcmCourses(
            activeCourses.map {
                StoredEnrolledFcmCourse(
                    programId = it.programId,
                    titleBn = it.titleBn,
                    phaseId = it.phaseId,
                    expiryDate = it.expiryDate,
                    isActive = it.isActive
                )
            }
        )

        val initialTopics = buildExpectedTopicsMultiCourse(
            userId = userId,
            activeCourses = activeCourses,
            selectedProgramId = selectedProgramId,
            selectedPhaseId = selectedPhaseId
        )

        val expiredNamesList = expiredCourses.map {
            it.titleBn.ifBlank { it.programId.takeLast(8) }
        }.distinct()

        // 1. Resolve real FirebaseApp + FID + FIS Auth JWT + FCM Token
        // Reuse recent credentials (< 5 min) during automatic syncs unless forceRefreshFisToken == true
        val nowCredMs = System.currentTimeMillis()
        val cachedCreds = lastSyncedCredentials
        val creds = if (!forceRefreshFisToken &&
            cachedCreds != null &&
            cachedCreds.firebaseApp != null &&
            cachedCreds.fid.isNotBlank() &&
            cachedCreds.fisAuthToken.isNotBlank() &&
            (nowCredMs - lastCredentialsResolvedAtMs) < 5 * 60 * 1000L
        ) {
            cachedCreds
        } else {
            resolveFirebaseCredentials(
                appContext = appContext,
                sessionManager = sessionManager,
                gmsAvailable = gmsAvailable,
                forceRefreshFisToken = forceRefreshFisToken
            ).also { resolved ->
                if (resolved.firebaseApp != null && resolved.fid.isNotBlank() && resolved.fisAuthToken.isNotBlank()) {
                    lastSyncedCredentials = resolved
                    lastCredentialsResolvedAtMs = System.currentTimeMillis()
                }
            }
        }

        if (creds.firebaseApp == null) {
            _connectionState.update { prev ->
                prev.copy(
                    isSyncing = false,
                    firebaseInitialized = false,
                    playServicesAvailable = gmsAvailable,
                    activeUserId = userId,
                    activeProgramId = selectedProgramId,
                    activePhaseId = selectedPhaseId,
                    activeCourseCount = activeCourses.size,
                    expiredCourseCount = expiredCourses.size,
                    expiredCourseNames = expiredNamesList,
                    topics = initialTopics.map { topic ->
                        if (topic.status == TopicConnectionStatus.SKIPPED) topic
                        else topic.copy(status = TopicConnectionStatus.FAILED, errorMessage = "FirebaseApp not initialized")
                    },
                    lastSyncedAt = nowTimeString(),
                    lastError = "FirebaseApp not initialized (check google-services.json)"
                )
            }
            return
        }

        // Invalidate session topic cache if user/FID changed or manual force-check was requested
        val currentIdentity = "${creds.fid}|$userId"
        if (forceRefreshFisToken || verifiedCacheIdentity != currentIdentity) {
            verifiedTopicCache.clear()
            if (verifiedCacheIdentity != currentIdentity) {
                unsubscribedSessionTopics.clear()
            }
            verifiedCacheIdentity = currentIdentity
        }

        // 2. Compute stale/expired topics to auto-unsubscribe (deduplicated per session!)
        val previouslySubscribed = sessionManager.getSubscribedCourseTopics().toMutableSet()
        val activeCourseTopics = initialTopics
            .filter { it.status != TopicConnectionStatus.SKIPPED && it.key.startsWith("course_") }
            .map { it.topicName }
            .toSet()

        val staleTopics = linkedSetOf<String>()
        for (oldTopic in previouslySubscribed) {
            if (oldTopic !in activeCourseTopics) {
                staleTopics.add(oldTopic)
            }
        }
        for (expired in expiredCourses) {
            if (userId.isNotBlank()) {
                val expUserTopic = "LIVE_ShikhoNotification_UserID_${userId}_Program_${expired.programId}"
                if (expUserTopic !in unsubscribedSessionTopics || forceRefreshFisToken) {
                    staleTopics.add(expUserTopic)
                }
            }
            if (!expired.phaseId.isNullOrBlank()) {
                val expPhaseTopic = "LIVE_ShikhoNotification_Program_${expired.programId}_Phase_${expired.phaseId}"
                if (expPhaseTopic !in unsubscribedSessionTopics || forceRefreshFisToken) {
                    staleTopics.add(expPhaseTopic)
                }
            }
        }
        // If a topic is back in active targets, remove it from unsubscribedSessionTopics
        unsubscribedSessionTopics.removeAll(activeCourseTopics)

        // Pre-fill already verified topics so UI never flickers on incremental updates
        val updatedTopics = initialTopics.map { item ->
            if (!forceRefreshFisToken && item.status != TopicConnectionStatus.SKIPPED) {
                val cached = verifiedTopicCache[item.topicName]
                if (cached != null && cached.status == TopicConnectionStatus.CONNECTED) {
                    item.copy(
                        status = TopicConnectionStatus.CONNECTED,
                        httpCode = cached.httpCode,
                        createTime = cached.createTime,
                        latencyMs = cached.latencyMs,
                        sdkVerified = cached.sdkVerified,
                        errorMessage = null
                    )
                } else item
            } else item
        }.toMutableList()

        val needsSubscribeCount = updatedTopics.count {
            it.status != TopicConnectionStatus.SKIPPED && it.status != TopicConnectionStatus.CONNECTED
        }

        // Fast-path: if NOTHING needs subscribing and NOTHING needs unsubscribing, update state cleanly without spamming logs/API!
        if (!forceRefreshFisToken && needsSubscribeCount == 0 && staleTopics.isEmpty()) {
            val savedOneSignalId = OneSignalPushManager.getSavedPlayerId(appContext)
            _connectionState.update { prev ->
                prev.copy(
                    isSyncing = false,
                    playServicesAvailable = gmsAvailable,
                    activeUserId = userId,
                    activeProgramId = selectedProgramId,
                    activePhaseId = selectedPhaseId,
                    activeCourseCount = activeCourses.size,
                    expiredCourseCount = expiredCourses.size,
                    expiredCourseNames = if (expiredNamesList.isNotEmpty()) expiredNamesList else prev.expiredCourseNames,
                    oneSignalConnected = prev.oneSignalConnected || savedOneSignalId.isNotBlank(),
                    oneSignalPlayerId = prev.oneSignalPlayerId.ifBlank { savedOneSignalId },
                    topics = updatedTopics.toList(),
                    lastSyncedAt = prev.lastSyncedAt.ifBlank { nowTimeString() },
                    lastError = null
                )
            }
            return
        }

        val savedOneSignalId = OneSignalPushManager.getSavedPlayerId(appContext)
        _connectionState.update { prev ->
            prev.copy(
                isSyncing = true,
                playServicesAvailable = gmsAvailable,
                activeUserId = userId,
                activeProgramId = selectedProgramId,
                activePhaseId = selectedPhaseId,
                activeCourseCount = activeCourses.size,
                expiredCourseCount = expiredCourses.size,
                expiredCourseNames = if (expiredNamesList.isNotEmpty()) expiredNamesList else prev.expiredCourseNames,
                oneSignalConnected = prev.oneSignalConnected || savedOneSignalId.isNotBlank(),
                oneSignalPlayerId = prev.oneSignalPlayerId.ifBlank { savedOneSignalId },
                topics = updatedTopics.toList(),
                lastError = null
            )
        }

        val syncModeTag = if (forceRefreshFisToken) "Live Full Verify" else "Smart Diff Sync"
        logTerminal(
            "INFO",
            "🚀 $syncModeTag | activeCourses=${activeCourses.size}, expired=${expiredCourses.size}, toSub=$needsSubscribeCount, toUnsub=${staleTopics.size}"
        )
        if (expiredNamesList.isNotEmpty() && staleTopics.isNotEmpty()) {
            logTerminal("WARN", "⏳ মেয়াদ শেষ / নিষ্ক্রিয় কোর্স শনাক্ত (${expiredNamesList.size}): ${expiredNamesList.joinToString(", ")}")
        }

        var unsubscribedNow = 0
        for (oldTopic in staleTopics) {
            logTerminal("WARN", "🗑️ Auto-unsubscribing expired/stale topic: $oldTopic")
            unsubscribeTopicReal(
                fcm = creds.fcm,
                gmsAvailable = gmsAvailable,
                projectId = creds.projectId,
                apiKey = creds.apiKey,
                fid = creds.fid,
                fisAuthToken = creds.fisAuthToken,
                topic = oldTopic
            )
            previouslySubscribed.remove(oldTopic)
            verifiedTopicCache.remove(oldTopic)
            unsubscribedSessionTopics.add(oldTopic)
            unsubscribedNow++
        }

        // 3. Subscribe & verify EVERY new/unverified active Shikho topic
        var anyError: String? = null

        for (i in updatedTopics.indices) {
            val item = updatedTopics[i]
            if (item.status == TopicConnectionStatus.SKIPPED) {
                logTerminal("WARN", "⏭️ Skipped [${item.label}]: ${item.errorMessage}")
                continue
            }
            if (!forceRefreshFisToken && item.status == TopicConnectionStatus.CONNECTED) {
                // Already verified CONNECTED in this session — skip redundant network call
                if (item.key.startsWith("course_")) {
                    previouslySubscribed.add(item.topicName)
                }
                continue
            }

            updatedTopics[i] = item.copy(status = TopicConnectionStatus.CONNECTING)
            _connectionState.update { it.copy(topics = updatedTopics.toList()) }

            // 3a. Android FirebaseMessaging SDK subscription (required for GMS delivery)
            var sdkOk = false
            var sdkErr: String? = null
            if (creds.fcm != null && gmsAvailable) {
                val sdkRes = awaitTaskResult(creds.fcm.subscribeToTopic(item.topicName))
                sdkRes.onSuccess {
                    sdkOk = true
                }.onFailure { err ->
                    sdkErr = err.message ?: err.javaClass.simpleName
                }
            } else if (!gmsAvailable) {
                sdkErr = "Google Play Services unavailable"
            }

            // 3b. Real fcmregistrations.googleapis.com REST :subscribe verification (exact Shikho protocol)
            var restResult: FcmRestResult? = null
            if (creds.projectId.isNotBlank() && creds.apiKey.isNotBlank() && creds.fid.isNotBlank() && creds.fisAuthToken.isNotBlank()) {
                restResult = callFcmRegistrationTopicAction(
                    projectId = creds.projectId,
                    apiKey = creds.apiKey,
                    fid = creds.fid,
                    fisAuthToken = creds.fisAuthToken,
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
            if (finalState.status == TopicConnectionStatus.CONNECTED) {
                verifiedTopicCache[item.topicName] = finalState
                if (item.key.startsWith("course_")) {
                    previouslySubscribed.add(item.topicName)
                }
            }
            _connectionState.update { it.copy(topics = updatedTopics.toList()) }
        }

        // Persist tracked subscription state
        if (selectedProgramId.isNotBlank()) {
            sessionManager.setSubscribedFcmProgramId(selectedProgramId)
            if (selectedPhaseId.isNotBlank()) {
                sessionManager.setSubscribedFcmPhaseId(selectedPhaseId)
            }
        }
        sessionManager.setSubscribedCourseTopics(previouslySubscribed)

        val connectedCount = updatedTopics.count { it.status == TopicConnectionStatus.CONNECTED }
        val activeCount = updatedTopics.count { it.status != TopicConnectionStatus.SKIPPED }
        val finishedAt = nowTimeString()

        _connectionState.update { prev ->
            prev.copy(
                isSyncing = false,
                activeCourseCount = activeCourses.size,
                expiredCourseCount = expiredCourses.size,
                expiredCourseNames = if (expiredNamesList.isNotEmpty()) expiredNamesList else prev.expiredCourseNames,
                autoUnsubscribedCount = unsubscribedSessionTopics.size,
                topics = updatedTopics.toList(),
                lastSyncedAt = finishedAt,
                lastError = anyError
            )
        }

        if (connectedCount > 0 && connectedCount == activeCount) {
            logTerminal(
                "SUCCESS",
                "🟢 Shikho Notification Connected ($connectedCount/$activeCount topics across ${activeCourses.size} active courses)"
            )
        } else if (connectedCount > 0) {
            logTerminal("WARN", "🟡 Partial Shikho Connection ($connectedCount/$activeCount topics connected)")
        } else {
            logTerminal("ERROR", "🔴 Shikho Notification Disconnected (0/$activeCount topics connected)")
        }
    }

    private fun buildExpectedTopicsMultiCourse(
        userId: String,
        activeCourses: List<EnrolledCourseFcmTarget>,
        selectedProgramId: String,
        selectedPhaseId: String
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

        // 3. Multi-course topics for EVERY active (non-expired) enrolled course
        val effectiveCourses = if (activeCourses.isNotEmpty()) {
            activeCourses
        } else if (selectedProgramId.isNotBlank()) {
            listOf(
                EnrolledCourseFcmTarget(
                    programId = selectedProgramId,
                    titleBn = "",
                    phaseId = selectedPhaseId.takeIf { it.isNotBlank() }
                )
            )
        } else {
            emptyList()
        }

        if (effectiveCourses.isEmpty()) {
            list.add(
                TopicSubscriptionState(
                    key = "course_none",
                    label = "Active Courses",
                    topicName = "LIVE_ShikhoNotification_UserID_{user}_Program_{prog}",
                    status = TopicConnectionStatus.SKIPPED,
                    errorMessage = "কোনো অ্যাক্টিভ এনরোল করা কোর্স পাওয়া যায়নি"
                )
            )
            return list
        }

        effectiveCourses.forEachIndexed { index, course ->
            val shortTitle = course.titleBn.trim().ifBlank { "Course #${index + 1}" }
            val resolvedPhaseId = course.phaseId?.trim()?.takeIf { it.isNotBlank() }
                ?: if (course.programId == selectedProgramId) selectedPhaseId.takeIf { it.isNotBlank() } else null

            // 3a. Course User topic
            if (userId.isNotBlank()) {
                list.add(
                    TopicSubscriptionState(
                        key = "course_user_${course.programId}",
                        label = "Course Live • ${shortTitle.take(22)}",
                        topicName = "LIVE_ShikhoNotification_UserID_${userId}_Program_${course.programId}",
                        status = TopicConnectionStatus.IDLE,
                        courseTitle = shortTitle
                    )
                )
            } else {
                list.add(
                    TopicSubscriptionState(
                        key = "course_user_${course.programId}",
                        label = "Course Live • ${shortTitle.take(22)}",
                        topicName = "LIVE_ShikhoNotification_UserID_{user}_Program_${course.programId}",
                        status = TopicConnectionStatus.SKIPPED,
                        errorMessage = "লগইন করা নেই",
                        courseTitle = shortTitle
                    )
                )
            }

            // 3b. Course Active Phase topic
            if (!resolvedPhaseId.isNullOrBlank()) {
                list.add(
                    TopicSubscriptionState(
                        key = "course_phase_${course.programId}",
                        label = "Phase Live • ${shortTitle.take(22)}",
                        topicName = "LIVE_ShikhoNotification_Program_${course.programId}_Phase_${resolvedPhaseId}",
                        status = TopicConnectionStatus.IDLE,
                        courseTitle = shortTitle
                    )
                )
            } else {
                list.add(
                    TopicSubscriptionState(
                        key = "course_phase_${course.programId}",
                        label = "Phase Live • ${shortTitle.take(22)}",
                        topicName = "LIVE_ShikhoNotification_Program_${course.programId}_Phase_{phase}",
                        status = TopicConnectionStatus.SKIPPED,
                        errorMessage = "এই কোর্সের অ্যাক্টিভ ফেজ ব্যাকগ্রাউন্ডে লোড হচ্ছে...",
                        courseTitle = shortTitle
                    )
                )
            }
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
                logTerminal("INFO", "🔄 SDK Unsubscribed topic: $topic")
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
            } else {
                logTerminal("WARN", "⚠️ REST :unsubscribe ($topic) -> HTTP ${rest.httpCode}: ${rest.errorMessage}")
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
     * Updates/ensures subscription for a specific active course (`newProgramId`) and its active phase (`newPhaseId`)
     * on Shikho's FCM server (`projects/shikho-tech`), while KEEPING all other active enrolled courses subscribed!
     * Old/ended phases of the same course are automatically unsubscribed.
     */
    @Synchronized
    fun updateProgramAndPhase(
        context: Context,
        newProgramId: String,
        newPhaseId: String?,
        courseTitleBn: String? = null
    ) {
        val cleanProgramId = newProgramId.trim()
        if (cleanProgramId.isBlank()) return
        val cleanPhaseId = newPhaseId?.trim()?.takeIf { it.isNotBlank() }

        try {
            val appContext = context.applicationContext
            val sessionManager = SessionManager.getInstance(appContext)
            val cachedCourse = sessionManager.getActiveEnrolledFcmCourses()
                .firstOrNull { it.programId == cleanProgramId }
            val effectivePhaseId = cleanPhaseId ?: cachedCourse?.phaseId

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
                    overridePhaseId = effectivePhaseId,
                    overrideCourseTitleBn = courseTitleBn
                )
            }
        } catch (e: Throwable) {
            Log.i(TAG, "Program topic update warning: ${e.message}")
            logTerminal("WARN", "⚠️ Program topic update warning: ${e.message}")
        }
    }

    /**
     * Unsubscribes from ALL active course & phase topics (e.g., when changing syllabus/class
     * or when no active courses remain) via BOTH FirebaseMessaging SDK and `fcmregistrations.googleapis.com`,
     * while keeping `SHIKHO_ALL` and `{userId}`.
     */
    @Synchronized
    fun unsubscribeActiveCourseTopics(context: Context) {
        val appContext = context.applicationContext
        val sessionManager = SessionManager.getInstance(appContext)
        val userId = sessionManager.getUserId()?.trim()
        val oldProgramId = sessionManager.getSubscribedFcmProgramId()?.trim()
            ?: sessionManager.getActiveProgramId()?.trim()
        val oldPhaseId = sessionManager.getSubscribedFcmPhaseId()?.trim()
            ?: sessionManager.getActiveProgramPhaseId()?.trim()
        val storedCourses = sessionManager.getActiveEnrolledFcmCourses()
        val topicsToRemove = sessionManager.getSubscribedCourseTopics().toMutableSet()

        if (!oldProgramId.isNullOrBlank()) {
            if (!userId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${oldProgramId}")
            }
            if (!oldPhaseId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_Program_${oldProgramId}_Phase_${oldPhaseId}")
            }
        }
        for (c in storedCourses) {
            if (!userId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${c.programId}")
            }
            if (!c.phaseId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_Program_${c.programId}_Phase_${c.phaseId}")
            }
        }

        sessionManager.setSubscribedFcmProgramId(null)
        sessionManager.setSubscribedFcmPhaseId(null)
        sessionManager.setSubscribedCourseTopics(emptySet())
        sessionManager.setActiveEnrolledFcmCourses(emptyList())

        if (topicsToRemove.isEmpty()) return

        scope.launch {
            syncMutex.withLock {
                val gmsAvailable = isGooglePlayServicesAvailable(appContext)
                val creds = resolveFirebaseCredentials(appContext, sessionManager, gmsAvailable, false)
                logTerminal("INFO", "🧹 Unsubscribing ${topicsToRemove.size} course topics...")
                for (topic in topicsToRemove) {
                    unsubscribeTopicReal(
                        fcm = creds.fcm,
                        gmsAvailable = gmsAvailable,
                        projectId = creds.projectId,
                        apiKey = creds.apiKey,
                        fid = creds.fid,
                        fisAuthToken = creds.fisAuthToken,
                        topic = topic
                    )
                }
                _connectionState.update { prev ->
                    prev.copy(
                        activeProgramId = "",
                        activePhaseId = "",
                        activeCourseCount = 0,
                        autoUnsubscribedCount = prev.autoUnsubscribedCount + topicsToRemove.size,
                        topics = prev.topics.filterNot { it.key.startsWith("course_") }
                    )
                }
            }
        }
    }

    /**
     * Unsubscribes from ALL user-specific and course/phase-specific topics upon logout
     * via BOTH Android `FirebaseMessaging` SDK and `fcmregistrations.googleapis.com` REST `:unsubscribe`.
     */
    @Synchronized
    fun unsubscribeAll(
        context: Context,
        userIdOverride: String? = null,
        topicsOverride: Set<String>? = null
    ) {
        val appContext = context.applicationContext
        val sessionManager = SessionManager.getInstance(appContext)
        val userId = userIdOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: sessionManager.getUserId()?.trim()

        val topicsToRemove = linkedSetOf<String>()
        if (!topicsOverride.isNullOrEmpty()) {
            topicsToRemove.addAll(topicsOverride)
        }
        topicsToRemove.addAll(sessionManager.getSubscribedCourseTopics())

        val oldProgramId = sessionManager.getSubscribedFcmProgramId()?.trim()
            ?: sessionManager.getActiveProgramId()?.trim()
        val oldPhaseId = sessionManager.getSubscribedFcmPhaseId()?.trim()
            ?: sessionManager.getActiveProgramPhaseId()?.trim()
        if (!oldProgramId.isNullOrBlank()) {
            if (!userId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${oldProgramId}")
            }
            if (!oldPhaseId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_Program_${oldProgramId}_Phase_${oldPhaseId}")
            }
        }
        for (c in sessionManager.getActiveEnrolledFcmCourses()) {
            if (!userId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${c.programId}")
            }
            if (!c.phaseId.isNullOrBlank()) {
                topicsToRemove.add("LIVE_ShikhoNotification_Program_${c.programId}_Phase_${c.phaseId}")
            }
        }
        if (!userId.isNullOrBlank()) {
            topicsToRemove.add(userId)
        }

        sessionManager.setSubscribedFcmProgramId(null)
        sessionManager.setSubscribedFcmPhaseId(null)
        sessionManager.setSubscribedCourseTopics(emptySet())
        sessionManager.setActiveEnrolledFcmCourses(emptyList())

        scope.launch {
            syncMutex.withLock {
                val gmsAvailable = isGooglePlayServicesAvailable(appContext)
                logTerminal("INFO", "🚪 লগআউট: ${topicsToRemove.size} টি ব্যক্তিগত ও কোর্স টপিক আনসাবস্ক্রাইব করা হচ্ছে...")
                val creds = resolveFirebaseCredentials(appContext, sessionManager, gmsAvailable, false)
                for (topic in topicsToRemove) {
                    unsubscribeTopicReal(
                        fcm = creds.fcm,
                        gmsAvailable = gmsAvailable,
                        projectId = creds.projectId,
                        apiKey = creds.apiKey,
                        fid = creds.fid,
                        fisAuthToken = creds.fisAuthToken,
                        topic = topic
                    )
                }
                verifiedTopicCache.clear()
                unsubscribedSessionTopics.clear()
                lastSyncedCredentials = null
                lastCredentialsResolvedAtMs = 0L
                _connectionState.update { prev ->
                    prev.copy(
                        isSyncing = false,
                        activeUserId = "",
                        activeProgramId = "",
                        activePhaseId = "",
                        activeCourseCount = 0,
                        expiredCourseCount = 0,
                        expiredCourseNames = emptyList(),
                        autoUnsubscribedCount = prev.autoUnsubscribedCount + topicsToRemove.size,
                        topics = emptyList(),
                        lastSyncedAt = nowTimeString()
                    )
                }
                logTerminal("SUCCESS", "✅ লগআউট ক্লিনআপ সম্পন্ন: সব ব্যক্তিগত ও কোর্স টপিক আনসাবস্ক্রাইব হয়েছে")
            }
        }
    }
}
