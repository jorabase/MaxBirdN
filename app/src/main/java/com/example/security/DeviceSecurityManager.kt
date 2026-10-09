package com.example.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * DeviceSecurityManager
 * 
 * Hardware-level device fingerprinting, APK certificate verification,
 * and cryptographic HMAC-SHA256 token signing to prevent MT Manager tampering.
 */
object DeviceSecurityManager {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val INTERNAL_APP_SALT = "MaxBird_Shikho_ZeroTrust_2026_SecureKey"

    /**
     * Stable, unique hardware fingerprint hash for the current physical device.
     * Combines Settings.Secure.ANDROID_ID with hardware board/brand characteristics.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceHardwareHash(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_android_id"

            val hardwareSeed = buildString {
                append(androidId)
                append("|")
                append(Build.BOARD)
                append("|")
                append(Build.BRAND)
                append("|")
                append(Build.MANUFACTURER)
                append("|")
                append(Build.DEVICE)
                append("|")
                append(Build.MODEL)
            }

            sha256(hardwareSeed)
        } catch (_: Exception) {
            "fallback_${Build.MODEL.hashCode()}_${Build.BOARD.hashCode()}"
        }
    }

    /**
     * User-friendly short device ID for display on UI screen (e.g. MX-7F3A-9B21-C04E).
     * Easy for students to copy and send to admin over Telegram/WhatsApp.
     */
    fun getDisplayDeviceId(context: Context): String {
        val hash = getDeviceHardwareHash(context).uppercase()
        val chunk1 = hash.take(4).ifEmpty { "A1B2" }
        val chunk2 = hash.drop(4).take(4).ifEmpty { "C3D4" }
        val chunk3 = hash.drop(8).take(4).ifEmpty { "E5F6" }
        return "MX-$chunk1-$chunk2-$chunk3"
    }

    /**
     * Computes the SHA-256 digest of the APK's release signing certificate.
     * If an attacker modifies smali in MT Manager and re-signs the APK with a custom key,
     * this hash changes and verification fails.
     */
    @Suppress("DEPRECATION")
    fun getAppSignatureSha256(context: Context): String {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pm.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val certBytes = signatures[0].toByteArray()
                sha256Bytes(certBytes)
            } else {
                "NO_SIGNATURE"
            }
        } catch (e: Exception) {
            "SIGNATURE_ERROR_${e.message?.take(10)}"
        }
    }

    /**
     * Signs a raw session token with HMAC-SHA256 using the device's hardware identity as key.
     * Format: rawToken:timestamp:hmacSignature
     */
    fun signToken(token: String, deviceHardwareHash: String): String {
        val timestamp = System.currentTimeMillis()
        val payload = "$token:$timestamp"
        val signature = computeHmac(payload, deviceHardwareHash)
        return "$payload:$signature"
    }

    /**
     * Validates whether a stored signed token was genuinely created on THIS specific device
     * and has not been edited in SharedPreferences by MT Manager.
     */
    fun verifyTokenIntegrity(signedToken: String?, deviceHardwareHash: String): Boolean {
        if (signedToken.isNullOrBlank()) return false
        val parts = signedToken.split(":")
        if (parts.size != 3) return false

        val token = parts[0]
        val timestamp = parts[1]
        val signature = parts[2]

        if (token.isBlank() || timestamp.isBlank() || signature.isBlank()) return false

        val payload = "$token:$timestamp"
        val expectedSignature = computeHmac(payload, deviceHardwareHash)
        return signature == expectedSignature
    }

    /**
     * Extracts raw token from signed payload if valid.
     */
    fun extractRawToken(signedToken: String?, deviceHardwareHash: String): String? {
        if (signedToken.isNullOrBlank()) return null
        val parts = signedToken.split(":")
        if (parts.size != 3) return null
        if (!verifyTokenIntegrity(signedToken, deviceHardwareHash)) return null
        return parts[0]
    }

    private fun computeHmac(data: String, deviceHardwareHash: String): String {
        val secret = "$INTERNAL_APP_SALT:$deviceHardwareHash"
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256(input: String): String {
        return sha256Bytes(input.toByteArray(Charsets.UTF_8))
    }

    private fun sha256Bytes(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
