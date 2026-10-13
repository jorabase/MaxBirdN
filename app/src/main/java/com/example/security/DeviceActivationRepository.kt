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

object DeviceActivationRepository {

    private const val TAG = "DeviceActivation"
    private const val PREFS_FILE = "maxbird_device_activation"
    private const val KEY_SIGNED_TOKEN = "key_signed_token"
    private const val KEY_ACTIVATED_CODE = "key_activated_code"
    private const val KEY_ACTIVATED_AT = "key_activated_at"
    private const val KEY_LAST_HEARTBEAT_AT = "key_last_heartbeat_at"
    private const val KEY_CAN_VIEW_PROFILES = "key_can_view_profiles"
    private const val KEY_LAST_PROFILE_PERMISSION_CHECK = "key_last_profile_permission_check"

    private val httpClient = OkHttpClient.Builder()
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

        // Strip any whitespace, quotes or hidden newlines from anonKey to prevent OkHttp header crash
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

    /**
     * Submits an access code to Supabase RPC 'activate_device' to bind this device.
     * Wrapped with comprehensive safety guards to prevent app shutdown on any error.
     */
    suspend fun activateDevice(context: Context, code: String): ActivationResult = withContext(Dispatchers.IO) {
        try {
            val cleanCode = code.trim().uppercase()
            if (cleanCode.isBlank()) {
                return@withContext ActivationResult.Error("দয়া করে সঠিক এক্সেস কোড লিখুন।")
            }

            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext ActivationResult.Error("Supabase কনফিগারেশন পাওয়া যায়নি। GitHub Secrets এ SUPABASE_URL এবং SUPABASE_ANON_KEY সেট করে নতুন APK ডাউনলোড করুন।")
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

            val request = try {
                Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to build request: ${e.message}", e)
                return@withContext ActivationResult.Error("রিকোয়েস্ট তৈরি করতে ব্যর্থ (URL বা Key যাচাই করুন): ${e.localizedMessage}")
            }

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Throwable) {
                Log.e(TAG, "HTTP execution failed: ${e.message}", e)
                return@withContext ActivationResult.Error("সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি: ${e.localizedMessage ?: "ইন্টারনেট কানেকশন চেক করুন"}")
            }

            var responseBody = response.body?.string() ?: ""
            var responseSuccess = response.isSuccessful

            // Fallback 1: If 8-param RPC failed due to candidate function overload or 400/404, retry with 3-param RPC
            if (!responseSuccess && (responseBody.contains("candidate function", ignoreCase = true) ||
                    responseBody.contains("PGRST203", ignoreCase = true) ||
                    responseBody.contains("42725") ||
                    response.code == 404)) {
                Log.w(TAG, "8-param RPC conflict detected, retrying with 3-param payload...")
                try {
                    val fallback3Json = JSONObject().apply {
                        put("p_code", cleanCode)
                        put("p_device_hash", deviceHardwareHash)
                        put("p_app_signature", appSignature)
                    }
                    val req3 = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .post(fallback3Json.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val resp3 = httpClient.newCall(req3).execute()
                    val body3 = resp3.body?.string() ?: ""
                    if (resp3.isSuccessful) {
                        responseSuccess = true
                        responseBody = body3
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "3-param retry failed: ${t.message}")
                }
            }

            // Fallback 2: If RPC still failed with Postgres function ambiguity, execute direct REST activation
            if (!responseSuccess && (responseBody.contains("candidate function", ignoreCase = true) ||
                    responseBody.contains("PGRST203", ignoreCase = true) ||
                    responseBody.contains("42725") ||
                    response.code == 400 || response.code == 404)) {
                Log.w(TAG, "RPC function mismatch detected, executing direct REST activation fallback...")
                val directResult = directRestActivation(
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
                return@withContext directResult
            }

            if (!responseSuccess) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    val msg = errJson.optString("message", "")
                    val hint = errJson.optString("hint", "")
                    val details = errJson.optString("details", "")
                    when {
                        msg.contains("candidate function", ignoreCase = true) || msg.contains("PGRST203") ->
                            "ডাটাবেজে ফাংশন কনফ্লিক্ট হয়েছে। দয়া করে Supabase-এ নতুন ক্লিন SQL স্ক্রিপ্টটি রান করুন।"
                        msg.isNotBlank() -> msg
                        details.isNotBlank() -> details
                        hint.isNotBlank() -> hint
                        else -> "সার্ভার এরর (HTTP ${response.code})"
                    }
                } catch (_: Throwable) {
                    "সার্ভার এরর (HTTP ${response.code})"
                }
                return@withContext ActivationResult.Error(errorMsg)
            }

            val jsonRes = try {
                JSONObject(responseBody)
            } catch (_: Throwable) {
                return@withContext ActivationResult.Error("সার্ভার থেকে সঠিক ফরম্যাটে তথ্য পাওয়া যায়নি: $responseBody")
            }

            val success = jsonRes.optBoolean("success", false)
            val isBanned = jsonRes.optBoolean("banned", false) || jsonRes.optBoolean("is_banned", false)
            val message = jsonRes.optString("message", "এক্সেস যাচাই সম্পন্ন হয়েছে।")

            if (isBanned) {
                clearActivation(context)
                return@withContext ActivationResult.Error(
                    message = if (message.isNotBlank()) message else "এই ডিভাইসটি অ্যাডমিন দ্বারা স্থায়ীভাবে ব্যান করা হয়েছে।",
                    isBanned = true
                )
            }

            if (success) {
                val rawToken = jsonRes.optString("token", "")
                if (rawToken.isBlank()) {
                    return@withContext ActivationResult.Error("সার্ভার থেকে সঠিক টোকেন পাওয়া যায়নি।")
                }

                // Sign the token with this device's unique hardware identity
                val signedToken = DeviceSecurityManager.signToken(rawToken, deviceHardwareHash)

                getPrefs(context).edit()
                    .putString(KEY_SIGNED_TOKEN, signedToken)
                    .putString(KEY_ACTIVATED_CODE, cleanCode)
                    .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
                    .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                    .commit() // Commit synchronously to ensure immediate state persistence

                Log.i(TAG, "Device successfully bound and activated with code: $cleanCode")
                ActivationResult.Success(message)
            } else {
                ActivationResult.Error(message, isBanned = isBanned)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal activation error", t)
            ActivationResult.Error("অপ্রত্যাশিত ত্রুটি: ${t.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}")
        }
    }

    /**
     * Resilient direct REST fallback activation when Supabase RPC encounters candidate function overload.
     */
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
            // 1. Check if device is banned in 'devices' table
            val devUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/devices?device_hash=eq.$deviceHardwareHash&select=is_banned,ban_reason"
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
                    val devObj = devArr.getJSONObject(0)
                    if (devObj.optBoolean("is_banned", false)) {
                        clearActivation(context)
                        val reason = devObj.optString("ban_reason", "এই ডিভাইসটি অ্যাডমিন দ্বারা স্থায়ীভাবে ব্যান করা হয়েছে!")
                        return@withContext ActivationResult.Error(reason, isBanned = true)
                    }
                }
            }

            // 2. Fetch code from access_codes table
            val codeUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/access_codes?code=ilike.$cleanCode&select=id,code,is_active,status,expires_at,device_limit,max_devices"
            val codeReq = Request.Builder()
                .url(codeUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            val codeResp = httpClient.newCall(codeReq).execute()
            if (!codeResp.isSuccessful) {
                return@withContext ActivationResult.Error("এক্সেস কোড ডাটাবেজ থেকে যাচাই করা যায়নি (HTTP ${codeResp.code})")
            }

            val codeBody = codeResp.body?.string() ?: "[]"
            val codeArr = org.json.JSONArray(codeBody)
            if (codeArr.length() == 0) {
                return@withContext ActivationResult.Error("ভুল এক্সেস কোড। সঠিক কোড দিন।")
            }

            val codeObj = codeArr.getJSONObject(0)
            val codeId = codeObj.getString("id")
            val isActive = codeObj.optBoolean("is_active", true)
            val status = codeObj.optString("status", "active")
            if (!isActive || status.equals("inactive", ignoreCase = true)) {
                return@withContext ActivationResult.Error("এই এক্সেস কোডটি অ্যাডমিন দ্বারা নিষ্ক্রিয় করা হয়েছে।")
            }

            // 3. Check bound devices quota
            val boundUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/activated_devices?code_id=eq.$codeId&select=id,device_hash,is_blocked,session_token"
            val boundReq = Request.Builder()
                .url(boundUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            val boundResp = httpClient.newCall(boundReq).execute()
            val boundArr = if (boundResp.isSuccessful) {
                org.json.JSONArray(boundResp.body?.string() ?: "[]")
            } else {
                org.json.JSONArray()
            }

            var alreadyBoundToken: String? = null
            var alreadyBoundId: String? = null
            for (i in 0 until boundArr.length()) {
                val b = boundArr.getJSONObject(i)
                if (b.optString("device_hash") == deviceHardwareHash) {
                    if (b.optBoolean("is_blocked", false)) {
                        return@withContext ActivationResult.Error("এই ডিভাইসটি ব্লক করা হয়েছে। অ্যাডমিনের সাথে যোগাযোগ করুন।", isBanned = true)
                    }
                    alreadyBoundToken = b.optString("session_token")
                    alreadyBoundId = b.optString("id")
                    break
                }
            }

            val rawToken = alreadyBoundToken?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
            val maxDevices = if (codeObj.has("max_devices") && !codeObj.isNull("max_devices")) codeObj.optInt("max_devices", 1)
                             else if (codeObj.has("device_limit") && !codeObj.isNull("device_limit")) codeObj.optInt("device_limit", 1)
                             else 1

            if (alreadyBoundToken == null && boundArr.length() >= maxDevices) {
                return@withContext ActivationResult.Error("এই কোডটি ইতিমধ্যে অন্য ফোনে ব্যবহার করা হয়েছে। ডিভাইস লিমিট শেষ।")
            }

            // 4. Save/Update binding in activated_devices
            if (alreadyBoundId != null) {
                val updateJson = JSONObject().apply {
                    put("session_token", rawToken)
                    put("app_signature", appSignature)
                }
                val updateReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/activated_devices?id=eq.$alreadyBoundId")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(updateJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(updateReq).execute()
            } else {
                val insertJson = JSONObject().apply {
                    put("code_id", codeId)
                    put("device_hash", deviceHardwareHash)
                    put("app_signature", appSignature)
                    put("session_token", rawToken)
                }
                val insertReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/activated_devices")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(insertJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(insertReq).execute()
            }

            // 5. Upsert telemetry in devices table
            val devTelemetryJson = JSONObject().apply {
                put("device_hash", deviceHardwareHash)
                put("display_device_id", displayDeviceId)
                put("device_model", deviceModel)
                put("device_brand", deviceBrand)
                put("android_version", androidVersion)
                put("app_version", appVersion)
                put("applied_code", cleanCode)
                put("status", "active")
            }
            val upsertReq = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/devices")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(devTelemetryJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(upsertReq).execute()

            // 6. Sign & save locally
            val signedToken = DeviceSecurityManager.signToken(rawToken, deviceHardwareHash)
            getPrefs(context).edit()
                .putString(KEY_SIGNED_TOKEN, signedToken)
                .putString(KEY_ACTIVATED_CODE, cleanCode)
                .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
                .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                .commit()

            Log.i(TAG, "Device successfully activated via direct REST fallback: $cleanCode")
            ActivationResult.Success("ডিভাইস এক্সেস সফলভাবে সক্রিয় হয়েছে!")
        } catch (t: Throwable) {
            Log.e(TAG, "directRestActivation error: ${t.message}", t)
            ActivationResult.Error("এক্সেস কোড যাচাই করা যায়নি: ${t.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}")
        }
    }

    /**
     * Checks if this device is currently banned in the Supabase database.
     * Also updates device telemetry so admin can see device immediately in admin panel.
     */
    suspend fun checkDeviceStatus(context: Context): DeviceStatusResult = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext DeviceStatusResult.Allowed
            }

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            val deviceBrand = android.os.Build.BRAND ?: ""
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = try { BuildConfig.VERSION_NAME } catch (_: Throwable) { "1.0.0" }

            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/check_device_status"

            val jsonBody = JSONObject().apply {
                put("p_device_hash", deviceHardwareHash)
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
                // Direct REST check fallback
                try {
                    val directUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/devices?device_hash=eq.$deviceHardwareHash&select=is_banned,ban_reason"
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
                            if (obj.optBoolean("is_banned", false)) {
                                clearActivation(context)
                                val reason = obj.optString("ban_reason", "এই ডিভাইসটি অ্যাডমিন কর্তৃক ব্যান করা হয়েছে।")
                                return@withContext DeviceStatusResult.Banned(reason)
                            }
                        }
                    }
                } catch (_: Throwable) {}
                return@withContext DeviceStatusResult.Allowed
            }

            val json = JSONObject(responseBody)
            val isBanned = json.optBoolean("is_banned", false)
            val reason = json.optString("reason", "এই ডিভাইসটি অ্যাডমিন কর্তৃক ব্যান করা হয়েছে।")

            if (isBanned) {
                clearActivation(context)
                DeviceStatusResult.Banned(reason)
            } else {
                DeviceStatusResult.Allowed
            }
        } catch (e: Throwable) {
            Log.e(TAG, "checkDeviceStatus error: ${e.message}")
            DeviceStatusResult.Allowed
        }
    }

    /**
     * Verifies if this device's token is still active and not banned in Supabase.
     */
    suspend fun verifyHeartbeat(context: Context): HeartbeatResult = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext HeartbeatResult.Valid // Safe fallback if config absent
            }

            val hardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val rawToken = getRawSessionToken(context) ?: return@withContext HeartbeatResult.Revoked("NO_TOKEN")

            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/verify_device_heartbeat"

            val jsonBody = JSONObject().apply {
                put("p_device_hash", hardwareHash)
                put("p_token", rawToken)
            }

            val request = try {
                Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            } catch (_: Throwable) {
                return@withContext HeartbeatResult.NetworkError("Invalid request configuration")
            }

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Throwable) {
                return@withContext HeartbeatResult.NetworkError(e.message ?: "Network error")
            }

            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext HeartbeatResult.NetworkError("HTTP ${response.code}")
            }

            val jsonRes = try {
                JSONObject(responseBody)
            } catch (_: Throwable) {
                return@withContext HeartbeatResult.NetworkError("Invalid JSON response")
            }

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
