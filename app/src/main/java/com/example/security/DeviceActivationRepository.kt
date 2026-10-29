package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ActivationResult {
    data class Success(val message: String) : ActivationResult()
    data class Error(val message: String, val isBanned: Boolean = false) : ActivationResult()
}

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
    data class Approved(val message: String = "আপনার ডিভাইসটি ভেরিফাইড রয়েছে। এখন অ্যাপে প্রবেশ করতে পারেন!") : StudentVerificationStatus()
    data class Blocked(val reason: String) : StudentVerificationStatus()
    data class Error(val message: String) : StudentVerificationStatus()
}

object DeviceActivationRepository {

    private const val TAG = "DeviceActivation"
    private const val PREFS_FILE = "maxbird_device_activation"
    private const val KEY_SIGNED_TOKEN = "key_signed_token"
    private const val KEY_ACTIVATED_CODE = "key_activated_code"
    private const val KEY_ACTIVATED_AT = "key_activated_at"
    private const val KEY_LAST_HEARTBEAT_AT = "key_last_heartbeat_at"
    private const val KEY_CAN_VIEW_PROFILES = "key_can_view_profiles"
    private const val KEY_LAST_PROFILE_PERMISSION_CHECK = "key_last_profile_permission_check"
    private const val KEY_PENDING_REQUESTED_LOCAL = "key_pending_requested_local"
    private const val KEY_SAVED_STUDENT_NAME = "key_saved_student_name"

    private val httpClient = OkHttpClient.Builder()
        .applyAntiCaptureSecurity()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            context.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Checks if Supabase URL and Anon Key are present in BuildConfig and sanitizes them.
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
            cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> cleanUrl
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

    fun getActivatedCode(context: Context): String? {
        return try {
            getPrefs(context).getString(KEY_ACTIVATED_CODE, null)
        } catch (_: Throwable) {
            null
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
                .remove(KEY_ACTIVATED_CODE)
                .remove(KEY_ACTIVATED_AT)
                .remove(KEY_LAST_HEARTBEAT_AT)
                .commit()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing activation: ${e.message}", e)
        }
    }

    private fun saveLocalVerifiedSession(
        context: Context,
        deviceHardwareHash: String,
        rawToken: String,
        codeOrTag: String = "VERIFIED-DEVICE"
    ) {
        val safeToken = rawToken.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
        val signedToken = DeviceSecurityManager.signToken(safeToken, deviceHardwareHash)
        getPrefs(context).edit()
            .putString(KEY_SIGNED_TOKEN, signedToken)
            .putString(KEY_ACTIVATED_CODE, codeOrTag)
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
            .putBoolean(KEY_PENDING_REQUESTED_LOCAL, false)
            .commit()
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
                return@withContext StudentVerificationStatus.Error("সার্ভার কনফিগারেশন পাওয়া যায়নি।")
            }

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            val deviceBrand = android.os.Build.BRAND ?: ""
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0.0" }

            // 1. Try dedicated RPC `check_student_verification_status` first
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

                val rpcResp = httpClient.newCall(rpcReq).execute()
                val rpcBody = rpcResp.body?.string() ?: ""
                if (rpcResp.isSuccessful && rpcBody.isNotBlank()) {
                    val json = JSONObject(rpcBody)
                    val isBanned = json.optBoolean("is_banned", false)
                    val status = json.optString("status", "").lowercase().trim()
                    val reason = json.optString("ban_reason", "").ifBlank {
                        json.optString("reason", "এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।")
                    }
                    val token = json.optString("session_token", "").ifBlank {
                        json.optString("token", "")
                    }
                    val studentName = json.optString("student_note", "")

                    if (isBanned || status == "banned" || status == "blocked" || status == "rejected") {
                        clearActivation(context)
                        getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, false).apply()
                        return@withContext StudentVerificationStatus.Blocked(reason)
                    }

                    if (status == "active" || status == "approved" || json.optBoolean("is_approved", false)) {
                        saveLocalVerifiedSession(
                            context = context,
                            deviceHardwareHash = deviceHardwareHash,
                            rawToken = token.ifBlank { "VERIFIED-$deviceHardwareHash" },
                            codeOrTag = json.optString("applied_code", "").ifBlank { "VERIFIED-DEVICE" }
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
            } catch (e: Throwable) {
                Log.w(TAG, "RPC check_student_verification_status fallback to REST: ${e.message}")
            }

            // 2. Direct REST Fallback against `devices` and `activated_devices` tables
            val directUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/devices?or=(device_hash.eq.$deviceHardwareHash,display_device_id.eq.$displayDeviceId)&select=*&limit=1"
            val directReq = Request.Builder()
                .url(directUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            val directResp = httpClient.newCall(directReq).execute()
            if (directResp.isSuccessful) {
                val arr = org.json.JSONArray(directResp.body?.string() ?: "[]")
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val isBanned = obj.optBoolean("is_banned", false)
                    val status = obj.optString("status", "").lowercase().trim()
                    val reason = obj.optString("ban_reason", "এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।")
                    val appliedCode = obj.optString("applied_code", "")

                    if (isBanned || status == "banned" || status == "blocked" || status == "rejected") {
                        clearActivation(context)
                        getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, false).apply()
                        return@withContext StudentVerificationStatus.Blocked(reason)
                    }

                    if (status == "active" || status == "approved") {
                        val token = obj.optString("session_token", "").ifBlank { "VERIFIED-$deviceHardwareHash" }
                        saveLocalVerifiedSession(
                            context = context,
                            deviceHardwareHash = deviceHardwareHash,
                            rawToken = token,
                            codeOrTag = appliedCode.ifBlank { "VERIFIED-DEVICE" }
                        )
                        return@withContext StudentVerificationStatus.Approved()
                    }

                    if (status == "pending" || status == "requested") {
                        getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, true).apply()
                        val resolvedNote = obj.optString("student_note", "").ifBlank {
                            appliedCode.removePrefix("REQ:").trim().takeIf {
                                it.isNotBlank() && !it.equals("STUDENT-VERIFY", ignoreCase = true)
                            } ?: getSavedStudentName(context)
                        }
                        return@withContext StudentVerificationStatus.Pending(
                            studentName = resolvedNote
                        )
                    }
                }
            }

            // Also check `activated_devices` table in case device was previously bound via access code
            val actUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/activated_devices?device_hash=eq.$deviceHardwareHash&select=id,is_blocked,session_token&limit=1"
            val actReq = Request.Builder()
                .url(actUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()
            val actResp = httpClient.newCall(actReq).execute()
            if (actResp.isSuccessful) {
                val actArr = org.json.JSONArray(actResp.body?.string() ?: "[]")
                if (actArr.length() > 0) {
                    val actObj = actArr.getJSONObject(0)
                    if (actObj.optBoolean("is_blocked", false)) {
                        clearActivation(context)
                        return@withContext StudentVerificationStatus.Blocked("এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।")
                    }
                    val existingToken = actObj.optString("session_token", "").ifBlank { "VERIFIED-$deviceHardwareHash" }
                    saveLocalVerifiedSession(
                        context = context,
                        deviceHardwareHash = deviceHardwareHash,
                        rawToken = existingToken,
                        codeOrTag = "VERIFIED-DEVICE"
                    )
                    return@withContext StudentVerificationStatus.Approved()
                }
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
                StudentVerificationStatus.Error("ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।")
            }
        }
    }

    /**
     * One-click Student Verification Request!
     * Immediately sends the verification request to Supabase so it appears in the Admin App under pending requests.
     */
    suspend fun submitStudentVerificationRequest(
        context: Context,
        studentName: String = ""
    ): StudentVerificationStatus = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext StudentVerificationStatus.Error("সার্ভার কনফিগারেশন পাওয়া যায়নি।")
            }

            val cleanName = studentName.trim()
            if (cleanName.isNotBlank()) {
                getPrefs(context).edit().putString(KEY_SAVED_STUDENT_NAME, cleanName).apply()
            }

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val appSignature = DeviceSecurityManager.getAppSignatureSha256(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            val deviceBrand = android.os.Build.BRAND ?: ""
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0.0" }

            // 1. Try RPC `request_student_verification` first
            try {
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

                val rpcResp = httpClient.newCall(rpcReq).execute()
                val rpcBody = rpcResp.body?.string() ?: ""
                if (rpcResp.isSuccessful && rpcBody.isNotBlank()) {
                    val json = JSONObject(rpcBody)
                    val isBanned = json.optBoolean("is_banned", false)
                    val status = json.optString("status", "pending").lowercase().trim()
                    if (isBanned || status == "banned" || status == "blocked") {
                        clearActivation(context)
                        return@withContext StudentVerificationStatus.Blocked(
                            json.optString("ban_reason", "এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।")
                        )
                    }
                    if (status == "active" || status == "approved") {
                        val token = json.optString("session_token", "").ifBlank { "VERIFIED-$deviceHardwareHash" }
                        saveLocalVerifiedSession(context, deviceHardwareHash, token, "VERIFIED-DEVICE")
                        return@withContext StudentVerificationStatus.Approved()
                    }
                    getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, true).commit()
                    return@withContext StudentVerificationStatus.Pending(studentName = cleanName)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "RPC request_student_verification fallback to REST: ${e.message}")
            }

            // 2. First check if already active or banned in `devices`
            val existingCheck = checkStudentVerificationStatus(context)
            if (existingCheck is StudentVerificationStatus.Approved || existingCheck is StudentVerificationStatus.Blocked) {
                return@withContext existingCheck
            }

            // 3. Direct REST upsert to `devices` table with status = 'pending'
            val upsertPayload = JSONObject().apply {
                put("device_hash", deviceHardwareHash)
                put("display_device_id", displayDeviceId)
                put("device_model", deviceModel)
                put("device_brand", deviceBrand)
                put("device_manufacturer", android.os.Build.MANUFACTURER ?: "")
                put("android_version", androidVersion)
                put("sdk_version", android.os.Build.VERSION.SDK_INT)
                put("app_version", appVersion)
                put("applied_code", if (cleanName.isNotBlank()) "REQ: $cleanName" else "STUDENT-VERIFY")
                put("status", "pending")
                put("is_banned", false)
            }

            val upsertReq = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/devices?on_conflict=device_hash")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(upsertPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val upsertResp = httpClient.newCall(upsertReq).execute()
            if (upsertResp.isSuccessful) {
                getPrefs(context).edit().putBoolean(KEY_PENDING_REQUESTED_LOCAL, true).commit()
                return@withContext StudentVerificationStatus.Pending(studentName = cleanName)
            } else {
                return@withContext StudentVerificationStatus.Error("আবেদন পাঠানো যায়নি (HTTP ${upsertResp.code})। ইন্টারনেট সংযোগ চেক করুন।")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "submitStudentVerificationRequest error: ${t.message}", t)
            StudentVerificationStatus.Error("সংযোগ ত্রুটি: অনুগ্রহ করে ইন্টারনেট কানেকশন চেক করে আবার চেষ্টা করুন।")
        }
    }

    /**
     * Legacy code activation method preserved for backward compatibility.
     */
    suspend fun activateDevice(context: Context, code: String): ActivationResult = withContext(Dispatchers.IO) {
        try {
            val cleanCode = code.trim().uppercase()
            if (cleanCode.isBlank()) {
                return@withContext ActivationResult.Error("দয়া করে সঠিক এক্সেস কোড লিখুন।")
            }

            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext ActivationResult.Error("সার্ভার কনফিগারেশন পাওয়া যায়নি।")
            }

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val appSignature = DeviceSecurityManager.getAppSignatureSha256(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            val deviceBrand = android.os.Build.BRAND ?: ""
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0.0" }

            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/activate_device"

            val jsonBody = JSONObject().apply {
                put("p_code", cleanCode)
                put("p_device_hash", deviceHardwareHash)
                put("p_app_signature", appSignature)
                put("p_display_device_id", displayDeviceId)
                put("p_device_model", deviceModel)
                put("p_device_brand", deviceBrand)
                put("p_android_version", androidVersion)
                put("p_app_version", appVersion)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext directRestActivation(
                    context = context,
                    cleanCode = cleanCode,
                    deviceHardwareHash = deviceHardwareHash,
                    appSignature = appSignature,
                    displayDeviceId = displayDeviceId,
                    deviceModel = deviceModel,
                    deviceBrand = deviceBrand,
                    androidVersion = androidVersion,
                    appVersion = appVersion,
                    supabaseUrl = supabaseUrl,
                    anonKey = anonKey
                )
            }

            val jsonRes = JSONObject(responseBody)
            val success = jsonRes.optBoolean("success", false)
            val isBanned = jsonRes.optBoolean("banned", false) || jsonRes.optBoolean("is_banned", false)
            val message = jsonRes.optString("message", "ভেরিফিকেশন সম্পন্ন হয়েছে।")

            if (isBanned) {
                clearActivation(context)
                return@withContext ActivationResult.Error(message = message, isBanned = true)
            }

            if (success) {
                val rawToken = jsonRes.optString("token", "VERIFIED-$deviceHardwareHash")
                saveLocalVerifiedSession(context, deviceHardwareHash, rawToken, cleanCode)
                ActivationResult.Success(message)
            } else {
                ActivationResult.Error(message, isBanned = isBanned)
            }
        } catch (t: Throwable) {
            ActivationResult.Error("অপ্রত্যাশিত ত্রুটি: ${t.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}")
        }
    }

    private suspend fun directRestActivation(
        context: Context,
        cleanCode: String,
        deviceHardwareHash: String,
        appSignature: String,
        displayDeviceId: String,
        deviceModel: String,
        deviceBrand: String,
        androidVersion: String,
        appVersion: String,
        supabaseUrl: String,
        anonKey: String
    ): ActivationResult = withContext(Dispatchers.IO) {
        try {
            val codeUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/access_codes?code=ilike.$cleanCode&select=id,code,is_active,status,expires_at,device_limit,max_devices"
            val codeReq = Request.Builder()
                .url(codeUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            val codeResp = httpClient.newCall(codeReq).execute()
            if (!codeResp.isSuccessful) {
                return@withContext ActivationResult.Error("যাচাই করা যায়নি (HTTP ${codeResp.code})")
            }

            val codeArr = org.json.JSONArray(codeResp.body?.string() ?: "[]")
            if (codeArr.length() == 0) {
                return@withContext ActivationResult.Error("সঠিক কোড দিন।")
            }

            val codeObj = codeArr.getJSONObject(0)
            val codeId = codeObj.optString("id", "")
            val isActive = codeObj.optBoolean("is_active", true)
            val status = codeObj.optString("status", "active")
            if (!isActive || status.equals("inactive", ignoreCase = true)) {
                return@withContext ActivationResult.Error("এই কোডটি নিষ্ক্রিয় রয়েছে।")
            }

            val rawToken = java.util.UUID.randomUUID().toString()
            val devTelemetryJson = JSONObject().apply {
                put("device_hash", deviceHardwareHash)
                put("display_device_id", displayDeviceId)
                put("device_model", deviceModel)
                put("device_brand", deviceBrand)
                put("device_manufacturer", android.os.Build.MANUFACTURER ?: "")
                put("android_version", androidVersion)
                put("sdk_version", android.os.Build.VERSION.SDK_INT)
                put("app_version", appVersion)
                put("applied_code", cleanCode)
                put("status", "active")
            }
            val upsertReq = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/devices?on_conflict=device_hash")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(devTelemetryJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(upsertReq).execute().close()

            if (codeId.isNotBlank()) {
                runCatching {
                    val actJson = JSONObject().apply {
                        put("code_id", codeId)
                        put("device_hash", deviceHardwareHash)
                        put("app_signature", appSignature)
                        put("session_token", rawToken)
                        put("is_blocked", false)
                    }
                    val actUpsertReq = Request.Builder()
                        .url("${supabaseUrl.trimEnd('/')}/rest/v1/activated_devices?on_conflict=code_id,device_hash")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(actJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(actUpsertReq).execute().close()
                }
            }

            saveLocalVerifiedSession(context, deviceHardwareHash, rawToken, cleanCode)
            ActivationResult.Success("ভেরিফিকেশন সফলভাবে সক্রিয় হয়েছে!")
        } catch (t: Throwable) {
            ActivationResult.Error("যাচাই করা যায়নি: ${t.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}")
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
     * Verifies if this device's verification is still active and not banned in Supabase.
     * Supports both one-click verified devices (`devices.status = 'active'`) and legacy code-bound devices.
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

            // 1. Check `devices` table directly first (authoritative for one-click student verification & bans)
            try {
                val devUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/devices?or=(device_hash.eq.$hardwareHash,display_device_id.eq.$displayDeviceId)&select=status,is_banned,ban_reason&limit=1"
                val devReq = Request.Builder()
                    .url(devUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()
                val devResp = httpClient.newCall(devReq).execute()
                if (devResp.isSuccessful) {
                    val arr = org.json.JSONArray(devResp.body?.string() ?: "[]")
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val isBanned = obj.optBoolean("is_banned", false)
                        val status = obj.optString("status", "active").lowercase().trim()
                        if (isBanned || status == "banned" || status == "blocked" || status == "rejected" || status == "pending" || status == "unverified") {
                            val reason = obj.optString("ban_reason", "ডিভাইস ভেরিফিকেশন স্থগিত করা হয়েছে।")
                            clearActivation(context)
                            return@withContext HeartbeatResult.Revoked(reason)
                        }
                        if (status == "active" || status == "approved") {
                            getPrefs(context).edit()
                                .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                                .apply()
                            return@withContext HeartbeatResult.Valid
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 2. Fallback to RPC `verify_device_heartbeat`
            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/verify_device_heartbeat"
            val jsonBody = JSONObject().apply {
                put("p_device_hash", hardwareHash)
                put("p_token", rawToken)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext HeartbeatResult.NetworkError("HTTP ${response.code}")
            }

            val jsonRes = JSONObject(responseBody)
            val isValid = jsonRes.optBoolean("valid", false)
            val reason = jsonRes.optString("reason", "")

            if (isValid) {
                getPrefs(context).edit()
                    .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                    .apply()
                HeartbeatResult.Valid
            } else {
                Log.w(TAG, "Heartbeat returned invalid: $reason. Clearing activation.")
                clearActivation(context)
                HeartbeatResult.Revoked(reason)
            }
        } catch (e: Throwable) {
            HeartbeatResult.NetworkError(e.message ?: "Network error")
        }
    }

    fun isProfileViewPermissionCached(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CAN_VIEW_PROFILES, false)
    }

    /**
     * Checks if profile viewing from Leaderboard is enabled by Admin for this device / access code in Supabase.
     * Defaults to false (disabled for all users) until admin toggles it ON in Supabase for this device or access code.
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
            val activatedCode = prefs.getString(KEY_ACTIVATED_CODE, "") ?: ""

            var canView = false

            // 1. Check devices table by device_hash or display_device_id
            try {
                val devUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/devices?or=(device_hash.eq.$deviceHardwareHash,display_device_id.eq.$displayDeviceId)&select=can_view_profiles"
                val devReq = Request.Builder()
                    .url(devUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()
                val devResp = httpClient.newCall(devReq).execute()
                if (devResp.isSuccessful) {
                    val devBody = devResp.body?.string() ?: "[]"
                    val devArr = org.json.JSONArray(devBody)
                    if (devArr.length() > 0) {
                        val obj = devArr.getJSONObject(0)
                        if (obj.optBoolean("can_view_profiles", false)) {
                            canView = true
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 2. If not yet true, check access_codes table by applied code
            if (!canView && activatedCode.isNotBlank()) {
                try {
                    val codeUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/access_codes?code=ilike.${activatedCode.trim()}&select=can_view_profiles"
                    val codeReq = Request.Builder()
                        .url(codeUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .get()
                        .build()
                    val codeResp = httpClient.newCall(codeReq).execute()
                    if (codeResp.isSuccessful) {
                        val codeBody = codeResp.body?.string() ?: "[]"
                        val codeArr = org.json.JSONArray(codeBody)
                        if (codeArr.length() > 0) {
                            val obj = codeArr.getJSONObject(0)
                            if (obj.optBoolean("can_view_profiles", false)) {
                                canView = true
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 3. If not yet true, check activated_devices table by device_hash
            if (!canView) {
                try {
                    val actUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/activated_devices?device_hash=eq.$deviceHardwareHash&select=can_view_profiles"
                    val actReq = Request.Builder()
                        .url(actUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .get()
                        .build()
                    val actResp = httpClient.newCall(actReq).execute()
                    if (actResp.isSuccessful) {
                        val actBody = actResp.body?.string() ?: "[]"
                        val actArr = org.json.JSONArray(actBody)
                        if (actArr.length() > 0) {
                            val obj = actArr.getJSONObject(0)
                            if (obj.optBoolean("can_view_profiles", false)) {
                                canView = true
                            }
                        }
                    }
                } catch (_: Throwable) {}
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
