package com.example.birdgram

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.example.auth.SessionManager
import com.example.security.DeviceActivationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Birdgram Repository - Cloud and Local Telegram Session Binding.
 *
 * Automatically associates the student's Telegram session with their active Shikho access credentials
 * (Activation Code, Student ID, or Phone).
 *
 * Capabilities:
 * 1. Local Persistence: Saves complete session (Cookies + LocalStorage) partitioned by student accessKey.
 * 2. Cloud Backup (Supabase): Automatically syncs Telegram auth keys to the central database.
 * 3. Auto-Restore: When logging out, switching accounts, or reinstalling the app, the student's
 *    Telegram account is automatically retrieved from the cloud database and restored into the app.
 */
class BirdgramRepository(
    private val context: Context,
    private val sessionManager: SessionManager? = null
) {
    companion object {
        private const val TAG = "BirdgramRepository"
        const val TELEGRAM_API_ID = "38634154"
        const val TELEGRAM_API_HASH = "a4194e506db4ad97f1dba20ec28ac383"
        private const val PREFS_NAME = "birdgram_prefs"
        private const val KEY_VARIANT = "tg_client_variant"
        private const val KEY_SESSION_PREFIX = "tg_cloud_session_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _sessionInfo = MutableStateFlow(loadInitialSession())
    val sessionInfo: StateFlow<TelegramSessionInfo> = _sessionInfo.asStateFlow()

    init {
        // Asynchronously check and sync from cloud on repository launch
        scope.launch {
            checkAndRestoreCloudSession()
        }
    }

    /**
     * Resolves the primary access key of the student account currently logged in
     */
    fun getCurrentAccessKey(): String {
        val activatedCode = DeviceActivationRepository.getActivatedCode(context)?.trim()
        if (!activatedCode.isNullOrBlank()) return activatedCode.uppercase()

        val userId = sessionManager?.getUserId()?.trim()
        if (!userId.isNullOrBlank()) return userId

        val phone = sessionManager?.getUserPhone()?.trim()
        if (!phone.isNullOrBlank()) return phone

        return "default_access"
    }

    private fun loadInitialSession(): TelegramSessionInfo {
        val savedVariantName = prefs.getString(KEY_VARIANT, TelegramClientVariant.WEB_K.name)
        val variant = try {
            TelegramClientVariant.valueOf(savedVariantName ?: TelegramClientVariant.WEB_K.name)
        } catch (_: Exception) {
            TelegramClientVariant.WEB_K
        }

        val accessKey = getCurrentAccessKey()
        val localBackup = getLocalSavedSession(accessKey)

        return TelegramSessionInfo(
            selectedVariant = variant,
            isConnected = true,
            lastActiveUrl = variant.url,
            activeAccessKey = accessKey,
            syncState = if (localBackup != null) {
                val formatted = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(localBackup.lastSyncedAt))
                TelegramSyncState.Synced(formatted)
            } else {
                TelegramSyncState.Idle
            }
        )
    }

    fun setClientVariant(variant: TelegramClientVariant) {
        prefs.edit().putString(KEY_VARIANT, variant.name).apply()
        _sessionInfo.update {
            it.copy(
                selectedVariant = variant,
                lastActiveUrl = variant.url
            )
        }
    }

    // -------------------------------------------------------------
    // Local Session Persistence (Per Access Key)
    // -------------------------------------------------------------

    private fun getLocalSavedSession(accessKey: String): UserTelegramCloudSession? {
        val rawJson = prefs.getString("$KEY_SESSION_PREFIX$accessKey", null) ?: return null
        return try {
            val obj = JSONObject(rawJson)
            UserTelegramCloudSession(
                accessKey = obj.optString("access_key", accessKey),
                studentName = obj.optString("student_name", ""),
                userPhone = obj.optString("user_phone", ""),
                cookiesData = obj.optString("cookies_data", ""),
                localStorageData = obj.optString("local_storage_data", ""),
                sessionVariant = obj.optString("session_variant", "WEB_K"),
                lastSyncedAt = obj.optLong("last_synced_at", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing local session: ${e.message}")
            null
        }
    }

    private fun saveLocalSession(session: UserTelegramCloudSession) {
        try {
            val obj = JSONObject().apply {
                put("access_key", session.accessKey)
                put("student_name", session.studentName)
                put("user_phone", session.userPhone)
                put("cookies_data", session.cookiesData)
                put("local_storage_data", session.localStorageData)
                put("session_variant", session.sessionVariant)
                put("last_synced_at", session.lastSyncedAt)
            }
            prefs.edit().putString("$KEY_SESSION_PREFIX${session.accessKey}", obj.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local session: ${e.message}")
        }
    }

    // -------------------------------------------------------------
    // Cloud Session Sync & Auto-Restore (Supabase Backend)
    // -------------------------------------------------------------

    /**
     * Checks if cloud database has a saved session for the current student access key
     * and synchronizes it locally.
     */
    suspend fun checkAndRestoreCloudSession(): UserTelegramCloudSession? = withContext(Dispatchers.IO) {
        val accessKey = getCurrentAccessKey()
        _sessionInfo.update { it.copy(activeAccessKey = accessKey) }

        val local = getLocalSavedSession(accessKey)
        val cloud = fetchSessionFromCloud(accessKey)

        val sessionToUse = when {
            cloud != null && (local == null || cloud.lastSyncedAt > local.lastSyncedAt) -> {
                saveLocalSession(cloud)
                val timeStr = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(cloud.lastSyncedAt))
                _sessionInfo.update {
                    it.copy(
                        syncState = TelegramSyncState.Restored("ক্লাউড থেকে সফলভাবে রিস্টোর হয়েছে ($timeStr)"),
                        isRestoredFromCloud = true
                    )
                }
                cloud
            }
            local != null -> {
                local
            }
            else -> null
        }

        sessionToUse
    }

    /**
     * Fetch session record from Supabase table 'user_telegram_sessions'
     */
    private suspend fun fetchSessionFromCloud(accessKey: String): UserTelegramCloudSession? = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext null

            val encodedKey = URLEncoder.encode(accessKey, "UTF-8")
            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/user_telegram_sessions?access_key=eq.$encodedKey&select=*&limit=1"

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val array = JSONArray(bodyStr)
                if (array.length() == 0) return@withContext null

                val obj = array.getJSONObject(0)
                UserTelegramCloudSession(
                    accessKey = obj.optString("access_key", accessKey),
                    studentName = obj.optString("student_name", ""),
                    userPhone = obj.optString("user_phone", ""),
                    cookiesData = obj.optString("cookies_data", ""),
                    localStorageData = obj.optString("local_storage_data", ""),
                    sessionVariant = obj.optString("session_variant", "WEB_K"),
                    lastSyncedAt = try {
                        val updatedAtStr = obj.optString("updated_at", "")
                        if (updatedAtStr.isNotBlank()) {
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(updatedAtStr)?.time
                                ?: System.currentTimeMillis()
                        } else System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to fetch cloud session: ${e.message}")
            null
        }
    }

    /**
     * Uploads the active Telegram session to Supabase database tied to this student's access credentials
     */
    private suspend fun uploadSessionToCloud(session: UserTelegramCloudSession) = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext

            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/user_telegram_sessions?on_conflict=access_key"

            val payload = JSONObject().apply {
                put("access_key", session.accessKey)
                put("student_name", session.studentName)
                put("user_phone", session.userPhone)
                put("cookies_data", session.cookiesData)
                put("local_storage_data", session.localStorageData)
                put("session_variant", session.sessionVariant)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Fallback to PATCH if POST on conflict is not configured
                    val encodedKey = URLEncoder.encode(session.accessKey, "UTF-8")
                    val patchEndpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/user_telegram_sessions?access_key=eq.$encodedKey"
                    val patchRequest = Request.Builder()
                        .url(patchEndpoint)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(patchRequest).execute().close()
                }
            }

            val formatted = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
            _sessionInfo.update {
                it.copy(syncState = TelegramSyncState.Synced("ক্লাউড ডাটাবেজে সংরক্ষিত ($formatted)"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to upload session to cloud: ${e.message}")
            _sessionInfo.update {
                it.copy(syncState = TelegramSyncState.Synced("লোকাল মেমোরিতে সেভ আছে"))
            }
        }
    }

    /**
     * Captured from WebView via JavaScript Interface when user is logged in
     */
    fun onTelegramSessionCaptured(cookies: String, localStorageJson: String) {
        val accessKey = getCurrentAccessKey()

        // Validate that session contains actual Telegram auth tokens / data
        val isAuthSignalPresent = localStorageJson.contains("user_auth", ignoreCase = true) ||
                localStorageJson.contains("dc", ignoreCase = true) ||
                localStorageJson.contains("auth_key", ignoreCase = true) ||
                localStorageJson.contains("tt-global-state", ignoreCase = true) ||
                localStorageJson.length > 120

        if (!isAuthSignalPresent) {
            return
        }

        val studentName = sessionManager?.getUserFullName() ?: sessionManager?.getUserFirstName() ?: ""
        val userPhone = sessionManager?.getUserPhone() ?: ""

        val session = UserTelegramCloudSession(
            accessKey = accessKey,
            studentName = studentName,
            userPhone = userPhone,
            cookiesData = cookies,
            localStorageData = localStorageJson,
            sessionVariant = _sessionInfo.value.selectedVariant.name,
            lastSyncedAt = System.currentTimeMillis()
        )

        // 1. Immediately persist locally
        saveLocalSession(session)

        // 2. Synchronize to Supabase Cloud in background
        scope.launch {
            _sessionInfo.update { it.copy(syncState = TelegramSyncState.Syncing) }
            uploadSessionToCloud(session)
        }
    }

    /**
     * Retrieve the saved session for the current access key (from local or cloud)
     */
    suspend fun getActiveSessionForInjection(): UserTelegramCloudSession? = withContext(Dispatchers.IO) {
        val accessKey = getCurrentAccessKey()
        var session = getLocalSavedSession(accessKey)
        if (session == null || session.localStorageData.isBlank()) {
            session = fetchSessionFromCloud(accessKey)
            if (session != null) {
                saveLocalSession(session)
            }
        }
        session
    }

    /**
     * Clear all Telegram session data for the current account
     */
    fun clearTelegramSession(onCleared: () -> Unit = {}) {
        val accessKey = getCurrentAccessKey()
        prefs.edit().remove("$KEY_SESSION_PREFIX$accessKey").apply()

        scope.launch {
            try {
                val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
                if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
                    val encodedKey = URLEncoder.encode(accessKey, "UTF-8")
                    val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/user_telegram_sessions?access_key=eq.$encodedKey"
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .delete()
                        .build()
                    httpClient.newCall(request).execute().close()
                }
            } catch (_: Throwable) {}

            withContext(Dispatchers.Main) {
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies {
                        cookieManager.flush()
                        WebStorage.getInstance().deleteAllData()
                        _sessionInfo.update {
                            it.copy(syncState = TelegramSyncState.Idle, isRestoredFromCloud = false)
                        }
                        onCleared()
                    }
                } catch (_: Exception) {
                    onCleared()
                }
            }
        }
    }
}
