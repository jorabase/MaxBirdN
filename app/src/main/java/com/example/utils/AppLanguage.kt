package com.example.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return when (code?.lowercase(Locale.US)?.trim()) {
                "bn", "bn-bd", "bangla", "bengali" -> BANGLA
                else -> ENGLISH
            }
        }
    }
}

/**
 * Global synchronous accessor for non-Composable code (ViewModels, Repositories, Utils, Formatters, Toasts).
 * Always kept in sync with SessionManager.appLanguageFlow and LocalAppLanguage.
 */
object AppLang {
    @Volatile
    var current: AppLanguage = AppLanguage.ENGLISH

    fun init(context: android.content.Context) {
        try {
            current = com.example.auth.SessionManager(context.applicationContext).getAppLanguage()
        } catch (_: Throwable) {
        }
    }

    fun isBn(): Boolean = current == AppLanguage.BANGLA

    fun isEn(): Boolean = current == AppLanguage.ENGLISH

    fun locale(): Locale = if (isBn()) Locale.forLanguageTag("bn-BD") else Locale.US

    fun tr(en: String, bn: String): String = if (isBn()) bn else en

    fun digits(input: Any?): String = formatLocalizedDigits(input)
}

/**
 * Reactive CompositionLocal so switching language in Settings immediately updates every screen
 * without restarting the app.
 */
val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

@Composable
@ReadOnlyComposable
fun isBangla(): Boolean = LocalAppLanguage.current == AppLanguage.BANGLA

@Composable
@ReadOnlyComposable
fun tr(en: String, bn: String): String =
    if (LocalAppLanguage.current == AppLanguage.BANGLA) bn else en

/**
 * Formats numbers with Bengali digits in Bangla mode and ASCII digits in English mode.
 */
fun formatLocalizedDigits(input: Any?): String {
    if (input == null) return if (AppLang.isBn()) "০" else "0"
    val str = input.toString()
    if (!AppLang.isBn()) {
        // Normalize any Bengali digits back to ASCII when in English mode
        val bnDigits = "০১২৩৪৫৬৭৮৯"
        val sb = StringBuilder(str.length)
        for (ch in str) {
            val idx = bnDigits.indexOf(ch)
            sb.append(if (idx >= 0) ('0' + idx) else ch)
        }
        return sb.toString()
    }
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder(str.length)
    for (char in str) {
        if (char in '0'..'9') {
            sb.append(banglaDigits[char - '0'])
        } else {
            sb.append(char)
        }
    }
    return sb.toString()
}
