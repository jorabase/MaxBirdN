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
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.StandardCharsets

object QrSessionUtils {

    private const val SESSION_PREFIX = "SHIKHO_SESSION_V1:"

    fun createSessionPayload(sessionManager: SessionManager): String? {
        val token = sessionManager.getAccessToken() ?: return null
        val refreshToken = sessionManager.getRefreshToken()
        val userId = sessionManager.getUserId() ?: "user_${System.currentTimeMillis()}"
        val phone = sessionManager.getUserPhone() ?: ""
        val firstName = sessionManager.getUserFirstName() ?: ""
        val classDisplay = sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "HSC"
        val avatar = sessionManager.getUserAvatar() ?: ""

        try {
            val json = JSONObject().apply {
                put("token", token)
                put("refresh_token", refreshToken ?: "")
                put("user_id", userId)
                put("phone", phone)
                put("first_name", firstName)
                put("class_name", classDisplay)
                put("avatar", avatar)
                put("timestamp", System.currentTimeMillis())
            }
            val base64Encoded = Base64.encodeToString(
                json.toString().toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            return "$SESSION_PREFIX$base64Encoded"
        } catch (e: Exception) {
            Log.e("QrSessionUtils", "Error creating session payload", e)
            return null
        }
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
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
        var cleanInput = rawInput.trim()
        if (cleanInput.startsWith(SESSION_PREFIX)) {
            cleanInput = cleanInput.removePrefix(SESSION_PREFIX)
        }

        try {
            val jsonString = if (cleanInput.startsWith("{")) {
                cleanInput
            } else {
                val decodedBytes = Base64.decode(cleanInput, Base64.NO_WRAP)
                String(decodedBytes, StandardCharsets.UTF_8)
            }

            val json = JSONObject(jsonString)
            val token = json.optString("token", "").ifBlank { json.optString("access_token", "") }
            if (token.isBlank()) return false

            val refreshToken = json.optString("refresh_token", "")
            val userId = json.optString("user_id", "user_${System.currentTimeMillis()}")
            val phone = json.optString("phone", "")
            val firstName = json.optString("first_name", "শিক্ষার্থী")
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

            try {
                com.example.ai.GeminiAiStudyRepository.triggerBackgroundSync(
                    context = com.example.ShikhoApp.instance,
                    sessionManager = sessionManager
                )
            } catch (_: Throwable) {}

            return true
        } catch (e: Exception) {
            Log.e("QrSessionUtils", "Error parsing session payload", e)
            return false
        }
    }
}
