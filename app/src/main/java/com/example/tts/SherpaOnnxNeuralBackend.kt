package com.example.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.lang.reflect.Constructor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Sherpa-ONNX (Coqui VITS / Piper ONNX) offline neural TTS backend.
 * Uses resilient reflection against `com.k2fsa.sherpa.onnx.OfflineTts` supporting both 0-arg constructors
 * and Kotlin synthetic `DefaultConstructorMarker` constructors across Sherpa-ONNX 1.10 -> 2.x AARs.
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

            val vitsCfg = instantiateWithFields(
                vitsCls,
                mapOf(
                    "model" to paths.modelOnnxPath,
                    "tokens" to paths.tokensTxtPath,
                    "dataDir" to paths.dataDirPath,
                    "lexicon" to paths.lexiconPath,
                    "noiseScale" to 0.667f,
                    "noiseScaleW" to 0.8f,
                    "lengthScale" to 1.0f
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

            // Prefer OfflineTts(assetManager = null, config = ttsCfg) to load directly from filesystem paths
            val twoArgCtor = ttsCls.constructors.firstOrNull { c ->
                c.parameterTypes.size == 2 && c.parameterTypes[1].name == cfgCls.name
            }
            val oneArgCtor = ttsCls.constructors.firstOrNull { c ->
                c.parameterTypes.size == 1 && c.parameterTypes[0].name == cfgCls.name
            }

            val engine = when {
                twoArgCtor != null -> twoArgCtor.newInstance(null, ttsCfg)
                oneArgCtor != null -> oneArgCtor.newInstance(ttsCfg)
                else -> return null
            }

            loadedEngines[paths.lang] = engine
            loadedPaths[paths.lang] = paths.modelOnnxPath
            Log.d(TAG, "Loaded Sherpa-ONNX neural model for [${paths.lang}]: ${paths.modelOnnxPath}")
            engine
        } catch (e: Throwable) {
            Log.w(TAG, "Sherpa-ONNX initialization failed: ${e.message}", e)
            null
        }
    }

    /**
     * Instantiates a Kotlin data class (even across version differences where new fields were added)
     * using either its 0-arg constructor or its synthetic `DefaultConstructorMarker` constructor,
     * then sets the requested properties via setters or backing fields.
     */
    private fun instantiateWithFields(cls: Class<*>, props: Map<String, Any>): Any? {
        val instance = tryCreateDefaultInstance(cls) ?: return null

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

    private fun tryCreateDefaultInstance(cls: Class<*>): Any? {
        // 1. Try standard 0-arg constructor
        runCatching {
            return cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }

        // 2. Try Kotlin synthetic default-parameter constructor ending with DefaultConstructorMarker
        val defaultMarkerCtor: Constructor<*>? = cls.declaredConstructors.firstOrNull { ctor ->
            val types = ctor.parameterTypes
            types.size >= 2 && types.last().name == "kotlin.jvm.internal.DefaultConstructorMarker"
        }
        if (defaultMarkerCtor != null) {
            runCatching {
                defaultMarkerCtor.isAccessible = true
                val types = defaultMarkerCtor.parameterTypes
                // Count trailing int bitmask parameters right before DefaultConstructorMarker
                val totalParams = types.size
                val args = arrayOfNulls<Any?>(totalParams)
                // Last arg is DefaultConstructorMarker -> null
                args[totalParams - 1] = null
                // Second-to-last arg is the bitmask int (all bits 1 = -1 means use Kotlin default for every param)
                // For classes with >32 params there can be multiple mask ints; -1 is safe for int masks.
                var maskStartIdx = totalParams - 2
                // Compute how many real params exist: each mask covers 32 params
                val realParamCountGuess = totalParams - 2
                val maskCount = ((realParamCountGuess + 31) / 32).coerceAtLeast(1)
                maskStartIdx = (totalParams - 1 - maskCount).coerceAtLeast(0)

                for (i in 0 until totalParams - 1) {
                    args[i] = if (i >= maskStartIdx && types[i] == Int::class.javaPrimitiveType) {
                        -1
                    } else {
                        defaultPrimitiveOrNull(types[i])
                    }
                }
                return defaultMarkerCtor.newInstance(*args)
            }
        }
        return null
    }

    private fun defaultPrimitiveOrNull(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        String::class.java -> ""
        else -> null
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
            val clampedSpeed = rate.coerceIn(0.6f, 2.0f)

            // Match generate(text: String, sid: Int, speed: Float) OR generate(text: String, speed: Float, sid: Int)
            val generateMethod = engine.javaClass.methods.firstOrNull { m ->
                m.name == "generate" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == String::class.java
            } ?: return false

            val p1 = generateMethod.parameterTypes[1]
            val generatedAudio = if (p1 == Float::class.javaPrimitiveType || p1 == java.lang.Float::class.java) {
                generateMethod.invoke(engine, text, clampedSpeed, 0)
            } else {
                generateMethod.invoke(engine, text, 0, clampedSpeed)
            } ?: return false

            val samples = runCatching {
                generatedAudio.javaClass.getMethod("getSamples").invoke(generatedAudio)
            }.getOrNull() as? FloatArray ?: return false

            val sampleRate = runCatching {
                generatedAudio.javaClass.getMethod("getSampleRate").invoke(generatedAudio)
            }.getOrNull() as? Int ?: 22050

            if (samples.isEmpty() || isStopped.get()) return true

            playPcmFloats(samples, sampleRate)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Sherpa-ONNX synthesis error: ${e.message}", e)
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
