package com.cadence.speech.audio

import android.content.Context
import android.os.SystemClock
import com.cadence.speech.data.SessionRepository
import com.cadence.speech.domain.RecommendationEngine
import com.cadence.speech.domain.SessionMode
import com.cadence.speech.domain.SessionReport
import com.cadence.speech.domain.profile
import com.cadence.speech.feedback.FeedbackDispatcher
import com.cadence.speech.service.SessionService
import com.cadence.speech.settings.UserPreferences
import com.cadence.speech.transcription.TranscriptionState
import com.cadence.speech.transcription.VoskTranscriber
import com.cadence.speech.transcription.voiceModelFor
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
    val transcription: TranscriptionState = TranscriptionState()
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
    private var transcriber: VoskTranscriber? = null
    private var mode: SessionMode = SessionMode.FREE
    private var startedAtWallClock = 0L
    private var startedAtElapsed = 0L

    fun start(mode: SessionMode) {
        if (_state.value.isRunning) return
        this.mode = mode
        startedAtWallClock = System.currentTimeMillis()
        startedAtElapsed = SystemClock.elapsedRealtime()
        preferences.onboardingDone = true
        preferences.pendingReportId = null

        val profile = mode.profile()
        val cooldown = (profile.cooldownMs / preferences.sensitivity).toLong().coerceIn(1_500L, 10_000L)
        val engine = MetricsEngine(
            cooldownMs = cooldown,
            profile = profile,
            onEvent = { event -> dispatcher.dispatch(event) }
        )
        this.engine = engine

        val transcriber = VoskTranscriber(context, voiceModelFor(preferences.transcriptionLanguage))
        this.transcriber = transcriber

        _state.value = SessionUiState(
            isRunning = true,
            startedAt = startedAtWallClock,
            transcription = transcriber.state.value
        )

        SessionService.start(context)

        val capture = AudioCapture()
        this.capture = capture

        jobs += scope.launch {
            runCatching {
                capture.frames().collect { frame ->
                    engine.process(frame.samples, frame.timestampMs)
                    transcriber.accept(frame.samples)
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

        jobs += scope.launch {
            transcriber.state.collect { transcription ->
                _state.update { it.copy(transcription = transcription) }
            }
        }

        jobs += scope.launch {
            transcriber.prepare()
        }
    }

    fun stop() {
        if (!_state.value.isRunning) return

        val currentEngine = engine
        val reportId = UUID.randomUUID().toString()
        try {
            runCatching { capture?.stop() }
            jobs.forEach { it.cancel() }
            jobs.clear()
            runCatching { SessionService.stop(context) }

            val transcript = runCatching {
                transcriber?.flush()
                transcriber?.plainText().orEmpty()
            }.getOrDefault("")
            val transcriptMarked = runCatching {
                transcriber?.markedText().orEmpty()
            }.getOrDefault("")
            runCatching { transcriber?.close() }
            transcriber = null

            val metrics = currentEngine?.snapshot() ?: LiveMetrics()
            val duration = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(1_000L)

            val baseReport = SessionReport(
                id = reportId,
                startedAt = startedAtWallClock,
                durationMs = duration,
                mode = mode,
                score = metrics.averageScore,
                pace = metrics.pacePerMinute,
                fillerCount = metrics.fillerCount,
                pauseCount = metrics.pauseCount,
                longPauseCount = metrics.longPauseCount,
                pitchStdHz = metrics.pitchStdHz,
                speechRatio = metrics.speechRatio,
                transcript = transcript,
                transcriptMarked = transcriptMarked,
                scoreHistory = metrics.scoreHistory,
                paceHistory = metrics.paceHistory,
                fillerMarkers = metrics.fillerMarkers,
                pauseMarkers = metrics.pauseMarkers
            )

            val language = context.resources.configuration.locales[0].language
            val suggestion = runCatching {
                RecommendationEngine.suggest(
                    report = baseReport,
                    language = language,
                    usedIds = preferences.usedSuggestionIds()
                )
            }.getOrNull()
            if (suggestion != null) {
                runCatching { preferences.markSuggestionUsed(suggestion.id) }
            }

            val report = baseReport.copy(
                recommendation = suggestion?.text.orEmpty(),
                recommendationId = suggestion?.id.orEmpty()
            )
            runCatching { repository.save(report) }
            preferences.pendingReportId = reportId
        } finally {
            engine = null
            capture = null
            _state.value = SessionUiState(isRunning = false, lastReportId = reportId)
        }
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
        runCatching { stop() }
        capture?.stop()
        jobs.forEach { it.cancel() }
        scope.cancel()
        transcriber?.close()
        transcriber = null
    }
}
