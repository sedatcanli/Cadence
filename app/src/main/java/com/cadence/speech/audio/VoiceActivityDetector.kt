package com.cadence.speech.audio

import kotlin.math.log10
import kotlin.math.sqrt

class VoiceActivityDetector {

    private var noiseFloorDb = -60f
    private var hangoverFrames = 0

    fun reset() {
        noiseFloorDb = -60f
        hangoverFrames = 0
    }

    fun isSpeech(frame: ShortArray): Boolean {
        val level = rmsDb(frame)
        if (level < noiseFloorDb) {
            noiseFloorDb += (level - noiseFloorDb) * 0.05f
        } else {
            noiseFloorDb += (level - noiseFloorDb) * 0.0005f
        }

        val speaking = level > noiseFloorDb + 8f
        if (speaking) {
            hangoverFrames = HANGOVER
        } else if (hangoverFrames > 0) {
            hangoverFrames--
        }
        return speaking || hangoverFrames > 0
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

    private companion object {
        const val HANGOVER = 15
    }
}
