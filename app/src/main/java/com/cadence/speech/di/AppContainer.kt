package com.cadence.speech.di

import android.content.Context
import com.cadence.speech.audio.SessionController
import com.cadence.speech.data.SessionRepository
import com.cadence.speech.feedback.AudioCueChannel
import com.cadence.speech.feedback.FeedbackDispatcher
import com.cadence.speech.feedback.PhoneHapticChannel
import com.cadence.speech.settings.UserPreferences

class AppContainer(context: Context) {

    val preferences = UserPreferences(context)

    val repository = SessionRepository(context)

    private val feedbackDispatcher = FeedbackDispatcher(
        listOf(
            PhoneHapticChannel(context, preferences),
            AudioCueChannel(preferences)
        )
    )

    val sessionController = SessionController(
        context = context,
        repository = repository,
        preferences = preferences,
        dispatcher = feedbackDispatcher
    )
}
