package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CourseScheduleTest {

    private fun course(dailyMinutes: Int, vararg mins: Int): Course = Course(
        title = "T",
        slug = "t",
        topicPrompt = "",
        priorKnowledge = "",
        dailyMinutes = dailyMinutes,
        style = "headfirst-inspired",
        model = "gemini",
        language = "English",
        created = "2026-09-03",
        chapters = mins.mapIndexed { i, m ->
            PersistedChapter(i + 1, "%02d-x".format(i + 1), "Ch ${i + 1}", emptyList(), m)
        },
    )

    @Test
    fun `chapters shorter than the daily budget share the day`() {
        val c = course(dailyMinutes = 60, 20, 30, 25, 15)
        val sched = courseDaySchedule(c)
        // Day 1 = 20 + 30 = 50 (10 left, ch3 doesn't fit fully → starts day 1, ends day 2)
        // Actually ch3 is 25: 10 fits today, 15 spills to day 2. ends day 2.
        // Ch4 (15) starts day 2 (has 45 left), fits fully, ends day 2.
        assertEquals(listOf(1, 1, 1, 2), sched.map { it.startDay })
        assertEquals(listOf(1, 1, 2, 2), sched.map { it.endDay })
    }

    @Test
    fun `a chapter longer than the daily budget spans multiple days`() {
        val c = course(dailyMinutes = 30, 90)
        val sched = courseDaySchedule(c)
        // 90 min at 30/day → days 1..3
        assertEquals(ChapterDay(1, 1, 3), sched[0])
        assertEquals("Day 1–3", sched[0].label)
    }

    @Test
    fun `single-day chapter renders as Day N, not Day N-N`() {
        val c = course(dailyMinutes = 45, 30)
        assertEquals("Day 1", courseDaySchedule(c)[0].label)
    }

    @Test
    fun `total days equals the max endDay`() {
        val c = course(dailyMinutes = 30, 45, 45, 30)  // → day 1-2, day 2-3, day 4
        assertEquals(4, totalCourseDays(c))
    }

    @Test
    fun `zero-minute chapter maps to the current day without advancing`() {
        val c = course(dailyMinutes = 30, 20, 0, 20)
        val sched = courseDaySchedule(c)
        // Ch1: day1 (used 20)
        // Ch2: day1 (0 min, no advance)
        // Ch3: day1 (20 min, but only 10 left → spills to day2)
        assertEquals(listOf(1, 1, 1), sched.map { it.startDay })
        assertEquals(listOf(1, 1, 2), sched.map { it.endDay })
    }

    @Test
    fun `dailyMinutes of zero degrades to daily=1 without div-by-zero`() {
        val c = course(dailyMinutes = 0, 5, 5)
        val sched = courseDaySchedule(c)
        // daily coerced to 1 — each minute is a day. Doesn't need to be sensible,
        // just needs to not crash.
        assertEquals(2, sched.size)
    }
}
