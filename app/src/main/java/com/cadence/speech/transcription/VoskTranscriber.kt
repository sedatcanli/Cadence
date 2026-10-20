package com.cadence.speech.transcription

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream

enum class TranscriptionStatus { IDLE, DOWNLOADING, LOADING, READY, ERROR }

data class TranscriptionState(
    val status: TranscriptionStatus = TranscriptionStatus.IDLE,
    val progress: Int = 0,
    val text: String = "",
    val error: String? = null
)

class VoskTranscriber(private val context: Context) {

    private val _state = MutableStateFlow(TranscriptionState())
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val committed = StringBuilder()
    private var lastEmitAt = 0L

    private val modelId: String = when (Locale.getDefault().language) {
        "tr" -> "vosk-model-small-tr-0.3"
        "de" -> "vosk-model-small-de-0.15"
        "fr" -> "vosk-model-small-fr-0.22"
        "es" -> "vosk-model-small-es-0.42"
        "ru" -> "vosk-model-small-ru-0.22"
        else -> "vosk-model-small-en-us-0.15"
    }

    suspend fun prepare() {
        if (recognizer != null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = modelDir()
                if (!File(dir, "conf").isDirectory) {
                    _state.update {
                        it.copy(status = TranscriptionStatus.DOWNLOADING, progress = 0, error = null)
                    }
                    downloadAndUnzip(dir)
                }
                _state.update { it.copy(status = TranscriptionStatus.LOADING) }
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val loaded = Model(dir.absolutePath)
                model = loaded
                recognizer = Recognizer(loaded, SAMPLE_RATE)
                _state.update { it.copy(status = TranscriptionStatus.READY, progress = 100) }
            }.onFailure { error ->
                Log.e(TAG, "Vosk init failed", error)
                _state.update {
                    it.copy(status = TranscriptionStatus.ERROR, error = error.message)
                }
            }
        }
    }

    @Synchronized
    fun accept(samples: ShortArray) {
        val rec = recognizer ?: return
        runCatching {
            if (rec.acceptWaveForm(samples, samples.size)) {
                val text = JSONObject(rec.result).optString("text").trim()
                if (text.isNotEmpty()) {
                    if (committed.isNotEmpty()) committed.append(' ')
                    committed.append(text)
                    _state.update { it.copy(text = trim(committed.toString())) }
                }
            } else {
                val partial = JSONObject(rec.partialResult).optString("partial").trim()
                if (partial.isNotEmpty()) {
                    val full = if (committed.isEmpty()) partial else "$committed $partial"
                    emitThrottled(trim(full))
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "acceptWaveForm failed", error)
        }
    }

    private fun emitThrottled(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastEmitAt < EMIT_INTERVAL_MS) return
        lastEmitAt = now
        _state.update { it.copy(text = text) }
    }

    private fun trim(text: String): String =
        if (text.length <= MAX_CHARS) text else text.takeLast(MAX_CHARS).trimStart()

    @Synchronized
    fun close() {
        runCatching { recognizer?.close() }
        runCatching { model?.close() }
        recognizer = null
        model = null
        committed.clear()
    }

    private fun modelDir(): File = File(context.filesDir, modelId)

    private fun downloadAndUnzip(target: File) {
        val archive = File(context.cacheDir, "$modelId.zip")
        if (!archive.exists()) {
            download(archive)
        }

        val temp = File(context.cacheDir, "$modelId-tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(temp, entry.name)
                if (!outFile.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                    error("Gecersiz model arsivi")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val root = temp.listFiles()?.firstOrNull { it.isDirectory }
            ?: error("Model arsivi bos")
        target.deleteRecursively()
        if (!root.renameTo(target)) {
            root.copyRecursively(target, overwrite = true)
        }
        temp.deleteRecursively()
        archive.delete()
    }

    private fun download(archive: File) {
        val partial = File(context.cacheDir, "$modelId.zip.part")
        partial.delete()
        val connection = URL("$MODEL_BASE_URL$modelId.zip").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Model indirilemedi (HTTP $code)")
            val total = connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val progress = (copied * 100 / total).toInt().coerceIn(0, 100)
                            _state.update { it.copy(progress = progress) }
                        }
                    }
                }
            }
            if (!partial.renameTo(archive)) {
                partial.copyTo(archive, overwrite = true)
                partial.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "VoskTranscriber"
        const val SAMPLE_RATE = 16000f
        const val MAX_CHARS = 400
        const val EMIT_INTERVAL_MS = 120L
        const val MODEL_BASE_URL = "https://alphacephei.com/vosk/models/"
    }
}
