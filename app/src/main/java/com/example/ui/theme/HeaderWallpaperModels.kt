package com.example.ui.theme

import android.net.Uri
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.utils.AppLang
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class HeaderWallpaperConfig(
    val type: String = PRESET_COSMIC,
    val customUri: String? = null,
    val customTitle: String? = null,
    val isAnimationEnabled: Boolean = true,
    val dimOpacity: Float = 0.25f,
    val customThemeCode: String? = null
) {
    companion object {
        const val PRESET_AUTO_DAILY = "preset_auto_daily"
        const val PRESET_COSMIC = "preset_cosmic"
        const val PRESET_NEBULA_PORTAL = "preset_nebula_portal"
        const val PRESET_CYBERPUNK_MATRIX = "preset_cyberpunk_matrix"
        const val PRESET_SUNSET = "preset_sunset"
        const val PRESET_AURORA = "preset_aurora"
        const val PRESET_FIREFLY_FOREST = "preset_firefly_forest"
        const val PRESET_SAKURA_BREEZE = "preset_sakura_breeze"
        const val PRESET_DEEP_OCEAN = "preset_deep_ocean"
        const val PRESET_ROYAL_VELVET = "preset_royal_velvet"
        const val PRESET_SOLAR_ECLIPSE = "preset_solar_eclipse"
        const val PRESET_CYBER = "preset_cyber"
        const val PRESET_MATRIX_STREAM = "preset_matrix_stream"
        const val PRESET_LOFI_NIGHT = "preset_lofi_night"
        const val PRESET_CRYSTAL_PRISM = "preset_crystal_prism"
        const val PRESET_VOLCANIC_EMBER = "preset_volcanic_ember"
        const val PRESET_NATURE = "preset_nature"
        const val CUSTOM_IMAGE = "custom_image"
        const val CUSTOM_CODE = "custom_code"
    }
}

data class WallpaperPresetItem(
    val id: String,
    val nameBn: String,
    val nameEn: String,
    val categoryBn: String,
    val categoryEn: String,
    val descriptionBn: String,
    val descriptionEn: String,
    val previewColors: List<Color>,
    val accentColor: Color,
    val isLiveAnimated: Boolean = true
) {
    val displayName: String get() = AppLang.tr(nameEn, nameBn)
    val displayCategory: String get() = AppLang.tr(categoryEn, categoryBn)
    val displayDescription: String get() = AppLang.tr(descriptionEn, descriptionBn)
    val description: String get() = displayDescription
}

data class ImportedWallpaperTheme(
    val id: String = "custom_" + UUID.randomUUID().toString().take(8),
    val titleBn: String,
    val effectStyle: String, // GALAXY, AURORA, MATRIX, PLASMA, SAKURA, FIREFLY, OCEAN, PRISM
    val primaryHex: String,
    val secondaryHex: String,
    val accentHex: String,
    val particleHex: String = "#FFFFFF",
    val speedMultiplier: Int = 1,
    val rawCode: String = ""
) {
    fun toShareableCode(): String {
        if (rawCode.startsWith("MAXTHEME:", ignoreCase = true)) return rawCode
        return "MAXTHEME:v1|${titleBn.replace("|", " ")}|$effectStyle|$primaryHex|$secondaryHex|$accentHex|$particleHex|$speedMultiplier"
    }

    fun parseColors(): List<Color> {
        return listOf(
            HeaderWallpaperCodeManager.parseColorSafe(primaryHex, Color(0xFF0B1120)),
            HeaderWallpaperCodeManager.parseColorSafe(secondaryHex, Color(0xFF1E1B4B)),
            HeaderWallpaperCodeManager.parseColorSafe(accentHex, Color(0xFF38BDF8))
        )
    }

    fun parseParticleColor(): Color {
        return HeaderWallpaperCodeManager.parseColorSafe(particleHex, Color.White)
    }
}

object HeaderWallpaperCodeManager {
    val effectStyles: List<Pair<String, String>>
        get() = listOf(
            "AURORA" to AppLang.tr("Aurora Ribbon", "অরোরা ওয়েভ (Aurora)"),
            "GALAXY" to AppLang.tr("Cosmic Galaxy", "কসমিক নেবুলা (Galaxy)"),
            "MATRIX" to AppLang.tr("Cyber Grid", "সাইবার গ্রিড (Matrix)"),
            "PLASMA" to AppLang.tr("Plasma Portal", "প্লাজমা পোর্টাল (Plasma)"),
            "SAKURA" to AppLang.tr("Sakura Petals", "সাকুরা পাপড়ি (Sakura)"),
            "FIREFLY" to AppLang.tr("Firefly Swarm", "জোনাকি আলো (Firefly)"),
            "OCEAN" to AppLang.tr("Bioluminescent Ocean", "মায়াবী সমুদ্র (Ocean)"),
            "PRISM" to AppLang.tr("Crystal Prism", "ক্রিস্টাল প্রিজম (Prism)")
        )

    val sampleCommunityCodes = listOf(
        ImportedWallpaperTheme(
            id = "sample_andro_galaxy",
            titleBn = "Andromeda Hyperspace",
            effectStyle = "GALAXY",
            primaryHex = "#050816",
            secondaryHex = "#1E1B4B",
            accentHex = "#A855F7",
            particleHex = "#38BDF8",
            speedMultiplier = 1
        ),
        ImportedWallpaperTheme(
            id = "sample_neon_tokyo",
            titleBn = "Neon Tokyo Nights",
            effectStyle = "MATRIX",
            primaryHex = "#090418",
            secondaryHex = "#3B0764",
            accentHex = "#EC4899",
            particleHex = "#22D3EE",
            speedMultiplier = 1
        ),
        ImportedWallpaperTheme(
            id = "sample_golden_emerald",
            titleBn = "Emerald Royal Gold",
            effectStyle = "AURORA",
            primaryHex = "#022C22",
            secondaryHex = "#064E3B",
            accentHex = "#F59E0B",
            particleHex = "#FDE68A",
            speedMultiplier = 1
        ),
        ImportedWallpaperTheme(
            id = "sample_crimson_moon",
            titleBn = "Crimson Eclipse",
            effectStyle = "PLASMA",
            primaryHex = "#180409",
            secondaryHex = "#4C0519",
            accentHex = "#F43F5E",
            particleHex = "#FDA4AF",
            speedMultiplier = 1
        )
    )

    fun parseColorSafe(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        val clean = hex.trim().removePrefix("#")
        return try {
            val fullHex = when (clean.length) {
                6 -> "FF$clean"
                8 -> clean
                else -> return fallback
            }
            Color(fullHex.toLong(16))
        } catch (_: Exception) {
            fallback
        }
    }

    fun parseThemeCode(rawInput: String): Result<ImportedWallpaperTheme> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return Result.failure(
                IllegalArgumentException(AppLang.tr("Theme code cannot be empty", "থিম কোড খালি রাখা যাবে না"))
            )
        }

        return try {
            val decodedCandidate = if (!trimmed.startsWith("MAXTHEME:", ignoreCase = true) &&
                !trimmed.startsWith("{") &&
                !trimmed.contains("#")
            ) {
                try {
                    String(Base64.decode(trimmed, Base64.DEFAULT)).trim()
                } catch (_: Exception) {
                    trimmed
                }
            } else {
                trimmed
            }

            if (decodedCandidate.startsWith("MAXTHEME:", ignoreCase = true)) {
                val payload = decodedCandidate.substringAfter(":")
                val parts = payload.split("|").map { it.trim() }
                if (parts.size >= 6) {
                    val isVersioned = parts[0].startsWith("v", ignoreCase = true)
                    val offset = if (isVersioned) 1 else 0
                    val title = parts.getOrNull(offset)?.ifBlank { AppLang.tr("Custom Live Theme", "কাস্টম লাইভ থিম") }
                        ?: AppLang.tr("Custom Live Theme", "কাস্টম লাইভ থিম")
                    val style = parts.getOrNull(offset + 1)?.uppercase() ?: "AURORA"
                    val c1 = ensureHex(parts.getOrNull(offset + 2), "#090D16")
                    val c2 = ensureHex(parts.getOrNull(offset + 3), "#1E1B4B")
                    val c3 = ensureHex(parts.getOrNull(offset + 4), "#38BDF8")
                    val pCol = ensureHex(parts.getOrNull(offset + 5), "#FFFFFF")
                    val speed = parts.getOrNull(offset + 6)?.toIntOrNull()?.coerceIn(1, 3) ?: 1

                    val theme = ImportedWallpaperTheme(
                        id = "custom_" + kotlin.math.abs(decodedCandidate.hashCode()),
                        titleBn = title,
                        effectStyle = validStyle(style),
                        primaryHex = c1,
                        secondaryHex = c2,
                        accentHex = c3,
                        particleHex = pCol,
                        speedMultiplier = speed,
                        rawCode = decodedCandidate
                    )
                    return Result.success(theme)
                }
            }

            if (decodedCandidate.startsWith("{") && decodedCandidate.endsWith("}")) {
                val obj = JSONObject(decodedCandidate)
                val title = obj.optString("title", obj.optString("name", AppLang.tr("Custom JSON Theme", "কাস্টম JSON থিম")))
                val style = validStyle(obj.optString("style", obj.optString("effect", "AURORA")).uppercase())
                val colorsArr = obj.optJSONArray("colors")
                val c1 = ensureHex(colorsArr?.optString(0) ?: obj.optString("primary"), "#090D16")
                val c2 = ensureHex(colorsArr?.optString(1) ?: obj.optString("secondary"), "#1E1B4B")
                val c3 = ensureHex(colorsArr?.optString(2) ?: obj.optString("accent"), "#38BDF8")
                val pCol = ensureHex(obj.optString("particle", "#FFFFFF"), "#FFFFFF")
                val speed = obj.optInt("speed", 1).coerceIn(1, 3)

                val theme = ImportedWallpaperTheme(
                    id = "custom_" + kotlin.math.abs(decodedCandidate.hashCode()),
                    titleBn = title,
                    effectStyle = style,
                    primaryHex = c1,
                    secondaryHex = c2,
                    accentHex = c3,
                    particleHex = pCol,
                    speedMultiplier = speed
                )
                return Result.success(theme.copy(rawCode = theme.toShareableCode()))
            }

            val hexRegex = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
            val foundHexes = hexRegex.findAll(decodedCandidate).map { "#" + it.groupValues[1].takeLast(6).uppercase() }.toList()
            if (foundHexes.isNotEmpty()) {
                val c1 = foundHexes.getOrElse(0) { "#090D16" }
                val c2 = foundHexes.getOrElse(1) { c1 }
                val c3 = foundHexes.getOrElse(2) { "#38BDF8" }
                val pCol = foundHexes.getOrElse(3) { "#FFFFFF" }

                val detectedStyle = effectStyles.map { it.first }.firstOrNull {
                    decodedCandidate.contains(it, ignoreCase = true)
                } ?: "AURORA"

                val customName = decodedCandidate
                    .replace(hexRegex, "")
                    .replace(Regex("[,|;:\\-_]+"), " ")
                    .trim()
                    .takeIf { it.length in 2..32 } ?: AppLang.tr("Custom Color Theme", "কাস্টম কালার থিম")

                val theme = ImportedWallpaperTheme(
                    id = "custom_" + kotlin.math.abs(decodedCandidate.hashCode()),
                    titleBn = customName,
                    effectStyle = detectedStyle,
                    primaryHex = c1,
                    secondaryHex = c2,
                    accentHex = c3,
                    particleHex = pCol,
                    speedMultiplier = 1
                )
                return Result.success(theme.copy(rawCode = theme.toShareableCode()))
            }

            Result.failure(
                IllegalArgumentException(
                    AppLang.tr(
                        "Enter a valid code (e.g. MAXTHEME:... or #Hex colors)",
                        "সঠিক থিম কোড দিন (যেমন: MAXTHEME:... অথবা #Hex কালার)"
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException(
                    AppLang.tr(
                        "Invalid theme code: ${e.localizedMessage ?: "parse error"}",
                        "থিম কোডটি পড়া যায়নি: ${e.localizedMessage ?: "অকার্যকর কোড"}"
                    )
                )
            )
        }
    }

    private fun ensureHex(candidate: String?, fallback: String): String {
        if (candidate.isNullOrBlank()) return fallback
        val clean = candidate.trim().removePrefix("#").takeLast(6)
        return if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "#${clean.uppercase()}"
        } else {
            fallback
        }
    }

    private fun validStyle(style: String): String {
        val valid = setOf("AURORA", "GALAXY", "MATRIX", "PLASMA", "SAKURA", "FIREFLY", "OCEAN", "PRISM")
        return if (style.uppercase() in valid) style.uppercase() else "AURORA"
    }

    fun parseThemeListJson(rawJson: String): List<ImportedWallpaperTheme> {
        val list = mutableListOf<ImportedWallpaperTheme>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val item = ImportedWallpaperTheme(
                    id = obj.optString("id", "custom_$i"),
                    titleBn = obj.optString("titleBn", "Custom Theme"),
                    effectStyle = obj.optString("effectStyle", "AURORA"),
                    primaryHex = obj.optString("primaryHex", "#090D16"),
                    secondaryHex = obj.optString("secondaryHex", "#1E1B4B"),
                    accentHex = obj.optString("accentHex", "#38BDF8"),
                    particleHex = obj.optString("particleHex", "#FFFFFF"),
                    speedMultiplier = obj.optInt("speedMultiplier", 1).coerceIn(1, 3),
                    rawCode = obj.optString("rawCode", "")
                )
                list.add(item.copy(rawCode = item.toShareableCode()))
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun serializeThemeListJson(themes: List<ImportedWallpaperTheme>): String {
        val arr = JSONArray()
        themes.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("titleBn", t.titleBn)
                put("effectStyle", t.effectStyle)
                put("primaryHex", t.primaryHex)
                put("secondaryHex", t.secondaryHex)
                put("accentHex", t.accentHex)
                put("particleHex", t.particleHex)
                put("speedMultiplier", t.speedMultiplier)
                put("rawCode", t.toShareableCode())
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}

object HeaderWallpaperPresets {
    const val CAT_ALL = "all"
    const val CAT_COSMIC = "cosmic"
    const val CAT_NATURE = "nature"
    const val CAT_LUXURY = "luxury"

    val categoryKeys = listOf(CAT_ALL, CAT_COSMIC, CAT_NATURE, CAT_LUXURY)

    fun categoryLabel(key: String): String = when (key) {
        CAT_COSMIC -> AppLang.tr("Cosmic", "মহাকাশ")
        CAT_NATURE -> AppLang.tr("Nature", "প্রকৃতি")
        CAT_LUXURY -> AppLang.tr("Neon & Royal", "নিয়ন ও রয়েল")
        else -> AppLang.tr("All", "সব থিম")
    }

    /**
     * 7 Signature Interactive Live Themes (1 for each day of the week in Auto-Daily mode).
     */
    val presets = listOf(
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_COSMIC,
            nameBn = "নীহারিকা গ্যালাক্সি",
            nameEn = "Cosmic Nebula",
            categoryBn = "মহাকাশ",
            categoryEn = "Cosmic",
            descriptionBn = "টাচে তারাগুলো কাছাকাছি এসে নক্ষত্রজাল তৈরি করে",
            descriptionEn = "Stars drift toward touch and weave glowing constellations",
            previewColors = listOf(Color(0xFF030816), Color(0xFF0F2547), Color(0xFF2563EB)),
            accentColor = Color(0xFF38BDF8)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_AURORA,
            nameBn = "নর্দার্ন অরোরা",
            nameEn = "Arctic Aurora",
            categoryBn = "প্রকৃতি",
            categoryEn = "Nature",
            descriptionBn = "আঙুলের স্পর্শে অরোরার আলোর পর্দা দুলে ওঠে",
            descriptionEn = "Silky northern light ribbons bend and flare with your touch",
            previewColors = listOf(Color(0xFF021419), Color(0xFF064E3B), Color(0xFF10B981)),
            accentColor = Color(0xFF34D399)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_DEEP_OCEAN,
            nameBn = "মায়াবী সমুদ্র",
            nameEn = "Bioluminescent Sea",
            categoryBn = "প্রকৃতি",
            categoryEn = "Nature",
            descriptionBn = "স্পর্শে পানির ঢেউ ও উজ্জ্বল মায়াবী আলো ছড়িয়ে পড়ে",
            descriptionEn = "Interactive ocean ripples and glowing bioluminescent waves",
            previewColors = listOf(Color(0xFF020C1B), Color(0xFF0A2E4D), Color(0xFF0EA5E9)),
            accentColor = Color(0xFF22D3EE)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_FIREFLY_FOREST,
            nameBn = "জোনাকি অরণ্য",
            nameEn = "Firefly Forest",
            categoryBn = "প্রকৃতি",
            categoryEn = "Nature",
            descriptionBn = "টাচ করলে সোনালী জোনাকিরা আলোর ঝাঁক হয়ে ছুটে আসে",
            descriptionEn = "Golden fireflies softly gather and glow around your fingertip",
            previewColors = listOf(Color(0xFF03110E), Color(0xFF063729), Color(0xFF84CC16)),
            accentColor = Color(0xFFFACC15)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_SAKURA_BREEZE,
            nameBn = "সাকুরা গোধূলি",
            nameEn = "Sakura Twilight",
            categoryBn = "প্রকৃতি",
            categoryEn = "Nature",
            descriptionBn = "স্পর্শের হাওয়ায় গোলাপি সাকুরা পাপড়ি ভেসে বেড়ায়",
            descriptionEn = "Touch creates a gentle twilight breeze that swirls cherry petals",
            previewColors = listOf(Color(0xFF1A0826), Color(0xFF701A75), Color(0xFFF472B6)),
            accentColor = Color(0xFFF9A8D4)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_CYBERPUNK_MATRIX,
            nameBn = "সাইবার নিয়ন",
            nameEn = "Cyber Neon",
            categoryBn = "নিয়ন ও রয়েল",
            categoryEn = "Neon & Royal",
            descriptionBn = "টাচে নিয়ন পালস রিং ও সিন্থওয়েভ এনার্জি ছড়িয়ে পড়ে",
            descriptionEn = "Synthwave horizon with reactive neon pulse rings on touch",
            previewColors = listOf(Color(0xFF090214), Color(0xFF4A044E), Color(0xFFEC4899)),
            accentColor = Color(0xFFF472B6)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_ROYAL_VELVET,
            nameBn = "রয়েল গোল্ড",
            nameEn = "Royal Gold",
            categoryBn = "নিয়ন ও রয়েল",
            categoryEn = "Neon & Royal",
            descriptionBn = "অবসিডিয়ান ভেলভেটে স্পর্শে ২৪ ক্যারেট স্বর্ণালী দ্যুতি",
            descriptionEn = "Liquid 24K gold silk with shimmering touch-reactive sparkles",
            previewColors = listOf(Color(0xFF0B0804), Color(0xFF422006), Color(0xFFD97706)),
            accentColor = Color(0xFFFBBF24)
        )
    )

    fun matchesCategory(preset: WallpaperPresetItem, categoryKey: String): Boolean = when (categoryKey) {
        CAT_COSMIC -> preset.categoryEn == "Cosmic"
        CAT_NATURE -> preset.categoryEn == "Nature"
        CAT_LUXURY -> preset.categoryEn == "Neon & Royal"
        else -> true
    }

    /** Returns 0..6 for Sunday..Saturday in Asia/Dhaka timezone */
    fun getTodayDayIndex(): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
        return (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
    }

    fun getPresetForDayIndex(dayIndex: Int): WallpaperPresetItem {
        return presets[dayIndex.mod(presets.size)]
    }

    fun getDayShortLabel(dayIndex: Int): String = when (dayIndex.mod(7)) {
        0 -> AppLang.tr("Sun", "রবি")
        1 -> AppLang.tr("Mon", "সোম")
        2 -> AppLang.tr("Tue", "মঙ্গল")
        3 -> AppLang.tr("Wed", "বুধ")
        4 -> AppLang.tr("Thu", "বৃহঃ")
        5 -> AppLang.tr("Fri", "শুক্র")
        else -> AppLang.tr("Sat", "শনি")
    }

    /** Resolves PRESET_AUTO_DAILY to today's theme preset ID. */
    fun resolveEffectiveType(type: String): String {
        return if (type == HeaderWallpaperConfig.PRESET_AUTO_DAILY) {
            getPresetForDayIndex(getTodayDayIndex()).id
        } else {
            type
        }
    }

    fun findById(id: String?): WallpaperPresetItem {
        if (id == HeaderWallpaperConfig.PRESET_AUTO_DAILY) {
            return getPresetForDayIndex(getTodayDayIndex())
        }
        return presets.find { it.id == id } ?: presets.first()
    }
}

/**
 * Interactive state passed to every live banner canvas so tapping or dragging anywhere
 * on the header creates organic ripples, attraction, flares, and light blooms.
 */
private data class BannerTouchState(
    val pointer: Offset? = null,
    val energy: Float = 0f,
    val rippleOrigin: Offset? = null,
    val rippleProgress: Float = 1f
)

/**
 * Animated High-Performance Interactive Live Header Background.
 * Supports non-blocking touch/drag interactivity across all 7 themes + Auto-Daily 7-Day mode.
 */
@Composable
fun LiveHeaderBackground(
    config: HeaderWallpaperConfig,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "SeamlessLiveWallpaperTransition")

    val phase by if (config.isAnimationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SeamlessWavePhase"
        )
    } else {
        remember { mutableFloatStateOf(0.8f) }
    }

    val unitCycle = remember(phase) {
        (phase / (2.0 * PI).toFloat()).coerceIn(0f, 1f)
    }

    val glowAlpha = remember(phase) {
        0.76f + 0.22f * sin(phase)
    }

    // Interactive non-blocking touch state
    var activePointer by remember { mutableStateOf<Offset?>(null) }
    var isTouching by remember { mutableStateOf(false) }
    var rippleOrigin by remember { mutableStateOf<Offset?>(null) }
    val rippleAnim = remember { Animatable(1f) }

    val touchEnergy by animateFloatAsState(
        targetValue = if (isTouching) 1f else 0f,
        animationSpec = tween(durationMillis = if (isTouching) 180 else 950, easing = FastOutSlowInEasing),
        label = "banner_touch_energy"
    )

    val touchState = BannerTouchState(
        pointer = activePointer,
        energy = if (interactive) touchEnergy else 0f,
        rippleOrigin = if (interactive) rippleOrigin else null,
        rippleProgress = if (interactive) rippleAnim.value else 1f
    )

    val effectiveType = remember(config.type) {
        HeaderWallpaperPresets.resolveEffectiveType(config.type)
    }

    val parsedCustomTheme = remember(effectiveType, config.customThemeCode) {
        if (effectiveType == HeaderWallpaperConfig.CUSTOM_CODE && !config.customThemeCode.isNullOrBlank()) {
            HeaderWallpaperCodeManager.parseThemeCode(config.customThemeCode).getOrNull()
        } else {
            null
        }
    }

    val touchModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val firstChange = event.changes.firstOrNull()
                    if (firstChange != null) {
                        val pos = firstChange.position
                        if (firstChange.pressed) {
                            if (!isTouching) {
                                rippleOrigin = pos
                                scope.launch {
                                    rippleAnim.snapTo(0f)
                                    rippleAnim.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                            activePointer = pos
                            isTouching = true
                        } else {
                            isTouching = false
                        }
                    } else {
                        isTouching = false
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize().then(touchModifier)) {
        when (effectiveType) {
            HeaderWallpaperConfig.CUSTOM_IMAGE -> {
                if (!config.customUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(config.customUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Custom Header Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val clampDim = config.dimOpacity.coerceIn(0.05f, 0.85f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = (clampDim * 0.85f).coerceIn(0f, 0.9f)),
                                        Color.Black.copy(alpha = (clampDim * 1.25f).coerceIn(0.15f, 0.92f))
                                    )
                                )
                            )
                    )
                } else {
                    CosmicSupernovaCanvas(phase, glowAlpha, touchState)
                }
            }

            HeaderWallpaperConfig.CUSTOM_CODE -> {
                if (parsedCustomTheme != null) {
                    CustomCodeWallpaperCanvas(
                        theme = parsedCustomTheme,
                        phase = phase,
                        unitCycle = unitCycle,
                        glowAlpha = glowAlpha,
                        touch = touchState
                    )
                } else {
                    CosmicSupernovaCanvas(phase, glowAlpha, touchState)
                }
            }

            HeaderWallpaperConfig.PRESET_AURORA,
            HeaderWallpaperConfig.PRESET_NATURE ->
                ArcticAuroraCanvas(phase, glowAlpha, touchState)

            HeaderWallpaperConfig.PRESET_DEEP_OCEAN ->
                BioluminescentOceanCanvas(phase, glowAlpha, touchState)

            HeaderWallpaperConfig.PRESET_CYBERPUNK_MATRIX,
            HeaderWallpaperConfig.PRESET_MATRIX_STREAM,
            HeaderWallpaperConfig.PRESET_CRYSTAL_PRISM ->
                CyberpunkNeonMatrixCanvas(phase, unitCycle, glowAlpha, touchState)

            HeaderWallpaperConfig.PRESET_FIREFLY_FOREST ->
                EnchantedFireflyForestCanvas(phase, glowAlpha, touchState)

            HeaderWallpaperConfig.PRESET_SAKURA_BREEZE,
            HeaderWallpaperConfig.PRESET_SUNSET ->
                SakuraBlossomBreezeCanvas(phase, glowAlpha, touchState)

            HeaderWallpaperConfig.PRESET_ROYAL_VELVET,
            HeaderWallpaperConfig.PRESET_CYBER,
            HeaderWallpaperConfig.PRESET_SOLAR_ECLIPSE,
            HeaderWallpaperConfig.PRESET_VOLCANIC_EMBER ->
                RoyalGoldenSilkCanvas(phase, glowAlpha, touchState)

            else ->
                CosmicSupernovaCanvas(phase, glowAlpha, touchState)
        }

        // Subtle bottom vignette for crisp text readability on all presets
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f)
                        )
                    )
                )
        )
    }
}

// ============================================================================
// 7 INTERACTIVE SEAMLESS HARMONIC LIVE WALLPAPER CANVASES
// ============================================================================

@Composable
private fun CosmicSupernovaCanvas(phase: Float, glowAlpha: Float, touch: BannerTouchState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF030816), Color(0xFF0A1936), Color(0xFF172554)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        val orb1Base = Offset(w * (0.76f + 0.08f * cos(phase)), h * (0.34f + 0.10f * sin(phase)))
        val orb1Center = if (tp != null && te > 0.01f) {
            Offset(orb1Base.x + (tp.x - orb1Base.x) * 0.24f * te, orb1Base.y + (tp.y - orb1Base.y) * 0.24f * te)
        } else orb1Base

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = (0.44f + 0.18f * te) * glowAlpha),
                    Color(0xFF6366F1).copy(alpha = 0.22f * glowAlpha),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = w * (0.54f + 0.08f * te)
            ),
            radius = w * (0.54f + 0.08f * te),
            center = orb1Center
        )

        val orb2Center = Offset(w * (0.24f + 0.07f * sin(phase)), h * (0.68f + 0.09f * cos(phase)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFA855F7).copy(alpha = 0.36f * glowAlpha),
                    Color(0xFF3B82F6).copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = orb2Center
        )

        drawFlowingRibbon(
            phase = phase + te * 0.6f,
            baseYRatio = 0.54f,
            amplitude = h * (0.11f + 0.03f * te),
            colorStart = Color(0xFF38BDF8).copy(alpha = 0.20f + 0.10f * te),
            colorEnd = Color(0xFFC084FC).copy(alpha = 0.24f + 0.08f * te)
        )
        drawFlowingRibbon(
            phase = -phase + 1.4f,
            baseYRatio = 0.72f,
            amplitude = h * 0.08f,
            colorStart = Color(0xFF6366F1).copy(alpha = 0.22f),
            colorEnd = Color(0xFF22D3EE).copy(alpha = 0.18f)
        )

        val stars = listOf(
            Triple(0.08f, 0.22f, 2.2f), Triple(0.20f, 0.16f, 2.8f),
            Triple(0.34f, 0.32f, 1.8f), Triple(0.48f, 0.18f, 2.4f),
            Triple(0.62f, 0.26f, 3.0f), Triple(0.76f, 0.20f, 2.3f),
            Triple(0.89f, 0.34f, 2.1f), Triple(0.84f, 0.66f, 2.6f),
            Triple(0.66f, 0.72f, 2.0f), Triple(0.44f, 0.68f, 2.4f),
            Triple(0.26f, 0.78f, 1.9f), Triple(0.14f, 0.54f, 2.2f),
            Triple(0.56f, 0.48f, 2.1f), Triple(0.38f, 0.48f, 1.9f)
        )

        val starPositions = stars.mapIndexed { idx, (rx, ry, rad) ->
            val baseX = w * rx + cos(phase + idx * 0.9f) * 6f
            val baseY = h * ry + sin(phase + idx * 0.9f) * 4f
            var sx = baseX
            var sy = baseY
            if (tp != null && te > 0.01f) {
                val dx = tp.x - baseX
                val dy = tp.y - baseY
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                if (dist < w * 0.42f) {
                    val pull = (1f - dist / (w * 0.42f)) * 28f * te
                    sx += (dx / dist) * pull
                    sy += (dy / dist) * pull
                }
            }
            Triple(Offset(sx, sy), rad, idx)
        }

        // Interactive glowing constellation lines near touch
        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, Color(0xFF38BDF8), Color(0xFFC084FC))
            starPositions.forEach { (pos, _, _) ->
                val d = hypot(tp.x - pos.x, tp.y - pos.y)
                if (d < w * 0.30f) {
                    val lineAlpha = (1f - d / (w * 0.30f)) * 0.55f * te
                    drawLine(
                        color = Color(0xFF7DD3FC).copy(alpha = lineAlpha),
                        start = tp,
                        end = pos,
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, Color(0xFF7DD3FC))
        }

        starPositions.forEach { (center, rad, idx) ->
            val twinkle = (sin(phase * 2f + idx * 1.15f) + 1f) * 0.5f
            drawCircle(
                color = Color.White.copy(alpha = 0.32f + twinkle * 0.68f),
                radius = rad * (0.85f + twinkle * 0.45f),
                center = center
            )
            if (idx % 3 == 0) {
                val flare = rad * (2.2f + 1.8f * twinkle)
                drawLine(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.42f * twinkle),
                    start = Offset(center.x - flare, center.y),
                    end = Offset(center.x + flare, center.y),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.42f * twinkle),
                    start = Offset(center.x, center.y - flare),
                    end = Offset(center.x, center.y + flare),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ArcticAuroraCanvas(phase: Float, glowAlpha: Float, touch: BannerTouchState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy
        val touchRatioY = if (tp != null && h > 0f) ((tp.y / h) - 0.5f) * 0.16f * te else 0f

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF021017), Color(0xFF04282E), Color(0xFF064E3B))
            )
        )

        val auroraOrb = if (tp != null && te > 0.01f) {
            Offset(
                w * (0.75f + 0.08f * sin(phase)) * (1f - 0.45f * te) + tp.x * 0.45f * te,
                h * (0.28f + 0.06f * cos(phase)) * (1f - 0.45f * te) + tp.y * 0.45f * te
            )
        } else {
            Offset(w * (0.75f + 0.08f * sin(phase)), h * (0.28f + 0.06f * cos(phase)))
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF34D399).copy(alpha = (0.40f + 0.20f * te) * glowAlpha),
                    Color(0xFF06B6D4).copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = auroraOrb,
                radius = w * (0.55f + 0.10f * te)
            ),
            radius = w * (0.55f + 0.10f * te),
            center = auroraOrb
        )

        drawFlowingRibbon(
            phase = phase + te * 0.8f,
            baseYRatio = (0.34f + touchRatioY).coerceIn(0.18f, 0.52f),
            amplitude = h * (0.13f + 0.05f * te),
            colorStart = Color(0xFF10B981).copy(alpha = (0.42f + 0.18f * te) * glowAlpha),
            colorEnd = Color(0xFF06B6D4).copy(alpha = (0.36f + 0.18f * te) * glowAlpha)
        )
        drawFlowingRibbon(
            phase = -phase + 1.8f - te * 0.5f,
            baseYRatio = (0.50f - touchRatioY * 0.6f).coerceIn(0.32f, 0.68f),
            amplitude = h * (0.10f + 0.04f * te),
            colorStart = Color(0xFF34D399).copy(alpha = 0.30f * glowAlpha),
            colorEnd = Color(0xFFA855F7).copy(alpha = (0.28f + 0.16f * te) * glowAlpha)
        )
        drawFlowingRibbon(
            phase = phase * 2f + 0.9f,
            baseYRatio = 0.64f,
            amplitude = h * 0.07f,
            colorStart = Color(0xFF2DD4BF).copy(alpha = 0.24f),
            colorEnd = Color(0xFF38BDF8).copy(alpha = 0.22f)
        )

        // Vertical aurora shimmer rays around touch
        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, Color(0xFF34D399), Color(0xFF22D3EE))
            for (r in -2..2) {
                val rx = tp.x + r * 26f
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF6EE7B7).copy(alpha = 0.34f * te), Color.Transparent)
                    ),
                    start = Offset(rx, 0f),
                    end = Offset(rx, h * 0.78f),
                    strokeWidth = 3.5f
                )
            }
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, Color(0xFF34D399))
        }

        val mountainPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.82f)
            lineTo(w * 0.18f, h * 0.68f)
            lineTo(w * 0.34f, h * 0.79f)
            lineTo(w * 0.55f, h * 0.63f)
            lineTo(w * 0.76f, h * 0.77f)
            lineTo(w * 0.90f, h * 0.69f)
            lineTo(w, h * 0.78f)
            lineTo(w, h)
            close()
        }
        drawPath(mountainPath, color = Color(0xFF021619).copy(alpha = 0.72f))
    }
}

@Composable
private fun BioluminescentOceanCanvas(phase: Float, glowAlpha: Float, touch: BannerTouchState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF020C1B), Color(0xFF062844), Color(0xFF0C4A6E))
            )
        )

        // Moonlit shimmer on ocean surface
        val moonCenter = Offset(w * (0.76f + 0.04f * cos(phase)), h * (0.24f + 0.04f * sin(phase)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF22D3EE).copy(alpha = 0.36f * glowAlpha),
                    Color(0xFF0284C7).copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = moonCenter,
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = moonCenter
        )

        // Bioluminescent layered ocean swells
        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.48f,
            amplitude = h * (0.10f + 0.04f * te),
            colorStart = Color(0xFF06B6D4).copy(alpha = 0.34f * glowAlpha),
            colorEnd = Color(0xFF38BDF8).copy(alpha = 0.28f * glowAlpha)
        )
        drawFlowingRibbon(
            phase = -phase + 2.1f,
            baseYRatio = 0.66f,
            amplitude = h * (0.09f + 0.03f * te),
            colorStart = Color(0xFF0EA5E9).copy(alpha = 0.30f),
            colorEnd = Color(0xFF2DD4BF).copy(alpha = 0.36f * glowAlpha)
        )
        drawFlowingRibbon(
            phase = phase * 2f + 0.7f,
            baseYRatio = 0.80f,
            amplitude = h * 0.06f,
            colorStart = Color(0xFF67E8F9).copy(alpha = 0.24f),
            colorEnd = Color(0xFF0284C7).copy(alpha = 0.26f)
        )

        // Glowing plankton & rising bubbles reacting to touch
        for (i in 0 until 16) {
            val seed = i * 0.44f
            var bx = w * ((i * 0.062f + 0.04f).coerceIn(0.04f, 0.96f)) + sin(phase + seed) * 14f
            var by = h * (0.20f + ((i * 0.19f) % 0.68f)) + cos(phase + seed) * 12f
            var boost = 0f
            if (tp != null && te > 0.01f) {
                val dx = bx - tp.x
                val dy = by - tp.y
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                if (dist < w * 0.36f) {
                    val factor = (1f - dist / (w * 0.36f)) * te
                    boost = factor
                    // Gentle water-wave push outward
                    bx += (dx / dist) * factor * 20f
                    by += (dy / dist) * factor * 14f
                }
            }
            val shimmer = (sin(phase * 2f + seed) + 1f) * 0.5f
            val glowRadius = (10f + boost * 14f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF67E8F9).copy(alpha = (0.45f + 0.45f * boost) * (0.5f + 0.5f * shimmer)),
                        Color.Transparent
                    ),
                    center = Offset(bx, by),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(bx, by)
            )
            drawCircle(
                color = Color(0xFFE0F2FE).copy(alpha = 0.55f + 0.45f * shimmer),
                radius = 2.2f + (i % 3) * 0.9f + boost * 1.5f,
                center = Offset(bx, by)
            )
        }

        // Double concentric water ripple on tap/drag
        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, Color(0xFF22D3EE), Color(0xFF38BDF8))
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, Color(0xFF67E8F9))
            val second = (touch.rippleProgress - 0.18f).coerceIn(0f, 1f)
            if (second > 0f) drawRippleRing(touch.rippleOrigin, second, Color(0xFF22D3EE))
        }
    }
}

@Composable
private fun CyberpunkNeonMatrixCanvas(phase: Float, unitCycle: Float, glowAlpha: Float, touch: BannerTouchState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070210), Color(0xFF1F0638), Color(0xFF4A044E))
            )
        )

        val horizonY = h * 0.56f

        val sunCenter = Offset(
            w * (0.78f + 0.02f * cos(phase)),
            horizonY - h * (0.06f + 0.02f * sin(phase))
        )
        val sunRadius = h * (0.27f + 0.015f * sin(phase) + 0.03f * te)
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.88f),
                    Color(0xFFF97316).copy(alpha = 0.86f),
                    Color(0xFFEC4899).copy(alpha = 0.92f)
                ),
                startY = sunCenter.y - sunRadius,
                endY = sunCenter.y + sunRadius
            ),
            radius = sunRadius,
            center = sunCenter
        )

        drawLine(
            color = Color(0xFF22D3EE).copy(alpha = (0.82f + 0.18f * te) * glowAlpha),
            start = Offset(0f, horizonY),
            end = Offset(w, horizonY),
            strokeWidth = 2.5f + 1.2f * te
        )

        // Perspective vanishing point responds smoothly to horizontal touch
        val baseVanishX = w * (0.5f + 0.03f * sin(phase))
        val vanishX = if (tp != null && te > 0.01f) {
            baseVanishX + (tp.x - baseVanishX) * 0.35f * te
        } else baseVanishX

        for (i in -6..6) {
            val bottomX = vanishX + i * (w * 0.14f)
            drawLine(
                color = Color(0xFFEC4899).copy(alpha = 0.34f + 0.16f * te),
                start = Offset(vanishX + i * (w * 0.03f), horizonY),
                end = Offset(bottomX, h),
                strokeWidth = 1.4f
            )
        }

        val gridCount = 6
        for (i in 0 until gridCount) {
            val p = ((i.toFloat() + unitCycle * gridCount) / gridCount) % 1f
            val y = horizonY + (h - horizonY) * (p * p)
            val alpha = sin(p * PI.toFloat()).coerceIn(0f, 1f) * (0.60f + 0.25f * te)
            drawLine(
                color = Color(0xFF22D3EE).copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.6f
            )
        }

        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, Color(0xFF22D3EE), Color(0xFFEC4899))
            // Reactive neon crosshair scanner line
            drawLine(
                color = Color(0xFF22D3EE).copy(alpha = 0.38f * te),
                start = Offset(tp.x, 0f),
                end = Offset(tp.x, h),
                strokeWidth = 1.2f
            )
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, Color(0xFF22D3EE))
        }
    }
}

@Composable
private fun EnchantedFireflyForestCanvas(phase: Float, glowAlpha: Float, touch: BannerTouchState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF03110E), Color(0xFF062920), Color(0xFF0B3B2D))
            )
        )

        val moonCenter = Offset(w * (0.82f + 0.04f * cos(phase)), h * (0.24f + 0.04f * sin(phase)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF34D399).copy(alpha = 0.28f * glowAlpha), Color.Transparent),
                center = moonCenter,
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = moonCenter
        )

        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.68f,
            amplitude = h * 0.08f,
            colorStart = Color(0xFF10B981).copy(alpha = 0.22f),
            colorEnd = Color(0xFF84CC16).copy(alpha = 0.18f)
        )

        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, Color(0xFFFDE047), Color(0xFF84CC16))
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, Color(0xFFFDE047))
        }

        val fireflySeeds = listOf(
            Triple(0.10f, 0.68f, 0.2f), Triple(0.22f, 0.42f, 1.1f),
            Triple(0.34f, 0.74f, 2.3f), Triple(0.46f, 0.36f, 3.4f),
            Triple(0.58f, 0.64f, 4.2f), Triple(0.70f, 0.46f, 5.1f),
            Triple(0.82f, 0.70f, 0.8f), Triple(0.91f, 0.38f, 2.7f),
            Triple(0.16f, 0.28f, 3.9f), Triple(0.52f, 0.80f, 1.6f),
            Triple(0.66f, 0.26f, 4.8f), Triple(0.40f, 0.54f, 5.7f),
            Triple(0.78f, 0.28f, 2.1f), Triple(0.28f, 0.58f, 3.1f)
        )
        fireflySeeds.forEachIndexed { index, (seedX, seedY, offset) ->
            var fx = w * seedX + sin(phase + offset) * (w * 0.055f) + cos(phase * 2f + offset) * (w * 0.02f)
            var fy = h * seedY + cos(phase + offset) * (h * 0.12f) + sin(phase * 2f + offset) * (h * 0.05f)
            var excited = 0f

            // Magnetic swarm attraction toward touch with graceful orbit
            if (tp != null && te > 0.01f) {
                val orbitAngle = phase * 3f + index * 0.7f
                val targetX = tp.x + cos(orbitAngle) * (24f + (index % 4) * 14f)
                val targetY = tp.y + sin(orbitAngle) * (18f + (index % 3) * 12f)
                val dist = hypot(tp.x - fx, tp.y - fy)
                if (dist < w * 0.52f) {
                    val pull = (1f - dist / (w * 0.52f)) * 0.58f * te
                    fx += (targetX - fx) * pull
                    fy += (targetY - fy) * pull
                    excited = pull
                }
            }

            val pulse = (sin(phase * 2f + index * 0.9f) + 1f) * 0.5f
            val alpha = (0.42f + 0.58f * pulse + 0.20f * excited).coerceAtMost(1f)
            val haloRadius = 15f + excited * 12f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE047).copy(alpha = alpha * 0.82f),
                        Color(0xFF84CC16).copy(alpha = alpha * 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(fx, fy),
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = Offset(fx, fy)
            )
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = alpha),
                radius = 2.8f + excited * 1.4f,
                center = Offset(fx, fy)
            )
        }
    }
}

@Composable
private fun SakuraBlossomBreezeCanvas(phase: Float, glowAlpha: Float, touch: BannerTouchState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1A0826), Color(0xFF4A1259), Color(0xFF831843))
            )
        )

        val sunCenter = Offset(w * (0.78f + 0.05f * cos(phase)), h * (0.30f + 0.05f * sin(phase)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF9A8D4).copy(alpha = 0.42f * glowAlpha),
                    Color(0xFFF43F5E).copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = sunCenter,
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = sunCenter
        )

        drawFlowingRibbon(
            phase = phase + te * 0.7f,
            baseYRatio = 0.62f,
            amplitude = h * (0.09f + 0.03f * te),
            colorStart = Color(0xFFF472B6).copy(alpha = 0.24f + 0.12f * te),
            colorEnd = Color(0xFFC084FC).copy(alpha = 0.22f + 0.10f * te)
        )

        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, Color(0xFFF9A8D4), Color(0xFFF472B6))
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, Color(0xFFF9A8D4))
        }

        // Interactive swirling sakura petals
        for (i in 0 until 16) {
            val seed = i * 0.52f
            val baseX = ((i * 0.064f) + 0.04f).coerceIn(0.04f, 0.96f)
            val baseY = ((i * 0.13f) % 0.76f) + 0.12f
            var px = w * baseX + sin(phase + seed) * (w * 0.06f) + cos(phase * 2f + seed) * (w * 0.025f)
            var py = h * baseY + cos(phase + seed) * (h * 0.10f)

            if (tp != null && te > 0.01f) {
                val dx = px - tp.x
                val dy = py - tp.y
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                if (dist < w * 0.44f) {
                    val swirl = (1f - dist / (w * 0.44f)) * 34f * te
                    // Tangential breeze swirl
                    px += (-dy / dist) * swirl + (dx / dist) * swirl * 0.35f
                    py += (dx / dist) * swirl + (dy / dist) * swirl * 0.35f
                }
            }

            val shimmer = 0.55f + 0.45f * sin(phase * 2f + seed)
            val petalColor = if (i % 2 == 0) Color(0xFFFBCFE8) else Color(0xFFF472B6)
            drawOval(
                color = petalColor.copy(alpha = (0.78f * shimmer + 0.18f * te).coerceAtMost(1f)),
                topLeft = Offset(px, py),
                size = Size(10f + (i % 3) * 3f, 6f + (i % 2) * 2f)
            )
        }
    }
}

@Composable
private fun RoyalGoldenSilkCanvas(phase: Float, glowAlpha: Float, touch: BannerTouchState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF090704), Color(0xFF1C1308), Color(0xFF33200A))
            )
        )

        val orbBase = Offset(w * (0.78f + 0.06f * cos(phase)), h * (0.28f + 0.06f * sin(phase)))
        val orbCenter = if (tp != null && te > 0.01f) {
            Offset(orbBase.x + (tp.x - orbBase.x) * 0.32f * te, orbBase.y + (tp.y - orbBase.y) * 0.32f * te)
        } else orbBase

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF59E0B).copy(alpha = (0.38f + 0.22f * te) * glowAlpha),
                    Color(0xFFB45309).copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = orbCenter,
                radius = w * (0.55f + 0.08f * te)
            ),
            radius = w * (0.55f + 0.08f * te),
            center = orbCenter
        )

        drawFlowingRibbon(
            phase = phase + te * 0.5f,
            baseYRatio = 0.46f,
            amplitude = h * (0.11f + 0.03f * te),
            colorStart = Color(0xFFFBBF24).copy(alpha = (0.34f + 0.16f * te) * glowAlpha),
            colorEnd = Color(0xFFD97706).copy(alpha = 0.22f)
        )
        drawFlowingRibbon(
            phase = -phase + 1.5f,
            baseYRatio = 0.66f,
            amplitude = h * 0.09f,
            colorStart = Color(0xFFFDE68A).copy(alpha = 0.24f * glowAlpha),
            colorEnd = Color(0xFF92400E).copy(alpha = 0.30f)
        )

        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, Color(0xFFFDE68A), Color(0xFFF59E0B))
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, Color(0xFFFDE68A))
        }

        // Shimmering 24K Gold Dust Motes sparkling brightly near touch
        for (i in 0 until 14) {
            val seed = i * 0.62f
            var gx = w * (0.06f + i * 0.066f) + sin(phase + seed) * 16f
            var gy = h * (0.22f + (i % 3) * 0.24f) + cos(phase + seed) * 12f
            var sparkleBoost = 0f
            if (tp != null && te > 0.01f) {
                val dx = tp.x - gx
                val dy = tp.y - gy
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                if (dist < w * 0.38f) {
                    val pull = (1f - dist / (w * 0.38f)) * te
                    gx += dx * 0.18f * pull
                    gy += dy * 0.18f * pull
                    sparkleBoost = pull
                }
            }
            val twinkle = (sin(phase * 2f + seed) + 1f) * 0.5f
            val r = 2.2f + (i % 2) * 1.2f + sparkleBoost * 2.2f
            drawCircle(
                color = Color(0xFFFDE68A).copy(alpha = (0.35f + 0.55f * twinkle + 0.25f * sparkleBoost).coerceAtMost(1f)),
                radius = r,
                center = Offset(gx, gy)
            )
            if (sparkleBoost > 0.25f) {
                val ray = r * 2.6f
                drawLine(Color(0xFFFEF3C7).copy(alpha = 0.7f * sparkleBoost), Offset(gx - ray, gy), Offset(gx + ray, gy), 1.2f)
                drawLine(Color(0xFFFEF3C7).copy(alpha = 0.7f * sparkleBoost), Offset(gx, gy - ray), Offset(gx, gy + ray), 1.2f)
            }
        }
    }
}

@Composable
private fun CustomCodeWallpaperCanvas(
    theme: ImportedWallpaperTheme,
    phase: Float,
    unitCycle: Float,
    glowAlpha: Float,
    touch: BannerTouchState
) {
    val colors = remember(theme) { theme.parseColors() }
    val particleCol = remember(theme) { theme.parseParticleColor() }
    val speed = theme.speedMultiplier.coerceIn(1, 3)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tp = touch.pointer
        val te = touch.energy

        drawRect(brush = Brush.verticalGradient(colors = colors))

        val effPhase = phase * speed
        val orbCenter = Offset(w * (0.76f + 0.06f * cos(effPhase)), h * (0.32f + 0.06f * sin(effPhase)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.last().copy(alpha = 0.42f * glowAlpha), Color.Transparent),
                center = orbCenter,
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = orbCenter
        )

        if (tp != null && te > 0.02f) {
            drawInteractiveAuraAndRipple(tp, te, touch.rippleOrigin, touch.rippleProgress, particleCol, colors.last())
        } else if (touch.rippleOrigin != null && touch.rippleProgress < 1f) {
            drawRippleRing(touch.rippleOrigin, touch.rippleProgress, particleCol)
        }

        when (theme.effectStyle.uppercase()) {
            "MATRIX" -> {
                for (c in 0 until 10) {
                    val p = ((unitCycle * speed) + c * 0.1f) % 1f
                    val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                    val x = w * (0.08f + c * 0.09f)
                    val headY = h * p
                    val tailY = (headY - h * 0.30f).coerceAtLeast(0f)
                    drawLine(
                        color = particleCol.copy(alpha = 0.65f * env),
                        start = Offset(x, tailY),
                        end = Offset(x, headY),
                        strokeWidth = 2.4f,
                        cap = StrokeCap.Round
                    )
                }
            }

            "GALAXY", "PLASMA" -> {
                for (i in 0 until 10) {
                    val angle = effPhase + i * 0.62f
                    val ox = w * 0.72f + cos(angle) * (w * 0.08f + i * 10f)
                    val oy = h * 0.42f + sin(angle) * (h * 0.08f + i * 5f)
                    drawCircle(color = particleCol.copy(alpha = 0.70f), radius = 2.5f + (i % 3), center = Offset(ox, oy))
                }
                drawFlowingRibbon(
                    phase = effPhase,
                    baseYRatio = 0.64f,
                    amplitude = h * 0.10f,
                    colorStart = colors.last().copy(alpha = 0.32f),
                    colorEnd = particleCol.copy(alpha = 0.24f)
                )
            }

            "SAKURA", "FIREFLY" -> {
                for (i in 0 until 12) {
                    val seed = i * 0.55f
                    val px = w * (0.08f + i * 0.075f) + sin(effPhase + seed) * 16f
                    val py = h * (0.28f + (i % 3) * 0.22f) + cos(effPhase + seed) * 14f
                    val pulse = 0.45f + 0.55f * ((sin(effPhase * 2f + seed) + 1f) * 0.5f)
                    drawCircle(color = particleCol.copy(alpha = 0.85f * pulse), radius = 3.2f, center = Offset(px, py))
                }
            }

            else -> {
                drawFlowingRibbon(
                    phase = effPhase,
                    baseYRatio = 0.48f,
                    amplitude = h * 0.12f,
                    colorStart = colors.last().copy(alpha = 0.36f * glowAlpha),
                    colorEnd = particleCol.copy(alpha = 0.26f)
                )
                drawFlowingRibbon(
                    phase = -effPhase + 1.4f,
                    baseYRatio = 0.68f,
                    amplitude = h * 0.09f,
                    colorStart = particleCol.copy(alpha = 0.22f),
                    colorEnd = colors.last().copy(alpha = 0.24f)
                )
            }
        }
    }
}

private fun DrawScope.drawInteractiveAuraAndRipple(
    pointer: Offset,
    energy: Float,
    rippleOrigin: Offset?,
    rippleProgress: Float,
    primaryGlow: Color,
    secondaryGlow: Color
) {
    val radius = size.minDimension * (0.48f + 0.18f * energy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryGlow.copy(alpha = 0.36f * energy),
                secondaryGlow.copy(alpha = 0.16f * energy),
                Color.Transparent
            ),
            center = pointer,
            radius = radius
        ),
        radius = radius,
        center = pointer
    )
    if (rippleOrigin != null && rippleProgress < 1f) {
        drawRippleRing(rippleOrigin, rippleProgress, primaryGlow)
    }
}

private fun DrawScope.drawRippleRing(origin: Offset, progress: Float, color: Color) {
    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.55f
    val radius = 14f + progress * size.minDimension * 0.72f
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = origin,
        style = Stroke(width = (2.6f * (1f - progress * 0.6f)).coerceAtLeast(0.8f))
    )
}

private fun DrawScope.drawFlowingRibbon(
    phase: Float,
    baseYRatio: Float,
    amplitude: Float,
    colorStart: Color,
    colorEnd: Color
) {
    val w = size.width
    val h = size.height
    val baseY = h * baseYRatio
    val path = Path().apply {
        moveTo(0f, h)
        val steps = 28
        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val x = w * t
            val y = baseY + sin(t * 2.0 * PI + phase).toFloat() * amplitude +
                cos(t * 4.0 * PI - phase).toFloat() * (amplitude * 0.35f)
            if (i == 0) lineTo(x, y) else lineTo(x, y)
        }
        lineTo(w, h)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(colors = listOf(colorStart, colorEnd))
    )
}
