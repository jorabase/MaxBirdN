package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ShikhoSkyBlue,
    onPrimary = Color(0xFF041C32),
    primaryContainer = Color(0xFF0C2D4D),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF042630),
    secondaryContainer = Color(0xFF132A40),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = ShikhoAmber,
    onTertiary = Color(0xFF291800),
    tertiaryContainer = Color(0xFF3A2405),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = ShikhoNavyDark,
    onBackground = Color(0xFFF8FAFC),
    surface = ShikhoNavyCard,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = ShikhoNavySurface,
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF050A14),
    surfaceContainerLow = Color(0xFF0B1424),
    surfaceContainer = ShikhoNavyCard,
    surfaceContainerHigh = Color(0xFF162338),
    surfaceContainerHighest = Color(0xFF1E2F49),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E2E47),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF45121B),
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = ShikhoElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECFEFF),
    onSecondaryContainer = Color(0xFF0E7490),
    tertiary = ShikhoAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFFB45309),
    background = CanvasLight,
    onBackground = TextNavyDark,
    surface = CanvasWhite,
    onSurface = TextNavyDark,
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = TextSlateMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE8EEF5),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = BorderSubtle,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

val ModernShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalAppDarkTheme = compositionLocalOf { false }

@Composable
fun isAppInDarkTheme(): Boolean = LocalAppDarkTheme.current

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ModernShapes,
            content = content
        )
    }
}

/**
 * Temporarily adjusts system status bar icon contrast for screens with dark top headers
 * (e.g., Home wallpaper header, Splash, Device Activation) even while the app is in Light Mode,
 * and cleanly restores the active app theme default when the composable leaves composition.
 */
@Composable
fun StatusBarAppearanceEffect(lightIcons: Boolean) {
    val view = LocalView.current
    val isDark = isAppInDarkTheme()
    if (!view.isInEditMode) {
        DisposableEffect(view, lightIcons, isDark) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.isAppearanceLightStatusBars = !lightIcons
            onDispose {
                controller?.isAppearanceLightStatusBars = !isDark
            }
        }
    }
}
