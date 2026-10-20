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

enum class TranscriptionStatus { IDLE, DOWNLOADING, LOADING, READY, ERROR }

data class TranscriptionState(
    val status: TranscriptionStatus = TranscriptionStatus.IDLE,
    val progress: Int = 0,
    val text: String = "",
    val error: String? = null
)

class VoskTranscriber(
    private val context: Context,
    private val voiceModel: VoiceModel
) {

    private val _state = MutableStateFlow(TranscriptionState())
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val committed = StringBuilder()
    private var lastEmitAt = 0L

    suspend fun prepare() {
        if (recognizer != null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = VoskModels.dir(context, voiceModel)
                if (!VoskModels.isReady(dir)) {
                    _state.update {
                        it.copy(status = TranscriptionStatus.DOWNLOADING, progress = 0, error = null)
                    }
                    VoskModels.ensure(context, voiceModel) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                }
                _state.update { it.copy(status = TranscriptionStatus.LOADING) }
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val loaded = Model(dir.absolutePath)
                model = loaded
                recognizer = Recognizer(loaded, SAMPLE_RATE)
                Log.i(TAG, "Vosk ready with model ${voiceModel.id}")
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

    private companion object {
        const val TAG = "VoskTranscriber"
        const val SAMPLE_RATE = 16000f
        const val MAX_CHARS = 400
        const val EMIT_INTERVAL_MS = 120L
    }
}
