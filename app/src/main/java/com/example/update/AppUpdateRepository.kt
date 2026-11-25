package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.security.DeviceActivationRepository
import kotlinx.coroutines.Dispatchers
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
    val killSwitchTitleBn: String = "অ্যাপ আপডেটের কাজ চলছে",
    val killSwitchMessageBn: String = "অ্যাপটিকে আরও উন্নত, দ্রুত ও নিরাপদ করতে বর্তমানে সার্ভার ও সিস্টেম আপডেটের কাজ চলছে। সাময়িক অসুবিধার জন্য আমরা আন্তরিকভাবে দুঃখিত। কাজ শেষ হওয়া মাত্রই অ্যাপটি পুনরায় চালু হবে।",
    val estimatedBackTimeBn: String? = null,
    val fileSizeMb: Double? = null
)

object AppUpdateRepository {
    private const val TAG = "AppUpdateRepository"
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

    suspend fun fetchLatestUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                Log.w(TAG, "Supabase config missing for update check.")
                return@withContext null
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
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val jsonArray = JSONArray(bodyStr)
                if (jsonArray.length() == 0) {
                    // Fallback: check live SECURITY DEFINER RPC `is_global_kill_switch_active`
                    // in case an admin enabled kill switch while `app_updates.is_active` is false under anon RLS.
                    if (isGlobalKillSwitchActiveRpc(supabaseUrl, anonKey)) {
                        return@withContext AppUpdateInfo(
                            versionCode = BuildConfig.VERSION_CODE,
                            versionName = BuildConfig.VERSION_NAME,
                            minSupportedVersionCode = BuildConfig.VERSION_CODE,
                            isForce = true,
                            titleBn = "অ্যাপ আপডেটের কাজ চলছে",
                            changelogBn = "",
                            downloadUrl = "",
                            buttonText = "এখনই ডাউনলোড ও আপডেট করুন",
                            signatureHash = null,
                            isKillSwitchActive = true
                        )
                    }
                    return@withContext null
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
                val titleBn = cleanStr("title", "নির্ধারিত সংস্করণ ইনস্টল করুন")
                val changelogBn = cleanStr("changelog", "অ্যাপে নতুন ফিচার এবং সিকিউরিটি আপডেট যুক্ত করা হয়েছে।")
                val downloadUrl = cleanStr("download_url")
                val buttonText = cleanStr("button_text", "এখনই ডাউনলোড ও আপডেট করুন")
                val signatureHash = cleanStr("apk_checksum_sha256").takeIf { it.isNotBlank() }

                val isKillSwitchActive = obj.optBoolean("is_kill_switch", false) ||
                    obj.optBoolean("is_maintenance", false)

                val killSwitchTitleBn = cleanStr("kill_switch_title", "অ্যাপ আপডেটের কাজ চলছে")
                val killSwitchMessageBn = cleanStr(
                    "kill_switch_message",
                    "অ্যাপটিকে আরও উন্নত, দ্রুত ও নিরাপদ করতে বর্তমানে সার্ভার ও সিস্টেম আপডেটের কাজ চলছে। সাময়িক অসুবিধার জন্য আমরা আন্তরিকভাবে দুঃখিত। কাজ শেষ হওয়া মাত্রই অ্যাপটি পুনরায় চালু হবে।"
                )
                val estimatedBackTimeBn = cleanStr("estimated_back_time").takeIf { it.isNotBlank() }
                val fileSizeMb = if (obj.has("file_size_mb") && !obj.isNull("file_size_mb")) {
                    obj.optDouble("file_size_mb", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
                } else null

                return@withContext AppUpdateInfo(
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
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception fetching update: ${e.message}", e)
            null
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
