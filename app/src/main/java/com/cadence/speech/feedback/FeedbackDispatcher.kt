package com.cadence.speech.feedback

import com.cadence.speech.audio.CoachingEvent

class FeedbackDispatcher(private val channels: List<FeedbackChannel>) {

    fun dispatch(event: CoachingEvent) {
        for (channel in channels) {
            runCatching { channel.play(event) }
        }
    }
}
