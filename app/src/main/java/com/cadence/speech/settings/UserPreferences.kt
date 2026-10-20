package com.cadence.speech.settings

import android.content.Context

class UserPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("cadence_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_FIRST_USE)) {
            prefs.edit().putLong(KEY_FIRST_USE, System.currentTimeMillis()).apply()
        }
    }

    val firstUseAt: Long
        get() = prefs.getLong(KEY_FIRST_USE, System.currentTimeMillis())

    fun isTrialActive(now: Long = System.currentTimeMillis()): Boolean =
        now < firstUseAt + TRIAL_DURATION_MS

    fun trialDaysLeft(now: Long = System.currentTimeMillis()): Int {
        val remaining = firstUseAt + TRIAL_DURATION_MS - now
        if (remaining <= 0L) return 0
        return ((remaining + DAY_MS - 1) / DAY_MS).toInt()
    }

    var profileName: String
        get() = prefs.getString(KEY_PROFILE_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PROFILE_NAME, value.trim()).apply()

    var uiLanguage: String
        get() = prefs.getString(KEY_UI_LANGUAGE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_UI_LANGUAGE, value).apply()

    var paywallAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_PAYWALL_ACK, false)
        set(value) = prefs.edit().putBoolean(KEY_PAYWALL_ACK, value).apply()

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

    fun usedSuggestionIds(): Set<String> {
        val raw = prefs.getString(KEY_USED_SUGGESTIONS, null) ?: return emptySet()
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    fun markSuggestionUsed(id: String) {
        if (id.isBlank()) return
        val ids = usedSuggestionIds().toMutableList()
        if (id in ids) return
        ids += id
        val trimmed = if (ids.size > MAX_USED_SUGGESTIONS) {
            ids.takeLast(MAX_USED_SUGGESTIONS)
        } else {
            ids
        }
        prefs.edit().putString(KEY_USED_SUGGESTIONS, trimmed.joinToString(",")).apply()
    }

    private companion object {
        const val KEY_HAPTIC = "haptic_enabled"
        const val KEY_AUDIO = "audio_enabled"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_LANGUAGE = "transcription_language"
        const val KEY_USED_SUGGESTIONS = "used_suggestions"
        const val KEY_FIRST_USE = "first_use_at"
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_UI_LANGUAGE = "ui_language"
        const val KEY_PAYWALL_ACK = "paywall_acknowledged"
        const val DEFAULT_LANGUAGE = "en"
        const val MAX_USED_SUGGESTIONS = 2_000
        const val DAY_MS = 24L * 60L * 60L * 1_000L
        const val TRIAL_DURATION_MS = 7L * DAY_MS
    }
}
