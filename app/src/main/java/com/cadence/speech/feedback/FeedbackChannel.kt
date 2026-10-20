package com.cadence.speech.feedback

import com.cadence.speech.audio.CoachingEvent

interface FeedbackChannel {
    fun play(event: CoachingEvent)
}
