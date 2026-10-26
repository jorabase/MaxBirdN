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

data class DeviceCoursePermissions(
    val isRestricted: Boolean = false,
    val isAccountAuthorized: Boolean = true,
    val boundUserId: String = "",
    val boundUserPhone: String = "",
    val allowedClasses: List<String> = emptyList(),
    val allowedGroups: List<String> = emptyList(),
    val allowedBatches: List<String> = emptyList(),
    val allowedPrograms: List<String> = emptyList()
) {
    fun isClassAllowed(classCode: String?, classNameBn: String? = null, classNameEn: String? = null): Boolean {
        if (!isRestricted || allowedClasses.isEmpty()) return true
        val candidates = listOfNotNull(classCode, classNameBn, classNameEn)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return true

        val normalizedCode = normalizeClassCode(classCode)
        return allowedClasses.any { allowedRaw ->
            val allowed = allowedRaw.trim()
            if (allowed.isEmpty() || allowed.equals("ALL", ignoreCase = true) || allowed == "*") return@any true
            val normalizedAllowed = normalizeClassCode(allowed)
            if (normalizedCode.isNotEmpty() && normalizedAllowed.isNotEmpty() && normalizedCode.equals(normalizedAllowed, ignoreCase = true)) {
                return@any true
            }
            candidates.any { cand ->
                cand.equals(allowed, ignoreCase = true) ||
                        cand.contains(allowed, ignoreCase = true) ||
                        allowed.contains(cand, ignoreCase = true)
            }
        }
    }

    fun isGroupAllowed(groupCode: String?, groupNameBn: String? = null, groupNameEn: String? = null): Boolean {
        if (!isRestricted || allowedGroups.isEmpty()) return true
        val candidates = listOfNotNull(groupCode, groupNameBn, groupNameEn)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return true

        val normalizedGroup = normalizeGroupCode(groupCode ?: groupNameEn ?: groupNameBn)
        return allowedGroups.any { allowedRaw ->
            val allowed = allowedRaw.trim()
            if (allowed.isEmpty() || allowed.equals("ALL", ignoreCase = true) || allowed == "*") return@any true
            val normalizedAllowed = normalizeGroupCode(allowed)
            if (normalizedGroup.isNotEmpty() && normalizedAllowed.isNotEmpty() && normalizedGroup.equals(normalizedAllowed, ignoreCase = true)) {
                return@any true
            }
            candidates.any { cand ->
                cand.equals(allowed, ignoreCase = true) ||
                        cand.contains(allowed, ignoreCase = true) ||
                        allowed.contains(cand, ignoreCase = true)
            }
        }
    }

    fun isBatchAllowed(batchYear: String?, batchLabel: String? = null): Boolean {
        if (!isRestricted || allowedBatches.isEmpty()) return true
        val cleanYear = convertBengaliToEnglishDigits(batchYear ?: "").trim()
        val cleanLabel = convertBengaliToEnglishDigits(batchLabel ?: "").trim()
        val yearDigits = Regex("\\d{4}").find(cleanYear)?.value ?: Regex("\\d{4}").find(cleanLabel)?.value ?: ""

        return allowedBatches.any { allowedRaw ->
            val allowed = convertBengaliToEnglishDigits(allowedRaw).trim()
            if (allowed.isEmpty() || allowed.equals("ALL", ignoreCase = true) || allowed == "*") return@any true
            val allowedYearDigits = Regex("\\d{4}").find(allowed)?.value ?: ""

            if (yearDigits.isNotEmpty() && allowedYearDigits.isNotEmpty() && yearDigits == allowedYearDigits) {
                return@any true
            }
            (cleanYear.isNotEmpty() && (cleanYear.equals(allowed, ignoreCase = true) || allowed.contains(cleanYear, ignoreCase = true))) ||
                    (cleanLabel.isNotEmpty() && (cleanLabel.equals(allowed, ignoreCase = true) || cleanLabel.contains(allowed, ignoreCase = true) || allowed.contains(cleanLabel, ignoreCase = true)))
        }
    }

    fun isProgramAllowed(programId: String?, titleBn: String? = null, titleEn: String? = null): Boolean {
        if (!isAccountAuthorized) return false
        if (allowedPrograms.any { it.trim().equals("__UNAUTHORIZED_ACCOUNT__", ignoreCase = true) }) return false
        if (!isRestricted || allowedPrograms.isEmpty()) return true
        val idClean = programId?.trim() ?: ""
        val bnClean = titleBn?.trim() ?: ""
        val enClean = titleEn?.trim() ?: ""

        return allowedPrograms.any { allowedRaw ->
            val allowed = allowedRaw.trim()
            if (allowed.isEmpty() || allowed.equals("ALL", ignoreCase = true) || allowed == "*") return@any true
            // Support "id|title" format if admin app stores combined strings
            val parts = allowed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            parts.any { part ->
                (idClean.isNotEmpty() && idClean.equals(part, ignoreCase = true)) ||
                        (bnClean.isNotEmpty() && (bnClean.equals(part, ignoreCase = true) || bnClean.contains(part, ignoreCase = true) || part.contains(bnClean, ignoreCase = true))) ||
                        (enClean.isNotEmpty() && (enClean.equals(part, ignoreCase = true) || enClean.contains(part, ignoreCase = true) || part.contains(enClean, ignoreCase = true)))
            }
        }
    }

    fun getPrimaryAllowedClassCode(): String? {
        if (!isRestricted || allowedClasses.isEmpty()) return null
        return allowedClasses.firstNotNullOfOrNull { raw ->
            normalizeClassCode(raw).takeIf { it.isNotBlank() }
        }
    }

    fun getPrimaryAllowedGroupCode(): String? {
        if (!isRestricted || allowedGroups.isEmpty()) return null
        return allowedGroups.firstNotNullOfOrNull { raw ->
            normalizeGroupCode(raw).takeIf { it.isNotBlank() }
        }
    }

    fun getPrimaryAllowedBatchYear(): String? {
        if (!isRestricted || allowedBatches.isEmpty()) return null
        return allowedBatches.firstNotNullOfOrNull { raw ->
            val clean = convertBengaliToEnglishDigits(raw).trim()
            Regex("\\d{4}").find(clean)?.value ?: clean.takeIf { it.isNotBlank() }
        }
    }

    companion object {
        fun convertBengaliToEnglishDigits(input: String): String {
            val bnDigits = "০১২৩৪৫৬৭৮৯"
            return input.map { ch ->
                val idx = bnDigits.indexOf(ch)
                if (idx >= 0) ('0' + idx) else ch
            }.joinToString("")
        }

        fun normalizeClassCode(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val clean = convertBengaliToEnglishDigits(raw).trim().uppercase()
            return when {
                clean in listOf("C5", "C05", "5", "CLASS 5", "CLASS-5") || raw.contains("পঞ্চম") -> "C5"
                clean in listOf("C6", "C06", "6", "CLASS 6", "CLASS-6") || raw.contains("ষষ্ঠ") -> "C6"
                clean in listOf("C7", "C07", "7", "CLASS 7", "CLASS-7") || raw.contains("সপ্তম") -> "C7"
                clean in listOf("C8", "C08", "8", "CLASS 8", "CLASS-8") || raw.contains("অষ্টম") -> "C8"
                clean in listOf("C9", "C09", "9", "CLASS 9", "CLASS-9") || raw.contains("নবম") -> "C9"
                clean in listOf("C10", "10", "CLASS 10", "CLASS-10", "SSC") || raw.contains("দশম") -> "C10"
                clean in listOf("C11", "11", "CLASS 11", "CLASS-11", "HSC 1ST") || raw.contains("একাদশ") -> "C11"
                clean in listOf("C12", "12", "CLASS 12", "CLASS-12", "HSC 2ND") || raw.contains("দ্বাদশ") -> "C12"
                clean.matches(Regex("C\\d+")) -> clean
                else -> clean
            }
        }

        fun normalizeGroupCode(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val clean = raw.trim().lowercase()
            return when {
                clean in listOf("science", "sci", "বিজ্ঞান") || clean.contains("বিজ্ঞান") || clean.contains("science") -> "Science"
                clean in listOf("humanities", "hum", "arts", "মানবিক") || clean.contains("মানবিক") || clean.contains("humanities") || clean.contains("arts") -> "Humanities"
                clean in listOf("businessstudies", "business_studies", "business", "commerce", "bus", "ব্যবসায় শিক্ষা", "ব্যবসায় শিক্ষা") || clean.contains("ব্যবসা") || clean.contains("business") || clean.contains("commerce") -> "BusinessStudies"
                clean in listOf("none", "all") -> "None"
                else -> raw.trim()
            }
        }
    }
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
    private const val KEY_COURSE_PERMISSIONS_JSON = "key_course_permissions_json"
    private const val KEY_LAST_COURSE_PERMISSIONS_CHECK = "key_last_course_permissions_check"

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

            val expiresAtStr = codeObj.optString("expires_at")
            if (!expiresAtStr.isNullOrBlank() && !expiresAtStr.equals("null", ignoreCase = true)) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val cleanExpiry = expiresAtStr.replace("Z", "").substringBefore("+").substringBefore(".")
                    val expireTime = sdf.parse(cleanExpiry)?.time ?: Long.MAX_VALUE
                    if (System.currentTimeMillis() > expireTime) {
                        return@withContext ActivationResult.Error("এই এক্সেস কোডটির মেয়াদ শেষ হয়ে গেছে। নতুন কোড সংগ্রহ করুন।")
                    }
                } catch (_: Exception) {}
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
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/devices?on_conflict=device_hash")
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

    private fun parseStringListFromJsonValue(obj: JSONObject, key: String): List<String> {
        if (!obj.has(key) || obj.isNull(key)) return emptyList()
        return try {
            val rawVal = obj.get(key)
            when (rawVal) {
                is org.json.JSONArray -> {
                    val list = mutableListOf<String>()
                    for (i in 0 until rawVal.length()) {
                        val item = rawVal.optString(i, "").trim()
                        if (item.isNotBlank() && !item.equals("null", ignoreCase = true)) {
                            list.add(item)
                        }
                    }
                    list
                }
                is String -> {
                    val trimmed = rawVal.trim()
                    if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed == "[]" || trimmed == "{}") {
                        emptyList()
                    } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        val arr = org.json.JSONArray(trimmed)
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val item = arr.optString(i, "").trim()
                            if (item.isNotBlank() && !item.equals("null", ignoreCase = true)) {
                                list.add(item)
                            }
                        }
                        list
                    } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        // Postgres TEXT[] literal format: {"C9","C10"}
                        trimmed.removeSurrounding("{", "}")
                            .split(",")
                            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").trim() }
                            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    } else {
                        trimmed.split(",")
                            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").trim() }
                            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    }
                }
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseCoursePermissionsFromJsonObject(
        obj: JSONObject,
        currentUserId: String = "",
        currentUserPhone: String = ""
    ): DeviceCoursePermissions? {
        val classes = parseStringListFromJsonValue(obj, "allowed_classes")
        val groups = parseStringListFromJsonValue(obj, "allowed_groups")
        val batches = parseStringListFromJsonValue(obj, "allowed_batches")
        val programs = parseStringListFromJsonValue(obj, "allowed_programs")
            .ifEmpty { parseStringListFromJsonValue(obj, "allowed_courses") }

        val explicitFlag = if (obj.has("is_access_restricted") && !obj.isNull("is_access_restricted")) {
            obj.optBoolean("is_access_restricted", false)
        } else if (obj.has("is_restricted") && !obj.isNull("is_restricted")) {
            obj.optBoolean("is_restricted", false)
        } else if (obj.has("is_access_granted") && !obj.isNull("is_access_granted")) {
            obj.optBoolean("is_access_granted", false)
        } else {
            null
        }

        val boundUserId = obj.optString("bound_user_id", "")
            .ifBlank { obj.optString("bound_shikho_user_id", "") }
            .ifBlank { obj.optString("shikho_user_id", "") }
            .trim()
        val boundUserPhone = obj.optString("bound_user_phone", "")
            .ifBlank { obj.optString("user_phone", "") }
            .trim()

        var isAccountAuthorized = if (obj.has("is_account_authorized") && !obj.isNull("is_account_authorized")) {
            obj.optBoolean("is_account_authorized", true)
        } else {
            true
        }

        // Check if boundUserId is set to a DIFFERENT account than the currently logged-in Shikho account
        if (isAccountAuthorized && boundUserId.isNotBlank() && currentUserId.isNotBlank()) {
            val uidMatches = boundUserId.equals(currentUserId.trim(), ignoreCase = true)
            val phoneMatches = boundUserPhone.isNotBlank() && currentUserPhone.isNotBlank() &&
                    boundUserPhone.equals(currentUserPhone.trim(), ignoreCase = true)
            if (!uidMatches && !phoneMatches) {
                isAccountAuthorized = false
            }
        }

        if (!isAccountAuthorized) {
            return DeviceCoursePermissions(
                isRestricted = true,
                isAccountAuthorized = false,
                boundUserId = boundUserId,
                boundUserPhone = boundUserPhone,
                allowedClasses = emptyList(),
                allowedGroups = emptyList(),
                allowedBatches = emptyList(),
                allowedPrograms = listOf("__UNAUTHORIZED_ACCOUNT__")
            )
        }

        val hasAnyRule = classes.isNotEmpty() || groups.isNotEmpty() || batches.isNotEmpty() || programs.isNotEmpty()
        val isRestricted = explicitFlag ?: hasAnyRule

        if (!isRestricted && !hasAnyRule) {
            return DeviceCoursePermissions(
                isRestricted = false,
                isAccountAuthorized = true,
                boundUserId = boundUserId,
                boundUserPhone = boundUserPhone
            )
        }

        return DeviceCoursePermissions(
            isRestricted = isRestricted || hasAnyRule,
            isAccountAuthorized = true,
            boundUserId = boundUserId,
            boundUserPhone = boundUserPhone,
            allowedClasses = classes,
            allowedGroups = groups,
            allowedBatches = batches,
            allowedPrograms = programs
        )
    }

    fun clearCachedCoursePermissions(context: Context) {
        try {
            getPrefs(context).edit()
                .remove(KEY_COURSE_PERMISSIONS_JSON)
                .remove(KEY_LAST_COURSE_PERMISSIONS_CHECK)
                .apply()
        } catch (_: Throwable) {}
    }

    fun getCachedCoursePermissions(context: Context): DeviceCoursePermissions {
        return try {
            val rawJson = getPrefs(context).getString(KEY_COURSE_PERMISSIONS_JSON, null)
            if (rawJson.isNullOrBlank()) return DeviceCoursePermissions(isRestricted = false)
            val obj = JSONObject(rawJson)
            val session = com.example.auth.SessionManager(context)
            val currentUid = session.getUserId()?.trim() ?: ""
            val currentPhone = session.getUserPhone()?.trim() ?: ""
            val cachedForUid = obj.optString("cached_for_user_id", "").trim()
            if (currentUid.isNotBlank() && cachedForUid.isNotBlank() && !currentUid.equals(cachedForUid, ignoreCase = true)) {
                return DeviceCoursePermissions(isRestricted = false)
            }
            parseCoursePermissionsFromJsonObject(obj, currentUid, currentPhone)
                ?: DeviceCoursePermissions(isRestricted = false)
        } catch (_: Throwable) {
            DeviceCoursePermissions(isRestricted = false)
        }
    }

    private fun saveCachedCoursePermissions(
        context: Context,
        perms: DeviceCoursePermissions,
        currentUserId: String = ""
    ) {
        try {
            val json = JSONObject().apply {
                put("is_access_restricted", perms.isRestricted)
                put("is_account_authorized", perms.isAccountAuthorized)
                put("bound_user_id", perms.boundUserId)
                put("bound_user_phone", perms.boundUserPhone)
                put("cached_for_user_id", currentUserId)
                put("allowed_classes", org.json.JSONArray(perms.allowedClasses))
                put("allowed_groups", org.json.JSONArray(perms.allowedGroups))
                put("allowed_batches", org.json.JSONArray(perms.allowedBatches))
                put("allowed_programs", org.json.JSONArray(perms.allowedPrograms))
            }
            getPrefs(context).edit()
                .putString(KEY_COURSE_PERMISSIONS_JSON, json.toString())
                .putLong(KEY_LAST_COURSE_PERMISSIONS_CHECK, System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {}
    }

    /**
     * Registers the currently logged-in Shikho account under this device in `public.device_shikho_accounts`
     * AND fetches the resolved per-account + per-device syllabus & course permissions.
     */
    suspend fun fetchDeviceCoursePermissions(
        context: Context,
        forceRefresh: Boolean = false
    ): DeviceCoursePermissions = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_COURSE_PERMISSIONS_CHECK, 0L)

        val sessionManager = com.example.auth.SessionManager(context)
        val currentUserId = sessionManager.getUserId()?.trim() ?: ""
        val currentUserPhone = sessionManager.getUserPhone()?.trim() ?: ""

        // Check if cached permissions belong to a different Shikho account
        val isSameUserCached = try {
            val rawJson = prefs.getString(KEY_COURSE_PERMISSIONS_JSON, null)
            if (rawJson.isNullOrBlank()) false
            else {
                val cachedUid = JSONObject(rawJson).optString("cached_for_user_id", "").trim()
                cachedUid == currentUserId
            }
        } catch (_: Throwable) { false }

        if (!forceRefresh && isSameUserCached && (now - lastCheck < 10_000L)) {
            return@withContext getCachedCoursePermissions(context)
        }

        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext getCachedCoursePermissions(context)
            }

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val activatedCode = prefs.getString(KEY_ACTIVATED_CODE, "")?.trim() ?: ""

            // 0. If user is logged into a Shikho account, register/sync the account under this device via `register_device_shikho_account`
            if (currentUserId.isNotBlank() || currentUserPhone.isNotBlank()) {
                try {
                    val regUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/register_device_shikho_account"
                    val regBody = JSONObject().apply {
                        put("p_device_hash", deviceHardwareHash)
                        put("p_display_device_id", displayDeviceId)
                        put("p_shikho_user_id", currentUserId.ifBlank { currentUserPhone })
                        put("p_user_phone", currentUserPhone)
                        put("p_student_name", sessionManager.getUserFullName() ?: "")
                        put("p_user_avatar", sessionManager.getUserAvatar() ?: "")
                        put("p_school_name", sessionManager.getUserSchoolName() ?: "")
                        put("p_current_class", sessionManager.getUserClassName() ?: "")
                        put("p_current_group", sessionManager.getUserGroup() ?: "")
                        put("p_current_batch", sessionManager.getUserBatchId() ?: "")
                    }
                    val regReq = Request.Builder()
                        .url(regUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .post(regBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val regResp = httpClient.newCall(regReq).execute()
                    if (regResp.isSuccessful) {
                        val bodyStr = regResp.body?.string() ?: ""
                        if (bodyStr.isNotBlank() && bodyStr.trim().startsWith("{")) {
                            val json = JSONObject(bodyStr)
                            val parsed = parseCoursePermissionsFromJsonObject(json, currentUserId, currentUserPhone)
                            if (parsed != null) {
                                saveCachedCoursePermissions(context, parsed, currentUserId)
                                return@withContext parsed
                            }
                        }
                    }
                } catch (_: Throwable) {}

                // Also try direct REST upsert into `device_shikho_accounts` in case RPC hasn't been created yet
                try {
                    val accUpsertUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/device_shikho_accounts?on_conflict=device_hash,shikho_user_id"
                    val accObj = JSONObject().apply {
                        put("device_hash", deviceHardwareHash)
                        put("display_device_id", displayDeviceId)
                        put("shikho_user_id", currentUserId.ifBlank { currentUserPhone })
                        put("user_phone", currentUserPhone)
                        put("student_name", sessionManager.getUserFullName() ?: "")
                        put("user_avatar", sessionManager.getUserAvatar() ?: "")
                        put("school_name", sessionManager.getUserSchoolName() ?: "")
                        put("current_class", sessionManager.getUserClassName() ?: "")
                        put("current_group", sessionManager.getUserGroup() ?: "")
                        put("current_batch", sessionManager.getUserBatchId() ?: "")
                        put("last_seen_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }.format(java.util.Date()))
                    }
                    val accReq = Request.Builder()
                        .url(accUpsertUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(accObj.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(accReq).execute().close()
                } catch (_: Throwable) {}
            }

            // 1. Try dedicated RPC `get_device_course_permissions` (with account parameters)
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_device_course_permissions"
                val rpcBody = JSONObject().apply {
                    put("p_device_hash", deviceHardwareHash)
                    put("p_display_device_id", displayDeviceId)
                    put("p_code", activatedCode)
                    put("p_shikho_user_id", currentUserId)
                    put("p_user_phone", currentUserPhone)
                }
                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val rpcResp = httpClient.newCall(rpcReq).execute()
                if (rpcResp.isSuccessful) {
                    val bodyStr = rpcResp.body?.string() ?: ""
                    if (bodyStr.isNotBlank() && bodyStr.trim().startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        val parsed = parseCoursePermissionsFromJsonObject(json, currentUserId, currentUserPhone)
                        if (parsed != null) {
                            saveCachedCoursePermissions(context, parsed, currentUserId)
                            return@withContext parsed
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 2. Fallback: Direct REST query on `device_shikho_accounts` table for this device
            var resolvedPermissions: DeviceCoursePermissions? = null
            if (currentUserId.isNotBlank() || currentUserPhone.isNotBlank()) {
                try {
                    val accListUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/device_shikho_accounts?or=(device_hash.eq.$deviceHardwareHash,display_device_id.eq.$displayDeviceId)&select=shikho_user_id,user_phone,is_access_granted,is_access_restricted,allowed_classes,allowed_groups,allowed_batches,allowed_programs"
                    val accListReq = Request.Builder()
                        .url(accListUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .get()
                        .build()
                    val accListResp = httpClient.newCall(accListReq).execute()
                    if (accListResp.isSuccessful) {
                        val arr = org.json.JSONArray(accListResp.body?.string() ?: "[]")
                        var myAccountPerm: DeviceCoursePermissions? = null
                        var otherAuthorizedAccountExists = false
                        var otherBoundUid = ""
                        var otherBoundPhone = ""

                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val itemUid = item.optString("shikho_user_id", "").trim()
                            val itemPhone = item.optString("user_phone", "").trim()
                            val isMine = (currentUserId.isNotBlank() && itemUid.equals(currentUserId, ignoreCase = true)) ||
                                    (currentUserPhone.isNotBlank() && itemPhone.isNotBlank() && itemPhone.equals(currentUserPhone, ignoreCase = true))

                            val parsed = parseCoursePermissionsFromJsonObject(item, itemUid, itemPhone)
                            val hasGrant = item.optBoolean("is_access_granted", false) ||
                                    item.optBoolean("is_access_restricted", false) ||
                                    (parsed != null && parsed.isRestricted)

                            if (isMine && hasGrant && parsed != null) {
                                myAccountPerm = parsed.copy(isAccountAuthorized = true)
                            } else if (!isMine && hasGrant) {
                                otherAuthorizedAccountExists = true
                                otherBoundUid = itemUid
                                otherBoundPhone = itemPhone
                            }
                        }

                        if (myAccountPerm != null) {
                            resolvedPermissions = myAccountPerm
                        } else if (otherAuthorizedAccountExists) {
                            // Another Shikho account on this same device was granted access by Admin!
                            // Block this secondary account from seeing unlocked courses.
                            resolvedPermissions = DeviceCoursePermissions(
                                isRestricted = true,
                                isAccountAuthorized = false,
                                boundUserId = otherBoundUid,
                                boundUserPhone = otherBoundPhone,
                                allowedPrograms = listOf("__UNAUTHORIZED_ACCOUNT__")
                            )
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 3. Fallback: Direct REST query on `devices` table
            if (resolvedPermissions == null) {
                try {
                    val devUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/devices?or=(device_hash.eq.$deviceHardwareHash,display_device_id.eq.$displayDeviceId)&select=is_access_restricted,bound_shikho_user_id,bound_user_phone,allowed_classes,allowed_groups,allowed_batches,allowed_programs"
                    val devReq = Request.Builder()
                        .url(devUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .get()
                        .build()
                    val devResp = httpClient.newCall(devReq).execute()
                    if (devResp.isSuccessful) {
                        val devArr = org.json.JSONArray(devResp.body?.string() ?: "[]")
                        if (devArr.length() > 0) {
                            val parsed = parseCoursePermissionsFromJsonObject(devArr.getJSONObject(0), currentUserId, currentUserPhone)
                            if (parsed != null && parsed.isRestricted) {
                                resolvedPermissions = parsed
                            } else if (parsed != null && resolvedPermissions == null) {
                                resolvedPermissions = parsed
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 4. Fallback: Check `access_codes` table if device itself didn't have active restrictions
            if ((resolvedPermissions == null || !resolvedPermissions.isRestricted) && activatedCode.isNotBlank()) {
                try {
                    val codeUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/access_codes?code=ilike.$activatedCode&select=is_access_restricted,allowed_classes,allowed_groups,allowed_batches,allowed_programs"
                    val codeReq = Request.Builder()
                        .url(codeUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .get()
                        .build()
                    val codeResp = httpClient.newCall(codeReq).execute()
                    if (codeResp.isSuccessful) {
                        val codeArr = org.json.JSONArray(codeResp.body?.string() ?: "[]")
                        if (codeArr.length() > 0) {
                            val parsed = parseCoursePermissionsFromJsonObject(codeArr.getJSONObject(0), currentUserId, currentUserPhone)
                            if (parsed != null && parsed.isRestricted) {
                                resolvedPermissions = parsed
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 5. Fallback: Check `activated_devices` table
            if (resolvedPermissions == null || !resolvedPermissions.isRestricted) {
                try {
                    val actUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/activated_devices?device_hash=eq.$deviceHardwareHash&select=is_access_restricted,allowed_classes,allowed_groups,allowed_batches,allowed_programs"
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
                            val parsed = parseCoursePermissionsFromJsonObject(actArr.getJSONObject(0), currentUserId, currentUserPhone)
                            if (parsed != null && parsed.isRestricted) {
                                resolvedPermissions = parsed
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            val finalPerms = resolvedPermissions ?: DeviceCoursePermissions(isRestricted = false, isAccountAuthorized = true)
            saveCachedCoursePermissions(context, finalPerms, currentUserId)
            finalPerms
        } catch (e: Throwable) {
            Log.e(TAG, "fetchDeviceCoursePermissions error: ${e.message}")
            getCachedCoursePermissions(context)
        }
    }

    /**
     * Automatically syncs discovered courses/programs to `public.available_courses_catalog` in Supabase
     * so the Admin App can display a clean checkbox list of courses for each class/group/batch.
     */
    suspend fun syncCourseCatalogToSupabase(
        programs: List<com.example.api.EnrolledProgram>,
        classCode: String?,
        groupCode: String?,
        batchId: String?
    ) = withContext(Dispatchers.IO) {
        if (programs.isEmpty()) return@withContext
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext

            val normalizedClass = DeviceCoursePermissions.normalizeClassCode(classCode).ifBlank { classCode ?: "" }
            val normalizedGroup = DeviceCoursePermissions.normalizeGroupCode(groupCode).ifBlank { groupCode ?: "None" }

            val jsonArray = org.json.JSONArray()
            programs.forEach { prog ->
                if (prog.id.isNotBlank()) {
                    val progClass = prog.classes?.firstOrNull()?.let { DeviceCoursePermissions.normalizeClassCode(it) }
                        ?.takeIf { it.isNotBlank() } ?: normalizedClass
                    val progBatch = prog.enrollment_details?.batch_id?.takeIf { it.isNotBlank() } ?: (batchId ?: "")
                    val obj = JSONObject().apply {
                        put("program_id", prog.id)
                        put("title_bn", prog.title_bn ?: "কোর্স")
                        put("class_code", progClass)
                        put("group_code", normalizedGroup)
                        put("batch_id", progBatch)
                        put("banner_url", prog.banner_url ?: "")
                        put("is_free", prog.is_free == true)
                    }
                    jsonArray.put(obj)
                }
            }

            if (jsonArray.length() == 0) return@withContext

            val catalogUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/available_courses_catalog?on_conflict=program_id"
            val req = Request.Builder()
                .url(catalogUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(jsonArray.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().close()
        } catch (_: Throwable) {
            // Non-fatal background sync for Admin App convenience
        }
    }

    /**
     * Syncs the active Shikho account & JWT token to Supabase (`public.device_shikho_accounts` and `public.devices`)
     * so the Admin App can see every Shikho account logged in on this device and manage per-account permissions.
     */
    suspend fun syncRealSyllabusAndTokenForAdmin(
        context: Context,
        sessionManager: com.example.auth.SessionManager,
        apiService: com.example.api.ShikhoApiService
    ) = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext

            val accessToken = sessionManager.getAccessToken() ?: ""
            val refreshToken = sessionManager.getRefreshToken() ?: ""
            val deviceHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val userId = sessionManager.getUserId()?.trim() ?: ""
            val userPhone = sessionManager.getUserPhone()?.trim() ?: ""

            // 1. Register / update this Shikho account in `public.device_shikho_accounts`
            if (userId.isNotBlank() || userPhone.isNotBlank()) {
                try {
                    val regUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/register_device_shikho_account"
                    val regBody = JSONObject().apply {
                        put("p_device_hash", deviceHash)
                        put("p_display_device_id", displayDeviceId)
                        put("p_shikho_user_id", userId.ifBlank { userPhone })
                        put("p_user_phone", userPhone)
                        put("p_student_name", sessionManager.getUserFullName() ?: "")
                        put("p_user_avatar", sessionManager.getUserAvatar() ?: "")
                        put("p_school_name", sessionManager.getUserSchoolName() ?: "")
                        put("p_current_class", sessionManager.getUserClassName() ?: "")
                        put("p_current_group", sessionManager.getUserGroup() ?: "")
                        put("p_current_batch", sessionManager.getUserBatchId() ?: "")
                    }
                    val regReq = Request.Builder()
                        .url(regUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .post(regBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(regReq).execute().close()
                } catch (_: Throwable) {}
            }

            // 2. Update device's latest live Shikho token in `public.devices`
            if (accessToken.isNotBlank()) {
                try {
                    val tokenUpdateJson = JSONObject().apply {
                        put("shikho_access_token", accessToken)
                        put("shikho_refresh_token", refreshToken)
                        put("updated_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }.format(java.util.Date()))
                    }
                    val req = Request.Builder()
                        .url("${supabaseUrl.trimEnd('/')}/rest/v1/devices?device_hash=eq.$deviceHash")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .patch(tokenUpdateJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().close()
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }
}
