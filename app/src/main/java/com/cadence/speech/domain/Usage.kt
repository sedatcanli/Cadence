package com.cadence.speech.domain

import java.util.Calendar

const val FREE_SESSIONS_PER_DAY = 1

fun isSameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply {
        timeInMillis = first
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val b = Calendar.getInstance().apply {
        timeInMillis = second
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

fun sessionsToday(
    sessions: List<SessionReport>,
    now: Long = System.currentTimeMillis()
): Int = sessions.count { isSameDay(it.startedAt, now) }

fun isDailyLimitReached(
    sessions: List<SessionReport>,
    trialActive: Boolean,
    now: Long = System.currentTimeMillis()
): Boolean = !trialActive && sessionsToday(sessions, now) >= FREE_SESSIONS_PER_DAY
