package com.cadence.speech.domain

import kotlin.math.roundToInt

object ScoreCalculator {

    const val TARGET_PACE_MIN = 120
    const val TARGET_PACE_MAX = 180

    fun paceScore(
        pace: Int,
        profile: ModeProfile = SessionMode.FREE.profile()
    ): Float = when {
        pace <= 0 -> 55f
        pace < profile.targetPaceMin -> 100f - (profile.targetPaceMin - pace) * 0.9f
        pace > profile.targetPaceMax -> 100f - (pace - profile.targetPaceMax) * 0.9f
        else -> 100f
    }.coerceIn(0f, 100f)

    fun score(
        pace: Int,
        fillerCount: Int,
        longPauseCount: Int,
        pitchStdHz: Float,
        speechRatio: Float,
        durationMs: Long,
        profile: ModeProfile = SessionMode.FREE.profile()
    ): Int {
        val minutes = (durationMs / 60_000f).coerceAtLeast(0.5f)
        val fillersPerMinute = fillerCount / minutes

        val fluency = (100f - fillersPerMinute * profile.fillerPenaltyPerMinute).coerceIn(0f, 100f)
        val paceScore = paceScore(pace, profile)
        val tone = ((pitchStdHz - 8f) / 32f * 100f).coerceIn(0f, 100f)
        val continuity = speechRatio.coerceIn(0f, 1f) * 100f

        val total = fluency * profile.weightFluency +
            paceScore * profile.weightPace +
            tone * profile.weightTone +
            continuity * profile.weightContinuity

        return total.roundToInt().coerceIn(0, 100)
    }
}
