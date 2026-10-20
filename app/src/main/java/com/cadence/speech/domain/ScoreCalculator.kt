package com.cadence.speech.domain

import kotlin.math.roundToInt

object ScoreCalculator {

    const val TARGET_PACE_MIN = 120
    const val TARGET_PACE_MAX = 180

    fun paceScore(pace: Int): Float = when {
        pace <= 0 -> 55f
        pace < TARGET_PACE_MIN -> 100f - (TARGET_PACE_MIN - pace) * 0.9f
        pace > TARGET_PACE_MAX -> 100f - (pace - TARGET_PACE_MAX) * 0.9f
        else -> 100f
    }.coerceIn(0f, 100f)

    fun score(
        pace: Int,
        fillerCount: Int,
        longPauseCount: Int,
        pitchStdHz: Float,
        speechRatio: Float,
        durationMs: Long
    ): Int {
        val minutes = (durationMs / 60_000f).coerceAtLeast(0.5f)
        val fillersPerMinute = fillerCount / minutes

        val fluency = (100f - fillersPerMinute * 8f).coerceIn(0f, 100f)
        val pace = paceScore(pace)
        val tone = ((pitchStdHz - 8f) / 32f * 100f).coerceIn(0f, 100f)
        val continuity = speechRatio.coerceIn(0f, 1f) * 100f

        val total = fluency * 0.30f +
            pace * 0.25f +
            tone * 0.25f +
            continuity * 0.20f

        return total.roundToInt().coerceIn(0, 100)
    }
}
