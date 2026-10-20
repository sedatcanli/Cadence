package com.cadence.speech.transcription

import java.util.Locale

const val LONG_PAUSE_MS = 1_500L

data class TranscriptSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val pauseBeforeMs: Long = 0L
)

private val FILLERS: Map<String, Set<String>> = mapOf(
    "tr" to setOf(
        "ee", "eee", "ıı", "ııı", "aa", "hmm", "hm", "mmm",
        "şey", "şeyi", "şeyler", "yani", "işte", "falan", "filan", "hani", "ya"
    ),
    "en" to setOf(
        "uh", "uhh", "um", "umm", "er", "erm", "ah", "hmm", "mm",
        "like", "actually", "basically", "literally"
    ),
    "de" to setOf("äh", "ähm", "hm", "also", "halt", "quasi", "sozusagen", "ne"),
    "fr" to setOf("euh", "heu", "ben", "bah", "quoi", "genre", "voilà"),
    "es" to setOf("eh", "este", "bueno", "pues", "digamos"),
    "ru" to setOf("э", "ээ", "ну", "вот", "типа")
)

fun fillerRanges(text: String, language: String): List<IntRange> {
    val fillers = FILLERS[language] ?: FILLERS.getValue("en")
    val ranges = mutableListOf<IntRange>()
    var start = -1
    for (i in text.indices) {
        val isLetter = Character.isLetter(text[i])
        if (isLetter && start < 0) {
            start = i
        } else if (!isLetter && start >= 0) {
            val token = text.substring(start, i).lowercase(Locale.ROOT)
            if (token in fillers) ranges += start until i
            start = -1
        }
    }
    if (start >= 0) {
        val token = text.substring(start).lowercase(Locale.ROOT)
        if (token in fillers) ranges += start until text.length
    }
    return ranges
}

fun formatPause(ms: Long): String =
    String.format(Locale.US, "%.1fs", ms / 1000f)

fun plainTranscript(segments: List<TranscriptSegment>): String =
    segments.joinToString(" ") { it.text.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()

fun markedTranscript(segments: List<TranscriptSegment>, language: String): String {
    val builder = StringBuilder()
    segments.forEach { segment ->
        if (segment.pauseBeforeMs >= LONG_PAUSE_MS) {
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append("[pause ").append(formatPause(segment.pauseBeforeMs)).append("] ")
        }
        val text = segment.text.trim()
        if (text.isNotEmpty()) {
            builder.append(markFillers(text, language)).append(' ')
        }
    }
    return builder.toString().trim()
}

private fun markFillers(text: String, language: String): String {
    val ranges = fillerRanges(text, language)
    if (ranges.isEmpty()) return text
    val builder = StringBuilder(text)
    ranges.sortedByDescending { it.first }.forEach { range ->
        builder.insert(range.last + 1, '*')
        builder.insert(range.first, '*')
    }
    return builder.toString()
}
