package com.example.notice

import android.content.Context
import android.util.Log
import com.example.security.DeviceActivationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AppNoticeRepository {
    private const val TAG = "AppNoticeRepository"
    private const val PREFS_NAME = "maxbird_notice_prefs"
    private const val KEY_DONT_SHOW_DATE = "dont_show_date"
    private const val KEY_SEEN_IDS = "seen_notice_ids"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedNotices: List<AppNotice> = emptyList()

    /**
     * Fetches all active notices from Supabase table `app_notices`.
     * Ordered by priority DESC (higher priority shows first), then created_at DESC.
     */
    suspend fun getActiveNotices(forceRefresh: Boolean = false): List<AppNotice> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedNotices.isNotEmpty()) {
            return@withContext cachedNotices
        }

        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            Log.w(TAG, "Supabase config missing - cannot fetch notices")
            return@withContext cachedNotices
        }

        val endpoint = "$supabaseUrl/rest/v1/app_notices?is_active=eq.true&order=priority.desc,created_at.desc"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch notices: HTTP ${response.code} - $responseBody")
                return@withContext cachedNotices
            }

            val jsonArray = JSONArray(responseBody)
            val list = mutableListOf<AppNotice>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val title = obj.optString("title", "").trim()
                val description = obj.optString("description", "").trim().takeIf { it.isNotBlank() }
                val imageUrl = obj.optString("image_url", "").trim()
                val actionUrl = obj.optString("action_url", "").trim().takeIf { it.isNotBlank() }
                val buttonText = obj.optString("action_button_text", "বিস্তারিত দেখুন").trim().ifBlank { "বিস্তারিত দেখুন" }
                val priority = obj.optInt("priority", 0)
                val isActive = obj.optBoolean("is_active", true)
                val showAsPopup = obj.optBoolean("show_as_popup", true)
                val createdAt = obj.optString("created_at", "").trim().takeIf { it.isNotBlank() }

                // Basic validation: must have ID and Image URL
                if (id.isNotBlank() && imageUrl.isNotBlank()) {
                    list.add(
                        AppNotice(
                            id = id,
                            title = title,
                            description = description,
                            imageUrl = imageUrl,
                            actionUrl = actionUrl,
                            actionButtonText = buttonText,
                            priority = priority,
                            isActive = isActive,
                            showAsPopup = showAsPopup,
                            createdAt = createdAt
                        )
                    )
                }
            }

            cachedNotices = list
            Log.d(TAG, "Loaded ${list.size} active notices successfully.")
            list
        } catch (e: Throwable) {
            Log.e(TAG, "Exception while fetching notices: ${e.message}", e)
            cachedNotices
        }
    }

    /**
     * Checks if the startup popup dialog should be shown to this user.
     * Returns true if there are active popup notices and the user hasn't chosen "Don't show again today",
     * OR if a completely new notice was published that was never seen before.
     */
    fun shouldShowPopup(context: Context, notices: List<AppNotice>): Boolean {
        val popupNotices = notices.filter { it.showAsPopup }
        if (popupNotices.isEmpty()) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val suppressedDate = prefs.getString(KEY_DONT_SHOW_DATE, null)
        val seenIds = prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet()

        val currentNoticeIds = popupNotices.map { it.id }.toSet()
        val hasBrandNewNotice = (currentNoticeIds - seenIds).isNotEmpty()

        // If user requested "don't show again today" and no brand new notice exists, suppress popup
        if (suppressedDate == today && !hasBrandNewNotice) {
            return false
        }

        return true
    }

    /**
     * Records that the current popup notices have been dismissed or suppressed.
     */
    fun markPopupDismissed(context: Context, notices: List<AppNotice>, dontShowAgainToday: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentNoticeIds = notices.filter { it.showAsPopup }.map { it.id }.toSet()
            val existingSeen = prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet()

            val editor = prefs.edit()
            editor.putStringSet(KEY_SEEN_IDS, existingSeen + currentNoticeIds)

            if (dontShowAgainToday) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                editor.putString(KEY_DONT_SHOW_DATE, today)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving dismissed notice state: ${e.message}")
        }
    }
}
