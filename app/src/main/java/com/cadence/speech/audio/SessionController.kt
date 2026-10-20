package com.cadence.speech.audio

import android.content.Context
import android.os.SystemClock
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
    val error: String? = null
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

        _state.value = SessionUiState(isRunning = true, startedAt = startedAtWallClock)

        SessionService.start(context)

        val capture = AudioCapture()
        this.capture = capture

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

    fun stop() {
        if (!_state.value.isRunning) return

        val currentEngine = engine
        capture?.stop()
        jobs.forEach { it.cancel() }
        jobs.clear()
        SessionService.stop(context)

        val metrics = currentEngine?.snapshot() ?: LiveMetrics()
        val duration = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(1_000L)

        val report = SessionReport(
            id = UUID.randomUUID().toString(),
            startedAt = startedAtWallClock,
            durationMs = duration,
            mode = mode,
            score = metrics.liveScore,
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
    }
}
