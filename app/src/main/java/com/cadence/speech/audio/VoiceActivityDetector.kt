package com.cadence.speech.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class VoiceActivityDetector(
    val calibrationMs: Long = 5_000L
) {

    private var calibrationStartMs = -1L
    private val calibrationLevels = ArrayList<Float>(512)
    private var noiseFloorDb = -60f
    private var hangoverFrames = 0

    var isCalibrated: Boolean = false
        private set

    var lastFrameSpeech: Boolean = false
        private set

    fun reset() {
        calibrationStartMs = -1L
        calibrationLevels.clear()
        noiseFloorDb = -60f
        hangoverFrames = 0
        isCalibrated = false
        lastFrameSpeech = false
    }

    fun calibrate(frame: ShortArray, timestampMs: Long): Boolean {
        if (isCalibrated) return true
        if (calibrationStartMs < 0) calibrationStartMs = timestampMs
        calibrationLevels += rmsDb(frame)
        if (timestampMs - calibrationStartMs >= calibrationMs) {
            val sorted = calibrationLevels.sorted()
            val index = (sorted.size * 0.2f).toInt().coerceIn(0, sorted.lastIndex)
            noiseFloorDb = sorted[index].coerceIn(-75f, -30f)
            calibrationLevels.clear()
            isCalibrated = true
        }
        return isCalibrated
    }

    fun isSpeech(frame: ShortArray): Boolean {
        val level = rmsDb(frame)
        if (level < noiseFloorDb) {
            noiseFloorDb += (level - noiseFloorDb) * 0.05f
        } else {
            noiseFloorDb += (level - noiseFloorDb) * 0.00005f
        }

        val threshold = max(noiseFloorDb + SPEECH_MARGIN_DB, MIN_SPEECH_DB)
        val rawSpeech = level > threshold && zeroCrossingRate(frame) < MAX_SPEECH_ZCR
        lastFrameSpeech = rawSpeech
        if (rawSpeech) {
            hangoverFrames = HANGOVER
        } else if (hangoverFrames > 0) {
            hangoverFrames--
        }
        return rawSpeech || hangoverFrames > 0
    }

    fun rmsDb(frame: ShortArray): Float {
        var sum = 0.0
        for (sample in frame) {
            val value = sample / 32768.0
            sum += value * value
        }
        val rms = sqrt(sum / frame.size.coerceAtLeast(1))
        return if (rms < 1e-7) -90f else (20.0 * log10(rms)).toFloat()
    }

    private fun zeroCrossingRate(frame: ShortArray): Float {
        if (frame.size < 2) return 0f
        var crossings = 0
        for (index in 1 until frame.size) {
            if ((frame[index - 1] >= 0) != (frame[index] >= 0)) crossings++
        }
        return crossings.toFloat() / (frame.size - 1)
    }

    private companion object {
        const val HANGOVER = 15
        const val SPEECH_MARGIN_DB = 9f
        const val MIN_SPEECH_DB = -50f
        const val MAX_SPEECH_ZCR = 0.35f
    }
}
