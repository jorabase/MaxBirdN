package com.example.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class TtsPlaybackState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
    val speechRate: Float = 1.0f,
    val detectedLanguage: String = "bn", // "bn" or "en"
    val engineLabelBn: String = "Google অফলাইন TTS প্রস্তুত হচ্ছে...",
    val hasOfflineBanglaVoice: Boolean = false,
    val hasOfflineEnglishVoice: Boolean = false,
    val selectedBanglaVoiceName: String = "",
    val selectedEnglishVoiceName: String = ""
)

data class BilingualTextSegment(
    val lang: String, // "bn" or "en"
    val text: String
)

/**
 * Offline Bangla + English TTS Controller for HTML Books using Google Speech Services / Android TTS.
 *
 * Features:
 * 1. Prioritizes Google Speech Services (`com.google.android.tts`) highest-quality local offline
 *    `bn-BD` and `en-US` voices with zero extra APK size and instant playback.
 * 2. Smart Bilingual Script Splitting: Automatically detects Bangla (`\u0980-\u09FF`) and English runs
 *    within the same sentence/paragraph and switches voices cleanly.
 * 3. Generation-safe completion callbacks: Guarantees `window.onAndroidTtsDone()` fires cleanly when
 *    finished and NEVER fires stale callbacks after `stop()` or rapid skipping.
 */
class OfflineBookTtsManager(context: Context) {

    companion object {
        private const val TAG = "OfflineBookTtsMgr"
        private const val GOOGLE_TTS_ENGINE = "com.google.android.tts"

        fun isBanglaChar(ch: Char): Boolean = ch in '\u0980'..'\u09FF'

        fun detectDominantLanguage(text: String): String {
            var bnCount = 0
            var enCount = 0
            for (ch in text) {
                when {
                    isBanglaChar(ch) -> bnCount++
                    ch in 'A'..'Z' || ch in 'a'..'z' -> enCount++
                }
            }
            return if (bnCount == 0 && enCount > 0) "en" else "bn"
        }

        /**
         * Cleans HTML artifacts and splits mixed Bangla + English text into natural speech segments,
         * keeping each segment compact so synthesis starts immediately with zero latency.
         */
        fun splitByLanguageAndSentences(rawText: String, maxChunkChars: Int = 340): List<BilingualTextSegment> {
            val cleaned = rawText
                .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isBlank()) return emptyList()

            val primaryLang = detectDominantLanguage(cleaned)
            val rawRuns = mutableListOf<BilingualTextSegment>()
            val currentBuf = StringBuilder()
            var currentLang = primaryLang

            val words = cleaned.split(" ")
            for (word in words) {
                if (word.isBlank()) continue
                val hasBn = word.any { isBanglaChar(it) }
                val hasEn = word.any { it in 'A'..'Z' || it in 'a'..'z' }
                val wordLang = when {
                    hasBn -> "bn"
                    hasEn -> "en"
                    else -> currentLang
                }

                if (currentBuf.isNotEmpty() && wordLang != currentLang) {
                    rawRuns.add(BilingualTextSegment(currentLang, currentBuf.toString().trim()))
                    currentBuf.clear()
                }
                currentLang = wordLang
                if (currentBuf.isNotEmpty()) currentBuf.append(' ')
                currentBuf.append(word)
            }
            if (currentBuf.isNotEmpty()) {
                rawRuns.add(BilingualTextSegment(currentLang, currentBuf.toString().trim()))
            }

            // Sub-split overly long runs at sentence punctuation (`।`, `?`, `!`, `.`, `;`) for responsive pause/stop
            val finalSegments = mutableListOf<BilingualTextSegment>()
            for (run in rawRuns) {
                if (run.text.length <= maxChunkChars) {
                    finalSegments.add(run)
                } else {
                    val sentences = run.text.split(Regex("(?<=[।?!.;])\\s+"))
                    val acc = StringBuilder()
                    for (sent in sentences) {
                        val s = sent.trim()
                        if (s.isBlank()) continue
                        if (acc.isNotEmpty() && acc.length + s.length + 1 > maxChunkChars) {
                            finalSegments.add(BilingualTextSegment(run.lang, acc.toString().trim()))
                            acc.clear()
                        }
                        if (s.length > maxChunkChars) {
                            if (acc.isNotEmpty()) {
                                finalSegments.add(BilingualTextSegment(run.lang, acc.toString().trim()))
                                acc.clear()
                            }
                            s.chunked(maxChunkChars).forEach { part ->
                                finalSegments.add(BilingualTextSegment(run.lang, part.trim()))
                            }
                        } else {
                            if (acc.isNotEmpty()) acc.append(' ')
                            acc.append(s)
                        }
                    }
                    if (acc.isNotEmpty()) {
                        finalSegments.add(BilingualTextSegment(run.lang, acc.toString().trim()))
                    }
                }
            }

            return finalSegments.filter { it.text.isNotBlank() }
        }

        /**
         * Opens Android's built-in TTS Voice Data / Speech Settings so users without offline Bangla
         * voice pack on older phones can install Google's free offline Bangla voice in 1 tap.
         */
        fun openSystemTtsVoiceSettings(context: Context) {
            try {
                val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            } catch (_: Throwable) {
                try {
                    val settingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                } catch (_: Throwable) {}
            }
        }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    @Volatile
    private var androidTts: TextToSpeech? = null
    private val androidTtsReady = CompletableDeferred<Boolean>()
    private var bestBanglaVoice: Voice? = null
    private var bestEnglishVoice: Voice? = null

    private val speakGeneration = AtomicLong(0L)
    @Volatile
    private var currentUtteranceDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var activeSpeakJob: Job? = null

    // Pause / Resume snapshot (resumes from the exact paused segment)
    @Volatile
    private var pausedRemainingSegments: List<BilingualTextSegment> = emptyList()
    @Volatile
    private var pausedRate: Float = 1.0f
    @Volatile
    private var pausedOnDone: (() -> Unit)? = null

    init {
        cleanupLegacyOfflineVoicePacks()
        initializeAndroidTts()
    }

    /**
     * Silently removes any old downloaded neural TTS model files so devices reclaim storage automatically.
     */
    private fun cleanupLegacyOfflineVoicePacks() {
        scope.launch {
            runCatching {
                val legacyDir = File(appContext.filesDir, "offline_neural_tts_models")
                if (legacyDir.exists()) {
                    legacyDir.deleteRecursively()
                }
            }
        }
    }

    private fun initializeAndroidTts() {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureBestOfflineVoices()
                if (!androidTtsReady.isCompleted) androidTtsReady.complete(true)
            } else {
                // Retry with system default engine if explicit Google engine failed
                if (androidTts?.defaultEngine == GOOGLE_TTS_ENGINE) {
                    runCatching { androidTts?.shutdown() }
                    androidTts = TextToSpeech(appContext) { fallbackStatus ->
                        if (fallbackStatus == TextToSpeech.SUCCESS) {
                            configureBestOfflineVoices()
                        }
                        if (!androidTtsReady.isCompleted) {
                            androidTtsReady.complete(fallbackStatus == TextToSpeech.SUCCESS)
                        }
                    }
                } else {
                    if (!androidTtsReady.isCompleted) androidTtsReady.complete(false)
                }
            }
        }

        // Prefer Google Speech Services (highest quality offline Bangla & English voices on Android)
        androidTts = try {
            val hasGoogleTts = runCatching {
                appContext.packageManager.getPackageInfo(GOOGLE_TTS_ENGINE, 0)
                true
            }.getOrDefault(false)
            if (hasGoogleTts) {
                TextToSpeech(appContext, listener, GOOGLE_TTS_ENGINE)
            } else {
                TextToSpeech(appContext, listener)
            }
        } catch (_: Throwable) {
            TextToSpeech(appContext, listener)
        }
    }

    fun refreshVoiceAvailability() {
        configureBestOfflineVoices()
    }

    private fun configureBestOfflineVoices() {
        val tts = androidTts
        var bnVoice: Voice? = null
        var enVoice: Voice? = null

        if (tts != null) {
            val allVoices = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())

            fun scoreVoice(v: Voice, targetLang: String, preferredCountry: String): Int {
                val locale = v.locale ?: return -1000
                if (!locale.language.equals(targetLang, ignoreCase = true)) return -1000
                var score = 0
                // Strongly prefer offline local voices so reading works with zero internet
                if (!v.isNetworkConnectionRequired) score += 500 else score -= 200
                // Prefer exact country (BD for Bangla, US/GB for English)
                if (locale.country.equals(preferredCountry, ignoreCase = true)) score += 120
                else if (targetLang == "bn" && locale.country.equals("IN", ignoreCase = true)) score += 60
                // Quality score (VERY_HIGH = 500, HIGH = 400, NORMAL = 300)
                score += v.quality
                // Prefer Google local voice variants (e.g. bn-bd-x-ban-local / baf-local)
                val lowerName = v.name.lowercase()
                if (lowerName.contains("-local")) score += 80
                if (lowerName.contains("ban") || lowerName.contains("baf")) score += 45
                if (lowerName.contains("network")) score -= 180
                if (v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) {
                    score -= 600
                }
                return score
            }

            bnVoice = allVoices
                .filter { it.locale?.language.equals("bn", ignoreCase = true) }
                .maxByOrNull { scoreVoice(it, "bn", "BD") }

            enVoice = allVoices
                .filter { it.locale?.language.equals("en", ignoreCase = true) }
                .maxByOrNull { scoreVoice(it, "en", "US") }

            bestBanglaVoice = bnVoice
            bestEnglishVoice = enVoice

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    currentUtteranceDeferred?.complete(true)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    currentUtteranceDeferred?.complete(false)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    currentUtteranceDeferred?.complete(false)
                }
            })
        }

        val hasOfflineBn = bnVoice != null && !bnVoice.isNetworkConnectionRequired
        val hasOfflineEn = enVoice != null && !enVoice.isNetworkConnectionRequired
        val engineLabel = if (hasOfflineBn) {
            "Google অফলাইন TTS (বাংলা + ইংরেজি)"
        } else {
            "ডিভাইস অফলাইন TTS (বাংলা + ইংরেজি)"
        }

        _state.update {
            it.copy(
                isReady = true,
                engineLabelBn = engineLabel,
                hasOfflineBanglaVoice = hasOfflineBn,
                hasOfflineEnglishVoice = hasOfflineEn,
                selectedBanglaVoiceName = bnVoice?.name.orEmpty(),
                selectedEnglishVoiceName = enVoice?.name.orEmpty()
            )
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.6f, 2.0f)
        _state.update { it.copy(speechRate = clamped) }
    }

    /**
     * Speaks [text] completely offline with automatic Bangla/English voice switching.
     * Invokes [onDone] ONLY when speech finishes naturally (never when cancelled via [stop]).
     */
    fun speak(
        text: String,
        rate: Float = _state.value.speechRate,
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        val clampedRate = rate.coerceIn(0.6f, 2.0f)
        val segments = splitByLanguageAndSentences(cleanText)
        if (segments.isEmpty()) {
            onDone?.invoke()
            return
        }

        startSpeakingSegments(segments, clampedRate, onDone)
    }

    private fun startSpeakingSegments(
        segments: List<BilingualTextSegment>,
        rate: Float,
        onDone: (() -> Unit)?
    ) {
        val gen = speakGeneration.incrementAndGet()
        pausedRemainingSegments = emptyList()
        pausedOnDone = null
        stopInternalPlaybackOnly()

        val prevJob = activeSpeakJob
        activeSpeakJob = scope.launch {
            prevJob?.cancelAndJoin()
            if (speakGeneration.get() != gen) return@launch

            withTimeoutOrNull(2500L) { androidTtsReady.await() }
            if (speakGeneration.get() != gen) return@launch

            _state.update {
                it.copy(
                    isSpeaking = true,
                    isPaused = false,
                    speechRate = rate,
                    detectedLanguage = segments.firstOrNull()?.lang ?: "bn"
                )
            }

            for (idx in segments.indices) {
                if (!isActive || speakGeneration.get() != gen) return@launch
                val segment = segments[idx]

                pausedRemainingSegments = segments.subList(idx, segments.size)
                pausedRate = rate
                pausedOnDone = onDone

                _state.update { it.copy(detectedLanguage = segment.lang) }

                speakSegmentViaAndroidTts(segment, rate, gen)

                if (idx < segments.lastIndex && isActive && speakGeneration.get() == gen) {
                    delay(55L)
                }
            }

            if (isActive && speakGeneration.get() == gen) {
                pausedRemainingSegments = emptyList()
                pausedOnDone = null
                _state.update { it.copy(isSpeaking = false, isPaused = false) }
                onDone?.invoke()
            }
        }
    }

    private suspend fun speakSegmentViaAndroidTts(
        segment: BilingualTextSegment,
        rate: Float,
        generation: Long
    ) {
        val tts = androidTts ?: return
        if (speakGeneration.get() != generation) return

        val targetLocale = if (segment.lang == "bn") Locale("bn", "BD") else Locale.US
        val targetVoice = if (segment.lang == "bn") bestBanglaVoice else bestEnglishVoice

        try {
            if (targetVoice != null) {
                tts.voice = targetVoice
            } else {
                val res = tts.setLanguage(targetLocale)
                if (segment.lang == "bn" && (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED)) {
                    tts.setLanguage(Locale("bn", "IN"))
                }
            }
            tts.setSpeechRate(rate)
            tts.setPitch(0.98f)

            val doneSignal = CompletableDeferred<Boolean>()
            currentUtteranceDeferred = doneSignal

            val utteranceId = "utt_${generation}_${System.nanoTime()}"
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val code = tts.speak(segment.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (code == TextToSpeech.SUCCESS) {
                val timeoutMs = (segment.text.length * 180L).coerceIn(8_000L, 45_000L)
                withTimeoutOrNull(timeoutMs) { doneSignal.await() }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Android TTS segment error: ${e.message}")
        } finally {
            currentUtteranceDeferred = null
        }
    }

    fun pause() {
        if (!_state.value.isSpeaking || _state.value.isPaused) return
        speakGeneration.incrementAndGet()
        stopInternalPlaybackOnly()
        activeSpeakJob?.cancel()
        _state.update { it.copy(isSpeaking = false, isPaused = true) }
    }

    fun resume() {
        if (!_state.value.isPaused) return
        val remaining = pausedRemainingSegments
        if (remaining.isNotEmpty()) {
            startSpeakingSegments(remaining, _state.value.speechRate, pausedOnDone)
        } else {
            _state.update { it.copy(isPaused = false) }
        }
    }

    fun stop() {
        speakGeneration.incrementAndGet()
        pausedRemainingSegments = emptyList()
        pausedOnDone = null
        stopInternalPlaybackOnly()
        activeSpeakJob?.cancel()
        activeSpeakJob = null
        _state.update { it.copy(isSpeaking = false, isPaused = false) }
    }

    private fun stopInternalPlaybackOnly() {
        currentUtteranceDeferred?.complete(false)
        currentUtteranceDeferred = null
        runCatching { androidTts?.stop() }
    }

    fun isCurrentlySpeaking(): Boolean = _state.value.isSpeaking

    fun release() {
        stop()
        runCatching { androidTts?.shutdown() }
        androidTts = null
    }
}
