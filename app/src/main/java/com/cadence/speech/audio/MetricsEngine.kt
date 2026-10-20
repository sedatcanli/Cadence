package com.cadence.speech.audio

import com.cadence.speech.domain.ModeProfile
import com.cadence.speech.domain.ScoreCalculator
import com.cadence.speech.domain.SessionMode
import com.cadence.speech.domain.profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt
import kotlin.math.roundToInt

class MetricsEngine(
    private val sampleRate: Int = 16_000,
    private val cooldownMs: Long = 4_000L,
    private val profile: ModeProfile = SessionMode.FREE.profile(),
    private val onEvent: (CoachingEvent) -> Unit
) {

    private val vad = VoiceActivityDetector()
    private val pitchDetector = PitchDetector(sampleRate)
    private val syllableCounter = SyllableCounter(sampleRate)
    private val fillerDetector = FillerDetector()

    private val _metrics = MutableStateFlow(LiveMetrics())
    val metrics: StateFlow<LiveMetrics> = _metrics.asStateFlow()

    private val pitchWindow = ArrayDeque<Pair<Long, Float>>()
    private val cooldowns = mutableMapOf<CoachingEvent, Long>()

    private var totalFrames = 0
    private var speechFrames = 0
    private var fillerCount = 0
    private var pauseCount = 0
    private var longPauseCount = 0

    private var pauseStartMs = -1L
    private var fastPaceStartMs = -1L
    private var quietStartMs = -1L
    private var lastSnapshotMs = 0L
    private var lastRmsDb = -90f
    private var lastSpeaking = false

    // Speech activity tracking
    private var hasSpeechStarted = false
    private var anySpeechDetected = false

    // Running average score
    private var scoreSum = 0f
    private var scoreSnapshotCount = 0

    private val scoreHistory = ArrayDeque<Int>()
    private var lastHistoryMs = 0L

    fun process(frame: ShortArray, timestampMs: Long) {
        if (totalFrames == 0) {
            lastSnapshotMs = 0L
        }
        totalFrames++

        val speaking = vad.isSpeech(frame)
        val rmsDb = vad.rmsDb(frame)
        lastSpeaking = speaking
        lastRmsDb = rmsDb
        if (speaking) {
            speechFrames++
            if (!hasSpeechStarted) {
                hasSpeechStarted = true
                anySpeechDetected = true
            }
        }

        // Only process events and metrics if speech has started
        if (hasSpeechStarted) {
            val pitch = if (speaking) {
                pitchDetector.detect(FloatArray(frame.size) { frame[it] / 32768f })
            } else {
                null
            }

            if (pitch != null && pitch in 70f..400f) {
                pitchWindow.addLast(timestampMs to pitch)
                while (pitchWindow.isNotEmpty() && timestampMs - pitchWindow.first().first > PITCH_WINDOW_MS) {
                    pitchWindow.removeFirst()
                }
            }

            updatePause(speaking, timestampMs)
            syllableCounter.process(frame, timestampMs)

            if (fillerDetector.update(speaking, timestampMs, pitch)) {
                fillerCount++
                emit(CoachingEvent.FILLER, timestampMs)
            }

            val pace = syllableCounter.syllablesPerMinute(timestampMs)
            updatePace(pace, timestampMs)
            updateVolume(speaking, rmsDb, timestampMs)
        }

        if (timestampMs - lastSnapshotMs >= SNAPSHOT_INTERVAL_MS) {
            lastSnapshotMs = timestampMs
            publish(timestampMs, syllableCounter.syllablesPerMinute(timestampMs))
        }
    }

    fun snapshot(): LiveMetrics = _metrics.value

    private fun updatePause(speaking: Boolean, timestampMs: Long) {
        if (speaking) {
            if (pauseStartMs >= 0) {
                val duration = timestampMs - pauseStartMs
                if (duration >= PAUSE_THRESHOLD_MS) {
                    pauseCount++
                    if (duration >= profile.longPauseThresholdMs) {
                        longPauseCount++
                        emit(CoachingEvent.LONG_PAUSE, timestampMs)
                    }
                }
                pauseStartMs = -1L
            }
        } else if (pauseStartMs < 0) {
            pauseStartMs = timestampMs
        }
    }

    private fun updatePace(pace: Int, timestampMs: Long) {
        if (pace > profile.fastPaceThreshold) {
            if (fastPaceStartMs < 0) fastPaceStartMs = timestampMs
            if (timestampMs - fastPaceStartMs > profile.paceHoldMs) {
                emit(CoachingEvent.PACE_FAST, timestampMs)
                fastPaceStartMs = timestampMs
            }
        } else {
            fastPaceStartMs = -1L
        }
    }

    private fun updateVolume(speaking: Boolean, rmsDb: Float, timestampMs: Long) {
        if (speaking && rmsDb < profile.quietThresholdDb) {
            if (quietStartMs < 0) quietStartMs = timestampMs
            if (timestampMs - quietStartMs > profile.quietHoldMs) {
                emit(CoachingEvent.TOO_QUIET, timestampMs)
                quietStartMs = timestampMs
            }
        } else {
            quietStartMs = -1L
        }
    }

    private fun emit(event: CoachingEvent, timestampMs: Long) {
        val until = cooldowns[event] ?: 0L
        if (timestampMs < until) return
        cooldowns[event] = timestampMs + cooldownMs
        onEvent(event)
    }

    private fun publish(timestampMs: Long, pace: Int) {
        val speechRatio = if (totalFrames == 0) 0f else speechFrames.toFloat() / totalFrames
        val pitchStd = pitchStdDeviation()
        val duration = timestampMs.coerceAtLeast(1_000L)

        var liveScore = 0
        var averageScore = 0

        if (hasSpeechStarted && anySpeechDetected) {
            liveScore = ScoreCalculator.score(
                pace = pace,
                fillerCount = fillerCount,
                longPauseCount = longPauseCount,
                pitchStdHz = pitchStd,
                speechRatio = speechRatio,
                durationMs = duration,
                profile = profile
            )

            scoreSum += liveScore
            scoreSnapshotCount++
            averageScore = (scoreSum / scoreSnapshotCount).roundToInt()

            if (timestampMs - lastHistoryMs >= SCORE_HISTORY_INTERVAL_MS) {
                lastHistoryMs = timestampMs
                scoreHistory.addLast(averageScore)
                while (scoreHistory.size > SCORE_HISTORY_MAX) {
                    scoreHistory.removeFirst()
                }
            }
        }

        _metrics.value = LiveMetrics(
            elapsedMs = timestampMs,
            pacePerMinute = pace,
            fillerCount = fillerCount,
            pauseCount = pauseCount,
            longPauseCount = longPauseCount,
            pitchStdHz = pitchStd,
            rmsDb = lastRmsDb,
            speechRatio = speechRatio,
            isSpeaking = lastSpeaking,
            liveScore = liveScore,
            averageScore = averageScore,
            hasAnySpeech = anySpeechDetected,
            transcript = "",
            scoreHistory = scoreHistory.toList()
        )
    }

    private fun pitchStdDeviation(): Float {
        if (pitchWindow.size < 3) return 0f
        val mean = pitchWindow.map { it.second }.average()
        var sum = 0.0
        for ((_, value) in pitchWindow) {
            val delta = value - mean
            sum += delta * delta
        }
        return sqrt(sum / pitchWindow.size).toFloat()
    }

    private companion object {
        const val SNAPSHOT_INTERVAL_MS = 100L
        const val PITCH_WINDOW_MS = 10_000L
        const val PAUSE_THRESHOLD_MS = 700L
        const val SCORE_HISTORY_INTERVAL_MS = 1_000L
        const val SCORE_HISTORY_MAX = 300
    }
}
