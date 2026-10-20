package com.cadence.speech.audio

class PitchDetector(private val sampleRate: Int = 16_000) {

    private val minFrequency = 70f
    private val maxFrequency = 400f
    private val threshold = 0.15f

    fun detect(samples: FloatArray): Float? {
        val minLag = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFrequency).toInt().coerceAtMost(samples.size / 2)
        if (maxLag <= minLag) return null

        val difference = FloatArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0f
            val limit = samples.size - lag
            var index = 0
            while (index < limit) {
                val delta = samples[index] - samples[index + lag]
                sum += delta * delta
                index++
            }
            difference[lag] = sum
        }

        val normalized = FloatArray(maxLag + 1)
        normalized[0] = 1f
        var runningSum = 0f
        for (lag in 1..maxLag) {
            runningSum += difference[lag]
            normalized[lag] = if (runningSum == 0f) 1f else difference[lag] * lag / runningSum
        }

        var lag = minLag
        while (lag <= maxLag) {
            if (normalized[lag] < threshold) {
                var best = lag
                val from = (lag - 2).coerceAtLeast(minLag)
                val to = (lag + 2).coerceAtMost(maxLag)
                for (candidate in from..to) {
                    if (normalized[candidate] < normalized[best]) best = candidate
                }
                return sampleRate.toFloat() / best
            }
            lag++
        }
        return null
    }
}
