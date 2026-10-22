package com.cadence.speech.data

import android.content.Context
import com.cadence.speech.domain.SessionMode
import com.cadence.speech.domain.SessionReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SessionRepository(context: Context) {

    private val file = File(context.filesDir, "sessions.json")

    private val _sessions = MutableStateFlow<List<SessionReport>>(emptyList())
    val sessions: StateFlow<List<SessionReport>> = _sessions.asStateFlow()

    init {
        _sessions.value = load()
    }

    fun save(report: SessionReport) {
        val updated = (listOf(report) + _sessions.value).sortedByDescending { it.startedAt }
        _sessions.value = updated
        write(updated)
    }

    fun get(id: String): SessionReport? = _sessions.value.firstOrNull { it.id == id }

    private fun write(reports: List<SessionReport>) {
        runCatching {
            val array = JSONArray()
            reports.forEach { array.put(it.toJson()) }
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(array.toString())
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (error: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun load(): List<SessionReport> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                add(array.getJSONObject(i).toReport())
            }
        }.sortedByDescending { it.startedAt }
    }.getOrDefault(emptyList())

    private fun SessionReport.toJson() = JSONObject().apply {
        put("id", id)
        put("startedAt", startedAt)
        put("durationMs", durationMs)
        put("mode", mode.name)
        put("score", score)
        put("pace", pace)
        put("fillerCount", fillerCount)
        put("pauseCount", pauseCount)
        put("longPauseCount", longPauseCount)
        put("pitchStdHz", pitchStdHz.toDouble())
        put("speechRatio", speechRatio.toDouble())
        put("transcript", transcript)
        put("transcriptMarked", transcriptMarked)
        put("recommendation", recommendation)
        put("recommendationId", recommendationId)
        put("scoreHistory", scoreHistory.toJsonArray())
        put("paceHistory", paceHistory.toJsonArray())
        put("fillerMarkers", fillerMarkers.toJsonArray())
        put("pauseMarkers", pauseMarkers.toJsonArray())
    }

    private fun List<Int>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { array.put(it) }
        return array
    }

    private fun JSONObject.optIntList(key: String): List<Int> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }
    }

    private fun JSONObject.toReport() = SessionReport(
        id = getString("id"),
        startedAt = getLong("startedAt"),
        durationMs = getLong("durationMs"),
        mode = runCatching { SessionMode.valueOf(getString("mode")) }.getOrDefault(SessionMode.FREE),
        score = getInt("score"),
        pace = getInt("pace"),
        fillerCount = getInt("fillerCount"),
        pauseCount = getInt("pauseCount"),
        longPauseCount = getInt("longPauseCount"),
        pitchStdHz = getDouble("pitchStdHz").toFloat(),
        speechRatio = getDouble("speechRatio").toFloat(),
        transcript = optString("transcript"),
        transcriptMarked = optString("transcriptMarked"),
        recommendation = optString("recommendation"),
        recommendationId = optString("recommendationId"),
        scoreHistory = optIntList("scoreHistory"),
        paceHistory = optIntList("paceHistory"),
        fillerMarkers = optIntList("fillerMarkers"),
        pauseMarkers = optIntList("pauseMarkers")
    )
}
