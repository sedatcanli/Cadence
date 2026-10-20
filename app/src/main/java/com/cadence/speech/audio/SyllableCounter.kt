package com.cadence.speech.audio

import kotlin.math.abs

class SyllableCounter(private val sampleRate: Int = 16_000) {

    private var envelope = 0f
    private var previousEnvelope = 0f
    private var rising = false
    private var lastPeakMs = -10_000L
    private val peaks = ArrayDeque<Long>()

    fun reset() {
        envelope = 0f
        previousEnvelope = 0f
        rising = false
        lastPeakMs = -10_000L
        peaks.clear()
    }

    fun process(frame: ShortArray, timestampMs: Long) {
        var sum = 0f
        for (sample in frame) {
            sum += abs(sample / 32768f)
        }
        val level = sum / frame.size.coerceAtLeast(1)
        envelope = 0.7f * envelope + 0.3f * level

        if (envelope > MIN_LEVEL) {
            if (envelope > previousEnvelope) {
                rising = true
            } else if (rising && timestampMs - lastPeakMs >= MIN_GAP_MS) {
                rising = false
                lastPeakMs = timestampMs
                peaks.addLast(timestampMs)
            }
        } else {
            rising = false
        }

        previousEnvelope = envelope
        while (peaks.isNotEmpty() && timestampMs - peaks.first() > WINDOW_MS) {
            peaks.removeFirst()
        }
    }

    fun syllablesPerMinute(timestampMs: Long): Int {
        while (peaks.isNotEmpty() && timestampMs - peaks.first() > WINDOW_MS) {
            peaks.removeFirst()
        }
        return peaks.size * (60_000L / WINDOW_MS).toInt()
    }

    private companion object {
        const val MIN_LEVEL = 0.008f
        const val MIN_GAP_MS = 120L
        const val WINDOW_MS = 5_000L
    }
}
