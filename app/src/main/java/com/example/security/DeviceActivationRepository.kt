package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class DeviceStatusResult {
    object Allowed : DeviceStatusResult()
    data class Banned(val reason: String) : DeviceStatusResult()
    data class Error(val message: String) : DeviceStatusResult()
}

sealed class HeartbeatResult {
    object Valid : HeartbeatResult()
    data class Revoked(val reason: String) : HeartbeatResult()
    data class NetworkError(val message: String) : HeartbeatResult()
}

sealed class StudentVerificationStatus {
    object NotRequested : StudentVerificationStatus()
    data class Pending(val studentName: String = "", val requestedAt: String = "") : StudentVerificationStatus()
    data class Approved(val message: String = com.example.utils.AppLang.tr("Your device is verified. You can enter the app now!", "আপনার ডিভাইসটি ভেরিফাইড রয়েছে। এখন অ্যাপে প্রবেশ করতে পারেন!")) : StudentVerificationStatus()
    data class Blocked(val reason: String) : StudentVerificationStatus()
    data class Error(val message: String) : StudentVerificationStatus()
}

object DeviceActivationRepository {

    private const val TAG = "DeviceActivation"
    private const val PREFS_FILE = "maxbird_device_activation"
    private const val PREFS_FILE_ENCRYPTED = "maxbird_device_activation_enc"
    private const val KEY_MIGRATED_TO_ENC = "key_migrated_to_enc_v1"
    private const val KEY_SIGNED_TOKEN = "key_signed_token"
    private const val KEY_ACTIVATED_AT = "key_activated_at"
    private const val KEY_LAST_HEARTBEAT_AT = "key_last_heartbeat_at"
    private const val KEY_CAN_VIEW_PROFILES = "key_can_view_profiles"
    private const val KEY_LAST_PROFILE_PERMISSION_CHECK = "key_last_profile_permission_check"
    private const val KEY_PENDING_REQUESTED_LOCAL = "key_pending_requested_local"
    private const val KEY_SAVED_STUDENT_NAME = "key_saved_student_name"

    private val prefsLock = Any()
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private val httpClient = com.example.network.SharedHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(prefsLock) {
            cachedPrefs?.let { return@synchronized it }
            val appCtx = context.applicationContext
            val resolved = try {
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val encPrefs = EncryptedSharedPreferences.create(
                    appCtx,
                    PREFS_FILE_ENCRYPTED,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                migrateLegacyPrefsIfNeeded(appCtx, encPrefs)
                encPrefs
            } catch (e: Throwable) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back safely: ${e.message}")
                try {
                    appCtx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                } catch (_: Throwable) {
                    appCtx.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
                }
            }
            cachedPrefs = resolved
            resolved
        }
    }

    private fun migrateLegacyPrefsIfNeeded(appCtx: Context, encPrefs: SharedPreferences) {
        try {
            if (encPrefs.getBoolean(KEY_MIGRATED_TO_ENC, false)) return
            val legacy = appCtx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val allEntries = legacy.all
            val editor = encPrefs.edit()
            if (allEntries.isNotEmpty()) {
                for ((key, value) in allEntries) {
                    if (encPrefs.contains(key)) continue
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Long -> editor.putLong(key, value)
                        is Int -> editor.putInt(key, value)
                        is Float -> editor.putFloat(key, value)
                    }
                }
            }
            editor.putBoolean(KEY_MIGRATED_TO_ENC, true).commit()
            if (allEntries.isNotEmpty()) {
                legacy.edit().clear().apply()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Legacy activation prefs migration skipped: ${e.message}")
        }
    }

    /**
     * Checks if Supabase URL and Anon Key are present in BuildConfig and sanitizes them.
     * Always enforces HTTPS so credentials and requests can never travel in cleartext.
     */
    fun getSupabaseConfig(): Pair<String, String> {
        val rawUrl = try {
            BuildConfig::class.java.getField("SUPABASE_URL").get(null) as? String ?: ""
        } catch (_: Throwable) { "" }

        val rawKey = try {
            BuildConfig::class.java.getField("SUPABASE_ANON_KEY").get(null) as? String ?: ""
        } catch (_: Throwable) { "" }

        val cleanUrl = rawUrl.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val formattedUrl = when {
            cleanUrl.isBlank() -> ""
            cleanUrl.startsWith("https://", ignoreCase = true) -> cleanUrl
            cleanUrl.startsWith("http://", ignoreCase = true) -> "https://" + cleanUrl.substring(7)
            !cleanUrl.contains(".") -> "https://$cleanUrl.supabase.co"
            else -> "https://$cleanUrl"
        }

        val cleanKey = rawKey.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .filter { !it.isWhitespace() }

        return Pair(formattedUrl, cleanKey)
    }

    fun isConfigured(): Boolean {
        val (url, key) = getSupabaseConfig()
        return url.isNotBlank() && key.isNotBlank()
    }

    /**
     * Rapid synchronous check to verify if the device has a cryptographically
     * valid, untampered activation token signed with this hardware identity.
     */
    fun isDeviceActivated(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)
            val signedToken = prefs.getString(KEY_SIGNED_TOKEN, null)
            if (signedToken.isNullOrBlank()) {
                return false
            }

            val hardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val isValid = DeviceSecurityManager.verifyTokenIntegrity(signedToken, hardwareHash)
            if (!isValid) {
                Log.w(TAG, "⚠️ Cryptographic token integrity check failed! Resetting activation state.")
                clearActivation(context)
                return false
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error in isDeviceActivated: ${t.message}", t)
            false
        }
    }

    fun getSavedStudentName(context: Context): String {
        return try {
            getPrefs(context).getString(KEY_SAVED_STUDENT_NAME, "") ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    fun isPendingRequestedLocally(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(KEY_PENDING_REQUESTED_LOCAL, false)
        } catch (_: Throwable) {
            false
        }
    }

    fun getRawSessionToken(context: Context): String? {
        return try {
            val prefs = getPrefs(context)
            val signedToken = prefs.getString(KEY_SIGNED_TOKEN, null) ?: return null
            val hardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            DeviceSecurityManager.extractRawToken(signedToken, hardwareHash)
        } catch (_: Throwable) {
            null
        }
    }

    fun clearActivation(context: Context) {
        try {
            getPrefs(context).edit()
                .remove(KEY_SIGNED_TOKEN)
                .remove(KEY_ACTIVATED_AT)
                .remove(KEY_LAST_HEARTBEAT_AT)
                .remove(KEY_CAN_VIEW_PROFILES)
                .remove(KEY_LAST_PROFILE_PERMISSION_CHECK)
                .commit()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing activation: ${e.message}", e)
        }
    }

    private fun JSONObject.optCleanString(key: String, fallback: String = ""): String {
        if (!has(key) || isNull(key)) return fallback
        val v = optString(key, "").trim()
        return if (v.isBlank() || v.equals("null", ignoreCase = true)) fallback else v
    }

    private fun isDeviceBlockedState(status: String?, isBanned: Boolean, isBlocked: Boolean): Boolean {
        val cleanStatus = status?.trim()?.lowercase().orEmpty()
        return isBanned || isBlocked || cleanStatus in setOf("blocked", "banned", "rejected", "revoked", "disabled", "suspended")
    }

    private fun saveLocalVerifiedSession(
        context: Context,
        deviceHardwareHash: String,
        rawToken: String,
        canViewProfiles: Boolean? = null
    ) {
        val safeToken = rawToken.takeIf { it.isNotBlank() } ?: "VERIFIED-$deviceHardwareHash"
        val signedToken = DeviceSecurityManager.signToken(safeToken, deviceHardwareHash)
        val editor = getPrefs(context).edit()
            .putString(KEY_SIGNED_TOKEN, signedToken)
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
            .putBoolean(KEY_PENDING_REQUESTED_LOCAL, false)
        if (canViewProfiles != null) {
            editor.putBoolean(KEY_CAN_VIEW_PROFILES, canViewProfiles)
            editor.putLong(KEY_LAST_PROFILE_PERMISSION_CHECK, System.currentTimeMillis())
        }
        editor.commit()
        runCatching {
            com.example.notification.OneSignalPushManager.triggerSyncAsync(context)
        }
    }

    /**
     * Checks the current device verification state from Supabase on screen open or manual refresh.
     * - If the device was already approved before (e.g., user reinstalled the app or Admin approved it),
     *   it automatically signs and restores local activation and returns [StudentVerificationStatus.Approved].
     * - If the device is banned/blocked, returns [StudentVerificationStatus.Blocked].
     * - If the device has a pending request, returns [StudentVerificationStatus.Pending].
     * - Otherwise returns [StudentVerificationStatus.NotRequested].
     */
    suspend fun checkStudentVerificationStatus(context: Context): StudentVerificationStatus = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext StudentVerificationStatus.Error(com.example.utils.AppLang.tr("Server configuration not found.", "সার্ভার কনফিগারেশন পাওয়া যায়নি।"))
            }

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            val deviceBrand = android.os.Build.BRAND ?: ""
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0.0" }

            // 1. Call dedicated RPC `check_student_verification_status` first
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/check_student_verification_status"
                val rpcPayload = JSONObject().apply {
                    put("p_device_hash", deviceHardwareHash)
                    put("p_display_device_id", displayDeviceId)
                    put("p_device_model", deviceModel)
                    put("p_device_brand", deviceBrand)
                    put("p_android_version", androidVersion)
                    put("p_app_version", appVersion)
                }
                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(rpcReq).execute().use { rpcResp ->
                    val rpcBody = rpcResp.body?.string() ?: ""
                    if (rpcResp.isSuccessful && rpcBody.isNotBlank()) {
                        val json = JSONObject(rpcBody)
                        val isBanned = json.optBoolean("is_banned", false) || json.optBoolean("banned", false)
                        val isBlocked = json.optBoolean("is_blocked", false) || json.optBoolean("blocked", false)
                        val status = json.optCleanString("status").lowercase()
                        val reason = json.optCleanString("ban_reason").ifBlank {
                            json.optCleanString("rejection_reason").ifBlank {
                                json.optCleanString("reason", com.example.utils.AppLang.tr("Access on this device has been suspended.", "এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।"))
                            }
                        }
                        val token = json.optCleanString("session_token").ifBlank {
                            json.optCleanString("token")
                        }
                        val studentName = json.optCleanString("student_note").ifBlank {
                            json.optCleanString("student_name")
                        }
                        val canViewProfiles = if (json.has("can_view_profiles")) json.optBoolean("can_view_profiles", false) else null

                        if (isDeviceBlockedState(status, isBanned, isBlocked)) {
                            clearActivation(context)
                            getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, false).apply()
                            return@withContext StudentVerificationStatus.Blocked(reason)
                        }

                        if (status == "active" || status == "approved" || json.optBoolean("is_approved", false)) {
                            saveLocalVerifiedSession(
                                context = context,
                                deviceHardwareHash = deviceHardwareHash,
                                rawToken = token.ifBlank { "VERIFIED-$deviceHardwareHash" },
                                canViewProfiles = canViewProfiles
                            )
                            return@withContext StudentVerificationStatus.Approved()
                        }

                        if (status == "pending" || status == "requested") {
                            getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, true).apply()
                            return@withContext StudentVerificationStatus.Pending(studentName = studentName)
                        }

                        if (status == "unverified" || status == "none" || status.isBlank()) {
                            getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, false).apply()
                            return@withContext StudentVerificationStatus.NotRequested
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "RPC check_student_verification_status error: ${e.message}")
            }

            if (isPendingRequestedLocally(context)) {
                StudentVerificationStatus.Pending(studentName = getSavedStudentName(context))
            } else {
                StudentVerificationStatus.NotRequested
            }
        } catch (t: Throwable) {
            Log.e(TAG, "checkStudentVerificationStatus error: ${t.message}", t)
            if (isPendingRequestedLocally(context)) {
                StudentVerificationStatus.Pending(studentName = getSavedStudentName(context))
            } else {
                StudentVerificationStatus.Error(com.example.utils.AppLang.tr("Please check your internet connection and try again.", "ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"))
            }
        }
    }

    /**
     * One-click Student Verification Request!
     * Immediately sends the verification request to Supabase via `request_student_verification`
     * so it appears in the Admin App under pending requests.
     */
    suspend fun submitStudentVerificationRequest(
        context: Context,
        studentName: String = ""
    ): StudentVerificationStatus = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext StudentVerificationStatus.Error(com.example.utils.AppLang.tr("Server configuration not found.", "সার্ভার কনফিগারেশন পাওয়া যায়নি।"))
            }

            val sessionManager = com.example.auth.SessionManager(context)
            val cleanName = studentName.trim().ifBlank { sessionManager.getUserFullName()?.trim().orEmpty() }
            if (cleanName.isNotBlank()) {
                getPrefs(context).edit().putString(KEY_SAVED_STUDENT_NAME, cleanName).apply()
            }
            val studentPhone = sessionManager.getNormalizedUserPhone()
            val oneSignalPlayerId = com.example.notification.OneSignalPushManager.getSavedPlayerId(context)
            val fcmToken = sessionManager.getFcmToken()?.trim().orEmpty()

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val appSignature = DeviceSecurityManager.getAppSignatureSha256(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            val deviceBrand = android.os.Build.BRAND ?: ""
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0.0" }

            // RPC-only: `request_student_verification` (direct `devices` REST access is disabled for anon)
            val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/request_student_verification"
            val rpcPayload = JSONObject().apply {
                put("p_device_hash", deviceHardwareHash)
                put("p_display_device_id", displayDeviceId)
                put("p_device_model", deviceModel)
                put("p_device_brand", deviceBrand)
                put("p_android_version", androidVersion)
                put("p_app_version", appVersion)
                put("p_student_note", cleanName)
                put("p_app_signature", appSignature)
            }

            val rpcReq = Request.Builder()
                .url(rpcUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(rpcReq).execute().use { rpcResp ->
                val rpcBody = rpcResp.body?.string() ?: ""
                if (rpcResp.isSuccessful && rpcBody.isNotBlank()) {
                    val json = JSONObject(rpcBody)
                    val isBanned = json.optBoolean("is_banned", false) || json.optBoolean("banned", false)
                    val isBlocked = json.optBoolean("is_blocked", false) || json.optBoolean("blocked", false)
                    val status = json.optCleanString("status", "pending").lowercase()
                    if (isDeviceBlockedState(status, isBanned, isBlocked)) {
                        clearActivation(context)
                        val banReason = json.optCleanString("ban_reason").ifBlank {
                            json.optCleanString("rejection_reason").ifBlank {
                                json.optCleanString("message", com.example.utils.AppLang.tr("Access on this device has been suspended.", "এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।"))
                            }
                        }
                        return@withContext StudentVerificationStatus.Blocked(banReason)
                    }
                    if (status == "error" || (json.has("success") && !json.optBoolean("success", true))) {
                        val errMsg = json.optCleanString("message", com.example.utils.AppLang.tr("Could not submit request. Please try again.", "আবেদন পাঠানো যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।"))
                        return@withContext StudentVerificationStatus.Error(errMsg)
                    }
                    if (status == "active" || status == "approved") {
                        val token = json.optCleanString("session_token").ifBlank { "VERIFIED-$deviceHardwareHash" }
                        val canViewProfiles = if (json.has("can_view_profiles")) json.optBoolean("can_view_profiles", false) else null
                        saveLocalVerifiedSession(context, deviceHardwareHash, token, canViewProfiles = canViewProfiles)
                        return@withContext StudentVerificationStatus.Approved()
                    }
                    getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, true).commit()
                    com.example.notification.OneSignalPushManager.notifyAdminDeviceAccessRequest(
                        context = context,
                        studentName = cleanName,
                        displayDeviceId = displayDeviceId,
                        deviceModel = deviceModel
                    )
                    return@withContext StudentVerificationStatus.Pending(studentName = cleanName)
                } else {
                    return@withContext StudentVerificationStatus.Error(
                        com.example.utils.AppLang.tr(
                            "Could not submit request (HTTP ${rpcResp.code}). Check your internet connection.",
                            "আবেদন পাঠানো যায়নি (HTTP ${rpcResp.code})। ইন্টারনেট সংযোগ চেক করুন।"
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "submitStudentVerificationRequest error: ${t.message}", t)
            StudentVerificationStatus.Error(com.example.utils.AppLang.tr("Connection error: please check your internet and try again.", "সংযোগ ত্রুটি: অনুগ্রহ করে ইন্টারনেট কানেকশন চেক করে আবার চেষ্টা করুন।"))
        }
    }

    /**
     * Checks if this device is currently banned in the Supabase database.
     */
    suspend fun checkDeviceStatus(context: Context): DeviceStatusResult = withContext(Dispatchers.IO) {
        when (val st = checkStudentVerificationStatus(context)) {
            is StudentVerificationStatus.Blocked -> DeviceStatusResult.Banned(st.reason)
            else -> DeviceStatusResult.Allowed
        }
    }

    /**
     * Verifies if this device's verification is still active, not banned, and on the required app version
     * in Supabase via `verify_device_heartbeat` RPC (with direct `devices` table fallback).
     */
    suspend fun verifyHeartbeat(context: Context): HeartbeatResult = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext HeartbeatResult.Valid
            }

            val hardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val rawToken = getRawSessionToken(context) ?: return@withContext HeartbeatResult.Revoked("NO_TOKEN")

            // 1. Primary: Call RPC `verify_device_heartbeat(p_device_hash, p_token, p_app_version)`
            try {
                val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/verify_device_heartbeat"
                val jsonBody = JSONObject().apply {
                    put("p_device_hash", hardwareHash)
                    put("p_token", rawToken)
                    put("p_app_version", BuildConfig.VERSION_NAME)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val jsonRes = JSONObject(responseBody)
                        val isValid = jsonRes.optBoolean("valid", false)
                        val isBanned = jsonRes.optBoolean("is_banned", false) || jsonRes.optBoolean("banned", false)
                        val isBlocked = jsonRes.optBoolean("is_blocked", false) || jsonRes.optBoolean("blocked", false)
                        val status = jsonRes.optCleanString("status").lowercase()
                        val reason = jsonRes.optCleanString("reason")

                        if (isValid && !isDeviceBlockedState(status, isBanned, isBlocked)) {
                            getPrefs(context).edit()
                                .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                                .apply()
                            return@withContext HeartbeatResult.Valid
                        } else if (status == "update_required") {
                            Log.w(TAG, "Heartbeat requires app update: $reason")
                            return@withContext HeartbeatResult.NetworkError(reason.ifBlank { "Update required" })
                        } else {
                            Log.w(TAG, "Heartbeat returned invalid: $reason. Clearing activation.")
                            clearActivation(context)
                            return@withContext HeartbeatResult.Revoked(reason)
                        }
                    }
                }
            } catch (_: Throwable) {}

            HeartbeatResult.NetworkError("Heartbeat verification unreachable")
        } catch (e: Throwable) {
            HeartbeatResult.NetworkError(e.message ?: "Network error")
        }
    }

    fun isProfileViewPermissionCached(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CAN_VIEW_PROFILES, false)
    }

    /**
     * Checks if profile viewing from Leaderboard is enabled by Admin for this device in `public.devices`.
     * Defaults to false (disabled for all users) until admin toggles `can_view_profiles` ON in Supabase.
     */
    suspend fun checkProfileViewPermission(context: Context, forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_PROFILE_PERMISSION_CHECK, 0L)

        if (!forceRefresh && (now - lastCheck < 20_000L)) {
            return@withContext prefs.getBoolean(KEY_CAN_VIEW_PROFILES, false)
        }

        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext prefs.getBoolean(KEY_CAN_VIEW_PROFILES, false)
            }

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)

            var canView = false
            var resolvedByRpc = false

            // 1. Primary (Zero-Trust RLS compatible): Call `check_student_verification_status` with exact 6-arg signature
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            val deviceBrand = android.os.Build.BRAND ?: ""
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0.0" }
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/check_student_verification_status"
                val rpcPayload = JSONObject().apply {
                    put("p_device_hash", deviceHardwareHash)
                    put("p_display_device_id", displayDeviceId)
                    put("p_device_model", deviceModel)
                    put("p_device_brand", deviceBrand)
                    put("p_android_version", androidVersion)
                    put("p_app_version", appVersion)
                }
                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(rpcReq).execute().use { rpcResp ->
                    if (rpcResp.isSuccessful) {
                        val rpcBody = rpcResp.body?.string() ?: ""
                        if (rpcBody.trim().startsWith("{")) {
                            val json = JSONObject(rpcBody)
                            if (json.has("can_view_profiles")) {
                                val isBanned = json.optBoolean("is_banned", false) || json.optBoolean("banned", false)
                                val isBlocked = json.optBoolean("is_blocked", false) || json.optBoolean("blocked", false)
                                val status = json.optCleanString("status", "active")
                                canView = !isDeviceBlockedState(status, isBanned, isBlocked) &&
                                    json.optBoolean("can_view_profiles", false)
                                resolvedByRpc = true
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            if (!resolvedByRpc) {
                return@withContext prefs.getBoolean(KEY_CAN_VIEW_PROFILES, false)
            }

            prefs.edit()
                .putBoolean(KEY_CAN_VIEW_PROFILES, canView)
                .putLong(KEY_LAST_PROFILE_PERMISSION_CHECK, now)
                .apply()

            canView
        } catch (e: Throwable) {
            Log.e(TAG, "checkProfileViewPermission error: ${e.message}")
            prefs.getBoolean(KEY_CAN_VIEW_PROFILES, false)
        }
    }
}
