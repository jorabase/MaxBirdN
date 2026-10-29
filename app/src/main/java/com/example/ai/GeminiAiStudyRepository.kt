package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.auth.SessionManager
import com.example.security.DeviceActivationRepository
import com.example.security.DeviceSecurityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    val liveVoiceModel: String = "gemini-3.8-live",
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
    val isError: Boolean = false,
    val isVoiceQuery: Boolean = false,
    val uploadedFileName: String? = null,
    val audioBytesWav: ByteArray? = null
)

sealed class GeminiTutorResult {
    data class Success(
        val explanation: String,
        val modelUsed: String,
        val transcribedQuery: String? = null,
        val audioBytesWav: ByteArray? = null
    ) : GeminiTutorResult()
    data class Error(val message: String, val isMissingKey: Boolean = false) : GeminiTutorResult()
}

object GeminiAiStudyRepository {
    private const val TAG = "GeminiAiStudyRepo"
    private const val PREFS_NAME = "shikho_gemini_ai_prefs"
    private const val KEY_ACTIVE_API_KEY = "active_gemini_api_key"
    private const val KEY_ACCOUNT_PREFIX = "gemini_key_acc_"
    private const val KEY_SYNC_STATUS = "gemini_sync_status_text"
    private const val KEY_IS_CLOUD_SYNCED = "gemini_is_cloud_synced"

    // Explicitly declared model constants (never downgraded to 1.5)
    const val SLIDE_ANALYSIS_MODEL = "gemini-3.8-flash"
    const val PRIMARY_MODEL_ID = "gemini-3.8-flash"
    const val DISPLAY_MODEL_NAME = "Gemini 3.8 Flash"
    const val LIVE_VOICE_MODEL = "gemini-3.8-live"
    const val LIVE_AUDIO_NATIVE_MODEL = "gemini-2.5-flash-native-audio"

    // Model candidates for Document & Lecture Slide Analysis (no Gemini 1.5 versions)
    private val MODEL_CANDIDATES = listOf(
        SLIDE_ANALYSIS_MODEL,
        "gemini-flash-latest",
        "gemini-2.5-flash"
    )

    // Model candidates for Live Voice / Real-Time Audio Conversation
    private val LIVE_VOICE_MODEL_CANDIDATES = listOf(
        LIVE_VOICE_MODEL,
        LIVE_AUDIO_NATIVE_MODEL
    )

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 60-second timeouts for complex multimodal slide analysis
    private val geminiHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Fast 8-second client for instant API Key validation so UI never hangs
    private val verifyHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val supabaseHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .writeTimeout(7, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val _configState = MutableStateFlow(GeminiConfigState())
    val configState: StateFlow<GeminiConfigState> = _configState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Resolves all account identifiers for the currently logged-in student so that
     * logging in via Phone+PIN, OTP, QR Session, or Device ID on ANY device restores their key.
     */
    data class StudentAccountIdentity(
        val accountKey: String,
        val userId: String,
        val userPhone: String,
        val accessKey: String,
        val studentName: String
    )

    fun resolveIdentity(context: Context, sessionManager: SessionManager? = null): StudentAccountIdentity {
        val appCtx = context.applicationContext
        val mgr = sessionManager ?: SessionManager(appCtx)
        val userId = mgr.getUserId()?.trim().orEmpty()
        val userPhone = mgr.getNormalizedUserPhone().ifBlank { mgr.getUserPhone()?.trim().orEmpty() }
        val deviceDisplayId = runCatching { DeviceSecurityManager.getDisplayDeviceId(appCtx).trim() }.getOrDefault("")
        val studentName = mgr.getUserFullName()?.trim()
            ?: mgr.getUserFirstName()?.trim()
            ?: "শিক্ষার্থী"

        val primaryKey = when {
            userPhone.isNotBlank() -> userPhone
            userId.isNotBlank() -> userId
            deviceDisplayId.isNotBlank() -> deviceDisplayId
            else -> "default_student_device"
        }

        return StudentAccountIdentity(
            accountKey = primaryKey,
            userId = userId,
            userPhone = userPhone,
            accessKey = deviceDisplayId,
            studentName = studentName
        )
    }

    /**
     * Initializes local state without blocking or resetting an ongoing sync.
     */
    fun initialize(context: Context, sessionManager: SessionManager? = null) {
        val prefs = getPrefs(context)
        val identity = resolveIdentity(context, sessionManager)

        val accountSavedKey = prefs.getString("$KEY_ACCOUNT_PREFIX${identity.accountKey}", null)?.trim().orEmpty()
            .ifBlank {
                if (identity.userId.isNotBlank()) prefs.getString("$KEY_ACCOUNT_PREFIX${identity.userId}", null)?.trim().orEmpty() else ""
            }
            .ifBlank {
                prefs.getString(KEY_ACTIVE_API_KEY, null)?.trim().orEmpty()
            }

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

        val currentlySyncing = _configState.value.isSyncing
        _configState.value = GeminiConfigState(
            apiKey = effectiveKey,
            isConfigured = effectiveKey.isNotBlank(),
            preferredModel = PRIMARY_MODEL_ID,
            displayModelName = DISPLAY_MODEL_NAME,
            syncStatusText = if (currentlySyncing) _configState.value.syncStatusText else savedStatus,
            isCloudSynced = isCloudSynced && effectiveKey.isNotBlank(),
            isSyncing = currentlySyncing,
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
        val cleanKey = apiKeyInput.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .filter { !it.isWhitespace() }

        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("অনুগ্রহ করে একটি সঠিক Gemini API Key দিন।"))
        }

        _configState.update { it.copy(isSyncing = true, syncStatusText = "API Key যাচাই ও সেভ করা হচ্ছে...") }

        try {
            if (verifyFirst) {
                val verifyRes = testApiKeyValidity(cleanKey)
                if (verifyRes.isFailure) {
                    val err = verifyRes.exceptionOrNull() ?: Exception("API Key সঠিক নয় বা কাজ করছে না।")
                    _configState.update {
                        it.copy(
                            isSyncing = false,
                            syncStatusText = "API Key যাচাই ব্যর্থ হয়েছে"
                        )
                    }
                    return@withContext Result.failure(err)
                }
            }

            val identity = resolveIdentity(context, sessionManager)
            val prefs = getPrefs(context)

            // 1. Immediately save locally so key works right away even if cloud DB is unreachable
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
            editor.commit()

            // 2. Sync to Supabase Cloud Database with strict 8-second cap so spinner never hangs
            val cloudSaved = withTimeoutOrNull(8_000L) {
                uploadKeyToSupabase(identity, cleanKey)
            } ?: false

            val timeFormatted = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
            val statusMsg = if (cloudSaved) {
                "☁️ ক্লাউড ডাটাবেজে সংরক্ষিত ($timeFormatted)"
            } else {
                "ডিভাইসে সংরক্ষিত আছে (ক্লাউড সিঙ্ক ব্যাকগ্রাউন্ডে সক্রিয়)"
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

            // If cloud sync timed out or failed transiently, retry silently in background
            if (!cloudSaved) {
                backgroundScope.launch {
                    val bgSaved = withTimeoutOrNull(12_000L) {
                        uploadKeyToSupabase(identity, cleanKey)
                    } ?: false
                    if (bgSaved) {
                        val bgStatus = "☁️ ক্লাউড ডাটাবেজে সংরক্ষিত ($timeFormatted)"
                        prefs.edit()
                            .putBoolean(KEY_IS_CLOUD_SYNCED, true)
                            .putString(KEY_SYNC_STATUS, bgStatus)
                            .apply()
                        _configState.update {
                            it.copy(isCloudSynced = true, syncStatusText = bgStatus)
                        }
                    }
                }
            }

            Result.success(
                if (cloudSaved) {
                    "Gemini API Key সফলভাবে যাচাই ও ক্লাউড ডাটাবেজে সেভ হয়েছে!"
                } else {
                    "Gemini API Key সফলভাবে যাচাই ও ডিভাইসে সেভ হয়েছে!"
                }
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                _configState.update { it.copy(isSyncing = false) }
            }
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "saveAndSyncApiKey error: ${e.message}", e)
            _configState.update {
                it.copy(
                    isSyncing = false,
                    syncStatusText = "যাচাই করতে ত্রুটি হয়েছে"
                )
            }
            Result.failure(Exception(e.localizedMessage ?: "API Key যাচাই করা যায়নি। আবার চেষ্টা করুন।"))
        } finally {
            withContext(NonCancellable) {
                _configState.update { it.copy(isSyncing = false) }
            }
        }
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
                return@withContext localKey
            }

            // Only show UI spinner when the user explicitly clicked the manual sync button
            if (forceRefresh) {
                _configState.update { it.copy(isSyncing = true, syncStatusText = "ক্লাউড ডাটাবেজ থেকে খোঁজা হচ্ছে...") }
            }

            val cloudKey = withTimeoutOrNull(8_000L) {
                fetchKeyFromSupabase(identity)
            }

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
                val uploaded = withTimeoutOrNull(6_000L) {
                    uploadKeyToSupabase(identity, localKey)
                } ?: false
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
                _configState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatusText = if (it.isConfigured) it.syncStatusText else "API Key যুক্ত করা হয়নি"
                    )
                }
                return@withContext null
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                _configState.update { it.copy(isSyncing = false) }
            }
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "syncApiKeyFromCloud error: ${e.message}")
            _configState.update { it.copy(isSyncing = false) }
            return@withContext _configState.value.apiKey.takeIf { it.isNotBlank() }
        } finally {
            if (forceRefresh) {
                withContext(NonCancellable) {
                    _configState.update { it.copy(isSyncing = false) }
                }
            }
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
            val restUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/user_gemini_api_keys?or=($orFilter)&select=gemini_api_key,preferred_model,updated_at&order=updated_at.desc&limit=1"

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
    // GEMINI 3.8 FLASH MULTIMODAL SLIDE TUTOR & LIVE VOICE ENGINE
    // =========================================================================

    /**
     * Validates the API key format and checks `/v1beta/models` without validating or replacing
     * model constants (`gemini-3.8-flash` / `gemini-3.8-live`).
     */
    private suspend fun testApiKeyValidity(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!apiKey.startsWith("AIza") || apiKey.length < 30) {
            return@withContext Result.failure(
                Exception("আপনার দেওয়া Gemini API Key-এর ফরম্যাট সঠিক নয় (Key অবশ্যই 'AIza' দিয়ে শুরু হতে হবে)।")
            )
        }

        try {
            val modelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=2"
            val listReq = Request.Builder()
                .url(modelsUrl)
                .get()
                .build()

            verifyHttpClient.newCall(listReq).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 429) {
                    return@withContext Result.success(true)
                }
                val errBody = resp.body?.string().orEmpty()
                if (resp.code == 400 && (errBody.contains("API_KEY_INVALID", ignoreCase = true) || errBody.contains("API key not valid", ignoreCase = true))) {
                    return@withContext Result.failure(
                        Exception("আপনার দেওয়া Gemini API Key-টি সঠিক নয়। অনুগ্রহ করে Google AI Studio থেকে সঠিক Key কপি করে দিন।")
                    )
                }
                if (resp.code == 403) {
                    return@withContext Result.failure(
                        Exception("এই API Key-তে পারমিশন নেই (HTTP 403)। নতুন একটি API Key তৈরি করে চেষ্টা করুন।")
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Models endpoint check warning, accepting valid AIza format key: ${e.message}")
        }

        // Key matches AIza format and was not rejected by Google Auth
        Result.success(true)
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
     * 1. PDF & Lecture Slide Analyzer (`analyzeSlidePDF`):
     * Converts uploaded PDF or slide images to Base64 (`inlineData`) GenAI Content Parts
     * and sends to `"gemini-3.8-flash"` to extract key lecture topics, clear bulleted summaries,
     * and revision questions in Bengali.
     */
    suspend fun analyzeSlidePDF(
        context: Context,
        slideTitle: String,
        uploadedBytes: ByteArray? = null,
        uploadedMimeType: String = "application/pdf",
        uploadedFileName: String? = null,
        slideBitmaps: List<Pair<Int, Bitmap>> = emptyList(),
        pdfFileFallback: File? = null,
        customInstruction: String? = null,
        onProgress: ((Float, String) -> Unit)? = null,
        sessionManager: SessionManager? = null
    ): GeminiTutorResult = withContext(Dispatchers.IO) {
        onProgress?.invoke(0.10f, "Gemini API Key যাচাই করা হচ্ছে...")
        var apiKey = getEffectiveApiKey(context, sessionManager)
        if (apiKey.isBlank()) {
            apiKey = syncApiKeyFromCloud(context, sessionManager, forceRefresh = true).orEmpty()
        }
        if (apiKey.isBlank()) {
            return@withContext GeminiTutorResult.Error(
                message = "স্লাইড ও PDF বিশ্লেষণ করতে আপনার Gemini API Key প্রয়োজন। সেটিংস থেকে আপনার Gemini API Key যোগ করুন।",
                isMissingKey = true
            )
        }

        try {
            onProgress?.invoke(0.28f, "স্লাইড / PDF ফাইল Base64 (inlineData)-এ প্রস্তুত করা হচ্ছে...")

            val partsArray = JSONArray()
            var hasAttachedMedia = false

            // A) If user uploaded a PDF or Slide Image directly
            if (uploadedBytes != null && uploadedBytes.isNotEmpty()) {
                if (uploadedBytes.size > 18 * 1024 * 1024) {
                    return@withContext GeminiTutorResult.Error(
                        message = "আপলোড করা ফাইলের সাইজ অনেক বড় (সর্বোচ্চ ১৮ MB সাপোর্ট করে)। অনুগ্রহ করে ছোট PDF বা স্লাইডের ছবি আপলোড করুন।"
                    )
                }
                val base64Data = Base64.encodeToString(uploadedBytes, Base64.NO_WRAP)
                partsArray.put(JSONObject().apply {
                    put("text", "[সংযুক্ত ফাইল: ${uploadedFileName ?: slideTitle} (${uploadedMimeType})]")
                })
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", uploadedMimeType)
                        put("data", base64Data)
                    })
                })
                hasAttachedMedia = true
            }

            // B) Attach rendered slide bitmaps if available
            if (!hasAttachedMedia && slideBitmaps.isNotEmpty()) {
                for ((pageNum, bmp) in slideBitmaps) {
                    val base64Jpeg = bitmapToBase64Jpeg(bmp)
                    partsArray.put(JSONObject().apply {
                        put("text", "[লেকচার স্লাইড পৃষ্ঠা নম্বর: $pageNum]")
                    })
                    partsArray.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Jpeg)
                        })
                    })
                }
                hasAttachedMedia = true
            }

            // C) Attach current PDF file fallback if under 15MB
            if (!hasAttachedMedia && pdfFileFallback != null && pdfFileFallback.exists() && pdfFileFallback.length() in 1..(15 * 1024 * 1024)) {
                val pdfBase64 = Base64.encodeToString(pdfFileFallback.readBytes(), Base64.NO_WRAP)
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "application/pdf")
                        put("data", pdfBase64)
                    })
                })
                hasAttachedMedia = true
            }

            if (!hasAttachedMedia) {
                return@withContext GeminiTutorResult.Error(
                    message = "বিশ্লেষণ করার জন্য কোনো স্লাইড বা PDF ফাইল পাওয়া যায়নি। অনুগ্রহ করে একটি PDF বা স্লাইডের ছবি নির্বাচন করুন।"
                )
            }

            onProgress?.invoke(0.55f, "Gemini 3.8 Flash স্লাইড ও ডকুমেন্ট বিশ্লেষণ করছে...")

            val analyzerSystemPrompt = """
                তুমি একজন বিশেষজ্ঞ একাডেমিক লেকচার স্লাইড ও ডকুমেন্ট অ্যানালাইজার (Powered by $SLIDE_ANALYSIS_MODEL)।
                সংযুক্ত লেকচার স্লাইড বা PDF ডকুমেন্টটি গভীরভাবে বিশ্লেষণ করে বাংলায় নিচের ৩টি অংশে অত্যন্ত গোছানো ও পরিষ্কারভাবে উত্তর দাও:
                
                ১. 📌 **মূল লেকচার টপিকসমূহ (Key Lecture Topics):**
                   - এই স্লাইড বা ডকুমেন্টে কোন কোন মূল টপিক, সূত্র, সংজ্ঞা বা ডায়াগ্রাম আলোচনা করা হয়েছে তার তালিকা।
                
                ২. 📝 **সহজ ও গোছানো বুলেট সারাংশ (Clear Bulleted Summaries):**
                   - প্রতিটি টপিকের মূল কথাগুলো সহজ বাংলায় বুলেট পয়েন্ট আকারে সংক্ষেপে ব্যাখ্যা করো যাতে একজন শিক্ষার্থী এক নজরে পুরো লেকচারটি রিভিশন দিতে পারে।
                
                ৩. ❓ **রিভিশন প্রশ্নোত্তর ও পরীক্ষার প্রস্তুতি (Revision Questions in Bengali):**
                   - এই স্লাইড থেকে বোর্ড ও ভর্তি পরীক্ষায় আসার মতো গুরুত্বপূর্ণ ৩টি MCQ প্রশ্ন (সঠিক উত্তর ও ব্যাখ্যাসহ) এবং ২টি সংক্ষিপ্ত/অনুধাবনমূলক সৃজনশীল প্রশ্ন ও উত্তর তৈরি করে দাও।
            """.trimIndent()

            val userPromptText = customInstruction?.takeIf { it.isNotBlank() }
                ?: "সংযুক্ত \"$slideTitle\" লেকচার স্লাইড/PDF-টি বিশ্লেষণ করে মূল লেকচার টপিকসমূহ, স্পষ্ট বুলেট সারাংশ এবং বাংলায় রিভিশন প্রশ্নোত্তর তৈরি করে দাও।"

            partsArray.put(JSONObject().apply {
                put("text", userPromptText)
            })

            val requestBodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", analyzerSystemPrompt)
                        })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", partsArray)
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.45)
                    put("topP", 0.92)
                    put("maxOutputTokens", 4096)
                })
            }

            onProgress?.invoke(0.78f, "মূল টপিক, সারাংশ ও রিভিশন প্রশ্ন তৈরি হচ্ছে...")

            val result = executeGenerateContentWithModelCandidates(apiKey, requestBodyJson)
            onProgress?.invoke(1.0f, "বিশ্লেষণ সম্পন্ন হয়েছে!")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in analyzeSlidePDF: ${e.message}", e)
            GeminiTutorResult.Error(
                message = "PDF/স্লাইড বিশ্লেষণ করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}"
            )
        }
    }

    /**
     * 2. Interactive Voice Session (`startVoiceSession`):
     * Connects to the Gemini Live API (`gemini-3.8-live` / `gemini-2.5-flash-native-audio`)
     * via WebSockets (`BidiGenerateContent`) or multimodal audio stream (`inlineData` audio bytes + slide image)
     * so students can ask spoken questions about the current slide and get a real-time Bengali explanation.
     */
    suspend fun startVoiceSession(
        context: Context,
        slideTitle: String,
        currentPage: Int,
        totalPages: Int,
        recordedAudioBytes: ByteArray,
        audioMimeType: String = "audio/mp4",
        spokenTranscriptHint: String = "",
        slideBitmaps: List<Pair<Int, Bitmap>> = emptyList(),
        chatHistory: List<AiChatMessage> = emptyList(),
        onStatusUpdate: ((String) -> Unit)? = null,
        sessionManager: SessionManager? = null
    ): GeminiTutorResult = withContext(Dispatchers.IO) {
        var apiKey = getEffectiveApiKey(context, sessionManager)
        if (apiKey.isBlank()) {
            apiKey = syncApiKeyFromCloud(context, sessionManager, forceRefresh = true).orEmpty()
        }
        if (apiKey.isBlank()) {
            return@withContext GeminiTutorResult.Error(
                message = "ভয়েস টিউটর ব্যবহার করতে আপনার Gemini API Key প্রয়োজন। সেটিংস থেকে আপনার Gemini API Key যোগ করুন।",
                isMissingKey = true
            )
        }

        if (recordedAudioBytes.isEmpty() && spokenTranscriptHint.isBlank()) {
            return@withContext GeminiTutorResult.Error(
                message = "কোনো ভয়েস রেকর্ড পাওয়া যায়নি। মাইক্রোফোন বাটনে ট্যাপ করে আপনার প্রশ্নটি বলুন।"
            )
        }

        onStatusUpdate?.invoke("$LIVE_VOICE_MODEL ভয়েস সেশন সংযুক্ত হচ্ছে...")

        // Step A: Attempt Live API WebSocket (`gemini-3.8-live` / `gemini-2.5-flash-native-audio`) bidirectional stream
        val liveWsResult = withTimeoutOrNull(10_000L) {
            tryLiveWebSocketVoiceTurn(
                apiKey = apiKey,
                slideTitle = slideTitle,
                currentPage = currentPage,
                totalPages = totalPages,
                recordedAudioBytes = recordedAudioBytes,
                audioMimeType = audioMimeType,
                spokenTranscriptHint = spokenTranscriptHint,
                slideBitmaps = slideBitmaps
            )
        }
        if (liveWsResult is GeminiTutorResult.Success && liveWsResult.explanation.isNotBlank()) {
            return@withContext liveWsResult
        }

        // Step B: Multimodal Native Audio + Slide Image analysis via `gemini-3.8-flash`
        onStatusUpdate?.invoke("আপনার ভয়েস প্রশ্ন ও স্লাইড বিশ্লেষণ করা হচ্ছে...")
        try {
            val voiceSystemInstruction = """
                তুমি একজন আন্তরিক ও দক্ষ বাংলা ভয়েস এআই শিক্ষক (Live Voice Tutor - Powered by $LIVE_VOICE_MODEL & $SLIDE_ANALYSIS_MODEL)।
                শিক্ষার্থী "$slideTitle" লেকচার স্লাইডের পৃষ্ঠা $currentPage (মোট $totalPages পৃষ্ঠা) দেখে মুখে প্রশ্ন করেছে।
                
                তোমার কাজ:
                ১. একদম শুরুতে **"🎙️ আপনার প্রশ্ন:"** লিখে শিক্ষার্থী অডিওতে কী প্রশ্ন করেছে তা ১ লাইনে বাংলায় লিখে দাও।
                ২. এরপর **"💡 সহজ ব্যাখ্যা:"** দিয়ে সংযুক্ত স্লাইডের ছবি, চিত্র বা সমীকরণ দেখে একদম সহজ ও প্রাঞ্জল বাংলায় ধাপে ধাপে উত্তর দাও, যেন শুনেই শিক্ষার্থী পুরো বিষয়টি পরিষ্কার বুঝতে পারে।
                ৩. উত্তরটি এমনভাবে গুছিয়ে লিখবে যেন অডিও প্লেব্যাক (Voice Playback) করে শুনতে খুব স্বাভাবিক ও স্পষ্ট লাগে।
            """.trimIndent()

            val contentsArray = JSONArray()
            val recentHistory = chatHistory.filter { !it.isError && it.text.isNotBlank() }.takeLast(4)
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

            val currentParts = JSONArray()

            // 1. Attach current slide bitmap(s)
            for ((pageNum, bmp) in slideBitmaps) {
                val base64Jpeg = bitmapToBase64Jpeg(bmp)
                currentParts.put(JSONObject().apply {
                    put("text", "[সংযুক্ত লেকচার স্লাইড পৃষ্ঠা: $pageNum / $totalPages]")
                })
                currentParts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Jpeg)
                    })
                })
            }

            // 2. Attach recorded student voice audio bytes (`inlineData`)
            if (recordedAudioBytes.isNotEmpty()) {
                val audioBase64 = Base64.encodeToString(recordedAudioBytes, Base64.NO_WRAP)
                currentParts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", audioMimeType)
                        put("data", audioBase64)
                    })
                })
            }

            val voicePrompt = buildString {
                append("শিক্ষার্থী এই স্লাইডটি দেখে অডিওতে প্রশ্ন করেছে। ")
                if (spokenTranscriptHint.isNotBlank()) {
                    append("(শ্রুতলিপি সংকেত: \"$spokenTranscriptHint\") ")
                }
                append("অনুগ্রহ করে শিক্ষার্থীর ভয়েস প্রশ্নটি শুনে এবং সংযুক্ত স্লাইডটি দেখে বাংলায় সহজ ও স্পষ্ট ব্যাখ্যা দাও।")
            }
            currentParts.put(JSONObject().apply {
                put("text", voicePrompt)
            })

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", currentParts)
            })

            val requestBodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", voiceSystemInstruction)
                        })
                    })
                })
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.5)
                    put("topP", 0.92)
                    put("maxOutputTokens", 3072)
                })
            }

            executeGenerateContentWithModelCandidates(
                apiKey = apiKey,
                requestBodyJson = requestBodyJson,
                displayModelOverride = "$LIVE_VOICE_MODEL / $DISPLAY_MODEL_NAME"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in startVoiceSession: ${e.message}", e)
            GeminiTutorResult.Error(
                message = "ভয়েস প্রশ্ন বিশ্লেষণ করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"
            )
        }
    }

    /**
     * Connects to the Gemini Live API (`BidiGenerateContent` WebSocket) with `gemini-3.8-live`
     * or `gemini-2.5-flash-native-audio` for real-time bidirectional voice/audio streaming.
     */
    private suspend fun tryLiveWebSocketVoiceTurn(
        apiKey: String,
        slideTitle: String,
        currentPage: Int,
        totalPages: Int,
        recordedAudioBytes: ByteArray,
        audioMimeType: String,
        spokenTranscriptHint: String,
        slideBitmaps: List<Pair<Int, Bitmap>>
    ): GeminiTutorResult? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()
        val responseTextBuilder = StringBuilder()
        val audioChunks = ByteArrayOutputStream()
        var completed = false

        val wsListener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                try {
                    // 1. Send Live API setup message with model `models/gemini-3.8-live`
                    val setupMsg = JSONObject().apply {
                        put("setup", JSONObject().apply {
                            put("model", "models/${LIVE_VOICE_MODEL_CANDIDATES.first()}")
                            put("generationConfig", JSONObject().apply {
                                put("responseModalities", JSONArray().apply {
                                    put("TEXT")
                                })
                            })
                            put("systemInstruction", JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", "তুমি একজন বাংলা একাডেমিক লাইভ ভয়েস টিউটর ($LIVE_VOICE_MODEL)। স্লাইড পৃষ্ঠা $currentPage ($slideTitle) দেখে শিক্ষার্থীর প্রশ্নের উত্তর সহজ বাংলায় দাও।")
                                    })
                                })
                            })
                        })
                    }
                    webSocket.send(setupMsg.toString())

                    // 2. Send multimodal turn parts (Slide image + Recorded Audio + Prompt)
                    val parts = JSONArray()
                    slideBitmaps.firstOrNull()?.let { (_, bmp) ->
                        parts.put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", bitmapToBase64Jpeg(bmp, maxDimension = 1280, quality = 80))
                            })
                        })
                    }
                    if (recordedAudioBytes.isNotEmpty()) {
                        parts.put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", audioMimeType)
                                put("data", Base64.encodeToString(recordedAudioBytes, Base64.NO_WRAP))
                            })
                        })
                    }
                    parts.put(JSONObject().apply {
                        put("text", spokenTranscriptHint.ifBlank { "এই স্লাইড সম্পর্কে আমার ভয়েস প্রশ্নটির সহজ বাংলায় উত্তর দাও।" })
                    })

                    val clientContentMsg = JSONObject().apply {
                        put("clientContent", JSONObject().apply {
                            put("turns", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("parts", parts)
                                })
                            })
                            put("turnComplete", true)
                        })
                    }
                    webSocket.send(clientContentMsg.toString())
                } catch (e: Exception) {
                    if (!completed && cont.isActive) {
                        completed = true
                        cont.resumeWith(Result.success(null))
                    }
                    webSocket.close(1000, "Error")
                }
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val serverContent = json.optJSONObject("serverContent")
                    val modelTurn = serverContent?.optJSONObject("modelTurn")
                    val parts = modelTurn?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            val partText = part.optString("text", "")
                            if (partText.isNotBlank()) {
                                responseTextBuilder.append(partText)
                            }
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null && inlineData.optString("mimeType").startsWith("audio/")) {
                                val b64 = inlineData.optString("data", "")
                                if (b64.isNotBlank()) {
                                    audioChunks.write(Base64.decode(b64, Base64.DEFAULT))
                                }
                            }
                        }
                    }
                    if (serverContent?.optBoolean("turnComplete", false) == true) {
                        val finalAnswer = responseTextBuilder.toString().trim()
                        if (!completed && cont.isActive) {
                            completed = true
                            if (finalAnswer.isNotBlank()) {
                                cont.resumeWith(
                                    Result.success(
                                        GeminiTutorResult.Success(
                                            explanation = finalAnswer,
                                            modelUsed = LIVE_VOICE_MODEL,
                                            audioBytesWav = audioChunks.toByteArray().takeIf { it.isNotEmpty() }
                                        )
                                    )
                                )
                            } else {
                                cont.resumeWith(Result.success(null))
                            }
                        }
                        webSocket.close(1000, "Turn complete")
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                if (!completed && cont.isActive) {
                    completed = true
                    cont.resumeWith(Result.success(null))
                }
            }
        }

        val ws = verifyHttpClient.newWebSocket(request, wsListener)
        cont.invokeOnCancellation {
            ws.cancel()
        }
    }

    /**
     * Shared execution helper that sends a standard `@google/genai` compatible `generateContent` payload
     * across `MODEL_CANDIDATES` (`gemini-3.8-flash` primary).
     */
    private fun executeGenerateContentWithModelCandidates(
        apiKey: String,
        requestBodyJson: JSONObject,
        displayModelOverride: String = DISPLAY_MODEL_NAME
    ): GeminiTutorResult {
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
                            return GeminiTutorResult.Success(
                                explanation = finalAnswer,
                                modelUsed = displayModelOverride
                            )
                        }
                    } else {
                        if (response.code == 400 && (respBody.contains("API_KEY_INVALID", true) || respBody.contains("API key not valid", true))) {
                            return GeminiTutorResult.Error(
                                message = "আপনার Gemini API Key-টি সঠিক নয়। অনুগ্রহ করে সেটিংস থেকে সঠিক API Key আপডেট করুন।",
                                isMissingKey = true
                            )
                        }
                        if (response.code == 429) {
                            lastError = "API রিকোয়েস্ট লিমিট পূর্ণ হয়েছে (Quota Exceeded)। কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন।"
                        } else if (response.code == 404 || response.code == 400) {
                            Log.d(TAG, "Model $modelId returned ${response.code}, routing via next candidate...")
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
        return GeminiTutorResult.Error(message = lastError)
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

            val contentsArray = JSONArray()
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

            executeGenerateContentWithModelCandidates(apiKey, requestBodyJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in askSlideQuestion: ${e.message}", e)
            GeminiTutorResult.Error(
                message = "স্লাইড বিশ্লেষণ করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}"
            )
        }
    }
}
