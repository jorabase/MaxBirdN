package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    private const val PREFS_FILE = "device_activation_secure_store"
    private const val KEY_SIGNED_TOKEN = "key_signed_token"
    private const val KEY_ACTIVATED_CODE = "key_activated_code"
    private const val KEY_ACTIVATED_AT = "key_activated_at"
    private const val KEY_LAST_HEARTBEAT_AT = "key_last_heartbeat_at"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences fallback: ${e.message}")
            context.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Checks if Supabase URL and Anon Key are present in BuildConfig.
     */
    fun getSupabaseConfig(): Pair<String, String> {
        val url = try {
            BuildConfig::class.java.getField("SUPABASE_URL").get(null) as? String ?: ""
        } catch (_: Exception) { "" }

        val key = try {
            BuildConfig::class.java.getField("SUPABASE_ANON_KEY").get(null) as? String ?: ""
        } catch (_: Exception) { "" }

        return Pair(url.trim(), key.trim())
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
        val prefs = getSecurePrefs(context)
        val signedToken = prefs.getString(KEY_SIGNED_TOKEN, null) ?: return false
        val hardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)

        val isValid = DeviceSecurityManager.verifyTokenIntegrity(signedToken, hardwareHash)
        if (!isValid) {
            Log.w(TAG, "⚠️ Cryptographic token integrity check failed! Resetting activation state.")
            clearActivation(context)
            return false
        }
        return true
    }

    fun getActivatedCode(context: Context): String? {
        return getSecurePrefs(context).getString(KEY_ACTIVATED_CODE, null)
    }

    fun getRawSessionToken(context: Context): String? {
        val prefs = getSecurePrefs(context)
        val signedToken = prefs.getString(KEY_SIGNED_TOKEN, null) ?: return null
        val hardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
        return DeviceSecurityManager.extractRawToken(signedToken, hardwareHash)
    }

    fun clearActivation(context: Context) {
        val prefs = getSecurePrefs(context)
        prefs.edit()
            .remove(KEY_SIGNED_TOKEN)
            .remove(KEY_ACTIVATED_CODE)
            .remove(KEY_ACTIVATED_AT)
            .remove(KEY_LAST_HEARTBEAT_AT)
            .apply()
    }

    /**
     * Submits an access code to Supabase RPC 'activate_device' to bind this device.
     */
    suspend fun activateDevice(context: Context, code: String): ActivationResult = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            return@withContext ActivationResult.Error("দয়া করে সঠিক এক্সেস কোড লিখুন।")
        }

        val (supabaseUrl, anonKey) = getSupabaseConfig()
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext ActivationResult.Error("Supabase কনফিগারেশন পাওয়া যায়নি। GitHub Secrets বা .env ফাইলে SUPABASE_URL এবং SUPABASE_ANON_KEY সেট করুন।")
        }

        val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
        val appSignature = DeviceSecurityManager.getAppSignatureSha256(context)

        val endpoint = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/activate_device"

        val jsonBody = JSONObject().apply {
            put("p_code", cleanCode)
            put("p_device_hash", deviceHardwareHash)
            put("p_app_signature", appSignature)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("message", "সার্ভার এরর (HTTP ${response.code})")
                } catch (_: Exception) {
                    "সার্ভার এরর (HTTP ${response.code})"
                }
                return@withContext ActivationResult.Error(errorMsg)
            }

            val jsonRes = JSONObject(responseBody)
            val success = jsonRes.optBoolean("success", false)
            val message = jsonRes.optString("message", "এক্সেস যাচাই সম্পন্ন হয়েছে।")

            if (success) {
                val rawToken = jsonRes.optString("token", "")
                if (rawToken.isBlank()) {
                    return@withContext ActivationResult.Error("সার্ভার থেকে সঠিক টোকেন পাওয়া যায়নি।")
                }

                // Sign the token with this device's unique hardware identity
                val signedToken = DeviceSecurityManager.signToken(rawToken, deviceHardwareHash)

                getSecurePrefs(context).edit()
                    .putString(KEY_SIGNED_TOKEN, signedToken)
                    .putString(KEY_ACTIVATED_CODE, cleanCode)
                    .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
                    .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                    .apply()

                Log.i(TAG, "Device successfully bound and activated with code: $cleanCode")
                ActivationResult.Success(message)
            } else {
                ActivationResult.Error(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Activation call failed", e)
            ActivationResult.Error("সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}")
        }
    }

    /**
     * Verifies if this device's token is still active and not banned in Supabase.
     */
    suspend fun verifyHeartbeat(context: Context): HeartbeatResult = withContext(Dispatchers.IO) {
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

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext HeartbeatResult.NetworkError("HTTP ${response.code}")
            }

            val jsonRes = JSONObject(responseBody)
            val isValid = jsonRes.optBoolean("valid", false)
            val reason = jsonRes.optString("reason", "")

            if (isValid) {
                getSecurePrefs(context).edit()
                    .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
                    .apply()
                HeartbeatResult.Valid
            } else {
                Log.w(TAG, "Heartbeat returned invalid: $reason. Clearing activation.")
                clearActivation(context)
                HeartbeatResult.Revoked(reason)
            }
        } catch (e: Exception) {
            HeartbeatResult.NetworkError(e.message ?: "Network error")
        }
    }
}
