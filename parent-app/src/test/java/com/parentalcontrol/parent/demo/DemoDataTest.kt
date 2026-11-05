package com.parentalcontrol.parent.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataTest {

    @Test
    fun `summary numbers are consistent`() {
        val s = DemoData.summary(now = 1_000_000_000_000L)
        assertEquals(s.appList.sumOf { it.durationMinutes }, s.totalScreenTimeMinutes)
        assertEquals(s.totalScreenTimeMinutes, s.categories.values.sum())
        assertEquals(s.totalScreenTimeMinutes, s.limitUsedMinutes)
        assertEquals(s.appList.first { it.isYoutubeKids }.durationMinutes, s.youtubeKidsMinutes)
        assertTrue(s.appList.all { it.lastTimeUsed < s.timestamp })
    }

    @Test
    fun `history covers the six previous days in order and each day adds up`() {
        val history = DemoData.history("2026-11-05")
        assertEquals(6, history.size)
        assertEquals("2026-10-30", history.first().date)
        assertEquals("2026-11-04", history.last().date)
        assertEquals(history.map { it.date }.sorted(), history.map { it.date })
        history.forEach { assertEquals(it.date, it.totalMinutes, it.categories.values.sum()) }
    }

    @Test
    fun `demo config limits youtube kids and keeps usage under the daily limit`() {
        val config = DemoData.config()
        val s = DemoData.summary(now = 0)
        val yt = config.appLimits.first { it.id == "youtube_kids" }
        assertEquals(60, yt.minutes)
        assertTrue(s.youtubeKidsMinutes < yt.minutes)
        assertTrue(s.limitUsedMinutes < config.dailyLimitMinutes)
    }
}
