package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import com.example.security.DeviceActivationRepository
import com.example.utils.AppLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int,
    val isForce: Boolean,
    val titleBn: String,
    val changelogBn: String,
    val downloadUrl: String,
    val buttonText: String,
    val signatureHash: String?,
    val isKillSwitchActive: Boolean = false,
    val killSwitchTitleBn: String = AppLang.tr("App Update in Progress", "অ্যাপ আপডেটের কাজ চলছে"),
    val killSwitchMessageBn: String = AppLang.tr(
        "We are currently updating our servers and systems to make the app faster, better, and more secure. Sorry for the temporary inconvenience. The app will resume shortly.",
        "অ্যাপটিকে আরও উন্নত, দ্রুত ও নিরাপদ করতে বর্তমানে সার্ভার ও সিস্টেম আপডেটের কাজ চলছে। সাময়িক অসুবিধার জন্য আমরা আন্তরিকভাবে দুঃখিত। কাজ শেষ হওয়া মাত্রই অ্যাপটি পুনরায় চালু হবে।"
    ),
    val estimatedBackTimeBn: String? = null,
    val fileSizeMb: Double? = null
)

object AppUpdateRepository {
    private const val TAG = "AppUpdateRepository"
    const val UPDATE_CHECK_INTERVAL_MS = 12 * 60_000L // 12 minutes between automatic checks

    private val fetchMutex = Mutex()
    @Volatile
    private var cachedUpdateInfo: AppUpdateInfo? = null
    @Volatile
    private var hasFetchedThisSession: Boolean = false
    @Volatile
    private var lastFetchElapsedMs: Long = 0L

    private val httpClient = OkHttpClient.Builder()
        .apply { com.example.security.AntiTamperSecurity.applyToOkHttpBuilder(this) }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Checks whether the app should be blocked due to either:
     * 1. Global Kill Switch / Maintenance Mode (`info.isKillSwitchActive == true`), OR
     * 2. Version mismatch (`isUpdateRequired == true`).
     */
    fun shouldBlockApp(
        info: AppUpdateInfo?,
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
        installedVersionName: String = BuildConfig.VERSION_NAME
    ): Boolean {
        if (info == null) return false
        if (info.isKillSwitchActive) return true
        return isUpdateRequired(info, installedVersionCode, installedVersionName)
    }

    /**
     * Checks whether the installed app version differs from the required version in the database.
     * Enforces exact version matching: if the database specifies version 7 and the user has
     * version 8 (or version 6), this returns true so the user must install the exact database version.
     */
    fun isUpdateRequired(
        info: AppUpdateInfo?,
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
        installedVersionName: String = BuildConfig.VERSION_NAME
    ): Boolean {
        if (info == null) return false
        val dbName = info.versionName.trim().removePrefix("v").removePrefix("V")
        val appName = installedVersionName.trim().removePrefix("v").removePrefix("V")

        val codeMatches = info.versionCode <= 0 ||
            info.versionCode == installedVersionCode ||
            (dbName.equals(appName, ignoreCase = true) &&
                (installedVersionCode % 100 == info.versionCode || info.versionCode % 100 == installedVersionCode))

        val nameMatches = dbName.isBlank() ||
            dbName.equals(appName, ignoreCase = true) ||
            (info.versionCode == installedVersionCode && dbName == info.versionCode.toString())

        return !(codeMatches && nameMatches)
    }

    fun isDowngradeRequired(
        info: AppUpdateInfo,
        installedVersionCode: Int = BuildConfig.VERSION_CODE
    ): Boolean {
        return info.versionCode in 1 until installedVersionCode
    }

    suspend fun fetchLatestUpdate(forceRefresh: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        fetchMutex.withLock {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (!forceRefresh && hasFetchedThisSession && (nowElapsed - lastFetchElapsedMs) < UPDATE_CHECK_INTERVAL_MS) {
                return@withLock cachedUpdateInfo
            }

            try {
                val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
                if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                    Log.w(TAG, "Supabase config missing for update check.")
                    return@withLock cachedUpdateInfo
                }

                // Order by updated_at.desc,created_at.desc so the newest active configuration is always fetched.
                val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/app_updates?select=*&is_active=eq.true&order=updated_at.desc,created_at.desc&limit=1"
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to fetch updates: ${response.code} - ${response.message}")
                        return@withLock cachedUpdateInfo
                    }
                    val bodyStr = response.body?.string() ?: return@withLock cachedUpdateInfo
                    val jsonArray = JSONArray(bodyStr)
                    if (jsonArray.length() == 0) {
                        // Fallback: check live SECURITY DEFINER RPC `is_global_kill_switch_active`
                        // in case an admin enabled kill switch while `app_updates.is_active` is false under anon RLS.
                        val fallbackInfo = if (isGlobalKillSwitchActiveRpc(supabaseUrl, anonKey)) {
                            AppUpdateInfo(
                                versionCode = BuildConfig.VERSION_CODE,
                                versionName = BuildConfig.VERSION_NAME,
                                minSupportedVersionCode = BuildConfig.VERSION_CODE,
                                isForce = true,
                                titleBn = AppLang.tr("App Update in Progress", "অ্যাপ আপডেটের কাজ চলছে"),
                                changelogBn = "",
                                downloadUrl = "",
                                buttonText = AppLang.tr("Download & Update Now", "এখনই ডাউনলোড ও আপডেট করুন"),
                                signatureHash = null,
                                isKillSwitchActive = true
                            )
                        } else {
                            null
                        }
                        cachedUpdateInfo = fallbackInfo
                        hasFetchedThisSession = true
                        lastFetchElapsedMs = SystemClock.elapsedRealtime()
                        return@withLock fallbackInfo
                    }

                    val obj = jsonArray.getJSONObject(0)
                    fun cleanStr(key: String, fallback: String = ""): String {
                        if (!obj.has(key) || obj.isNull(key)) return fallback
                        val v = obj.optString(key, "").trim()
                        return if (v.isBlank() || v.equals("null", ignoreCase = true)) fallback else v
                    }

                    val versionCode = obj.optInt("latest_version_code", 0)
                    val versionName = cleanStr("latest_version_name", "1.0.0")
                    val minSupportedCode = if (!obj.isNull("min_supported_version_code")) {
                        obj.optInt("min_supported_version_code", versionCode)
                    } else {
                        versionCode
                    }
                    val isForce = true
                    val titleBn = cleanStr("title", AppLang.tr("Install Required Version", "নির্ধারিত সংস্করণ ইনস্টল করুন"))
                    val changelogBn = cleanStr("changelog", AppLang.tr("New features and security updates have been added.", "অ্যাপে নতুন ফিচার এবং সিকিউরিটি আপডেট যুক্ত করা হয়েছে।"))
                    val downloadUrl = cleanStr("download_url")
                    val buttonText = cleanStr("button_text", AppLang.tr("Download & Update Now", "এখনই ডাউনলোড ও আপডেট করুন"))
                    val signatureHash = cleanStr("apk_checksum_sha256").takeIf { it.isNotBlank() }

                    val isKillSwitchActive = obj.optBoolean("is_kill_switch", false) ||
                        obj.optBoolean("is_maintenance", false)

                    val killSwitchTitleBn = cleanStr("kill_switch_title", AppLang.tr("App Update in Progress", "অ্যাপ আপডেটের কাজ চলছে"))
                    val killSwitchMessageBn = cleanStr(
                        "kill_switch_message",
                        AppLang.tr(
                            "We are currently updating our servers and systems to make the app faster, better, and more secure. Sorry for the temporary inconvenience. The app will resume shortly.",
                            "অ্যাপটিকে আরও উন্নত, দ্রুত ও নিরাপদ করতে বর্তমানে সার্ভার ও সিস্টেম আপডেটের কাজ চলছে। সাময়িক অসুবিধার জন্য আমরা আন্তরিকভাবে দুঃখিত। কাজ শেষ হওয়া মাত্রই অ্যাপটি পুনরায় চালু হবে।"
                        )
                    )
                    val estimatedBackTimeBn = cleanStr("estimated_back_time").takeIf { it.isNotBlank() }
                    val fileSizeMb = if (obj.has("file_size_mb") && !obj.isNull("file_size_mb")) {
                        obj.optDouble("file_size_mb", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
                    } else null

                    val parsedInfo = AppUpdateInfo(
                        versionCode = versionCode,
                        versionName = versionName,
                        minSupportedVersionCode = minSupportedCode,
                        isForce = isForce,
                        titleBn = titleBn,
                        changelogBn = changelogBn,
                        downloadUrl = downloadUrl,
                        buttonText = buttonText,
                        signatureHash = signatureHash,
                        isKillSwitchActive = isKillSwitchActive,
                        killSwitchTitleBn = killSwitchTitleBn,
                        killSwitchMessageBn = killSwitchMessageBn,
                        estimatedBackTimeBn = estimatedBackTimeBn,
                        fileSizeMb = fileSizeMb
                    )
                    cachedUpdateInfo = parsedInfo
                    hasFetchedThisSession = true
                    lastFetchElapsedMs = SystemClock.elapsedRealtime()
                    return@withLock parsedInfo
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception fetching update: ${e.message}", e)
                cachedUpdateInfo
            }
        }
    }

    private fun isGlobalKillSwitchActiveRpc(supabaseUrl: String, anonKey: String): Boolean {
        return try {
            val rpcReq = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/is_global_kill_switch_active")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(rpcReq).execute().use { resp ->
                if (!resp.isSuccessful) return false
                resp.body?.string()?.trim()?.equals("true", ignoreCase = true) == true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val cleanUrl = url.trim()
            if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
                Log.w(TAG, "Blocked non-HTTPS update URL")
                return
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to open update url: ${e.message}")
        }
    }
}
