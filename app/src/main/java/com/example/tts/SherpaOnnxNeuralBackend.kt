package com.example.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional native Sherpa-ONNX (Piper / VITS) offline neural TTS backend.
 * Uses clean reflection against `com.k2fsa.sherpa.onnx.OfflineTts` so the app compiles and runs
 * cleanly with zero APK bloat when `sherpa-onnx-*.aar` is not yet dropped into `app/libs/`, and
 * automatically activates native on-device ONNX neural voice synthesis the moment the AAR + R2 voice pack are present.
 */
class SherpaOnnxNeuralBackend {

    companion object {
        private const val TAG = "SherpaNeuralBackend"
    }

    private val loadedEngines = mutableMapOf<String, Any>()
    private val loadedPaths = mutableMapOf<String, String>()
    private val isStopped = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    fun isAvailable(): Boolean = TtsVoicePackManager.isSherpaRuntimeBundled()

    @Synchronized
    private fun getOrCreateEngine(paths: InstalledNeuralModelPaths): Any? {
        val existingPath = loadedPaths[paths.lang]
        if (existingPath == paths.modelOnnxPath && loadedEngines.containsKey(paths.lang)) {
            return loadedEngines[paths.lang]
        }

        // Release stale engine for this language if model was updated
        loadedEngines.remove(paths.lang)?.let { releaseEngineInstance(it) }

        return try {
            val vitsCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig")
            val modelCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsModelConfig")
            val cfgCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig")
            val ttsCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")

            // Instantiate via default/property setters (compatible across Sherpa-ONNX 1.10 -> 2.x)
            val vitsCfg = instantiateWithFields(
                vitsCls,
                mapOf(
                    "model" to paths.modelOnnxPath,
                    "tokens" to paths.tokensTxtPath,
                    "dataDir" to paths.dataDirPath,
                    "lexicon" to paths.lexiconPath
                )
            ) ?: return null

            val modelCfg = instantiateWithFields(
                modelCls,
                mapOf(
                    "vits" to vitsCfg,
                    "numThreads" to 2,
                    "debug" to false,
                    "provider" to "cpu"
                )
            ) ?: return null

            val ttsCfg = instantiateWithFields(
                cfgCls,
                mapOf(
                    "model" to modelCfg,
                    "maxNumSentences" to 1,
                    "silenceScale" to 0.2f
                )
            ) ?: return null

            // OfflineTts(assetManager = null, config = ttsCfg) loads directly from filesystem paths
            val ctor = ttsCls.constructors.firstOrNull { c ->
                c.parameterTypes.size == 2 &&
                    c.parameterTypes[1].name == cfgCls.name
            } ?: return null

            val engine = ctor.newInstance(null, ttsCfg)
            loadedEngines[paths.lang] = engine
            loadedPaths[paths.lang] = paths.modelOnnxPath
            Log.d(TAG, "Loaded Sherpa-ONNX neural model for [${paths.lang}]: ${paths.modelOnnxPath}")
            engine
        } catch (e: Throwable) {
            Log.w(TAG, "Sherpa-ONNX initialization skipped: ${e.message}")
            null
        }
    }

    private fun instantiateWithFields(cls: Class<*>, props: Map<String, Any>): Any? {
        val instance = try {
            cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        } catch (_: Throwable) {
            // Try Kotlin default constructor with DefaultConstructorMarker
            return null
        }

        for ((name, value) in props) {
            val setterName = "set" + name.replaceFirstChar { it.uppercaseChar() }
            val setter = cls.methods.firstOrNull { m ->
                m.name == setterName && m.parameterTypes.size == 1
            }
            if (setter != null) {
                runCatching { setter.invoke(instance, value) }
            } else {
                runCatching {
                    val field = cls.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(instance, value)
                }
            }
        }
        return instance
    }

    /**
     * Synchronously synthesizes and plays [text] on the calling IO thread via AudioTrack.
     * Returns `true` if synthesized & played via Sherpa-ONNX, or `false` if unavailable (caller should fallback).
     */
    fun speakBlocking(
        text: String,
        paths: InstalledNeuralModelPaths,
        rate: Float
    ): Boolean {
        if (!isAvailable()) return false
        val engine = getOrCreateEngine(paths) ?: return false
        isStopped.set(false)
        isPaused.set(false)

        return try {
            val generateMethod = engine.javaClass.methods.firstOrNull { m ->
                m.name == "generate" && m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == String::class.java
            } ?: return false

            val clampedSpeed = rate.coerceIn(0.6f, 2.0f)
            val generatedAudio = generateMethod.invoke(engine, text, 0, clampedSpeed) ?: return false

            val samplesField = runCatching {
                generatedAudio.javaClass.getMethod("getSamples").invoke(generatedAudio)
            }.getOrNull() as? FloatArray ?: return false

            val sampleRate = runCatching {
                generatedAudio.javaClass.getMethod("getSampleRate").invoke(generatedAudio)
            }.getOrNull() as? Int ?: 22050

            if (samplesField.isEmpty() || isStopped.get()) return true

            playPcmFloats(samplesField, sampleRate)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Sherpa-ONNX synthesis error: ${e.message}")
            false
        }
    }

    private fun playPcmFloats(samples: FloatArray, sampleRate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeAudioTrack = track
        try {
            track.play()
            val chunkSize = 2048
            var offset = 0
            while (offset < samples.size && !isStopped.get()) {
                while (isPaused.get() && !isStopped.get()) {
                    Thread.sleep(60L)
                }
                if (isStopped.get()) break
                val count = minOf(chunkSize, samples.size - offset)
                track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
                offset += count
            }
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
            if (activeAudioTrack === track) {
                activeAudioTrack = null
            }
        }
    }

    fun pause() {
        isPaused.set(true)
        runCatching { activeAudioTrack?.pause() }
    }

    fun resume() {
        isPaused.set(false)
        runCatching { activeAudioTrack?.play() }
    }

    fun stop() {
        isStopped.set(true)
        isPaused.set(false)
        runCatching {
            activeAudioTrack?.pause()
            activeAudioTrack?.flush()
            activeAudioTrack?.stop()
        }
    }

    private fun releaseEngineInstance(engine: Any) {
        runCatching {
            engine.javaClass.methods.firstOrNull { it.name == "release" && it.parameterTypes.isEmpty() }
                ?.invoke(engine)
        }
    }

    @Synchronized
    fun release() {
        stop()
        loadedEngines.values.forEach { releaseEngineInstance(it) }
        loadedEngines.clear()
        loadedPaths.clear()
    }
}
