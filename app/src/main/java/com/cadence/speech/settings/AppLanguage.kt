package com.cadence.speech.settings

data class AppLanguage(
    val code: String,
    val label: String
)

val APP_LANGUAGES = listOf(
    AppLanguage("", "System default"),
    AppLanguage("en", "English"),
    AppLanguage("tr", "Türkçe"),
    AppLanguage("ar", "العربية"),
    AppLanguage("bn", "বাংলা"),
    AppLanguage("cs", "Čeština"),
    AppLanguage("da", "Dansk"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("el", "Ελληνικά"),
    AppLanguage("es", "Español"),
    AppLanguage("fa", "فارسی"),
    AppLanguage("fi", "Suomi"),
    AppLanguage("fr", "Français"),
    AppLanguage("iw", "עברית"),
    AppLanguage("hi", "हिन्दी"),
    AppLanguage("hu", "Magyar"),
    AppLanguage("in", "Bahasa Indonesia"),
    AppLanguage("it", "Italiano"),
    AppLanguage("ja", "日本語"),
    AppLanguage("ko", "한국어"),
    AppLanguage("ms", "Bahasa Melayu"),
    AppLanguage("nl", "Nederlands"),
    AppLanguage("nb", "Norsk"),
    AppLanguage("pl", "Polski"),
    AppLanguage("pt", "Português"),
    AppLanguage("ro", "Română"),
    AppLanguage("ru", "Русский"),
    AppLanguage("sk", "Slovenčina"),
    AppLanguage("sv", "Svenska"),
    AppLanguage("th", "ไทย"),
    AppLanguage("uk", "Українська"),
    AppLanguage("vi", "Tiếng Việt"),
    AppLanguage("zh", "中文")
)
