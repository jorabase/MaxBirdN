package com.example.notification

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.auth.SessionManager
import com.example.security.DeviceActivationRepository
import com.example.security.DeviceSecurityManager
import com.example.security.applyAntiCaptureSecurity
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages OneSignal + Firebase Cloud Messaging (FCM) two-way push notifications:
 * 1. Registers the student's Android device with OneSignal (linking FCM token, `onesignal_player_id`,
 *    `external_user_id`, and tags: `device_hash`, `display_device_id`, `student_phone`, `student_key`).
 * 2. Syncs `onesignal_player_id` and `fcm_token` to Supabase (`devices` and `course_access_enrollments`)
 *    so the Admin App and Supabase Triggers can send targeted push notifications to this exact device.
 * 3. Sends instant push notifications to the Admin App (`role = admin` / `ADMIN_MASTER`) whenever a
 *    student requests device access, requests course access, starts a free trial, or submits a payment.
 */
object OneSignalPushManager {

    private const val TAG = "OneSignalPushMgr"
    private const val PREFS_NAME = "maxbird_onesignal_prefs"
    private const val KEY_PLAYER_ID = "onesignal_player_id"
    private const val KEY_CACHED_APP_ID = "cached_onesignal_app_id"
    private const val KEY_LAST_SYNCED_FCM = "last_synced_fcm_token"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    @Volatile
    private var lastSyncedSignature: String = ""
    @Volatile
    private var lastSyncedAtMs: Long = 0L

    private val httpClient = OkHttpClient.Builder()
        .applyAntiCaptureSecurity()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedPlayerId(context: Context): String {
        return try {
            getPrefs(context).getString(KEY_PLAYER_ID, "") ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Resolves the OneSignal App ID from BuildConfig (GitHub Actions Secret / .env)
     * or falls back to cached Supabase configuration if set in `onesignal_config`.
     */
    fun getOneSignalAppId(context: Context): String {
        val fromBuildConfig = try {
            BuildConfig.ONESIGNAL_APP_ID.trim().removeSurrounding("\"").removeSurrounding("'")
        } catch (_: Throwable) {
            ""
        }
        if (fromBuildConfig.isNotBlank() && !fromBuildConfig.contains("your-onesignal", ignoreCase = true)) {
            return fromBuildConfig
        }
        return try {
            getPrefs(context).getString(KEY_CACHED_APP_ID, "")?.trim().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Initializes OneSignal & FCM token acquisition on app launch.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            fetchRemoteOneSignalConfigIfNeeded(appContext)
            acquireFcmAndSync(appContext)
        }
    }

    @Volatile
    private var lastSyncTriggerMs: Long = 0L
    private const val SYNC_THROTTLE_MS = 30_000L

    /**
     * Triggers an asynchronous sync of the student's current device, phone number, and FCM token
     * with OneSignal and Supabase. Throttled to avoid duplicate burst calls on app startup/resume.
     */
    fun triggerSyncAsync(
        context: Context,
        studentNameOverride: String? = null,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && studentNameOverride.isNullOrBlank() && (now - lastSyncTriggerMs) < SYNC_THROTTLE_MS) {
            return
        }
        lastSyncTriggerMs = now
        val appContext = context.applicationContext
        scope.launch {
            acquireFcmAndSync(appContext, studentNameOverride = studentNameOverride)
        }
    }

    private suspend fun fetchRemoteOneSignalConfigIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext

            // Fetch public OneSignal App ID via secure RPC (never exposes REST API Key)
            val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_onesignal_public_app_id"
            val rpcReq = Request.Builder()
                .url(rpcUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(rpcReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string()?.trim().orEmpty()
                    if (bodyStr.startsWith("{")) {
                        val obj = JSONObject(bodyStr)
                        val remoteAppId = obj.optString("onesignal_app_id", "").trim()
                        if (remoteAppId.isNotBlank()) {
                            getPrefs(context).edit().putString(KEY_CACHED_APP_ID, remoteAppId).apply()
                            return@withContext
                        }
                    } else if (bodyStr.startsWith("\"") && bodyStr.endsWith("\"")) {
                        val remoteAppId = bodyStr.removeSurrounding("\"").trim()
                        if (remoteAppId.isNotBlank()) {
                            getPrefs(context).edit().putString(KEY_CACHED_APP_ID, remoteAppId).apply()
                            return@withContext
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private const val MAXBIRD_FIREBASE_APP_NAME = "maxbird-notification"
    private const val MAXBIRD_PROJECT_ID = "maxbird-notification"
    private const val MAXBIRD_APP_ID = "1:915326183735:android:9d6e6c275d9ede207992b2"
    private const val MAXBIRD_SENDER_ID = "915326183735"
    private const val MAXBIRD_API_KEY_B64 = "QUl6YVN5QmM0SmgyRG1ZRFZaWDQzVlVEVnBQVVJ4ZXVqSDE3TGpN"

    private fun getOrInitMaxBirdFirebaseApp(context: Context): com.google.firebase.FirebaseApp? {
        return try {
            val appContext = context.applicationContext
            val existingApps = com.google.firebase.FirebaseApp.getApps(appContext)
            if (existingApps.isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(appContext)
            }
            val existingMaxBird = existingApps.firstOrNull { it.name == MAXBIRD_FIREBASE_APP_NAME }
            if (existingMaxBird != null) {
                return existingMaxBird
            }
            val apiKey = String(android.util.Base64.decode(MAXBIRD_API_KEY_B64, android.util.Base64.DEFAULT)).trim()
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setProjectId(MAXBIRD_PROJECT_ID)
                .setApplicationId(MAXBIRD_APP_ID)
                .setGcmSenderId(MAXBIRD_SENDER_ID)
                .setApiKey(apiKey)
                .setStorageBucket("maxbird-notification.firebasestorage.app")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(appContext, options, MAXBIRD_FIREBASE_APP_NAME)
        } catch (e: Throwable) {
            try {
                com.google.firebase.FirebaseApp.getInstance(MAXBIRD_FIREBASE_APP_NAME)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun getFirebaseMessagingForApp(firebaseApp: com.google.firebase.FirebaseApp?): FirebaseMessaging? {
        if (firebaseApp == null) return null
        return try {
            firebaseApp.get(FirebaseMessaging::class.java)
        } catch (_: Throwable) {
            try {
                val method = FirebaseMessaging::class.java.getDeclaredMethod(
                    "getInstance",
                    com.google.firebase.FirebaseApp::class.java
                )
                method.isAccessible = true
                method.invoke(null, firebaseApp) as? FirebaseMessaging
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun acquireFcmAndSync(context: Context, studentNameOverride: String? = null) {
        try {
            val maxBirdApp = getOrInitMaxBirdFirebaseApp(context)
            val maxBirdFcm = getFirebaseMessagingForApp(maxBirdApp)
            val defaultFcm = try { FirebaseMessaging.getInstance() } catch (_: Throwable) { null }

            // Also ensure default (shikho-tech) FCM token is initialized for Shikho topic subscriptions
            try {
                defaultFcm?.isAutoInitEnabled = true
                defaultFcm?.token?.addOnCompleteListener { defaultTask ->
                    if (defaultTask.isSuccessful && !defaultTask.result.isNullOrBlank()) {
                        SessionManager(context).setFcmToken(defaultTask.result)
                    }
                }
            } catch (_: Throwable) {}

            val targetFcm = maxBirdFcm ?: defaultFcm
            if (targetFcm != null) {
                try {
                    targetFcm.isAutoInitEnabled = true
                } catch (_: Throwable) {}
                targetFcm.token.addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result else null
                    if (!token.isNullOrBlank()) {
                        getPrefs(context).edit().putString(KEY_LAST_SYNCED_FCM, token).apply()
                        scope.launch {
                            syncStudentDeviceWithOneSignal(
                                context = context,
                                fcmToken = token,
                                studentNameOverride = studentNameOverride
                            )
                        }
                    } else {
                        // Fallback to default FCM token if secondary token task didn't return
                        defaultFcm?.token?.addOnCompleteListener { defTask ->
                            val fallbackToken = if (defTask.isSuccessful && !defTask.result.isNullOrBlank()) {
                                defTask.result
                            } else {
                                getPrefs(context).getString(KEY_LAST_SYNCED_FCM, null)
                                    ?: SessionManager(context).getFcmToken().orEmpty()
                            }
                            scope.launch {
                                syncStudentDeviceWithOneSignal(
                                    context = context,
                                    fcmToken = fallbackToken,
                                    studentNameOverride = studentNameOverride
                                )
                            }
                        } ?: scope.launch {
                            syncStudentDeviceWithOneSignal(
                                context = context,
                                fcmToken = getPrefs(context).getString(KEY_LAST_SYNCED_FCM, null)
                                    ?: SessionManager(context).getFcmToken().orEmpty(),
                                studentNameOverride = studentNameOverride
                            )
                        }
                    }
                }
            } else {
                scope.launch {
                    syncStudentDeviceWithOneSignal(
                        context = context,
                        fcmToken = getPrefs(context).getString(KEY_LAST_SYNCED_FCM, null)
                            ?: SessionManager(context).getFcmToken().orEmpty(),
                        studentNameOverride = studentNameOverride
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "acquireFcmAndSync warning: ${e.message}")
            scope.launch {
                syncStudentDeviceWithOneSignal(
                    context = context,
                    fcmToken = getPrefs(context).getString(KEY_LAST_SYNCED_FCM, null)
                        ?: SessionManager(context).getFcmToken().orEmpty(),
                    studentNameOverride = studentNameOverride
                )
            }
        }
    }

    /**
     * Registers or updates this Android device in OneSignal and stores the `onesignal_player_id`
     * and `fcm_token` in Supabase (`devices` and `course_access_enrollments`).
     */
    suspend fun syncStudentDeviceWithOneSignal(
        context: Context,
        fcmToken: String? = null,
        studentNameOverride: String? = null
    ) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                fetchRemoteOneSignalConfigIfNeeded(context)

                val session = SessionManager(context)
                val resolvedFcm = (fcmToken ?: session.getFcmToken() ?: "").trim()
                val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
                val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
                val normPhone = session.getNormalizedUserPhone()
                val userId = (session.getUserId() ?: "").trim()
                val studentName = (studentNameOverride?.trim()?.takeIf { it.isNotBlank() }
                    ?: session.getUserFullName()?.trim()?.takeIf { it.isNotBlank() }
                    ?: DeviceActivationRepository.getSavedStudentName(context).trim())
                    .ifBlank { "শিক্ষার্থী" }

                // Primary external_user_id is the student's 11-digit phone if logged in, otherwise deviceHardwareHash
                val externalUserId = normPhone.ifBlank { deviceHardwareHash }
                val appId = getOneSignalAppId(context)

                var playerId = getSavedPlayerId(context)
                val currentSignature = "$appId|$resolvedFcm|$externalUserId|$studentName"
                val nowMs = System.currentTimeMillis()
                if (playerId.isNotBlank() && currentSignature == lastSyncedSignature && (nowMs - lastSyncedAtMs) < 15_000L) {
                    ShikhoNotificationManager.updateOneSignalStatus(playerId, externalUserId)
                    return@withLock
                }

                // 1. Register / Update with OneSignal Players API if App ID & FCM token are available
            if (appId.isNotBlank() && resolvedFcm.isNotBlank()) {
                val tagsJson = JSONObject().apply {
                    put("role", "student")
                    put("is_admin", "false")
                    put("device_hash", deviceHardwareHash)
                    put("display_device_id", displayDeviceId)
                    put("student_name", studentName)
                    if (normPhone.isNotBlank()) {
                        put("student_phone", normPhone)
                        put("student_key", normPhone)
                    } else {
                        put("student_key", deviceHardwareHash)
                    }
                    if (userId.isNotBlank()) {
                        put("shikho_user_id", userId)
                    }
                }

                val playerPayload = JSONObject().apply {
                    put("app_id", appId)
                    put("device_type", 1) // 1 = Android
                    put("identifier", resolvedFcm)
                    put("notification_types", 1) // 1 = Subscribed
                    put("external_user_id", externalUserId)
                    put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    put("device_os", Build.VERSION.RELEASE ?: "Android")
                    put("game_version", try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0" })
                    put("tags", tagsJson)
                }

                try {
                    val req = Request.Builder()
                        .url("https://onesignal.com/api/v1/players")
                        .addHeader("Content-Type", "application/json")
                        .post(playerPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        val bodyStr = resp.body?.string() ?: ""
                        if (resp.isSuccessful && bodyStr.trim().startsWith("{")) {
                            val json = JSONObject(bodyStr)
                            val newPlayerId = json.optString("id", "").trim()
                            if (newPlayerId.isNotBlank()) {
                                    playerId = newPlayerId
                                    getPrefs(context).edit()
                                        .putString(KEY_PLAYER_ID, newPlayerId)
                                        .putString(KEY_LAST_SYNCED_FCM, resolvedFcm)
                                        .apply()
                                    lastSyncedSignature = currentSignature
                                    lastSyncedAtMs = System.currentTimeMillis()
                                    ShikhoNotificationManager.updateOneSignalStatus(newPlayerId, externalUserId)
                                    ShikhoNotificationManager.logTerminal(
                                        "SUCCESS",
                                        "🔔 OneSignal Device Registered (Player ID: ${newPlayerId.take(12)}..., Key: $externalUserId)"
                                    )
                                }
                            }
                        }

                        // Also ensure tags & external_user_id are updated on existing player_id
                        if (playerId.isNotBlank()) {
                            ShikhoNotificationManager.updateOneSignalStatus(playerId, externalUserId)
                            val putReq = Request.Builder()
                                .url("https://onesignal.com/api/v1/players/$playerId")
                                .addHeader("Content-Type", "application/json")
                                .put(playerPayload.toString().toRequestBody("application/json".toMediaType()))
                                .build()
                            runCatching { httpClient.newCall(putReq).execute().close() }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "OneSignal player registration warning: ${e.message}")
                    }
                }

            // 2. Sync `onesignal_player_id` and `fcm_token` to Supabase via RPC (with scoped fallback)
            syncPushIdentityToSupabase(
                deviceHardwareHash = deviceHardwareHash,
                displayDeviceId = displayDeviceId,
                studentPhone = normPhone,
                studentName = studentName,
                oneSignalPlayerId = playerId,
                fcmToken = resolvedFcm,
                studentKey = externalUserId
            )
            } catch (e: Throwable) {
                Log.w(TAG, "syncStudentDeviceWithOneSignal error: ${e.message}")
            }
        }
    }

    private fun syncPushIdentityToSupabase(
        deviceHardwareHash: String,
        displayDeviceId: String,
        studentPhone: String,
        studentName: String,
        oneSignalPlayerId: String,
        fcmToken: String,
        studentKey: String
    ) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return

            // 1. Primary: Call Supabase RPC `sync_student_push_identity`
            val currentAppVersion = try { BuildConfig.VERSION_NAME.trim() } catch (_: Throwable) { "" }
            var rpcSucceeded = false
            try {
                val rpcPayload = JSONObject().apply {
                    put("p_device_hash", deviceHardwareHash)
                    put("p_display_device_id", displayDeviceId)
                    put("p_student_phone", studentPhone)
                    put("p_student_key", studentKey)
                    put("p_onesignal_player_id", oneSignalPlayerId)
                    put("p_fcm_token", fcmToken)
                    if (currentAppVersion.isNotBlank()) {
                        put("p_app_version", currentAppVersion)
                    }
                }
                val rpcReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/sync_student_push_identity")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(rpcReq).execute().use { resp ->
                    rpcSucceeded = resp.isSuccessful
                }
            } catch (_: Throwable) {}

            if (rpcSucceeded) return

            // 2. Fallback: Scoped PATCH on `devices` table only if RPC is unavailable
            try {
                val devPatch = JSONObject().apply {
                    put("device_id", deviceHardwareHash)
                    if (oneSignalPlayerId.isNotBlank()) put("onesignal_player_id", oneSignalPlayerId)
                    if (fcmToken.isNotBlank()) put("fcm_token", fcmToken)
                    if (currentAppVersion.isNotBlank()) put("app_version", currentAppVersion)
                    if (studentName.isNotBlank()) {
                        put("student_name", studentName)
                        put("student_note", studentName)
                    }
                    if (studentPhone.isNotBlank()) {
                        put("student_phone", studentPhone)
                    }
                }
                val devReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/devices?device_hash=eq.$deviceHardwareHash")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(devPatch.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(devReq).execute().close()
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // PUSH NOTIFICATION DISPATCHERS (STUDENT APP -> SUPABASE RPC -> ADMIN APP)
    // =========================================================================

    /**
     * 1. Notifies Admin App when a student requests Device Access on the Gate Screen.
     */
    fun notifyAdminDeviceAccessRequest(
        context: Context,
        studentName: String,
        displayDeviceId: String,
        deviceModel: String
    ) {
        val cleanName = studentName.trim().ifBlank { "নতুন শিক্ষার্থী" }
        val deviceHash = runCatching { DeviceSecurityManager.getDeviceHardwareHash(context) }.getOrDefault(displayDeviceId)
        val studentPhone = runCatching { SessionManager(context).getNormalizedUserPhone() }.getOrDefault("")
        val title = "🔔 নতুন ডিভাইস এক্সেস রিকোয়েস্ট!"
        val message = "$cleanName ($deviceModel • ID: $displayDeviceId) অ্যাপে প্রবেশের জন্য এক্সেস আবেদন করেছেন।"
        val data = JSONObject().apply {
            put("notification_type", "ADMIN_DEVICE_ACCESS_REQUEST")
            put("event_type", "DEVICE_ACCESS_REQUEST")
            put("type", "DEVICE_ACCESS_REQUEST")
            put("device_hash", deviceHash)
            put("student_name", cleanName)
            put("student_phone", studentPhone.ifBlank { "N/A" })
            put("display_device_id", displayDeviceId)
            put("device_model", deviceModel)
        }
        triggerSyncAsync(context, studentNameOverride = cleanName)
        sendPushToAdminAsync(context, title, message, data)
    }

    /**
     * 2. Notifies Admin App when a student requests Course Access ("কোর্সে এক্সেস পান").
     */
    fun notifyAdminCourseAccessRequest(
        context: Context,
        studentName: String,
        studentPhone: String,
        programTitle: String,
        programId: String
    ) {
        val cleanName = studentName.trim().ifBlank { "শিক্ষার্থী" }
        val title = "📚 নতুন কোর্স এক্সেস রিকোয়েস্ট!"
        val message = "$cleanName ($studentPhone) '$programTitle' কোর্সের দাম, কিস্তি ও ফ্রি ট্রায়াল এক্সেস চেয়েছেন।"
        val data = JSONObject().apply {
            put("notification_type", "ADMIN_COURSE_ACCESS_REQUEST")
            put("event_type", "COURSE_ACCESS_REQUEST")
            put("type", "COURSE_ACCESS_REQUEST")
            put("student_name", cleanName)
            put("student_phone", studentPhone)
            put("program_id", programId)
            put("program_title", programTitle)
        }
        triggerSyncAsync(context, studentNameOverride = cleanName)
        sendPushToAdminAsync(context, title, message, data)
    }

    /**
     * 3. Notifies Admin App when a student submits a Course Purchase or Installment Payment.
     */
    fun notifyAdminCoursePaymentSubmitted(
        context: Context,
        studentName: String,
        studentPhone: String,
        programTitle: String,
        paymentMethod: String,
        amount: Int,
        transactionId: String,
        planTitle: String
    ) {
        val cleanName = studentName.trim().ifBlank { "শিক্ষার্থী" }
        val title = "💳 নতুন কোর্স পেমেন্ট জমা হয়েছে (৳$amount)!"
        val message = "$cleanName ($studentPhone) '$programTitle' এর জন্য $paymentMethod এ ৳$amount পাঠিয়েছেন (TrxID: $transactionId)।"
        val data = JSONObject().apply {
            put("notification_type", "ADMIN_COURSE_PAYMENT_SUBMITTED")
            put("event_type", "COURSE_PAYMENT_SUBMITTED")
            put("type", "COURSE_PAYMENT_SUBMITTED")
            put("student_name", cleanName)
            put("student_phone", studentPhone)
            put("program_title", programTitle)
            put("payment_method", paymentMethod)
            put("amount", amount)
            put("transaction_id", transactionId)
            put("plan_title", planTitle)
        }
        triggerSyncAsync(context, studentNameOverride = cleanName)
        sendPushToAdminAsync(context, title, message, data)
    }

    /**
     * 4. Notifies Admin App when a student starts a Free Trial on a course.
     */
    fun notifyAdminFreeTrialStarted(
        context: Context,
        studentName: String,
        studentPhone: String,
        programTitle: String,
        durationLabelBn: String
    ) {
        val cleanName = studentName.trim().ifBlank { "শিক্ষার্থী" }
        val title = "🎁 স্টুডেন্ট ফ্রি ট্রায়াল শুরু করেছে!"
        val message = "$cleanName ($studentPhone) '$programTitle' কোর্সে $durationLabelBn ফ্রি ট্রায়াল চালু করেছেন।"
        val data = JSONObject().apply {
            put("notification_type", "ADMIN_FREE_TRIAL_STARTED")
            put("event_type", "FREE_TRIAL_STARTED")
            put("type", "FREE_TRIAL_STARTED")
            put("student_name", cleanName)
            put("student_phone", studentPhone)
            put("program_title", programTitle)
            put("duration_label", durationLabelBn)
        }
        triggerSyncAsync(context, studentNameOverride = cleanName)
        sendPushToAdminAsync(context, title, message, data)
    }

    /**
     * Dispatches admin notifications strictly via the server-side Supabase RPC `notify_admin_via_onesignal`
     * so that the master OneSignal REST API key is never compiled into or exposed by the student APK.
     */
    private fun sendPushToAdminAsync(
        context: Context,
        title: String,
        message: String,
        data: JSONObject
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                fetchRemoteOneSignalConfigIfNeeded(appContext)
                val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
                if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
                    val eventType = data.optString("event_type", "ADMIN_ALERT").ifBlank { "ADMIN_ALERT" }
                    val rpcPayload = JSONObject().apply {
                        put("p_title", title)
                        put("p_message", message)
                        put("p_event_type", eventType)
                        put("p_extra_data", data)
                    }
                    val rpcReq = Request.Builder()
                        .url("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/notify_admin_via_onesignal")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(rpcReq).execute().close()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "sendPushToAdminAsync error: ${e.message}")
            }
        }
    }
}
