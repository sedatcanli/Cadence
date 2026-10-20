package com.cadence.speech.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.cadence.speech.data.SessionRepository
import com.cadence.speech.domain.SessionMode
import com.cadence.speech.domain.SessionReport
import com.cadence.speech.feedback.FeedbackDispatcher
import com.cadence.speech.service.SessionService
import com.cadence.speech.settings.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class SessionUiState(
    val isRunning: Boolean = false,
    val startedAt: Long = 0L,
    val metrics: LiveMetrics = LiveMetrics(),
    val lastReportId: String? = null,
    val error: String? = null,
    val transcript: String = ""
)

class SessionController(
    private val context: Context,
    private val repository: SessionRepository,
    private val preferences: UserPreferences,
    private val dispatcher: FeedbackDispatcher
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableListOf<Job>()

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var engine: MetricsEngine? = null
    private var capture: AudioCapture? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var mode: SessionMode = SessionMode.FREE
    private var startedAtWallClock = 0L
    private var startedAtElapsed = 0L

    fun start(mode: SessionMode) {
        if (_state.value.isRunning) return
        this.mode = mode
        startedAtWallClock = System.currentTimeMillis()
        startedAtElapsed = SystemClock.elapsedRealtime()
        preferences.onboardingDone = true

        val cooldown = (4_000f / preferences.sensitivity).toLong().coerceIn(1_500L, 8_000L)
        val engine = MetricsEngine(
            cooldownMs = cooldown,
            onEvent = { event -> dispatcher.dispatch(event) }
        )
        this.engine = engine

        _state.value = SessionUiState(isRunning = true, startedAt = startedAtWallClock, transcript = "")

        SessionService.start(context)

        val capture = AudioCapture()
        this.capture = capture

        // Start SpeechRecognizer for on-device transcription
        startSpeechRecognizer()

        jobs += scope.launch {
            runCatching {
                capture.frames().collect { frame ->
                    engine.process(frame.samples, frame.timestampMs)
                }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message) }
                stop()
            }
        }

        jobs += scope.launch {
            engine.metrics.collect { metrics ->
                _state.update { it.copy(metrics = metrics) }
            }
        }
    }

    private fun startSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // Ignore common errors (no speech, timeout, etc.)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { finalText ->
                    _state.update { it.copy(transcript = finalText) }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { partialText ->
                    _state.update { it.copy(transcript = partialText) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            // Ignore if already listening
        }
    }

    fun stop() {
        if (!_state.value.isRunning) return

        val currentEngine = engine
        capture?.stop()
        jobs.forEach { it.cancel() }
        jobs.clear()
        SessionService.stop(context)

        // Stop SpeechRecognizer
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        val metrics = currentEngine?.snapshot() ?: LiveMetrics()
        val duration = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(1_000L)

        val report = SessionReport(
            id = UUID.randomUUID().toString(),
            startedAt = startedAtWallClock,
            durationMs = duration,
            mode = mode,
            score = metrics.averageScore,
            pace = metrics.pacePerMinute,
            fillerCount = metrics.fillerCount,
            pauseCount = metrics.pauseCount,
            longPauseCount = metrics.longPauseCount,
            pitchStdHz = metrics.pitchStdHz,
            speechRatio = metrics.speechRatio
        )
        repository.save(report)

        engine = null
        capture = null
        _state.value = SessionUiState(isRunning = false, lastReportId = report.id)
    }

    fun consumeLastReport(): String? {
        val id = _state.value.lastReportId ?: return null
        _state.update { it.copy(lastReportId = null) }
        return id
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun dispose() {
        capture?.stop()
        jobs.forEach { it.cancel() }
        scope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}