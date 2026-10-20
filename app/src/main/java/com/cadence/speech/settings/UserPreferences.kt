package com.cadence.speech.settings

import android.content.Context

class UserPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("cadence_prefs", Context.MODE_PRIVATE)

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var audioCueEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO, value).apply()

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 1f)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0.5f, 2f)).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var transcriptionLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    private companion object {
        const val KEY_HAPTIC = "haptic_enabled"
        const val KEY_AUDIO = "audio_enabled"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_LANGUAGE = "transcription_language"
        const val DEFAULT_LANGUAGE = "en"
    }
}
