package com.cadence.speech.audio

import kotlin.math.sqrt

class FillerDetector {

    private var segmentStartMs = -1L
    private var lastVoicedMs = -1L
    private var emittedForSegment = false
    private val pitches = mutableListOf<Float>()

    fun reset() {
        segmentStartMs = -1L
        lastVoicedMs = -1L
        emittedForSegment = false
        pitches.clear()
    }

    fun update(voiced: Boolean, timestampMs: Long, pitchHz: Float?): Boolean {
        if (voiced) {
            if (segmentStartMs < 0 || timestampMs - lastVoicedMs > SEGMENT_GAP_MS) {
                segmentStartMs = timestampMs
                emittedForSegment = false
                pitches.clear()
            }
            lastVoicedMs = timestampMs
            if (pitchHz != null) pitches.add(pitchHz)

            val duration = timestampMs - segmentStartMs
            if (!emittedForSegment &&
                duration in MIN_DURATION_MS..MAX_DURATION_MS &&
                pitches.size >= 5 &&
                isSteady()
            ) {
                emittedForSegment = true
                return true
            }
        } else if (segmentStartMs >= 0 && timestampMs - lastVoicedMs > SEGMENT_GAP_MS) {
            segmentStartMs = -1L
            emittedForSegment = false
            pitches.clear()
        }
        return false
    }

    private fun isSteady(): Boolean {
        val mean = pitches.average().toFloat()
        var sum = 0.0
        for (value in pitches) {
            val delta = value - mean
            sum += delta * delta
        }
        val std = sqrt(sum / pitches.size)
        return std < MAX_STD_HZ
    }

    private companion object {
        const val SEGMENT_GAP_MS = 150L
        const val MIN_DURATION_MS = 600L
        const val MAX_DURATION_MS = 1_500L
        const val MAX_STD_HZ = 12.0
    }
}
