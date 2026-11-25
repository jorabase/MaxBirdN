package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.auth.SessionManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object QrSessionUtils {

    private const val SESSION_PREFIX = "SHIKHO_SESSION_V1:"
    private const val SESSION_PREFIX_GZ = "SHIKHO_SESSION_GZ:"
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1024 // 64 KB cap against GZIP bombs
    private const val MAX_SESSION_PAYLOAD_AGE_MS = 24L * 60L * 60L * 1000L // 24 hours
    private const val MAX_FUTURE_CLOCK_SKEW_MS = 5L * 60L * 1000L // 5 minutes

    private fun gzipCompressToBase64(input: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(input.toByteArray(StandardCharsets.UTF_8))
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun gunzipFromBase64(base64Str: String): String {
        if (base64Str.length > MAX_DECOMPRESSED_BYTES) {
            throw IllegalArgumentException("Payload exceeds safe size limit")
        }
        val compressed = Base64.decode(base64Str, Base64.DEFAULT)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            var total = 0
            while (true) {
                val n = gis.read(buf)
                if (n == -1) break
                total += n
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw IllegalArgumentException("Decompressed payload exceeds 64KB security cap")
                }
                out.write(buf, 0, n)
            }
            return out.toString(StandardCharsets.UTF_8.name())
        }
    }

    fun createSessionPayload(sessionManager: SessionManager): String? {
        val token = sessionManager.getAccessToken()?.takeIf { it.isNotBlank() } ?: return null
        val refreshToken = sessionManager.getRefreshToken() ?: ""
        val userId = sessionManager.getUserId() ?: "user_${System.currentTimeMillis()}"
        val phone = sessionManager.getUserPhone() ?: ""
        val firstName = sessionManager.getUserFirstName() ?: ""
        val classDisplay = sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "HSC"
        val rawAvatar = sessionManager.getUserAvatar() ?: ""
        val safeAvatar = if (!rawAvatar.startsWith("data:") && rawAvatar.length <= 240) rawAvatar else ""

        return try {
            val json = JSONObject().apply {
                put("token", token)
                put("refresh_token", refreshToken)
                put("user_id", userId)
                put("phone", phone)
                put("first_name", firstName)
                put("class_name", classDisplay)
                if (safeAvatar.isNotEmpty()) put("avatar", safeAvatar)
                put("timestamp", System.currentTimeMillis())
            }
            val compressedBase64 = gzipCompressToBase64(json.toString())
            "$SESSION_PREFIX_GZ$compressedBase64"
        } catch (e: Exception) {
            Log.e("QrSessionUtils", "Error creating session payload", e)
            null
        }
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = QRCodeWriter()
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 1
            )
            val qrContent = if (content.length > 2200 && !content.startsWith(SESSION_PREFIX_GZ)) {
                "$SESSION_PREFIX_GZ${gzipCompressToBase64(content)}"
            } else {
                content
            }
            val bitMatrix = try {
                writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size, hints)
            } catch (_: Exception) {
                // Ultimate fallback if payload is still extra large
                val compact = qrContent.take(1800)
                writer.encode(compact, BarcodeFormat.QR_CODE, size, size, hints)
            }
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            Log.e("QrSessionUtils", "Error generating QR Bitmap", e)
            null
        }
    }

    fun decodeQrFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                decodeQrFromBitmap(bitmap)
            } else null
        } catch (e: Exception) {
            Log.e("QrSessionUtils", "Error reading bitmap from Uri", e)
            null
        }
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val intArray = IntArray(width * height)
            bitmap.getPixels(intArray, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, intArray)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            Log.e("QrSessionUtils", "Error decoding QR from bitmap", e)
            null
        }
    }

    fun importSessionPayload(rawInput: String, sessionManager: SessionManager): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return false

        try {
            val jsonString = when {
                trimmed.startsWith(SESSION_PREFIX_GZ) -> {
                    val b64 = trimmed.removePrefix(SESSION_PREFIX_GZ).trim()
                    val decompressed = gunzipFromBase64(b64)
                    if (decompressed.startsWith(SESSION_PREFIX)) {
                        val innerBytes = Base64.decode(decompressed.removePrefix(SESSION_PREFIX), Base64.DEFAULT)
                        String(innerBytes, StandardCharsets.UTF_8)
                    } else {
                        decompressed
                    }
                }
                trimmed.startsWith(SESSION_PREFIX) -> {
                    val b64 = trimmed.removePrefix(SESSION_PREFIX).trim()
                    val decodedBytes = Base64.decode(b64, Base64.DEFAULT)
                    String(decodedBytes, StandardCharsets.UTF_8)
                }
                trimmed.startsWith("{") -> trimmed
                trimmed.count { it == '.' } == 2 && trimmed.startsWith("eyJ") -> {
                    JSONObject().apply {
                        put("token", trimmed)
                    }.toString()
                }
                else -> {
                    // Try GZIP first, then standard Base64
                    try {
                        gunzipFromBase64(trimmed)
                    } catch (_: Exception) {
                        val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
                        String(decodedBytes, StandardCharsets.UTF_8)
                    }
                }
            }

            val json = JSONObject(jsonString)
            val token = json.optString("token", "").ifBlank { json.optString("access_token", "") }
            if (token.isBlank()) return false

            // Enforce 24-hour validity window on timestamped session backups so leaked QR/text cannot be reused indefinitely
            val payloadTimestamp = json.optLong("timestamp", 0L)
            if (payloadTimestamp > 0L) {
                val now = System.currentTimeMillis()
                if (now - payloadTimestamp > MAX_SESSION_PAYLOAD_AGE_MS || payloadTimestamp - now > MAX_FUTURE_CLOCK_SKEW_MS) {
                    Log.w("QrSessionUtils", "Rejected expired or invalid-timestamp session payload")
                    return false
                }
            }

            val refreshToken = json.optString("refresh_token", "")
            val userId = json.optString("user_id", "user_${System.currentTimeMillis()}")
            val phone = json.optString("phone", "")
            val firstName = json.optString("first_name", AppLang.tr("Student", "শিক্ষার্থী"))
            val className = json.optString("class_name", "HSC")
            val avatar = json.optString("avatar", "")

            sessionManager.saveTokens(
                accessToken = token,
                refreshToken = refreshToken.ifBlank { null },
                userId = userId
            )
            sessionManager.saveUserProfile(
                firstName = firstName,
                lastName = "",
                avatar = avatar.ifBlank { null },
                classDisplay = className
            )
            if (phone.isNotBlank()) {
                sessionManager.saveUserPhone(phone)
            }
            sessionManager.setAccountComplete(true)
            sessionManager.setJustSignedUp(false)

            return true
        } catch (e: Exception) {
            Log.e("QrSessionUtils", "Error parsing session payload", e)
            return false
        }
    }
}
