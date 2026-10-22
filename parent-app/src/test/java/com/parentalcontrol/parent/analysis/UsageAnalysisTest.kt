package com.parentalcontrol.parent.analysis

import com.parentalcontrol.parent.model.DayHistory
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAnalysisTest {

    private val today = "2026-10-22"

    private fun day(date: String, total: Long, cats: Map<String, Long> = emptyMap()) =
        DayHistory(date = date, totalMinutes = total, categories = cats)

    @Test
    fun `duration formatting`() {
        assertEquals("0m", UsageFormat.duration(0))
        assertEquals("45m", UsageFormat.duration(45))
        assertEquals("2j 00m", UsageFormat.duration(120))
        assertEquals("2j 30m", UsageFormat.duration(150))
        assertEquals("0m", UsageFormat.duration(-5))
    }

    @Test
    fun `lastDays uses calendar days not entry count`() {
        val history = listOf(day("2026-10-10", 10), day("2026-10-16", 20), day("2026-10-20", 30), day(today, 40))
        // 7 hari terakhir: 16..22 Oktober
        assertEquals(listOf("2026-10-16", "2026-10-20", today), UsageAnalysis.lastDays(history, 7, today).map { it.date })
    }

    @Test
    fun `dailyTotals fills gaps with zero in chronological order`() {
        val totals = UsageAnalysis.dailyTotals(listOf(day("2026-10-21", 30), day(today, 50)), 3, today)
        assertEquals(listOf("2026-10-20" to 0L, "2026-10-21" to 30L, today to 50L), totals)
    }

    @Test
    fun `stats sums categories and averages over days with data`() {
        val stats = UsageAnalysis.stats(
            listOf(
                day("2026-10-21", 60, mapOf("game" to 20L, "edukasi" to 40L)),
                day(today, 30, mapOf("game" to 10L, "hiburan" to 20L))
            )
        )
        assertEquals(90L, stats.totalMinutes)
        assertEquals(45L, stats.averagePerDay)
        assertEquals(30L, stats.categories["game"])
        assertEquals(40L, stats.categories["edukasi"])
        assertEquals(20L, stats.categories["hiburan"])
        assertEquals(0L, stats.categories["lainnya"])
    }

    @Test
    fun `stats of empty list is zero`() {
        val stats = UsageAnalysis.stats(emptyList())
        assertEquals(0L, stats.totalMinutes)
        assertEquals(0L, stats.averagePerDay)
    }

    @Test
    fun `live summary replaces today's stored entry`() {
        val summary = DeviceSummary(totalScreenTimeMinutes = 99, categories = mapOf("game" to 99L))
        val merged = UsageAnalysis.withLiveToday(listOf(day("2026-10-21", 10), day(today, 5)), summary, today)
        assertEquals(listOf("2026-10-21", today), merged.map { it.date })
        assertEquals(99L, merged.last().totalMinutes)
    }

    @Test
    fun `trend compares to average of previous days only`() {
        val history = listOf(day("2026-10-20", 100), day("2026-10-21", 100), day(today, 999))
        assertEquals(20, UsageAnalysis.trendPercent(history, today, 120))
        assertEquals(-50, UsageAnalysis.trendPercent(history, today, 50))
        assertNull(UsageAnalysis.trendPercent(listOf(day(today, 10)), today, 10))
    }

    @Test
    fun `insight warns when daily limit nearly used`() {
        val config = LimitsConfig(dailyLimitEnabled = true, dailyLimitMinutes = 60)
        val summary = DeviceSummary(limitUsedMinutes = 50, totalScreenTimeMinutes = 50)
        val insights = UsageAnalysis.buildInsights(emptyList(), summary, config, today)
        assertEquals(1, insights.size)
        assertEquals(InsightLevel.WARNING, insights[0].level)
    }

    @Test
    fun `insight accounts for bonus minutes`() {
        val config = LimitsConfig(
            dailyLimitEnabled = true, dailyLimitMinutes = 60, bonusDate = today, bonusMinutes = 60
        )
        val summary = DeviceSummary(limitUsedMinutes = 90)
        val insights = UsageAnalysis.buildInsights(emptyList(), summary, config, today)
        assertEquals(InsightLevel.OK, insights[0].level)
        assertTrue(insights[0].detail.contains("30m"))
    }

    @Test
    fun `no insights without limit or history`() {
        assertTrue(UsageAnalysis.buildInsights(emptyList(), DeviceSummary(), LimitsConfig(), today).isEmpty())
    }
}
