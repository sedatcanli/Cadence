package com.cadence.speech.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AudioCapture(private val sampleRate: Int = 16_000) {

    @Volatile
    private var record: AudioRecord? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    fun frames(): Flow<AudioFrame> = flow {
        val frameSize = sampleRate / 50
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, frameSize * 8)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("AudioRecord could not be initialized")
        }

        record = audioRecord
        running = true
        audioRecord.startRecording()

        val buffer = ShortArray(frameSize)
        var totalSamples = 0L
        try {
            while (running) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val timestamp = totalSamples * 1000L / sampleRate
                    emit(AudioFrame(buffer.copyOf(read), timestamp, sampleRate))
                    totalSamples += read
                }
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
            record = null
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        running = false
        runCatching { record?.stop() }
    }
}
