package com.example.tts

import android.content.Context
import android.util.Log
import com.example.library.TtsModelPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class InstalledNeuralModelPaths(
    val lang: String,
    val modelOnnxPath: String,
    val tokensTxtPath: String,
    val dataDirPath: String,
    val lexiconPath: String = "",
    val totalSizeBytes: Long = 0L
)

data class VoicePackDownloadProgress(
    val isDownloading: Boolean = false,
    val progressFraction: Float = 0f,
    val statusTextBn: String = "",
    val errorTextBn: String? = null
)

/**
 * Manages on-demand Cloudflare R2 Neural TTS Voice Packs (`bn` and `en`) in private internal storage
 * so the main APK stays tiny while users can download offline Piper/VITS ONNX models once and listen offline forever.
 */
object TtsVoicePackManager {

    private const val TAG = "TtsVoicePackMgr"
    private const val MODELS_ROOT_DIR = "offline_neural_tts_models"

    private val _downloadStates = MutableStateFlow<Map<String, VoicePackDownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<String, VoicePackDownloadProgress>> = _downloadStates.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { com.example.security.AntiTamperSecurity.applyToOkHttpBuilder(this) }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun isSherpaRuntimeBundled(): Boolean {
        return try {
            Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getLangDir(context: Context, lang: String): File {
        val safeLang = lang.trim().lowercase().replace("[^a-z0-9_]".toRegex(), "")
        return File(File(context.applicationContext.filesDir, MODELS_ROOT_DIR), safeLang)
    }

    /**
     * Locates an installed ONNX voice model (`*.onnx` + `tokens.txt` + optional `espeak-ng-data`) for [lang].
     */
    fun getInstalledModelPaths(context: Context, lang: String): InstalledNeuralModelPaths? {
        val dir = getLangDir(context, lang)
        if (!dir.exists() || !dir.isDirectory) return null

        var onnxFile: File? = null
        var tokensFile: File? = null
        var lexiconFile: File? = null
        var espeakDir: File? = null
        var totalSize = 0L

        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                totalSize += f.length()
                val lower = f.name.lowercase()
                if (lower.endsWith(".onnx") && !lower.contains("quantized_tmp") && onnxFile == null) {
                    onnxFile = f
                } else if (lower == "tokens.txt" && tokensFile == null) {
                    tokensFile = f
                } else if (lower == "lexicon.txt" && lexiconFile == null) {
                    lexiconFile = f
                }
            } else if (f.isDirectory && f.name.equals("espeak-ng-data", ignoreCase = true) && espeakDir == null) {
                espeakDir = f
            }
        }

        val resolvedOnnx = onnxFile ?: return null
        val resolvedTokens = tokensFile ?: return null
        if (resolvedOnnx.length() <= 0L || resolvedTokens.length() <= 0L) return null

        return InstalledNeuralModelPaths(
            lang = lang.trim().lowercase(),
            modelOnnxPath = resolvedOnnx.absolutePath,
            tokensTxtPath = resolvedTokens.absolutePath,
            dataDirPath = espeakDir?.absolutePath.orEmpty(),
            lexiconPath = lexiconFile?.absolutePath.orEmpty(),
            totalSizeBytes = totalSize
        )
    }

    fun isModelInstalled(context: Context, lang: String): Boolean {
        return getInstalledModelPaths(context, lang) != null
    }

    fun deleteVoicePack(context: Context, lang: String): Boolean {
        val dir = getLangDir(context, lang)
        return try {
            val ok = !dir.exists() || dir.deleteRecursively()
            _downloadStates.update { it - lang.lowercase() }
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete voice pack $lang: ${e.message}")
            false
        }
    }

    /**
     * Downloads and extracts a Cloudflare R2 `.zip` neural TTS voice pack for [packInfo].
     * Protects against zip-slip path traversal and validates `.onnx` + `tokens.txt`.
     */
    suspend fun downloadAndInstallVoicePack(
        context: Context,
        packInfo: TtsModelPackInfo
    ): Result<InstalledNeuralModelPaths> = withContext(Dispatchers.IO) {
        val lang = packInfo.lang.trim().lowercase()
        val url = packInfo.url.trim()
        if (url.isBlank()) {
            return@withContext Result.failure(Exception("ভয়েস প্যাক ডাউনলোড লিংক পাওয়া যায়নি।"))
        }

        _downloadStates.update {
            it + (lang to VoicePackDownloadProgress(
                isDownloading = true,
                progressFraction = 0.02f,
                statusTextBn = "ভয়েস প্যাক ডাউনলোড শুরু হচ্ছে..."
            ))
        }

        val tempZip = File(context.applicationContext.cacheDir, "tts_pack_${lang}.zip.tmp")
        val targetDir = getLangDir(context, lang)
        val stagingDir = File(context.applicationContext.cacheDir, "tts_stage_${lang}")

        try {
            if (tempZip.exists()) tempZip.delete()
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            val req = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw Exception("ডাউনলোড ব্যর্থ হয়েছে (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw Exception("খালি রেসপন্স পাওয়া গেছে")
                val totalLen = body.contentLength().coerceAtLeast(1L)
                var downloaded = 0L

                FileOutputStream(tempZip).buffered(65536).use { out ->
                    val buf = ByteArray(65536)
                    val input = body.byteStream()
                    var read: Int
                    var lastEmit = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 180L) {
                            lastEmit = now
                            val frac = (downloaded.toFloat() / totalLen.toFloat()).coerceIn(0.02f, 0.85f)
                            val mbDone = downloaded / (1024.0 * 1024.0)
                            val mbTotal = totalLen / (1024.0 * 1024.0)
                            _downloadStates.update {
                                it + (lang to VoicePackDownloadProgress(
                                    isDownloading = true,
                                    progressFraction = frac,
                                    statusTextBn = String.format("ডাউনলোড হচ্ছে %.1f / %.1f MB", mbDone, mbTotal)
                                ))
                            }
                        }
                    }
                    out.flush()
                }
            }

            _downloadStates.update {
                it + (lang to VoicePackDownloadProgress(
                    isDownloading = true,
                    progressFraction = 0.9f,
                    statusTextBn = "ভয়েস মডেল আনপ্যাক করা হচ্ছে..."
                ))
            }

            // Safely unzip with Zip-Slip canonical path guard
            val canonicalStage = stagingDir.canonicalPath
            ZipInputStream(BufferedInputStream(tempZip.inputStream())).use { zis ->
                val buffer = ByteArray(32768)
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(stagingDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(canonicalStage + File.separator)) {
                        throw SecurityException("অবৈধ ফাইল পাথ শনাক্ত হয়েছে")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).buffered().use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.parentFile?.mkdirs()
            if (!stagingDir.renameTo(targetDir)) {
                stagingDir.copyRecursively(targetDir, overwrite = true)
                stagingDir.deleteRecursively()
            }
            tempZip.delete()

            val installed = getInstalledModelPaths(context, lang)
                ?: throw Exception("ভয়েস প্যাকে .onnx বা tokens.txt ফাইল পাওয়া যায়নি।")

            _downloadStates.update {
                it + (lang to VoicePackDownloadProgress(
                    isDownloading = false,
                    progressFraction = 1f,
                    statusTextBn = "অফলাইন ভয়েস প্যাক প্রস্তুত!"
                ))
            }

            Result.success(installed)
        } catch (e: Throwable) {
            tempZip.delete()
            stagingDir.deleteRecursively()
            val msg = e.localizedMessage ?: "ভয়েস প্যাক ডাউনলোড ব্যর্থ হয়েছে।"
            Log.e(TAG, "Voice pack download error ($lang): $msg", e)
            _downloadStates.update {
                it + (lang to VoicePackDownloadProgress(
                    isDownloading = false,
                    progressFraction = 0f,
                    statusTextBn = "",
                    errorTextBn = msg
                ))
            }
            Result.failure(Exception(msg))
        }
    }
}
