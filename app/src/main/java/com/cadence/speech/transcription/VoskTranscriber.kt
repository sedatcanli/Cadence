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
    val segments: List<TranscriptSegment> = emptyList(),
    val partial: String = "",
    val language: String = "en",
    val error: String? = null
)

class VoskTranscriber(
    private val context: Context,
    private val voiceModel: VoiceModel
) {

    private val _state = MutableStateFlow(TranscriptionState(language = voiceModel.code))
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val segments = mutableListOf<TranscriptSegment>()
    private var lastEndMs = 0L
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
                recognizer = Recognizer(loaded, SAMPLE_RATE).apply { setWords(true) }
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
                appendFinal(JSONObject(rec.result))
                _state.update { it.copy(segments = segments.toList(), partial = "") }
            } else {
                val partial = JSONObject(rec.partialResult).optString("partial").trim()
                if (partial.isNotEmpty()) {
                    emitThrottled(partial)
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "acceptWaveForm failed", error)
        }
    }

    @Synchronized
    fun flush() {
        val rec = recognizer ?: return
        runCatching {
            appendFinal(JSONObject(rec.finalResult))
            _state.update { it.copy(segments = segments.toList(), partial = "") }
        }.onFailure { error ->
            Log.w(TAG, "flush failed", error)
        }
    }

    fun plainText(): String = plainTranscript(segments)

    fun markedText(): String = markedTranscript(segments, voiceModel.code)

    private fun appendFinal(json: JSONObject) {
        val text = json.optString("text").trim()
        if (text.isEmpty()) return

        var startMs = lastEndMs
        var endMs = lastEndMs
        val words = json.optJSONArray("result")
        if (words != null && words.length() > 0) {
            val firstStart = words.optJSONObject(0)?.optDouble("start", -1.0) ?: -1.0
            val lastEnd = words.optJSONObject(words.length() - 1)?.optDouble("end", -1.0) ?: -1.0
            if (firstStart >= 0) startMs = (firstStart * 1000).toLong()
            if (lastEnd >= 0) endMs = (lastEnd * 1000).toLong()
        }
        if (endMs < startMs) endMs = startMs

        val gap = if (lastEndMs > 0 && startMs > lastEndMs) startMs - lastEndMs else 0L
        val pause = if (gap >= LONG_PAUSE_MS) gap else 0L

        segments += TranscriptSegment(text, startMs, endMs, pause)
        lastEndMs = maxOf(lastEndMs, endMs)
    }

    private fun emitThrottled(partial: String) {
        if (_state.value.partial == partial) return
        val now = System.currentTimeMillis()
        if (now - lastEmitAt < EMIT_INTERVAL_MS) return
        lastEmitAt = now
        _state.update { it.copy(partial = partial) }
    }

    @Synchronized
    fun close() {
        runCatching { recognizer?.close() }
        runCatching { model?.close() }
        recognizer = null
        model = null
        segments.clear()
        lastEndMs = 0L
    }

    private companion object {
        const val TAG = "VoskTranscriber"
        const val SAMPLE_RATE = 16000f
        const val EMIT_INTERVAL_MS = 120L
    }
}
