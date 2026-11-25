package com.example.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.utils.AppLang

data class ExternalEqualizerAppInfo(
    val packageName: String,
    val appName: String,
    val activityName: String? = null,
    val icon: Drawable? = null,
    val isSystemDefault: Boolean = false
)

/**
 * Studio-grade Audio Equalizer & Sound FX Manager.
 * Persists both Built-In 5-band Equalizer/Bass/Loudness settings and the user's
 * selected Mobile Equalizer app so it automatically connects on every audio session
 * without needing to be re-selected.
 */
object EqualizerManager {
    private const val TAG = "EqualizerManager"
    private const val PREFS_NAME = "shikho_equalizer_prefs"

    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET_INDEX = "eq_preset_index"
    private const val KEY_BASS_STRENGTH = "eq_bass_strength" // 0..1000
    private const val KEY_LOUDNESS_GAIN = "eq_loudness_gain" // 0..1500 mB
    private const val KEY_BAND_PREFIX = "eq_band_"
    private const val KEY_EXT_BROADCAST = "eq_external_broadcast"
    private const val KEY_LAST_EXT_APP = "eq_last_external_pkg"
    private const val KEY_LAST_EXT_APP_NAME = "eq_last_external_name"
    private const val KEY_ACTIVE_MODE = "eq_active_mode" // "mobile" or "builtin"

    const val MODE_MOBILE = "mobile"
    const val MODE_BUILTIN = "builtin"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = 0
    private var appContextRef: Context? = null

    data class PresetItem(
        val index: Int,
        val nameBn: String,
        val nameEn: String,
        val subtitleBn: String,
        val subtitleEn: String,
        val bandOffsetsPercent: List<Int> // 5 bands from -100 to +100
    )

    val presets = listOf(
        PresetItem(0, "ভোকাল বুস্ট", "Vocal Clear", "ক্লাসের কথা স্পষ্ট শোনার জন্য সেরা", "Best for crisp, clear lecture voices", listOf(-20, 10, 65, 55, 20)),
        PresetItem(1, "নরমাল স্টুডিও", "Studio Flat", "স্বাভাবিক ও ব্যালেন্সড সাউন্ড", "Natural and balanced studio sound", listOf(0, 0, 0, 0, 0)),
        PresetItem(2, "বেস বুস্ট", "Deep Bass", "গভীর ও ভারী সাউন্ড ইফেক্ট", "Rich, deep low-end bass response", listOf(75, 50, 0, 10, 20)),
        PresetItem(3, "ক্রিস্টাল ক্লিয়ার", "Treble HD", "হাই-ডেফিনিশন শার্প অডিও", "High-definition crisp vocal clarity", listOf(-10, 15, 35, 65, 80)),
        PresetItem(4, "নাইট স্টাডি", "Soft Night", "কানের আরামের জন্য কোমল সাউন্ড", "Gentle sound for late-night study", listOf(20, 25, 15, -15, -25)),
        PresetItem(5, "কাস্টম প্রোফাইল", "Custom EQ", "নিজের মতো ব্যান্ড কাস্টমাইজ করুন", "Customize each frequency band", listOf(0, 0, 0, 0, 0))
    )

    fun bindPlayer(context: Context, player: androidx.media3.exoplayer.ExoPlayer) {
        appContextRef = context.applicationContext
        val currentId = player.audioSessionId
        if (currentId > 0) {
            bindToAudioSession(context, currentId)
        }
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) {
                    bindToAudioSession(context, audioSessionId)
                }
            }
        })
    }

    fun unbindPlayer(context: Context) {
        if (currentAudioSessionId > 0) {
            broadcastAudioSessionClose(context, currentAudioSessionId)
        }
    }

    fun bindToAudioSession(context: Context, audioSessionId: Int) {
        appContextRef = context.applicationContext
        if (audioSessionId <= 0) return

        // Always broadcast to mobile/external equalizers if enabled or if a mobile equalizer is selected
        if (isExternalBroadcastEnabled(context) || getSelectedExternalAppPackage(context) != null) {
            broadcastAudioSessionOpen(context, audioSessionId)
        }

        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            applySavedSettings(context)
            return
        }

        releaseInternal()
        currentAudioSessionId = audioSessionId

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled(context)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer init failed: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isEnabled(context)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "BassBoost init failed: ${e.message}")
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = isEnabled(context)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer init failed: ${e.message}")
        }

        applySavedSettings(context)
    }

    fun getCurrentAudioSessionId(): Int = currentAudioSessionId

    /**
     * Broadcasts ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION so the user's selected mobile equalizer
     * (e.g. Wavelet, Poweramp Equalizer, Dolby Atmos, Xiaomi/Samsung Sound) automatically attaches.
     */
    fun broadcastAudioSessionOpen(context: Context, sessionId: Int = currentAudioSessionId) {
        if (sessionId <= 0) return
        try {
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
            }
            context.sendBroadcast(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to broadcast audio session open: ${e.message}")
        }
    }

    fun broadcastAudioSessionClose(context: Context, sessionId: Int = currentAudioSessionId) {
        if (sessionId <= 0) return
        try {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to broadcast audio session close: ${e.message}")
        }
    }

    /**
     * Scans the device for all system and third-party Equalizer / Sound FX apps.
     */
    fun scanInstalledEqualizerApps(context: Context): List<ExternalEqualizerAppInfo> {
        val pm = context.packageManager
        val discovered = linkedMapOf<String, ExternalEqualizerAppInfo>()

        // 1. Query activities responding to DISPLAY_AUDIO_EFFECT_CONTROL_PANEL
        try {
            val panelIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                PackageManager.MATCH_DEFAULT_ONLY
            }
            val resolveList = pm.queryIntentActivities(panelIntent, flags)
            for (info in resolveList) {
                val pkg = info.activityInfo?.packageName ?: continue
                val actName = info.activityInfo?.name
                val label = try {
                    info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
                } catch (_: Throwable) {
                    pkg
                }
                val icon = try {
                    info.loadIcon(pm)
                } catch (_: Throwable) {
                    null
                }
                discovered[pkg] = ExternalEqualizerAppInfo(
                    packageName = pkg,
                    appName = label,
                    activityName = actName,
                    icon = icon,
                    isSystemDefault = true
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Panel intent scan error: ${e.message}")
        }

        // 2. Check popular external & OEM equalizer packages directly
        val knownEqualizerPackages = listOf(
            "com.pittvandewitt.wavelet" to "Wavelet Headphone EQ",
            "com.maxmpz.equalizer" to "Poweramp Equalizer",
            "com.jazibkhan.equalizer" to "Equalizer Pro",
            "com.devdnua.equalizer.free" to "Equalizer FX",
            "com.devdnua.equalizer" to "Equalizer FX Pro",
            "equalizer.flat.bass.music" to "Flat Equalizer & Bass",
            "com.frack.spotiq" to "SpotEQ 31 Band Equalizer",
            "com.frack.spotiqten" to "SpotEQ 10 Band Equalizer",
            "hr.podlanica.equalizer" to "Music Volume EQ",
            "com. beansmobile.amy.equalizer" to "Equalizer & Bass Booster",
            "music.bassbooster.equalizer" to "Bass Booster & Equalizer",
            "com.oneplus.sound.tuner" to "OnePlus Audio Tuner",
            "com.miui.misound" to "Xiaomi Mi Sound & Dolby",
            "com.sec.android.app.soundalive" to "Samsung SoundAlive / Dolby Atmos",
            "com.samsung.android.soundassistant" to "Samsung SoundAssistant",
            "com.dolby.daxappui" to "Dolby Atmos",
            "com.dolby.daxappCoreUI" to "Dolby Atmos Studio",
            "com.motorola.dolby.dolbyui" to "Moto Dolby Audio",
            "com.realme.dolby" to "Realme Sound / Dolby",
            "com.oppo.music.effect" to "OPPO Real Sound",
            "com.vivo.audiofx" to "Vivo Audio Effect",
            "com.android.musicfx" to "Android System MusicFX",
            "org.lineageos.audiofx" to "AudioFX System Equalizer",
            "me.timschneeberger.rootlessjamesdsp" to "Rootless JamesDSP",
            "com.pittvandewitt.viperfx" to "ViPER4Android FX"
        )

        for ((pkg, fallbackTitle) in knownEqualizerPackages) {
            if (discovered.containsKey(pkg)) continue
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = try {
                    pm.getApplicationLabel(appInfo).toString().takeIf { it.isNotBlank() } ?: fallbackTitle
                } catch (_: Throwable) {
                    fallbackTitle
                }
                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (_: Throwable) {
                    null
                }
                discovered[pkg] = ExternalEqualizerAppInfo(
                    packageName = pkg,
                    appName = label,
                    activityName = null,
                    icon = icon,
                    isSystemDefault = false
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            } catch (_: Throwable) {
            }
        }

        // 3. Always include the universal Android System Audio Control option if not already found
        if (discovered.isEmpty()) {
            discovered["system_default_audiofx"] = ExternalEqualizerAppInfo(
                packageName = "system_default_audiofx",
                appName = AppLang.tr("System AudioFX", "সিস্টেম সাউন্ড ইঞ্জিন"),
                activityName = null,
                icon = null,
                isSystemDefault = true
            )
        }

        return discovered.values.toList()
    }

    /**
     * Persists the user's selected Mobile Equalizer so it remains selected permanently
     * across app restarts without ever needing to be re-selected.
     */
    fun selectMobileEqualizerApp(
        context: Context,
        appInfo: ExternalEqualizerAppInfo,
        openPanelImmediately: Boolean = false
    ) {
        val sessionId = if (currentAudioSessionId > 0) currentAudioSessionId else 0
        setExternalBroadcastEnabled(context, true)
        broadcastAudioSessionOpen(context, sessionId)

        prefs(context).edit()
            .putString(KEY_LAST_EXT_APP, appInfo.packageName)
            .putString(KEY_LAST_EXT_APP_NAME, appInfo.appName)
            .putString(KEY_ACTIVE_MODE, MODE_MOBILE)
            .apply()

        if (openPanelImmediately) {
            launchExternalEqualizerPanel(context, appInfo)
        }
    }

    /**
     * Opens the UI panel of a mobile equalizer app if the user explicitly taps "কন্ট্রোল প্যানেল খুলুন".
     */
    fun launchExternalEqualizerPanel(context: Context, appInfo: ExternalEqualizerAppInfo): Boolean {
        val sessionId = if (currentAudioSessionId > 0) currentAudioSessionId else 0
        setExternalBroadcastEnabled(context, true)
        broadcastAudioSessionOpen(context, sessionId)

        val pm = context.packageManager

        // 1. Try launching specific panel activity
        if (!appInfo.activityName.isNullOrBlank()) {
            try {
                val panelIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    setClassName(appInfo.packageName, appInfo.activityName)
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                return true
            } catch (e: Throwable) {
                Log.w(TAG, "Direct panel activity launch failed: ${e.message}")
            }
        }

        // 2. Try package-scoped DISPLAY_AUDIO_EFFECT_CONTROL_PANEL
        if (appInfo.packageName != "system_default_audiofx") {
            try {
                val pkgPanelIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    setPackage(appInfo.packageName)
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (pkgPanelIntent.resolveActivity(pm) != null) {
                    context.startActivity(pkgPanelIntent)
                    return true
                }
            } catch (_: Throwable) {
            }

            // 3. Try normal launcher intent for that package
            try {
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    launchIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    context.startActivity(launchIntent)
                    return true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Package launch intent failed: ${e.message}")
            }
        }

        // 4. Fallback to generic system control panel
        return openSystemEqualizerPanel(context)
    }

    fun openSystemEqualizerPanel(context: Context): Boolean {
        val sessionId = if (currentAudioSessionId > 0) currentAudioSessionId else 0
        setExternalBroadcastEnabled(context, true)
        broadcastAudioSessionOpen(context, sessionId)
        return try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                val soundSettingsIntent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(soundSettingsIntent)
                Toast.makeText(
                    context,
                    AppLang.tr("Sound settings opened", "সাউন্ড সেটিংস খোলা হয়েছে"),
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        } catch (e: Throwable) {
            Toast.makeText(
                context,
                AppLang.tr("Device audio control panel not found", "ডিভাইসের অডিও কন্ট্রোল প্যানেল পাওয়া যায়নি"),
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    fun getActiveMode(context: Context): String {
        val saved = prefs(context).getString(KEY_ACTIVE_MODE, null)
        if (!saved.isNullOrBlank()) return saved
        return if (getSelectedExternalAppPackage(context) != null) MODE_MOBILE else MODE_BUILTIN
    }

    fun setActiveMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_ACTIVE_MODE, mode).apply()
    }

    fun getSelectedExternalAppPackage(context: Context): String? =
        prefs(context).getString(KEY_LAST_EXT_APP, null)

    fun getSelectedExternalAppName(context: Context): String? =
        prefs(context).getString(KEY_LAST_EXT_APP_NAME, null)

    fun getLastSelectedExternalApp(context: Context): String? =
        getSelectedExternalAppPackage(context)

    fun clearSelectedExternalApp(context: Context) {
        prefs(context).edit()
            .remove(KEY_LAST_EXT_APP)
            .remove(KEY_LAST_EXT_APP_NAME)
            .putString(KEY_ACTIVE_MODE, MODE_BUILTIN)
            .apply()
    }

    fun isExternalBroadcastEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EXT_BROADCAST, true)

    fun setExternalBroadcastEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXT_BROADCAST, enabled).apply()
        if (currentAudioSessionId > 0) {
            if (enabled) {
                broadcastAudioSessionOpen(context, currentAudioSessionId)
            } else {
                broadcastAudioSessionClose(context, currentAudioSessionId)
            }
        }
    }

    fun applySavedSettings(context: Context) {
        val enabled = isEnabled(context)
        val presetIdx = getSelectedPresetIndex(context)
        val bass = getBassStrength(context)
        val loudness = getLoudnessGain(context)

        try {
            equalizer?.enabled = enabled
            if (enabled) {
                val eq = equalizer
                if (eq != null) {
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minEq = range[0].toInt()
                    val maxEq = range[1].toInt()
                    val preset = presets.find { it.index == presetIdx } ?: presets[0]

                    for (b in 0 until numBands) {
                        val percent = if (presetIdx == 5) {
                            getCustomBandPercent(context, b)
                        } else {
                            preset.bandOffsetsPercent.getOrElse(b % preset.bandOffsetsPercent.size) { 0 }
                        }
                        val mid = (minEq + maxEq) / 2
                        val halfSpan = (maxEq - minEq) / 2
                        val targetLevel = (mid + (percent / 100f) * halfSpan)
                            .toInt()
                            .coerceIn(minEq, maxEq)
                            .toShort()
                        eq.setBandLevel(b.toShort(), targetLevel)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error applying EQ bands: ${e.message}")
        }

        try {
            bassBoost?.enabled = enabled && bass > 0
            if (enabled && bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bass.coerceIn(0, 1000).toShort())
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error applying BassBoost: ${e.message}")
        }

        try {
            loudnessEnhancer?.enabled = enabled && loudness > 0
            if (enabled) {
                loudnessEnhancer?.setTargetGain(loudness.coerceIn(0, 1500))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error applying LoudnessEnhancer: ${e.message}")
        }
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        applySavedSettings(context)
    }

    fun getSelectedPresetIndex(context: Context): Int =
        prefs(context).getInt(KEY_PRESET_INDEX, 0)

    fun setSelectedPresetIndex(context: Context, index: Int) {
        val preset = presets.find { it.index == index } ?: presets[0]
        val editor = prefs(context).edit().putInt(KEY_PRESET_INDEX, index)
        if (index != 5) {
            preset.bandOffsetsPercent.forEachIndexed { b, pct ->
                editor.putInt("$KEY_BAND_PREFIX$b", pct)
            }
        }
        editor.apply()
        applySavedSettings(context)
    }

    fun getBassStrength(context: Context): Int =
        prefs(context).getInt(KEY_BASS_STRENGTH, 250)

    fun setBassStrength(context: Context, strength: Int) {
        prefs(context).edit().putInt(KEY_BASS_STRENGTH, strength.coerceIn(0, 1000)).apply()
        applySavedSettings(context)
    }

    fun getLoudnessGain(context: Context): Int =
        prefs(context).getInt(KEY_LOUDNESS_GAIN, 300)

    fun setLoudnessGain(context: Context, gainMb: Int) {
        prefs(context).edit().putInt(KEY_LOUDNESS_GAIN, gainMb.coerceIn(0, 1500)).apply()
        applySavedSettings(context)
    }

    fun getCustomBandPercent(context: Context, bandIndex: Int): Int =
        prefs(context).getInt("$KEY_BAND_PREFIX$bandIndex", 0).coerceIn(-100, 100)

    fun setCustomBandPercent(context: Context, bandIndex: Int, percent: Int) {
        prefs(context).edit()
            .putInt("$KEY_BAND_PREFIX$bandIndex", percent.coerceIn(-100, 100))
            .putInt(KEY_PRESET_INDEX, 5)
            .apply()
        applySavedSettings(context)
    }

    fun getActiveStatusLabel(context: Context): String {
        val extName = getSelectedExternalAppName(context)
        if (!extName.isNullOrBlank()) {
            return extName
        }
        if (isEnabled(context)) {
            val presetIdx = getSelectedPresetIndex(context)
            val preset = presets.find { it.index == presetIdx } ?: presets[0]
            return AppLang.tr(preset.nameEn, preset.nameBn)
        }
        return AppLang.tr("Equalizer", "ইকুলাইজার")
    }

    fun release() {
        appContextRef?.let { ctx ->
            if (currentAudioSessionId > 0) {
                broadcastAudioSessionClose(ctx, currentAudioSessionId)
            }
        }
        releaseInternal()
    }

    private fun releaseInternal() {
        try { equalizer?.release() } catch (_: Throwable) {}
        try { bassBoost?.release() } catch (_: Throwable) {}
        try { loudnessEnhancer?.release() } catch (_: Throwable) {}
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        currentAudioSessionId = 0
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
