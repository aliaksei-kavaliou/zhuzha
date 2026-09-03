package com.inkfirst.core

/**
 * A chapter's position in the course's daily reading schedule. Days are
 * 1-indexed. If [startDay] == [endDay] the chapter fits in a single session;
 * otherwise it spans multiple days. Descriptive only — nothing in the app
 * gates on this.
 */
data class ChapterDay(
    val chapterN: Int,
    val startDay: Int,
    val endDay: Int,
) {
    /** "Day 3" or "Day 5–6" — an en-dash separates a range. */
    val label: String
        get() = if (startDay == endDay) "Day $startDay" else "Day $startDay–$endDay"
}

/**
 * Derives a reading schedule from the course's chapter estMinutes and
 * [Course.dailyMinutes]. Rules:
 * - A day is exactly [Course.dailyMinutes] minutes of budget.
 * - Chapters are consumed in order; a chapter starts on whatever day has
 *   any unfilled budget (even one minute is enough to start).
 * - A chapter that overflows the current day continues into subsequent
 *   days until its estMinutes are exhausted.
 *
 * Zero-minute chapters and zero-or-negative dailyMinutes both degrade
 * gracefully — every chapter maps to Day 1 in the pathological case.
 */
fun courseDaySchedule(course: Course): List<ChapterDay> {
    val daily = course.dailyMinutes.coerceAtLeast(1)
    val result = mutableListOf<ChapterDay>()
    var currentDay = 1
    var minutesUsedToday = 0
    for (ch in course.chapters) {
        val est = ch.estMinutes.coerceAtLeast(0)
        // If today has no room left for even a start, roll to the next day.
        if (minutesUsedToday >= daily) {
            currentDay++
            minutesUsedToday = 0
        }
        val startDay = currentDay
        // Absorb this chapter into successive days until estMinutes is spent.
        var remaining = est
        val leftToday = daily - minutesUsedToday
        if (remaining <= leftToday || remaining == 0) {
            minutesUsedToday += remaining
        } else {
            // Fill today, then spill over.
            remaining -= leftToday
            minutesUsedToday = daily
            while (remaining > daily) {
                currentDay++
                remaining -= daily
            }
            currentDay++
            minutesUsedToday = remaining
        }
        result += ChapterDay(chapterN = ch.n, startDay = startDay, endDay = currentDay)
    }
    return result
}

/** Total days spanned by the schedule; at least 1. */
fun totalCourseDays(course: Course): Int =
    courseDaySchedule(course).maxOfOrNull { it.endDay } ?: 1
