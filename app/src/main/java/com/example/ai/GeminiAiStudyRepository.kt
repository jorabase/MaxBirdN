package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.auth.SessionManager
import com.example.security.DeviceActivationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GeminiConfigState(
    val apiKey: String = "",
    val isConfigured: Boolean = false,
    val preferredModel: String = "gemini-3.8-flash",
    val displayModelName: String = "Gemini 3.8 Flash",
    val syncStatusText: String = "সেটআপ করা হয়নি",
    val isCloudSynced: Boolean = false,
    val isSyncing: Boolean = false,
    val accountIdentifier: String = ""
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val attachedSlidePage: Int? = null,
    val attachedSlideCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

sealed class GeminiTutorResult {
    data class Success(val explanation: String, val modelUsed: String) : GeminiTutorResult()
    data class Error(val message: String, val isMissingKey: Boolean = false) : GeminiTutorResult()
}

object GeminiAiStudyRepository {
    private const val TAG = "GeminiAiStudyRepo"
    private const val PREFS_NAME = "shikho_gemini_ai_prefs"
    private const val KEY_ACTIVE_API_KEY = "active_gemini_api_key"
    private const val KEY_ACCOUNT_PREFIX = "gemini_key_acc_"
    private const val KEY_SYNC_STATUS = "gemini_sync_status_text"
    private const val KEY_IS_CLOUD_SYNCED = "gemini_is_cloud_synced"

    const val PRIMARY_MODEL_ID = "gemini-3.8-flash"
    const val DISPLAY_MODEL_NAME = "Gemini 3.8 Flash"

    // Fallback chain in case the public Google endpoint routes 3.8 Flash under gemini-flash-latest or gemini-3.5-flash
    private val MODEL_CANDIDATES = listOf(
        "gemini-3.8-flash",
        "gemini-flash-latest",
        "gemini-3.5-flash"
    )

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 60-second timeouts as required by Gemini API best practices for complex multimodal slide analysis
    private val geminiHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val supabaseHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _configState = MutableStateFlow(GeminiConfigState())
    val configState: StateFlow<GeminiConfigState> = _configState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Resolves all account identifiers for the currently logged-in student so that
     * logging in via Phone+PIN, OTP, QR Session, or Access Code on ANY device restores their key.
     */
    data class StudentAccountIdentity(
        val accountKey: String,
        val userId: String,
        val userPhone: String,
        val accessKey: String,
        val studentName: String
    )

    fun resolveIdentity(context: Context, sessionManager: SessionManager? = null): StudentAccountIdentity {
        val mgr = sessionManager ?: SessionManager(context.applicationContext)
        val userId = mgr.getUserId()?.trim().orEmpty()
        val userPhone = mgr.getNormalizedUserPhone().ifBlank { mgr.getUserPhone()?.trim().orEmpty() }
        val studentName = mgr.getUserFullName()?.trim()
            ?: mgr.getUserFirstName()?.trim()
            ?: "শিক্ষার্থী"

        val primaryKey = when {
            userPhone.isNotBlank() -> userPhone
            userId.isNotBlank() -> userId
            else -> ""
        }

        return StudentAccountIdentity(
            accountKey = primaryKey,
            userId = userId,
            userPhone = userPhone,
            accessKey = "",
            studentName = studentName
        )
    }

    /**
     * Initializes local state and triggers a non-blocking cloud sync if needed.
     */
    fun initialize(context: Context, sessionManager: SessionManager? = null) {
        val prefs = getPrefs(context)
        val identity = resolveIdentity(context, sessionManager)

        // Check account-specific key first (by phone or userId), then BuildConfig fallback (only if valid AIza key)
        val accountSavedKey = if (identity.accountKey.isNotBlank()) {
            prefs.getString("$KEY_ACCOUNT_PREFIX${identity.accountKey}", null)?.trim().orEmpty()
                .ifBlank {
                    if (identity.userId.isNotBlank()) prefs.getString("$KEY_ACCOUNT_PREFIX${identity.userId}", null)?.trim().orEmpty() else ""
                }
        } else ""
        val rawBuildConfigKey = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        val buildConfigKey = if (rawBuildConfigKey.startsWith("AIza")) rawBuildConfigKey else ""

        val effectiveKey = when {
            accountSavedKey.isNotBlank() -> accountSavedKey
            buildConfigKey.isNotBlank() -> buildConfigKey
            else -> ""
        }

        val isCloudSynced = prefs.getBoolean(KEY_IS_CLOUD_SYNCED, false)
        val savedStatus = prefs.getString(KEY_SYNC_STATUS, null) ?: if (effectiveKey.isNotBlank()) {
            "ডিভাইসে সংরক্ষিত আছে"
        } else {
            "API Key যুক্ত করা হয়নি"
        }

        _configState.value = GeminiConfigState(
            apiKey = effectiveKey,
            isConfigured = effectiveKey.isNotBlank(),
            preferredModel = PRIMARY_MODEL_ID,
            displayModelName = DISPLAY_MODEL_NAME,
            syncStatusText = savedStatus,
            isCloudSynced = isCloudSynced && effectiveKey.isNotBlank(),
            isSyncing = false,
            accountIdentifier = identity.userPhone.ifBlank { identity.userId.ifBlank { identity.accessKey } }
        )
    }

    /**
     * Returns the currently active Gemini API Key.
     */
    fun getEffectiveApiKey(context: Context, sessionManager: SessionManager? = null): String {
        val currentInState = _configState.value.apiKey.trim()
        if (currentInState.isNotBlank()) return currentInState

        initialize(context, sessionManager)
        return _configState.value.apiKey.trim()
    }

    /**
     * Saves the user's Gemini API key locally and synchronizes it with the Supabase database
     * so that reinstalling the app or logging in on another phone automatically restores it.
     */
    suspend fun saveAndSyncApiKey(
        context: Context,
        apiKeyInput: String,
        sessionManager: SessionManager? = null,
        verifyFirst: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKeyInput.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("অনুগ্রহ করে একটি সঠিক Gemini API Key দিন।"))
        }

        _configState.update { it.copy(isSyncing = true, syncStatusText = "API Key যাচাই ও সেভ করা হচ্ছে...") }

        if (verifyFirst) {
            val verifyRes = testApiKeyValidity(cleanKey)
            if (verifyRes.isFailure) {
                _configState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatusText = "API Key যাচাই ব্যর্থ হয়েছে"
                    )
                }
                return@withContext Result.failure(
                    verifyRes.exceptionOrNull() ?: Exception("API Key সঠিক নয় বা কাজ করছে না।")
                )
            }
        }

        val identity = resolveIdentity(context, sessionManager)
        val prefs = getPrefs(context)

        // Save locally for both active session and specific account identifiers
        val editor = prefs.edit()
            .putString(KEY_ACTIVE_API_KEY, cleanKey)
            .putString("$KEY_ACCOUNT_PREFIX${identity.accountKey}", cleanKey)
        if (identity.userId.isNotBlank()) {
            editor.putString("$KEY_ACCOUNT_PREFIX${identity.userId}", cleanKey)
        }
        if (identity.userPhone.isNotBlank()) {
            editor.putString("$KEY_ACCOUNT_PREFIX${identity.userPhone}", cleanKey)
        }
        if (identity.accessKey.isNotBlank()) {
            editor.putString("$KEY_ACCOUNT_PREFIX${identity.accessKey}", cleanKey)
        }
        editor.apply()

        // Sync to Supabase Cloud Database
        val cloudSaved = uploadKeyToSupabase(identity, cleanKey)
        val timeFormatted = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
        val statusMsg = if (cloudSaved) {
            "☁️ ক্লাউড ডাটাবেজে সংরক্ষিত ($timeFormatted)"
        } else {
            "ডিভাইসে সংরক্ষিত আছে (ক্লাউড সিঙ্ক অপেক্ষমাণ)"
        }

        prefs.edit()
            .putBoolean(KEY_IS_CLOUD_SYNCED, cloudSaved)
            .putString(KEY_SYNC_STATUS, statusMsg)
            .apply()

        _configState.value = GeminiConfigState(
            apiKey = cleanKey,
            isConfigured = true,
            preferredModel = PRIMARY_MODEL_ID,
            displayModelName = DISPLAY_MODEL_NAME,
            syncStatusText = statusMsg,
            isCloudSynced = cloudSaved,
            isSyncing = false,
            accountIdentifier = identity.userPhone.ifBlank { identity.userId.ifBlank { identity.accessKey } }
        )

        Result.success(
            if (cloudSaved) {
                "Gemini API Key সফলভাবে যাচাই ও ক্লাউড ডাটাবেজে সেভ হয়েছে!"
            } else {
                "Gemini API Key সফলভাবে সেভ হয়েছে!"
            }
        )
    }

    /**
     * Automatically fetches and restores the user's Gemini API key from Supabase upon login or app launch.
     */
    suspend fun syncApiKeyFromCloud(
        context: Context,
        sessionManager: SessionManager? = null,
        forceRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        try {
            initialize(context, sessionManager)
            val identity = resolveIdentity(context, sessionManager)
            val prefs = getPrefs(context)

            val localKey = prefs.getString("$KEY_ACCOUNT_PREFIX${identity.accountKey}", null)?.trim()
                ?: prefs.getString(KEY_ACTIVE_API_KEY, null)?.trim()
                ?: ""

            if (localKey.isNotBlank() && !forceRefresh && prefs.getBoolean(KEY_IS_CLOUD_SYNCED, false)) {
                // Already synced for this account; ensure active slot has it
                return@withContext localKey
            }

            _configState.update { it.copy(isSyncing = true) }

            val cloudKey = fetchKeyFromSupabase(identity)
            if (!cloudKey.isNullOrBlank()) {
                val timeFormatted = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
                val statusMsg = "☁️ ক্লাউড ডাটাবেজ থেকে রিস্টোর হয়েছে ($timeFormatted)"

                val editor = prefs.edit()
                    .putString(KEY_ACTIVE_API_KEY, cloudKey)
                    .putString("$KEY_ACCOUNT_PREFIX${identity.accountKey}", cloudKey)
                    .putBoolean(KEY_IS_CLOUD_SYNCED, true)
                    .putString(KEY_SYNC_STATUS, statusMsg)

                if (identity.userId.isNotBlank()) editor.putString("$KEY_ACCOUNT_PREFIX${identity.userId}", cloudKey)
                if (identity.userPhone.isNotBlank()) editor.putString("$KEY_ACCOUNT_PREFIX${identity.userPhone}", cloudKey)
                if (identity.accessKey.isNotBlank()) editor.putString("$KEY_ACCOUNT_PREFIX${identity.accessKey}", cloudKey)
                editor.apply()

                _configState.value = GeminiConfigState(
                    apiKey = cloudKey,
                    isConfigured = true,
                    preferredModel = PRIMARY_MODEL_ID,
                    displayModelName = DISPLAY_MODEL_NAME,
                    syncStatusText = statusMsg,
                    isCloudSynced = true,
                    isSyncing = false,
                    accountIdentifier = identity.userPhone.ifBlank { identity.userId.ifBlank { identity.accessKey } }
                )
                return@withContext cloudKey
            } else if (localKey.isNotBlank()) {
                // We have a local key that wasn't in cloud yet -> push it to cloud!
                val uploaded = uploadKeyToSupabase(identity, localKey)
                val statusMsg = if (uploaded) "☁️ ক্লাউড ডাটাবেজে সংরক্ষিত" else "ডিভাইসে সংরক্ষিত আছে"
                prefs.edit()
                    .putBoolean(KEY_IS_CLOUD_SYNCED, uploaded)
                    .putString(KEY_SYNC_STATUS, statusMsg)
                    .apply()
                _configState.update {
                    it.copy(
                        apiKey = localKey,
                        isConfigured = true,
                        isCloudSynced = uploaded,
                        syncStatusText = statusMsg,
                        isSyncing = false
                    )
                }
                return@withContext localKey
            } else {
                _configState.update { it.copy(isSyncing = false) }
                return@withContext null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "syncApiKeyFromCloud error: ${e.message}")
            _configState.update { it.copy(isSyncing = false) }
            return@withContext _configState.value.apiKey.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Triggers background cloud sync without blocking caller (ideal for Login / QR import / App start).
     */
    fun triggerBackgroundSync(context: Context, sessionManager: SessionManager? = null) {
        val appCtx = context.applicationContext
        backgroundScope.launch {
            syncApiKeyFromCloud(appCtx, sessionManager, forceRefresh = true)
        }
    }

    /**
     * Removes the API key from both local storage and Supabase cloud for the current account.
     */
    suspend fun deleteApiKey(
        context: Context,
        sessionManager: SessionManager? = null
    ) = withContext(Dispatchers.IO) {
        val identity = resolveIdentity(context, sessionManager)
        val prefs = getPrefs(context)

        val editor = prefs.edit()
            .remove(KEY_ACTIVE_API_KEY)
            .remove("$KEY_ACCOUNT_PREFIX${identity.accountKey}")
            .putBoolean(KEY_IS_CLOUD_SYNCED, false)
            .putString(KEY_SYNC_STATUS, "API Key মুছে ফেলা হয়েছে")

        if (identity.userId.isNotBlank()) editor.remove("$KEY_ACCOUNT_PREFIX${identity.userId}")
        if (identity.userPhone.isNotBlank()) editor.remove("$KEY_ACCOUNT_PREFIX${identity.userPhone}")
        if (identity.accessKey.isNotBlank()) editor.remove("$KEY_ACCOUNT_PREFIX${identity.accessKey}")
        editor.apply()

        // Also clear from Supabase
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
                val encAccount = URLEncoder.encode(identity.accountKey, "UTF-8")
                val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/user_gemini_api_keys?account_key=eq.$encAccount"
                val req = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .delete()
                    .build()
                supabaseHttpClient.newCall(req).execute().close()
            }
        } catch (_: Throwable) {}

        _configState.value = GeminiConfigState(
            apiKey = "",
            isConfigured = false,
            preferredModel = PRIMARY_MODEL_ID,
            displayModelName = DISPLAY_MODEL_NAME,
            syncStatusText = "API Key যুক্ত করা হয়নি",
            isCloudSynced = false,
            isSyncing = false,
            accountIdentifier = identity.userPhone.ifBlank { identity.userId.ifBlank { identity.accessKey } }
        )
    }

    /**
     * Clears the active session key when the user logs out so the next account fetches its own key.
     */
    fun onUserLogout(context: Context) {
        try {
            getPrefs(context).edit()
                .remove(KEY_ACTIVE_API_KEY)
                .putBoolean(KEY_IS_CLOUD_SYNCED, false)
                .putString(KEY_SYNC_STATUS, "API Key যুক্ত করা হয়নি")
                .apply()
            _configState.value = GeminiConfigState()
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // SUPABASE CLOUD SYNC HELPERS (RPC + Direct REST Fallback)
    // =========================================================================

    private suspend fun uploadKeyToSupabase(
        identity: StudentAccountIdentity,
        apiKey: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext false

            // 1. Try Atomic RPC `upsert_user_gemini_api_key` first
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/upsert_user_gemini_api_key"
                val rpcPayload = JSONObject().apply {
                    put("p_account_key", identity.accountKey)
                    put("p_user_id", identity.userId)
                    put("p_user_phone", identity.userPhone)
                    put("p_access_key", identity.accessKey)
                    put("p_student_name", identity.studentName)
                    put("p_gemini_api_key", apiKey)
                    put("p_preferred_model", PRIMARY_MODEL_ID)
                }
                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                supabaseHttpClient.newCall(rpcReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext true
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "RPC upsert fallback to REST: ${e.message}")
            }

            // 2. Direct REST Table Upsert Fallback (`/rest/v1/user_gemini_api_keys`)
            val restUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/user_gemini_api_keys?on_conflict=account_key"
            val payload = JSONObject().apply {
                put("account_key", identity.accountKey)
                put("user_id", identity.userId)
                put("user_phone", identity.userPhone)
                put("access_key", identity.accessKey)
                put("student_name", identity.studentName)
                put("gemini_api_key", apiKey)
                put("preferred_model", PRIMARY_MODEL_ID)
                put("is_active", true)
            }

            val req = Request.Builder()
                .url(restUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            supabaseHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) return@withContext true

                // Fallback PATCH if row already exists
                val encKey = URLEncoder.encode(identity.accountKey, "UTF-8")
                val patchUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/user_gemini_api_keys?account_key=eq.$encKey"
                val patchReq = Request.Builder()
                    .url(patchUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                supabaseHttpClient.newCall(patchReq).execute().use { patchResp ->
                    return@withContext patchResp.isSuccessful
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "uploadKeyToSupabase failed: ${e.message}")
            false
        }
    }

    private suspend fun fetchKeyFromSupabase(identity: StudentAccountIdentity): String? = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext null

            // 1. Try Atomic RPC `get_user_gemini_api_key` first
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_user_gemini_api_key"
                val rpcPayload = JSONObject().apply {
                    put("p_account_key", identity.accountKey)
                    put("p_user_id", identity.userId)
                    put("p_user_phone", identity.userPhone)
                    put("p_access_key", identity.accessKey)
                }
                val rpcReq = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                supabaseHttpClient.newCall(rpcReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank() && body.trim().startsWith("{")) {
                            val json = JSONObject(body)
                            if (json.optBoolean("found", false)) {
                                val key = json.optString("gemini_api_key", "").trim()
                                if (key.isNotBlank()) return@withContext key
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "RPC get_user_gemini_api_key fallback to REST: ${e.message}")
            }

            // 2. Direct REST Query Fallback matching any of the user's identifiers
            val conditions = mutableListOf<String>()
            if (identity.accountKey.isNotBlank()) {
                conditions.add("account_key.eq.${URLEncoder.encode(identity.accountKey, "UTF-8")}")
            }
            if (identity.userId.isNotBlank()) {
                conditions.add("user_id.eq.${URLEncoder.encode(identity.userId, "UTF-8")}")
            }
            if (identity.userPhone.isNotBlank()) {
                conditions.add("user_phone.eq.${URLEncoder.encode(identity.userPhone, "UTF-8")}")
            }
            if (identity.accessKey.isNotBlank()) {
                conditions.add("access_key.eq.${URLEncoder.encode(identity.accessKey, "UTF-8")}")
            }
            if (conditions.isEmpty()) return@withContext null

            val orFilter = conditions.distinct().joinToString(",")
            val restUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/user_gemini_api_keys?or=($orFilter)&is_active=eq.true&select=gemini_api_key,updated_at&order=updated_at.desc&limit=1"

            val req = Request.Builder()
                .url(restUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            supabaseHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bodyStr = resp.body?.string().orEmpty()
                val arr = JSONArray(bodyStr)
                if (arr.length() == 0) return@withContext null
                val key = arr.getJSONObject(0).optString("gemini_api_key", "").trim()
                return@withContext key.takeIf { it.isNotBlank() }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "fetchKeyFromSupabase failed: ${e.message}")
            null
        }
    }

    // =========================================================================
    // GEMINI 3.8 FLASH MULTIMODAL SLIDE TUTOR ENGINE
    // =========================================================================

    private suspend fun testApiKeyValidity(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val testPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Hi")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 10)
            })
        }

        var lastErrorMsg = "API Key যাচাই করা যায়নি"
        for (modelId in MODEL_CANDIDATES) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(testPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                geminiHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext Result.success(true)
                    }
                    val errBody = resp.body?.string().orEmpty()
                    if (resp.code == 400 && (errBody.contains("API_KEY_INVALID", ignoreCase = true) || errBody.contains("API key not valid", ignoreCase = true))) {
                        return@withContext Result.failure(Exception("আপনার দেওয়া Gemini API Key-টি সঠিক নয়। অনুগ্রহ করে Google AI Studio থেকে সঠিক Key কপি করে দিন।"))
                    }
                    if (resp.code == 403) {
                        return@withContext Result.failure(Exception("এই API Key-তে পারমিশন নেই (HTTP 403)। নতুন একটি API Key তৈরি করে চেষ্টা করুন।"))
                    }
                    lastErrorMsg = "HTTP ${resp.code}"
                }
            } catch (e: Exception) {
                lastErrorMsg = e.localizedMessage ?: "নেটওয়ার্ক ত্রুটি"
            }
        }
        Result.failure(Exception("API Key যাচাই করতে সমস্যা হয়েছে ($lastErrorMsg)। ইন্টারনেট সংযোগ ও Key চেক করুন।"))
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, maxDimension: Int = 1600, quality: Int = 84): String {
        val width = bitmap.width
        val height = bitmap.height
        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val targetW: Int
            val targetH: Int
            if (width >= height) {
                targetW = maxDimension
                targetH = (maxDimension / ratio).toInt().coerceAtLeast(100)
            } else {
                targetH = maxDimension
                targetW = (maxDimension * ratio).toInt().coerceAtLeast(100)
            }
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Builds the Master Academic Tutor System Instruction in Bengali.
     * Specifically crafted for students who watched the teacher's lecture video and read the slide
     * on their own, yet still couldn't grasp a concept (e.g., HSC ICT Chapter 3 Logic Gates, Boolean Algebra,
     * Physics/Chemistry/Math derivations, etc.).
     */
    private fun buildMasterTutorSystemInstruction(
        slideTitle: String,
        currentPage: Int,
        totalPages: Int
    ): String {
        return """
            তুমি একজন অত্যন্ত অভিজ্ঞ, আন্তরিক এবং বাংলাদেশের সেরা একাডেমিক এআই শিক্ষক (Master AI Study Tutor - Powered by Gemini 3.8 Flash)।
            শিক্ষার্থী বর্তমানে "$slideTitle" লেকচার স্লাইডের পৃষ্ঠা $currentPage (মোট $totalPages পৃষ্ঠা) দেখছে।
            
            গুরুত্বপূর্ণ প্রেক্ষাপট:
            শিক্ষার্থী ক্লাসের শিক্ষকের লেকচার ভিডিও দেখেছে এবং নিজে স্লাইডটি পড়েও এই অংশটুকু (যেমন কোনো নির্দিষ্ট লজিক গেইট, সার্কিট, সত্যক সারণি/Truth Table, গাণিতিক সমীকরণ, চিত্র বা থিওরি) পুরোপুরি বুঝতে পারছে না। তাই তোমাকে এমনভাবে বোঝাতে হবে যেন যেকোনো দুর্বল শিক্ষার্থীও ১০০% পরিষ্কারভাবে বুঝতে পারে!
            
            তোমার বোঝানোর নিয়মাবলি (অবশ্যই মেনে চলবে):
            ১. **সহজ ও বাস্তব উদাহরণ দিয়ে শুরু (Zero-to-Hero Analogy):** একদম শুরুতে কঠিন বইয়ের ভাষা না বলে বাস্তব জীবনের একটি অতি সহজ উদাহরণ (যেমন লজিক গেইট হলে বৈদ্যুতিক সুইচ ও বাল্ব, অথবা দৈনন্দিন জীবনের ঘটনা) দিয়ে মূল কনসেপ্টটি মাথার ভেতর ঢুকিয়ে দাও।
            ২. **স্লাইডের ছবি/ডায়াগ্রাম ও লেখার নিখুঁত ব্যবচ্ছেদ (Slide Visual Breakdown):** সংযুক্ত স্লাইডের ছবিতে যা যা লেখা আছে, শিক্ষক যেভাবে তীর চিহ্ন বা নোট লিখেছেন, যে চিত্র/গেইট/সার্কিট বা সমীকরণ দেওয়া আছে—তার প্রতিটি অংশ ধাপে ধাপে (Step-by-Step) ভেঙে বাংলায় বুঝিয়ে দাও। ইনপুট কী দিলে কেন সেই আউটপুট আসছে তা পরিষ্কার করে দেখাও।
            ৩. **মনে রাখার জাদুকরী শর্টকাট (Memory Trick):** শিক্ষার্থী যেন পরীক্ষায় ভুলে না যায়, সেজন্য ১টি দারুণ শর্টকাট টেকনিক বা ছন্দ শিখিয়ে দাও।
            ৪. **পরীক্ষার ফোকাস (Board & Admission Exam Tips):** এই অংশ থেকে HSC/SSC বোর্ড পরীক্ষায় (সৃজনশীল গ/ঘ ও MCQ) কীভাবে প্রশ্ন আসে এবং কোথায় শিক্ষার্থীরা ভুল করে তা সংক্ষেপে বলে দাও।
            ৫. **আন্তরিক ভাষা ও সুন্দর ফরম্যাটিং:** পয়েন্ট আকারে, ইমোজি ও বোল্ড হেডিং ব্যবহার করে খুব সুন্দর, গোছানো ও উৎসাহব্যঞ্জক বাংলা ভাষায় উত্তর দেবে। শেষে শিক্ষার্থীকে ছোট ১টি ফলো-আপ প্রশ্ন করবে যাতে সে নিজেই বুঝতে পারে তার কনসেপ্ট ক্লিয়ার হয়েছে কি না।
        """.trimIndent()
    }

    /**
     * Sends the current lecture slide (and optional adjacent slides / conversation history)
     * to Gemini 3.8 Flash and returns a deeply helpful academic explanation.
     */
    suspend fun askSlideQuestion(
        context: Context,
        slideTitle: String,
        currentPage: Int,
        totalPages: Int,
        userQuestion: String,
        slideBitmaps: List<Pair<Int, Bitmap>>,
        pdfFileFallback: File? = null,
        chatHistory: List<AiChatMessage> = emptyList(),
        sessionManager: SessionManager? = null
    ): GeminiTutorResult = withContext(Dispatchers.IO) {
        // 1. Ensure API Key is loaded (try cloud sync if blank)
        var apiKey = getEffectiveApiKey(context, sessionManager)
        if (apiKey.isBlank()) {
            apiKey = syncApiKeyFromCloud(context, sessionManager, forceRefresh = true).orEmpty()
        }
        if (apiKey.isBlank()) {
            return@withContext GeminiTutorResult.Error(
                message = "এআই টিউটর ব্যবহার করতে আপনার Gemini API Key প্রয়োজন। নিচের বাটনে ক্লিক করে অথবা সেটিংস থেকে আপনার Gemini API Key যোগ করুন।",
                isMissingKey = true
            )
        }

        try {
            val systemInstructionText = buildMasterTutorSystemInstruction(
                slideTitle = slideTitle,
                currentPage = currentPage,
                totalPages = totalPages
            )

            // Build contents array with conversation history + current multimodal turn
            val contentsArray = JSONArray()

            // Include recent conversation turns (up to last 6 messages) for follow-up continuity
            val recentHistory = chatHistory.filter { !it.isError && it.text.isNotBlank() }.takeLast(6)
            for (msg in recentHistory) {
                contentsArray.put(JSONObject().apply {
                    put("role", if (msg.isUser) "user" else "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", msg.text)
                        })
                    })
                })
            }

            // Build current user turn parts (Slide Images + Question Prompt)
            val currentParts = JSONArray()

            if (slideBitmaps.isNotEmpty()) {
                for ((pageNum, bmp) in slideBitmaps) {
                    val base64Jpeg = bitmapToBase64Jpeg(bmp)
                    currentParts.put(JSONObject().apply {
                        put("text", "[সংযুক্ত লেকচার স্লাইডের ছবি - পৃষ্ঠা নম্বর: $pageNum / $totalPages]")
                    })
                    currentParts.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Jpeg)
                        })
                    })
                }
            } else if (pdfFileFallback != null && pdfFileFallback.exists() && pdfFileFallback.length() in 1..(12 * 1024 * 1024)) {
                // Fallback to sending PDF inline if under 12MB and no bitmap was rendered
                val pdfBase64 = Base64.encodeToString(pdfFileFallback.readBytes(), Base64.NO_WRAP)
                currentParts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "application/pdf")
                        put("data", pdfBase64)
                    })
                })
            }

            val contextualPrompt = buildString {
                append("লেকচারের নাম: \"$slideTitle\"\n")
                append("বর্তমান স্লাইড পৃষ্ঠা: $currentPage (মোট $totalPages পৃষ্ঠা)\n\n")
                append("শিক্ষার্থীর প্রশ্ন / সমস্যা:\n")
                append(userQuestion)
                append("\n\nঅনুগ্রহ করে সংযুক্ত স্লাইডটি ভালো করে দেখে একদম সহজ ভাষায়, ধাপে ধাপে উদাহরণসহ বুঝিয়ে দাও যাতে ক্লাসের লেকচারে না বুঝলেও এখন পুরোপুরি পরিষ্কার হয়ে যায়।")
            }

            currentParts.put(JSONObject().apply {
                put("text", contextualPrompt)
            })

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", currentParts)
            })

            val requestBodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstructionText)
                        })
                    })
                })
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.55)
                    put("topP", 0.92)
                    put("maxOutputTokens", 4096)
                })
            }

            val mediaType = "application/json".toMediaType()
            val bodyString = requestBodyJson.toString()

            var lastError = "এআই সার্ভার থেকে উত্তর পাওয়া যায়নি।"
            for (modelId in MODEL_CANDIDATES) {
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("Content-Type", "application/json")
                        .post(bodyString.toRequestBody(mediaType))
                        .build()

                    geminiHttpClient.newCall(request).execute().use { response ->
                        val respBody = response.body?.string().orEmpty()
                        if (response.isSuccessful && respBody.isNotBlank()) {
                            val json = JSONObject(respBody)
                            val candidates = json.optJSONArray("candidates")
                            val firstCandidate = candidates?.optJSONObject(0)
                            val content = firstCandidate?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val sb = StringBuilder()
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val partText = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                                    if (partText.isNotBlank()) {
                                        sb.append(partText)
                                    }
                                }
                            }
                            val finalAnswer = sb.toString().trim()
                            if (finalAnswer.isNotBlank()) {
                                return@withContext GeminiTutorResult.Success(
                                    explanation = finalAnswer,
                                    modelUsed = DISPLAY_MODEL_NAME
                                )
                            }
                        } else {
                            // Check if API key is invalid or quota exceeded
                            if (response.code == 400 && (respBody.contains("API_KEY_INVALID", true) || respBody.contains("API key not valid", true))) {
                                return@withContext GeminiTutorResult.Error(
                                    message = "আপনার Gemini API Key-টি সঠিক নয়। অনুগ্রহ করে সেটিংস থেকে সঠিক API Key আপডেট করুন।",
                                    isMissingKey = true
                                )
                            }
                            if (response.code == 429) {
                                lastError = "API রিকোয়েস্ট লিমিট পূর্ণ হয়েছে (Quota Exceeded)। কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন।"
                            } else if (response.code == 404 || response.code == 400) {
                                // Try next model alias in MODEL_CANDIDATES
                                Log.d(TAG, "Model $modelId returned ${response.code}, trying fallback model...")
                                continue
                            } else {
                                val apiErrMsg = try {
                                    JSONObject(respBody).optJSONObject("error")?.optString("message").orEmpty()
                                } catch (_: Exception) { "" }
                                lastError = apiErrMsg.ifBlank { "সার্ভার ত্রুটি (HTTP ${response.code})" }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling model $modelId: ${e.message}")
                    lastError = "ইন্টারনেট সংযোগে সমস্যা হয়েছে: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"
                }
            }

            GeminiTutorResult.Error(message = lastError)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in askSlideQuestion: ${e.message}", e)
            GeminiTutorResult.Error(
                message = "স্লাইড বিশ্লেষণ করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}"
            )
        }
    }
}
