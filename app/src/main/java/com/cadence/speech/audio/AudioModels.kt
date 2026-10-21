package com.cadence.speech.audio

data class AudioFrame(
    val samples: ShortArray,
    val timestampMs: Long,
    val sampleRate: Int = 16_000
)

data class LiveMetrics(
    val elapsedMs: Long = 0L,
    val pacePerMinute: Int = 0,
    val fillerCount: Int = 0,
    val pauseCount: Int = 0,
    val longPauseCount: Int = 0,
    val pitchStdHz: Float = 0f,
    val rmsDb: Float = -90f,
    val speechRatio: Float = 0f,
    val isSpeaking: Boolean = false,
    val liveScore: Int = 0,
    val recentScore: Int = 0,
    val averageScore: Int = 0,
    val isCalibrating: Boolean = false,
    val calibrationProgress: Int = 100,
    val hasAnySpeech: Boolean = false,
    val transcript: String = "",
    val scoreHistory: List<Int> = emptyList(),
    val paceHistory: List<Int> = emptyList(),
    val fillerMarkers: List<Int> = emptyList(),
    val pauseMarkers: List<Int> = emptyList()
)

enum class CoachingEvent {
    FILLER,
    PACE_FAST,
    LONG_PAUSE,
    TOO_QUIET
}
