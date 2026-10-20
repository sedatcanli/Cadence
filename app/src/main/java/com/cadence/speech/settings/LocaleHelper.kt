package com.cadence.speech.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context): Context {
        val code = UserPreferences(context).uiLanguage
        val systemLocale = context.resources.configuration.locales[0]
        if (code.isBlank()) {
            Locale.setDefault(systemLocale)
            return context
        }

        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
