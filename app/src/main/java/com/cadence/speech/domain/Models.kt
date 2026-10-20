package com.cadence.speech.domain

enum class SessionMode {
    INTERVIEW,
    PRESENTATION,
    FREE
}

data class SessionReport(
    val id: String,
    val startedAt: Long,
    val durationMs: Long,
    val mode: SessionMode,
    val score: Int,
    val pace: Int,
    val fillerCount: Int,
    val pauseCount: Int,
    val longPauseCount: Int,
    val pitchStdHz: Float,
    val speechRatio: Float,
    val transcript: String = "",
    val transcriptMarked: String = "",
    val recommendation: String = "",
    val recommendationId: String = "",
    val scoreHistory: List<Int> = emptyList(),
    val paceHistory: List<Int> = emptyList(),
    val fillerMarkers: List<Int> = emptyList(),
    val pauseMarkers: List<Int> = emptyList()
)

enum class Insight {
    FLUENCY,
    PACE,
    TONE,
    CONTINUITY,
    GOOD
}

fun SessionReport.insight(): Insight {
    val minutes = (durationMs / 60_000f).coerceAtLeast(0.5f)
    val fillersPerMinute = fillerCount / minutes
    val profile = mode.profile()

    val scores = linkedMapOf(
        Insight.FLUENCY to (100f - fillersPerMinute * profile.fillerPenaltyPerMinute).coerceIn(0f, 100f),
        Insight.PACE to ScoreCalculator.paceScore(pace, profile),
        Insight.TONE to ((pitchStdHz - 8f) / 32f * 100f).coerceIn(0f, 100f),
        Insight.CONTINUITY to speechRatio.coerceIn(0f, 1f) * 100f
    )

    val weakest = scores.minByOrNull { it.value } ?: return Insight.GOOD
    return if (weakest.value >= 70f) Insight.GOOD else weakest.key
}
