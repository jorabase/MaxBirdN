package com.example.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerAppInfo(
    val packageName: String,
    val activityName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSystemDefault: Boolean = false
)

data class EqualizerPreset(
    val id: String,
    val nameBangla: String,
    val description: String,
    val bandGains: List<Int>, // dB values for 5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
    val bassBoost: Int = 0,   // 0 - 100%
    val virtualizer: Int = 0, // 0 - 100%
    val loudnessGainDb: Float = 0f // 0 - 12 dB
)

/**
 * Universal Audio Equalizer & Sound Control Manager.
 * Connects directly to ExoPlayer audio sessions and bridges to:
 * 1. Third-party and System Equalizer apps installed on the phone (Wavelet, Poweramp, MusicFX, Dolby Atmos, etc.)
 *    via AudioEffect broadcast intents (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION & ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).
 * 2. Android Native Hardware AudioFX (Equalizer, BassBoost, Virtualizer, LoudnessEnhancer) with 5-band controls and presets.
 */
@OptIn(UnstableApi::class)
object EqualizerManager {

    private const val TAG = "EqualizerManager"
    private const val PREFS_NAME = "shikho_equalizer_prefs"
    private const val KEY_CONNECTED = "eq_connected"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_BASS_BOOST = "eq_bass_boost"
    private const val KEY_VIRTUALIZER = "eq_virtualizer"
    private const val KEY_LOUDNESS = "eq_loudness"
    private const val KEY_LAST_EXT_APP = "eq_last_ext_app"

    val PRESETS = listOf(
        EqualizerPreset(
            id = "LECTURE_VOCAL",
            nameBangla = "লেকচার ও স্পষ্ট কণ্ঠ",
            description = "শিক্ষকের কণ্ঠ পরিষ্কার ও স্পষ্ট শোনার জন্য আদর্শ",
            bandGains = listOf(-2, 0, 5, 6, 3),
            bassBoost = 0,
            virtualizer = 10,
            loudnessGainDb = 3.0f
        ),
        EqualizerPreset(
            id = "FLAT",
            nameBangla = "স্বাভাবিক (Flat)",
            description = "অডিওর মূল কোয়ালিটি কোনো ফিল্টার ছাড়া",
            bandGains = listOf(0, 0, 0, 0, 0),
            bassBoost = 0,
            virtualizer = 0,
            loudnessGainDb = 0f
        ),
        EqualizerPreset(
            id = "BASS_BOOST",
            nameBangla = "বেস বুস্ট (Bass)",
            description = "লো-ফ্রিকোয়েন্সি ও ভারী সাউন্ডের জন্য",
            bandGains = listOf(7, 5, 1, 0, 0),
            bassBoost = 65,
            virtualizer = 20,
            loudnessGainDb = 1.5f
        ),
        EqualizerPreset(
            id = "TREBLE_BOOST",
            nameBangla = "ট্রেবল বুস্ট (Treble)",
            description = "হাই-ফ্রিকোয়েন্সি ও শার্প ক্রিস্প সাউন্ডের জন্য",
            bandGains = listOf(-1, 0, 2, 6, 8),
            bassBoost = 0,
            virtualizer = 15,
            loudnessGainDb = 2.0f
        ),
        EqualizerPreset(
            id = "LOUDNESS",
            nameBangla = "লাউডনেস বুস্ট (+Vol)",
            description = "কম সাউন্ডের রেকর্ডিংয়ে উচ্চ ভলিউম আউটপুট",
            bandGains = listOf(3, 3, 5, 5, 4),
            bassBoost = 25,
            virtualizer = 25,
            loudnessGainDb = 7.0f
        ),
        EqualizerPreset(
            id = "POP",
            nameBangla = "পপ (Pop)",
            description = "সুষম মেলোডি ও মাঝারি বেস",
            bandGains = listOf(-1, 2, 5, 3, -1),
            bassBoost = 20,
            virtualizer = 30,
            loudnessGainDb = 1.0f
        ),
        EqualizerPreset(
            id = "ROCK",
            nameBangla = "রক (Rock)",
            description = "হাই এনার্জি রিদম ও গতিশীল সাউন্ড",
            bandGains = listOf(5, 3, -1, 4, 6),
            bassBoost = 40,
            virtualizer = 35,
            loudnessGainDb = 2.0f
        ),
        EqualizerPreset(
            id = "CLASSICAL",
            nameBangla = "ক্লাসিক্যাল (Classical)",
            description = "উচ্চ ডাইনামিক রেঞ্জ ও স্বচ্ছতা",
            bandGains = listOf(4, 3, 0, 3, 4),
            bassBoost = 15,
            virtualizer = 25,
            loudnessGainDb = 1.0f
        ),
        EqualizerPreset(
            id = "CUSTOM",
            nameBangla = "কাস্টম (Custom)",
            description = "আপনার নিজস্ব পছন্দমতো ম্যানুয়াল সেটিংস",
            bandGains = listOf(0, 0, 0, 0, 0),
            bassBoost = 0,
            virtualizer = 0,
            loudnessGainDb = 0f
        )
    )

    // Observable UI States
    private val _isConnected = MutableStateFlow(true)
    val isConnected = _isConnected.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled = _isEnabled.asStateFlow()

    private val _currentSessionId = MutableStateFlow(0)
    val currentSessionId = _currentSessionId.asStateFlow()

    private val _currentPreset = MutableStateFlow("LECTURE_VOCAL")
    val currentPreset = _currentPreset.asStateFlow()

    private val _bandGains = MutableStateFlow(listOf(-2, 0, 5, 6, 3))
    val bandGains = _bandGains.asStateFlow()

    private val _bassBoost = MutableStateFlow(0)
    val bassBoost = _bassBoost.asStateFlow()

    private val _virtualizer = MutableStateFlow(10)
    val virtualizer = _virtualizer.asStateFlow()

    private val _loudnessGainDb = MutableStateFlow(3.0f)
    val loudnessGainDb = _loudnessGainDb.asStateFlow()

    private val _installedEqualizerApps = MutableStateFlow<List<EqualizerAppInfo>>(emptyList())
    val installedEqualizerApps = _installedEqualizerApps.asStateFlow()

    private val _selectedExternalApp = MutableStateFlow<EqualizerAppInfo?>(null)
    val selectedExternalApp = _selectedExternalApp.asStateFlow()

    private val _hardwareStatusMessage = MutableStateFlow("ইকুলাইজার প্রস্তুত")
    val hardwareStatusMessage = _hardwareStatusMessage.asStateFlow()

    // Native Audio Effects instances
    private var nativeEqualizer: Equalizer? = null
    private var nativeBassBoost: BassBoost? = null
    private var nativeVirtualizer: Virtualizer? = null
    private var nativeLoudnessEnhancer: LoudnessEnhancer? = null

    private var activePlayer: ExoPlayer? = null
    private var activeAnalyticsListener: AnalyticsListener? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val prefs = getPrefs(context)
        _isConnected.value = prefs.getBoolean(KEY_CONNECTED, true)
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, true)
        val savedPreset = prefs.getString(KEY_PRESET, "LECTURE_VOCAL") ?: "LECTURE_VOCAL"
        _currentPreset.value = savedPreset

        val gains = mutableListOf<Int>()
        for (i in 0 until 5) {
            val defaultGain = PRESETS.find { it.id == savedPreset }?.bandGains?.getOrNull(i) ?: 0
            gains.add(prefs.getInt("eq_band_$i", defaultGain))
        }
        _bandGains.value = gains
        _bassBoost.value = prefs.getInt(KEY_BASS_BOOST, 0)
        _virtualizer.value = prefs.getInt(KEY_VIRTUALIZER, 10)
        _loudnessGainDb.value = prefs.getFloat(KEY_LOUDNESS, 3.0f)

        scanInstalledEqualizerApps(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Scans user's device for all installed Audio Equalizer apps.
     */
    fun scanInstalledEqualizerApps(context: Context): List<EqualizerAppInfo> {
        val pm = context.packageManager
        val list = mutableListOf<EqualizerAppInfo>()

        try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

            for (info in resolveInfos) {
                val pkgName = info.activityInfo.packageName
                // Skip self if registered
                if (pkgName == context.packageName) continue

                val appName = try {
                    info.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkgName
                }
                val icon = try {
                    info.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }

                list.add(
                    EqualizerAppInfo(
                        packageName = pkgName,
                        activityName = info.activityInfo.name,
                        appName = appName,
                        icon = icon,
                        isSystemDefault = (info.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning equalizer apps: ${e.message}")
        }

        // Also check common known equalizer apps if not caught by intent filter
        val knownEqualizers = listOf(
            "com.pittvandewitt.wavelet" to "Wavelet: headphone specific EQ",
            "com.maxmpz.equalizer" to "Poweramp Equalizer",
            "com.android.musicfx" to "MusicFX (System)",
            "com.samsung.android.soundalive" to "SoundAlive (Samsung)",
            "com.dolby.daxappui" to "Dolby Atmos",
            "com.devdnua.equalizer" to "Equalizer FX",
            "com.jazibkhan.equalizer" to "Flat Equalizer",
            "com.smartandroidapps.audiowidget" to "Equalizer Music Player",
            "com.equalizer.bassbooster.musicplayer" to "Bass Booster & Equalizer"
        )

        for ((pkg, defaultName) in knownEqualizers) {
            if (list.none { it.packageName == pkg }) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString().ifBlank { defaultName }
                    val icon = pm.getApplicationIcon(appInfo)
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    val actName = launchIntent?.component?.className ?: ""
                    list.add(
                        EqualizerAppInfo(
                            packageName = pkg,
                            activityName = actName,
                            appName = label,
                            icon = icon,
                            isSystemDefault = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    )
                } catch (_: Exception) {
                    // Not installed on device, normal
                }
            }
        }

        _installedEqualizerApps.value = list

        val savedLast = getPrefs(context).getString(KEY_LAST_EXT_APP, null)
        if (savedLast != null) {
            _selectedExternalApp.value = list.find { it.packageName == savedLast }
        }

        return list
    }

    /**
     * Binds ExoPlayer to EqualizerManager.
     * Hooks into player's audio session ID and analytics listener.
     */
    fun bindPlayer(context: Context, player: ExoPlayer) {
        init(context)
        activePlayer = player

        // Listen for session ID changes
        val listener = object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (audioSessionId > 0 && audioSessionId != _currentSessionId.value) {
                    Log.d(TAG, "ExoPlayer onAudioSessionIdChanged: $audioSessionId")
                    updateAudioSession(context, audioSessionId)
                }
            }
        }
        activeAnalyticsListener = listener
        player.addAnalyticsListener(listener)

        val initialSession = try {
            player.audioSessionId
        } catch (_: Exception) {
            0
        }

        if (initialSession > 0) {
            updateAudioSession(context, initialSession)
        }
    }

    /**
     * Unbinds the active player and releases effects.
     */
    fun unbindPlayer(context: Context) {
        val player = activePlayer
        val listener = activeAnalyticsListener
        if (player != null && listener != null) {
            try {
                player.removeAnalyticsListener(listener)
            } catch (_: Exception) {}
        }
        activePlayer = null
        activeAnalyticsListener = null

        val session = _currentSessionId.value
        if (session > 0) {
            sendCloseAudioSessionBroadcast(context, session)
        }
        releaseNativeEffects()
    }

    /**
     * Called when ExoPlayer establishes or switches audio session ID.
     */
    fun updateAudioSession(context: Context, sessionId: Int) {
        if (sessionId <= 0) return
        _currentSessionId.value = sessionId

        if (_isConnected.value) {
            sendOpenAudioSessionBroadcast(context, sessionId)
            setupNativeEffects(context, sessionId)
        }
    }

    /**
     * Sends the official Android broadcast that connects third-party equalizers
     * (Wavelet, Poweramp, MusicFX, Dolby Atmos, Xiaomi/Samsung sound enhancers) to our audio stream!
     */
    fun sendOpenAudioSessionBroadcast(context: Context, sessionId: Int = _currentSessionId.value) {
        if (sessionId <= 0) return
        try {
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Sent ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION for session $sessionId")
            _hardwareStatusMessage.value = "মোবাইল ইকুলাইজার সেশনে সংযুক্ত (#$sessionId)"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send open session broadcast: ${e.message}")
        }
    }

    /**
     * Sends broadcast to notify equalizers that audio session has closed.
     */
    fun sendCloseAudioSessionBroadcast(context: Context, sessionId: Int = _currentSessionId.value) {
        if (sessionId <= 0) return
        try {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Sent ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION for session $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send close session broadcast: ${e.message}")
        }
    }

    /**
     * Launches the selected external Equalizer app or system equalizer panel.
     */
    fun openEqualizerApp(context: Context, appInfo: EqualizerAppInfo? = null): Boolean {
        val targetApp = appInfo ?: _selectedExternalApp.value
        val sessionId = _currentSessionId.value

        // Always make sure open session broadcast is sent first
        if (sessionId > 0) {
            sendOpenAudioSessionBroadcast(context, sessionId)
        }

        if (targetApp != null) {
            _selectedExternalApp.value = targetApp
            getPrefs(context).edit().putString(KEY_LAST_EXT_APP, targetApp.packageName).apply()

            // Try with ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL targeting this package
            val panelIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                setPackage(targetApp.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(panelIntent)
                return true
            } catch (_: Exception) {
                // Try launching app launcher intent
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetApp.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return true
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Error launching app: ${e2.message}")
                }
            }
        }

        // Fallback: Open system chooser or control panel
        return openSystemEqualizerChooser(context)
    }

    /**
     * Opens Android System Equalizer panel or lets user pick from installed equalizer apps.
     */
    fun openSystemEqualizerChooser(context: Context): Boolean {
        val sessionId = _currentSessionId.value
        if (sessionId > 0) {
            sendOpenAudioSessionBroadcast(context, sessionId)
        }

        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            val chooser = Intent.createChooser(intent, "ইকুলাইজার নির্বাচন করুন").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            // If no activity responds, open Sound Settings
            try {
                val soundSettings = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(soundSettings)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open sound settings: ${e.message}")
                false
            }
        }
    }

    /**
     * Toggles connection state.
     */
    fun setConnected(context: Context, connected: Boolean) {
        _isConnected.value = connected
        getPrefs(context).edit().putBoolean(KEY_CONNECTED, connected).apply()

        val session = _currentSessionId.value
        if (connected) {
            if (session > 0) {
                sendOpenAudioSessionBroadcast(context, session)
                setupNativeEffects(context, session)
            }
            _hardwareStatusMessage.value = "ইকুলাইজার সক্রিয় ও সংযুক্ত"
        } else {
            if (session > 0) {
                sendCloseAudioSessionBroadcast(context, session)
            }
            releaseNativeEffects()
            _hardwareStatusMessage.value = "ইকুলাইজার সংযোগ বিচ্ছিন্ন করা হয়েছে"
        }
    }

    /**
     * Toggles Equalizer Power (enabled/disabled).
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        _isEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

        try {
            nativeEqualizer?.enabled = enabled
            nativeBassBoost?.enabled = enabled
            nativeVirtualizer?.enabled = enabled
            nativeLoudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error setting effects enabled: ${e.message}")
        }
    }

    /**
     * Sets Preset (e.g. LECTURE_VOCAL, BASS_BOOST, FLAT, etc.)
     */
    fun applyPreset(context: Context, presetId: String) {
        _currentPreset.value = presetId
        getPrefs(context).edit().putString(KEY_PRESET, presetId).apply()

        val preset = PRESETS.find { it.id == presetId } ?: return
        if (presetId != "CUSTOM") {
            _bandGains.value = preset.bandGains
            _bassBoost.value = preset.bassBoost
            _virtualizer.value = preset.virtualizer
            _loudnessGainDb.value = preset.loudnessGainDb

            val editor = getPrefs(context).edit()
            for (i in preset.bandGains.indices) {
                editor.putInt("eq_band_$i", preset.bandGains[i])
            }
            editor.putInt(KEY_BASS_BOOST, preset.bassBoost)
            editor.putInt(KEY_VIRTUALIZER, preset.virtualizer)
            editor.putFloat(KEY_LOUDNESS, preset.loudnessGainDb)
            editor.apply()

            applyAllEffectsToHardware()
        }
    }

    /**
     * Sets individual band gain (-15 dB to +15 dB).
     */
    fun setBandGain(context: Context, bandIndex: Int, gainDb: Int) {
        val current = _bandGains.value.toMutableList()
        if (bandIndex in current.indices) {
            current[bandIndex] = gainDb.coerceIn(-15, 15)
            _bandGains.value = current
            _currentPreset.value = "CUSTOM"

            getPrefs(context).edit()
                .putString(KEY_PRESET, "CUSTOM")
                .putInt("eq_band_$bandIndex", current[bandIndex])
                .apply()

            applyBandToHardware(bandIndex, current[bandIndex])
        }
    }

    /**
     * Sets Bass Boost strength (0 to 100%).
     */
    fun setBassBoost(context: Context, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _bassBoost.value = clamped
        _currentPreset.value = "CUSTOM"
        getPrefs(context).edit()
            .putString(KEY_PRESET, "CUSTOM")
            .putInt(KEY_BASS_BOOST, clamped)
            .apply()

        try {
            nativeBassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    val strengthShort = (clamped * 10).toShort() // 0 - 1000
                    bb.setStrength(strengthShort)
                    bb.enabled = clamped > 0 && _isEnabled.value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting BassBoost: ${e.message}")
        }
    }

    /**
     * Sets Virtualizer 3D spatial effect (0 to 100%).
     */
    fun setVirtualizer(context: Context, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _virtualizer.value = clamped
        _currentPreset.value = "CUSTOM"
        getPrefs(context).edit()
            .putString(KEY_PRESET, "CUSTOM")
            .putInt(KEY_VIRTUALIZER, clamped)
            .apply()

        try {
            nativeVirtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    val strengthShort = (clamped * 10).toShort() // 0 - 1000
                    virt.setStrength(strengthShort)
                    virt.enabled = clamped > 0 && _isEnabled.value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Virtualizer: ${e.message}")
        }
    }

    /**
     * Sets Loudness Enhancer gain (0f to 12f dB).
     */
    fun setLoudnessGain(context: Context, gainDb: Float) {
        val clamped = gainDb.coerceIn(0f, 12f)
        _loudnessGainDb.value = clamped
        _currentPreset.value = "CUSTOM"
        getPrefs(context).edit()
            .putString(KEY_PRESET, "CUSTOM")
            .putFloat(KEY_LOUDNESS, clamped)
            .apply()

        try {
            nativeLoudnessEnhancer?.let { le ->
                val gainmB = (clamped * 100).toInt() // milliBels
                le.setTargetGain(gainmB)
                le.enabled = clamped > 0.1f && _isEnabled.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting LoudnessEnhancer: ${e.message}")
        }
    }

    /**
     * Resets everything to Flat defaults.
     */
    fun resetToDefault(context: Context) {
        applyPreset(context, "FLAT")
    }

    /**
     * Initializes native Android audio effects on the active audio session ID.
     */
    private fun setupNativeEffects(context: Context, sessionId: Int) {
        if (sessionId <= 0) return
        releaseNativeEffects()

        try {
            // 1. Equalizer
            nativeEqualizer = Equalizer(0, sessionId).apply {
                enabled = _isEnabled.value
            }

            // 2. BassBoost
            nativeBassBoost = BassBoost(0, sessionId).apply {
                enabled = _isEnabled.value && _bassBoost.value > 0
            }

            // 3. Virtualizer
            nativeVirtualizer = Virtualizer(0, sessionId).apply {
                enabled = _isEnabled.value && _virtualizer.value > 0
            }

            // 4. LoudnessEnhancer (API 19+)
            nativeLoudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = _isEnabled.value && _loudnessGainDb.value > 0.1f
            }

            applyAllEffectsToHardware()
            _hardwareStatusMessage.value = "অডিও ইঞ্জিন সম্পূর্ণ সংযুক্ত (Session #$sessionId)"
        } catch (e: Exception) {
            Log.e(TAG, "Hardware audio effect init note: ${e.message}")
            _hardwareStatusMessage.value = "সিস্টেম ব্রডকাস্টের মাধ্যমে ইকুলাইজার সংযুক্ত"
        }
    }

    private fun applyAllEffectsToHardware() {
        val gains = _bandGains.value
        for (i in gains.indices) {
            applyBandToHardware(i, gains[i])
        }

        try {
            nativeBassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength((_bassBoost.value * 10).toShort())
                    bb.enabled = _bassBoost.value > 0 && _isEnabled.value
                }
            }
        } catch (_: Exception) {}

        try {
            nativeVirtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    virt.setStrength((_virtualizer.value * 10).toShort())
                    virt.enabled = _virtualizer.value > 0 && _isEnabled.value
                }
            }
        } catch (_: Exception) {}

        try {
            nativeLoudnessEnhancer?.let { le ->
                le.setTargetGain((_loudnessGainDb.value * 100).toInt())
                le.enabled = _loudnessGainDb.value > 0.1f && _isEnabled.value
            }
        } catch (_: Exception) {}
    }

    private fun applyBandToHardware(bandIndex: Int, gainDb: Int) {
        val eq = nativeEqualizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            if (bandIndex < numBands) {
                val range = eq.bandLevelRange // ShortArray [min, max] in mB (e.g. -1500 to +1500)
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                // Map dB (-15 to 15) to mB (-1500 to 1500)
                val targetmB = (gainDb * 100).coerceIn(minLevel, maxLevel)
                eq.setBandLevel(bandIndex.toShort(), targetmB.toShort())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying band level: ${e.message}")
        }
    }

    private fun releaseNativeEffects() {
        try {
            nativeEqualizer?.release()
        } catch (_: Exception) {}
        nativeEqualizer = null

        try {
            nativeBassBoost?.release()
        } catch (_: Exception) {}
        nativeBassBoost = null

        try {
            nativeVirtualizer?.release()
        } catch (_: Exception) {}
        nativeVirtualizer = null

        try {
            nativeLoudnessEnhancer?.release()
        } catch (_: Exception) {}
        nativeLoudnessEnhancer = null
    }
}
