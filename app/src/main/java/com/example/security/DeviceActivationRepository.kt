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
    data class Error(val message: String) : ActivationResult()
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
        val formattedUrl = if (cleanUrl.isNotBlank() && !cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else {
            cleanUrl
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

            val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/activate_device"

            val jsonBody = JSONObject().apply {
                put("p_code", cleanCode)
                put("p_device_hash", deviceHardwareHash)
                put("p_app_signature", appSignature)
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

            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    val msg = errJson.optString("message", "")
                    val hint = errJson.optString("hint", "")
                    val details = errJson.optString("details", "")
                    when {
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
            val message = jsonRes.optString("message", "এক্সেস যাচাই সম্পন্ন হয়েছে।")

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
                ActivationResult.Error(message)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal activation error", t)
            ActivationResult.Error("অপ্রত্যাশিত ত্রুটি: ${t.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}")
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
}
