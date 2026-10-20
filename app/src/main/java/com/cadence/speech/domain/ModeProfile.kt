package com.cadence.speech.domain

data class ModeProfile(
    val targetPaceMin: Int,
    val targetPaceMax: Int,
    val fastPaceThreshold: Int,
    val paceHoldMs: Long,
    val longPauseThresholdMs: Long,
    val quietThresholdDb: Float,
    val quietHoldMs: Long,
    val cooldownMs: Long,
    val weightFluency: Float,
    val weightPace: Float,
    val weightTone: Float,
    val weightContinuity: Float,
    val fillerPenaltyPerMinute: Float
)

fun SessionMode.profile(): ModeProfile = when (this) {
    SessionMode.INTERVIEW -> ModeProfile(
        targetPaceMin = 110,
        targetPaceMax = 150,
        fastPaceThreshold = 170,
        paceHoldMs = 3_000L,
        longPauseThresholdMs = 2_500L,
        quietThresholdDb = -38f,
        quietHoldMs = 3_000L,
        cooldownMs = 4_000L,
        weightFluency = 0.40f,
        weightPace = 0.25f,
        weightTone = 0.15f,
        weightContinuity = 0.20f,
        fillerPenaltyPerMinute = 10f
    )

    SessionMode.PRESENTATION -> ModeProfile(
        targetPaceMin = 120,
        targetPaceMax = 170,
        fastPaceThreshold = 190,
        paceHoldMs = 2_500L,
        longPauseThresholdMs = 1_800L,
        quietThresholdDb = -34f,
        quietHoldMs = 2_500L,
        cooldownMs = 3_500L,
        weightFluency = 0.25f,
        weightPace = 0.30f,
        weightTone = 0.30f,
        weightContinuity = 0.15f,
        fillerPenaltyPerMinute = 8f
    )

    SessionMode.FREE -> ModeProfile(
        targetPaceMin = 110,
        targetPaceMax = 190,
        fastPaceThreshold = 210,
        paceHoldMs = 4_000L,
        longPauseThresholdMs = 4_000L,
        quietThresholdDb = -42f,
        quietHoldMs = 4_000L,
        cooldownMs = 6_000L,
        weightFluency = 0.25f,
        weightPace = 0.20f,
        weightTone = 0.20f,
        weightContinuity = 0.35f,
        fillerPenaltyPerMinute = 6f
    )
}
