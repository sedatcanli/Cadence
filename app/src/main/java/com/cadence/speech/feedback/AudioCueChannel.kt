package com.cadence.speech.feedback

import android.media.AudioManager
import android.media.ToneGenerator
import com.cadence.speech.audio.CoachingEvent
import com.cadence.speech.settings.UserPreferences

class AudioCueChannel(private val preferences: UserPreferences) : FeedbackChannel {

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
    }.getOrNull()

    override fun play(event: CoachingEvent) {
        if (!preferences.audioCueEnabled) return
        val generator = toneGenerator ?: return

        val (tone, duration) = when (event) {
            CoachingEvent.FILLER -> ToneGenerator.TONE_PROP_BEEP to 60
            CoachingEvent.PACE_FAST -> ToneGenerator.TONE_PROP_BEEP2 to 80
            CoachingEvent.LONG_PAUSE -> ToneGenerator.TONE_PROP_ACK to 120
            CoachingEvent.TOO_QUIET -> ToneGenerator.TONE_PROP_NACK to 80
        }
        runCatching { generator.startTone(tone, duration) }
    }

    private companion object {
        const val VOLUME = 30
    }
}
