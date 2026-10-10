package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.security.DeviceActivationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val signatureHash: String?
)

object AppUpdateRepository {
    private const val TAG = "AppUpdateRepository"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                Log.w(TAG, "Supabase config missing for update check.")
                return@withContext null
            }

            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/app_updates?select=*&is_active=eq.true&order=latest_version_code.desc&limit=1"
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
                if (jsonArray.length() == 0) return@withContext null

                val obj = jsonArray.getJSONObject(0)
                val versionCode = obj.optInt("latest_version_code", 0)
                val versionName = obj.optString("latest_version_name", "1.0.0")
                val minSupportedCode = obj.optInt("min_supported_version_code", 0)
                val isForce = obj.optBoolean("is_force_update", false)
                val titleBn = obj.optString("title", "নতুন আপডেট উপলব্ধ!")
                val changelogBn = obj.optString("changelog", "অ্যাপে নতুন ফিচার এবং বাগ ফিক্স করা হয়েছে।")
                val downloadUrl = obj.optString("download_url", "https://t.me/")
                val buttonText = obj.optString("button_text", "এখনই আপডেট করুন")
                val signatureHash = obj.optString("apk_checksum_sha256", null)

                return@withContext AppUpdateInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    minSupportedVersionCode = minSupportedCode,
                    isForce = isForce,
                    titleBn = titleBn,
                    changelogBn = changelogBn,
                    downloadUrl = downloadUrl,
                    buttonText = buttonText,
                    signatureHash = signatureHash
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception fetching update: ${e.message}", e)
            null
        }
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to open update url: ${e.message}")
        }
    }
}
