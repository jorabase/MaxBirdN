package com.example.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.PI
import kotlin.math.sin

data class HeaderWallpaperConfig(
    val type: String = PRESET_COSMIC,
    val customUri: String? = null,
    val customTitle: String? = null,
    val isAnimationEnabled: Boolean = true,
    val dimOpacity: Float = 0.25f
) {
    companion object {
        const val PRESET_COSMIC = "preset_cosmic"
        const val PRESET_SUNSET = "preset_sunset"
        const val PRESET_CYBER = "preset_cyber"
        const val PRESET_AURORA = "preset_aurora"
        const val PRESET_NATURE = "preset_nature"
        const val CUSTOM_IMAGE = "custom_image"
    }
}

data class WallpaperPresetItem(
    val id: String,
    val nameBn: String,
    val nameEn: String,
    val description: String,
    val previewColors: List<Color>,
    val isLiveAnimated: Boolean = true
)

object HeaderWallpaperPresets {
    val presets = listOf(
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_COSMIC,
            nameBn = "কসমিক গ্যালাক্সি",
            nameEn = "Cosmic Galaxy",
            description = "নীল তারা, নীহারিকা ও ভাসমান আলোর কণা",
            previewColors = listOf(Color(0xFF07152B), Color(0xFF0F2744), Color(0xFF1E3A8A)),
            isLiveAnimated = true
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_SUNSET,
            nameBn = "গোধূলি দিগন্ত (বিকাশ স্টাইল)",
            nameEn = "Sunset Horizon",
            description = "গোলাপি ল্যান্ডস্কেপ, নদী, ভাসমান মেঘ ও গোধূলি আভা",
            previewColors = listOf(Color(0xFF9D174D), Color(0xFFBE185D), Color(0xFFFB7185)),
            isLiveAnimated = true
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_CYBER,
            nameBn = "সাইবার গোল্ড",
            nameEn = "Cyber Gold",
            description = "বেগুনি ও গোল্ডেন মেটালিক এনার্জি ওয়েভ",
            previewColors = listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFFD97706)),
            isLiveAnimated = true
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_AURORA,
            nameBn = "অরোরা বোরিয়ালিস",
            nameEn = "Emerald Aurora",
            description = "রহস্যময় সবুজ-নীল নর্দার্ন লাইটস ও শান্ত রাত",
            previewColors = listOf(Color(0xFF042F2E), Color(0xFF065F46), Color(0xFF0D9488)),
            isLiveAnimated = true
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_NATURE,
            nameBn = "উজ্জ্বল নীল আকাশ",
            nameEn = "Azure Skies",
            description = "নির্মল নীল আকাশ ও মৃদু ভাসমান শুভ্র মেঘ",
            previewColors = listOf(Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF38BDF8)),
            isLiveAnimated = true
        )
    )

    fun findById(id: String?): WallpaperPresetItem {
        return presets.find { it.id == id } ?: presets.first()
    }
}

/**
 * Animated High-Performance Live Header Background Composable
 */
@Composable
fun LiveHeaderBackground(
    config: HeaderWallpaperConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Infinite animation transition for live effects
    val infiniteTransition = rememberInfiniteTransition(label = "LiveWallpaperTransition")
    
    val phase by if (config.isAnimationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WavePhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val glowAlpha by if (config.isAnimationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowPulse"
        )
    } else {
        remember { mutableFloatStateOf(0.6f) }
    }

    val cloudOffset by if (config.isAnimationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "CloudDrift"
        )
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (config.type) {
            HeaderWallpaperConfig.CUSTOM_IMAGE -> {
                if (!config.customUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(config.customUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Custom Header Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // High-contrast scrim overlay so text is crystal clear
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )
                } else {
                    // Fallback to cosmic
                    CosmicGalaxyCanvas(phase, glowAlpha)
                }
            }

            HeaderWallpaperConfig.PRESET_SUNSET -> {
                SunsetBloomCanvas(phase, cloudOffset, glowAlpha)
            }

            HeaderWallpaperConfig.PRESET_CYBER -> {
                CyberGoldCanvas(phase, glowAlpha)
            }

            HeaderWallpaperConfig.PRESET_AURORA -> {
                EmeraldAuroraCanvas(phase, glowAlpha)
            }

            HeaderWallpaperConfig.PRESET_NATURE -> {
                AzureSkiesCanvas(cloudOffset)
            }

            else -> { // Default: Cosmic Galaxy
                CosmicGalaxyCanvas(phase, glowAlpha)
            }
        }

        // Universal subtle dark vignette at the bottom for smooth contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f)
                        )
                    )
                )
        )
    }
}

// -------------------------------------------------------------
// Vector Art Canvases (Ultra lightweight, silky smooth 60fps)
// -------------------------------------------------------------

@Composable
private fun CosmicGalaxyCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Base gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF040D1B), Color(0xFF07172E), Color(0xFF0A2244))
            )
        )

        // Shimmering Nebula Orbs
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.35f * glowAlpha), Color.Transparent),
                center = Offset(w * 0.8f, h * 0.3f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.8f, h * 0.3f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF818CF8).copy(alpha = 0.25f * glowAlpha), Color.Transparent),
                center = Offset(w * 0.2f, h * 0.8f),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w * 0.2f, h * 0.8f)
        )

        // Subtle glowing stars
        val starPositions = listOf(
            Offset(w * 0.12f, h * 0.25f) to 1.8f,
            Offset(w * 0.28f, h * 0.15f) to 2.4f,
            Offset(w * 0.45f, h * 0.35f) to 1.5f,
            Offset(w * 0.65f, h * 0.18f) to 2.0f,
            Offset(w * 0.88f, h * 0.28f) to 2.5f,
            Offset(w * 0.75f, h * 0.7f) to 1.6f,
            Offset(w * 0.35f, h * 0.65f) to 2.2f,
            Offset(w * 0.52f, h * 0.8f) to 1.4f
        )

        starPositions.forEachIndexed { index, (pos, rad) ->
            val twinkle = (sin(phase + index * 1.3f) + 1f) / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.3f + twinkle * 0.7f),
                radius = rad * (0.8f + twinkle * 0.4f),
                center = pos
            )
        }
    }
}

@Composable
private fun SunsetBloomCanvas(phase: Float, cloudOffset: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Rich Sunset Sky Gradient (Inspired by bKash & Bengali riverine sunset)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF831843), // deep magenta
                    Color(0xFFBE185D), // rich pink
                    Color(0xFFDB2777), // rose
                    Color(0xFFF43F5E), // sunset red-pink
                    Color(0xFFFB7185)  // soft peach
                )
            )
        )

        // Glowing Sun / Moon Aura behind hills
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.5f * glowAlpha),
                    Color(0xFFF43F5E).copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(w * 0.3f, h * 0.5f),
                radius = w * 0.4f
            ),
            radius = w * 0.4f,
            center = Offset(w * 0.3f, h * 0.5f)
        )

        // Drifting Soft Clouds
        drawSoftCloud(Offset((w * (cloudOffset * 1.5f)) % (w * 1.6f) - w * 0.3f, h * 0.22f), scale = 1.1f, Color.White.copy(alpha = 0.18f))
        drawSoftCloud(Offset((w * (cloudOffset * 1.1f + 0.5f)) % (w * 1.6f) - w * 0.3f, h * 0.4f), scale = 0.85f, Color.White.copy(alpha = 0.14f))

        // Rolling Hill Layer 1 (Back)
        val hillPathBack = Path().apply {
            moveTo(0f, h * 0.65f)
            cubicTo(w * 0.25f, h * 0.55f, w * 0.45f, h * 0.72f, w * 0.7f, h * 0.60f)
            cubicTo(w * 0.85f, h * 0.52f, w * 0.95f, h * 0.58f, w, h * 0.55f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hillPathBack, color = Color(0xFF9D174D).copy(alpha = 0.45f))

        // Rolling Hill Layer 2 (Front with river shine)
        val hillPathFront = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.3f, h * 0.68f, w * 0.55f, h * 0.85f, w * 0.8f, h * 0.72f)
            cubicTo(w * 0.92f, h * 0.66f, w * 0.98f, h * 0.70f, w, h * 0.68f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hillPathFront, color = Color(0xFF701A75).copy(alpha = 0.65f))

        // Subtle River Curve at bottom
        val riverPath = Path().apply {
            moveTo(w * 0.2f, h)
            cubicTo(w * 0.4f, h * 0.88f, w * 0.6f, h * 0.92f, w * 0.75f, h)
            close()
        }
        drawPath(riverPath, color = Color(0xFFFDE047).copy(alpha = 0.25f * glowAlpha))
    }
}

@Composable
private fun CyberGoldCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Dark cyber purple/black gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0F0B24), Color(0xFF1B113B), Color(0xFF2E1065))
            )
        )

        // Dynamic Golden Mesh Light
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.35f * glowAlpha), Color.Transparent),
                center = Offset(w * 0.85f, h * 0.25f),
                radius = w * 0.6f
            ),
            radius = w * 0.6f,
            center = Offset(w * 0.85f, h * 0.25f)
        )

        // Waving Cyber Grid Line
        val wavePath = Path().apply {
            moveTo(0f, h * 0.7f)
            for (x in 0..w.toInt() step 20) {
                val xf = x.toFloat()
                val yf = h * 0.7f + sin(xf * 0.015f + phase) * 16f
                lineTo(xf, yf)
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            wavePath,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFD97706).copy(alpha = 0.3f), Color(0xFF7C3AED).copy(alpha = 0.5f))
            )
        )
    }
}

@Composable
private fun EmeraldAuroraCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep Night Teal
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF021E1E), Color(0xFF042F2E), Color(0xFF064E3B))
            )
        )

        // Aurora Ribbon 1
        val aurora1 = Path().apply {
            moveTo(0f, h * 0.4f)
            cubicTo(
                w * 0.3f, h * 0.2f + sin(phase) * 18f,
                w * 0.7f, h * 0.6f + sin(phase + 1f) * 22f,
                w, h * 0.35f
            )
            lineTo(w, h * 0.8f)
            cubicTo(
                w * 0.7f, h * 0.85f,
                w * 0.3f, h * 0.65f,
                0f, h * 0.75f
            )
            close()
        }
        drawPath(
            aurora1,
            brush = Brush.linearGradient(
                listOf(Color(0xFF10B981).copy(alpha = 0.35f * glowAlpha), Color(0xFF06B6D4).copy(alpha = 0.25f * glowAlpha))
            )
        )
    }
}

@Composable
private fun AzureSkiesCanvas(cloudOffset: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF38BDF8))
            )
        )

        drawSoftCloud(Offset((w * (cloudOffset * 1.3f)) % (w * 1.5f) - w * 0.2f, h * 0.3f), scale = 1.2f, Color.White.copy(alpha = 0.35f))
        drawSoftCloud(Offset((w * (cloudOffset * 0.9f + 0.6f)) % (w * 1.5f) - w * 0.2f, h * 0.55f), scale = 0.9f, Color.White.copy(alpha = 0.28f))
    }
}

private fun DrawScope.drawSoftCloud(center: Offset, scale: Float, color: Color) {
    val r = 24.dp.toPx() * scale
    drawCircle(color, radius = r, center = center)
    drawCircle(color, radius = r * 0.8f, center = Offset(center.x - r * 0.7f, center.y + r * 0.2f))
    drawCircle(color, radius = r * 0.85f, center = Offset(center.x + r * 0.7f, center.y + r * 0.2f))
    drawCircle(color, radius = r * 0.65f, center = Offset(center.x + r * 1.3f, center.y + r * 0.3f))
}
