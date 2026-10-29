package com.example.ai

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale

data class UploadedSlideDocument(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

/**
 * Modular Voice & File Utility Controller for Gemini 3.8 Flash (`analyzeSlidePDF`)
 * and Gemini 3.8 Live (`startVoiceSession`).
 */
class GeminiVoiceSessionController(
    private val context: Context,
    private val onSpeakingStateChanged: (String?) -> Unit
) {
    companion object {
        private const val TAG = "GeminiVoiceController"

        /**
         * Reads an uploaded PDF or Slide Image from an Android content [Uri]
         * and returns its raw bytes, MIME type, and display name for `analyzeSlidePDF()`.
         */
        fun readUploadedSlideUri(context: Context, uri: Uri): Result<UploadedSlideDocument> {
            return try {
                val resolver = context.contentResolver
                val detectedMime = resolver.getType(uri)?.lowercase().orEmpty()
                var displayName = "uploaded_slide.pdf"

                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                }

                val mimeType = when {
                    detectedMime.contains("pdf") || displayName.endsWith(".pdf", true) -> "application/pdf"
                    detectedMime.contains("png") || displayName.endsWith(".png", true) -> "image/png"
                    detectedMime.contains("webp") || displayName.endsWith(".webp", true) -> "image/webp"
                    detectedMime.startsWith("image/") -> "image/jpeg"
                    else -> "application/pdf"
                }

                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return Result.failure(Exception("ফাইলটি পড়া যায়নি। অনুগ্রহ করে অন্য ফাইল নির্বাচন করুন।"))

                if (bytes.isEmpty()) {
                    return Result.failure(Exception("নির্বাচিত ফাইলটি খালি।"))
                }

                Result.success(
                    UploadedSlideDocument(
                        fileName = displayName,
                        mimeType = mimeType,
                        bytes = bytes
                    )
                )
            } catch (e: Exception) {
                Result.failure(Exception("ফাইল লোড করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"))
            }
        }
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var isRecordingInternal: Boolean = false

    private var tts: TextToSpeech? = null
    private var isTtsReady: Boolean = false
    private var mediaPlayer: MediaPlayer? = null
    private var activeSpeakingMessageId: String? = null

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val bdLocale = Locale("bn", "BD")
                    val inLocale = Locale("bn", "IN")
                    val res = tts?.setLanguage(bdLocale)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(inLocale)
                    }
                    tts?.setSpeechRate(0.96f)
                    tts?.setPitch(1.0f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            activeSpeakingMessageId = utteranceId
                            onSpeakingStateChanged(utteranceId)
                        }

                        override fun onDone(utteranceId: String?) {
                            if (activeSpeakingMessageId == utteranceId) {
                                activeSpeakingMessageId = null
                                onSpeakingStateChanged(null)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (activeSpeakingMessageId == utteranceId) {
                                activeSpeakingMessageId = null
                                onSpeakingStateChanged(null)
                            }
                        }
                    })
                    isTtsReady = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS init warning: ${e.message}")
        }
    }

    /**
     * Starts recording user voice query from microphone into an `.m4a` (`audio/mp4`) file.
     */
    fun startAudioRecording(): Result<File> {
        stopSpeaking()
        stopAudioRecording()

        return try {
            val outFile = File(context.cacheDir, "gemini_voice_query_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentAudioFile = outFile
            isRecordingInternal = true
            Result.success(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording: ${e.message}", e)
            isRecordingInternal = false
            Result.failure(Exception("মাইক্রোফোন চালু করা যায়নি: ${e.localizedMessage ?: "পারমিশন চেক করুন"}"))
        }
    }

    /**
     * Stops recording and returns the recorded audio bytes for `startVoiceSession()`.
     */
    fun stopAudioRecording(): ByteArray? {
        if (!isRecordingInternal && mediaRecorder == null) return null
        isRecordingInternal = false

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }

        val file = currentAudioFile
        currentAudioFile = null
        return if (file != null && file.exists() && file.length() > 100) {
            try {
                val bytes = file.readBytes()
                file.delete()
                bytes
            } catch (_: Exception) {
                null
            }
        } else {
            file?.delete()
            null
        }
    }

    fun cancelAudioRecording() {
        isRecordingInternal = false
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }
        currentAudioFile?.delete()
        currentAudioFile = null
    }

    /**
     * Plays back the AI Tutor's explanation using either raw audio bytes from Live API
     * or clean Bengali Text-To-Speech synthesis.
     */
    fun toggleSpeakMessage(messageId: String, rawText: String, audioBytes: ByteArray? = null) {
        if (activeSpeakingMessageId == messageId) {
            stopSpeaking()
            return
        }

        stopSpeaking()

        // 1. If raw audio stream bytes are present, try playing them via MediaPlayer
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            try {
                val tempAudio = File(context.cacheDir, "gemini_live_reply_$messageId.wav")
                tempAudio.writeBytes(audioBytes)
                val mp = MediaPlayer().apply {
                    setDataSource(tempAudio.absolutePath)
                    setOnCompletionListener {
                        activeSpeakingMessageId = null
                        onSpeakingStateChanged(null)
                        tempAudio.delete()
                    }
                    setOnErrorListener { _, _, _ ->
                        activeSpeakingMessageId = null
                        onSpeakingStateChanged(null)
                        tempAudio.delete()
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = mp
                activeSpeakingMessageId = messageId
                onSpeakingStateChanged(messageId)
                return
            } catch (_: Exception) {
                // Fall back to Bengali TTS below
            }
        }

        // 2. Clean markdown symbols for natural Bengali voice playback
        val cleanSpeechText = rawText
            .replace(Regex("[*#_`~>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3800)

        if (cleanSpeechText.isBlank()) return

        if (isTtsReady && tts != null) {
            activeSpeakingMessageId = messageId
            onSpeakingStateChanged(messageId)
            tts?.speak(cleanSpeechText, TextToSpeech.QUEUE_FLUSH, null, messageId)
        }
    }

    fun stopSpeaking() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }

        try {
            tts?.stop()
        } catch (_: Exception) {}

        if (activeSpeakingMessageId != null) {
            activeSpeakingMessageId = null
            onSpeakingStateChanged(null)
        }
    }

    fun release() {
        cancelAudioRecording()
        stopSpeaking()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
    }
}
