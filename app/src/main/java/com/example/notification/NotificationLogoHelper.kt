package com.example.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.R

/**
 * Provides a crisp circular Maxbird app logo bitmap for Android notifications.
 *
 * On Android 8.0+ (API 26+), [BitmapFactory.decodeResource] on `R.mipmap.ic_launcher`
 * returns `null` because `mipmap-anydpi-v26/ic_launcher.xml` is an `<adaptive-icon>` XML.
 * Rendering the high-resolution PNG foreground asset onto a circular white badge ensures
 * all push, class alarm, and download notifications reliably display the Maxbird logo.
 */
object NotificationLogoHelper {
    private const val LOGO_SIZE_PX = 192

    @Volatile
    private var cachedLogoBitmap: Bitmap? = null

    fun getCircularAppLogo(context: Context): Bitmap? {
        val current = cachedLogoBitmap
        if (current != null && !current.isRecycled) {
            return current
        }
        return synchronized(this) {
            val recheck = cachedLogoBitmap
            if (recheck != null && !recheck.isRecycled) {
                recheck
            } else {
                createCircularLogo(context.applicationContext)?.also {
                    cachedLogoBitmap = it
                }
            }
        }
    }

    private fun createCircularLogo(context: Context): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.ic_launcher_foreground_asset,
                options
            ) ?: return null

            val output = Bitmap.createBitmap(LOGO_SIZE_PX, LOGO_SIZE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val radius = LOGO_SIZE_PX / 2f

            // Clean circular white backing matching the adaptive launcher icon
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(radius, radius, radius, bgPaint)

            // Draw the Maxbird artwork centered with balanced breathing room (~76% diameter)
            val inset = (LOGO_SIZE_PX * 0.12f).toInt()
            val destRect = Rect(inset, inset, LOGO_SIZE_PX - inset, LOGO_SIZE_PX - inset)
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(raw, null, destRect, bitmapPaint)

            if (raw != output && !raw.isRecycled) {
                raw.recycle()
            }
            output
        } catch (_: Throwable) {
            null
        }
    }
}
