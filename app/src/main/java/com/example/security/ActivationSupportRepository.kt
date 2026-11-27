package com.example.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ActivationSupportLink(
    val id: String = "",
    val title: String,
    val subtitle: String? = null,
    val iconType: String = "telegram", // "telegram", "whatsapp", "facebook", "website", "phone", "email", "other"
    val url: String,
    val colorHex: String? = null,
    val isActive: Boolean = true,
    val priority: Int = 0
)

object ActivationSupportRepository {
    private const val TAG = "ActivationSupportRepo"
    private const val PREFS_NAME = "maxbird_activation_support_prefs"
    private const val KEY_CACHED_LINKS = "cached_support_links"

    private val httpClient = com.example.network.SharedHttpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    val fallbackLinks: List<ActivationSupportLink>
        get() = listOf(
            ActivationSupportLink(
                id = "default_telegram",
                title = com.example.utils.AppLang.tr("Telegram Support", "Telegram সাপোর্ট"),
                subtitle = com.example.utils.AppLang.tr("Message @fahim017740", "@fahim017740 এ মেসেজ দিন"),
                iconType = "telegram",
                url = "https://t.me/fahim017740",
                colorHex = "#229ED9",
                isActive = true,
                priority = 10
            ),
            ActivationSupportLink(
                id = "default_whatsapp",
                title = com.example.utils.AppLang.tr("WhatsApp Helpline", "WhatsApp হেল্পলাইন"),
                subtitle = com.example.utils.AppLang.tr("Contact us directly on WhatsApp", "সরাসরি WhatsApp এ যোগাযোগ করুন"),
                iconType = "whatsapp",
                url = "https://wa.me/8801774092040",
                colorHex = "#25D366",
                isActive = true,
                priority = 9
            )
        )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCachedLinks(context: Context): List<ActivationSupportLink> {
        return try {
            val raw = getPrefs(context).getString(KEY_CACHED_LINKS, null)
            if (!raw.isNullOrBlank()) {
                parseJsonArray(raw)
            } else {
                fallbackLinks
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error reading cached support links: ${t.message}")
            fallbackLinks
        }
    }

    suspend fun fetchSupportLinks(context: Context): List<ActivationSupportLink> = withContext(Dispatchers.IO) {
        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext getCachedLinks(context)
        }

        val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/activation_support_links?is_active=eq.true&order=priority.desc,created_at.desc"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Supabase HTTP error ${response.code}: $text")
                    return@withContext getCachedLinks(context)
                }
                text
            }

            val list = parseJsonArray(body)
            if (list.isNotEmpty()) {
                getPrefs(context).edit().putString(KEY_CACHED_LINKS, body).apply()
                return@withContext list
            } else {
                return@withContext getCachedLinks(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to fetch support links from Supabase: ${t.message}")
            return@withContext getCachedLinks(context)
        }
    }

    private fun parseJsonArray(rawJson: String): List<ActivationSupportLink> {
        val list = mutableListOf<ActivationSupportLink>()
        val jsonArray = JSONArray(rawJson)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            fun cleanStr(key: String, fallback: String = ""): String {
                if (!obj.has(key) || obj.isNull(key)) return fallback
                val v = obj.optString(key, "").trim()
                return if (v.isBlank() || v.equals("null", ignoreCase = true)) fallback else v
            }
            val title = cleanStr("title")
            val url = cleanStr("url")
            if (title.isBlank() || url.isBlank()) continue

            list.add(
                ActivationSupportLink(
                    id = cleanStr("id", "link_$i"),
                    title = title,
                    subtitle = cleanStr("subtitle").takeIf { it.isNotBlank() },
                    iconType = cleanStr("icon_type", "other").lowercase(),
                    url = url,
                    colorHex = cleanStr("color_hex").takeIf { it.isNotBlank() },
                    isActive = obj.optBoolean("is_active", true),
                    priority = obj.optInt("priority", 0)
                )
            )
        }
        return list
    }
}
