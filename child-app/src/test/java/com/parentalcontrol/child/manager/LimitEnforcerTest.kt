package com.parentalcontrol.child.manager

import com.parentalcontrol.child.model.LimitRule
import com.parentalcontrol.child.model.LimitsConfig
import com.parentalcontrol.child.model.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LimitEnforcerTest {

    private fun minutes(h: Int, m: Int = 0) = h * 60 + m
    private fun clock(day: Int = Calendar.MONDAY, minute: Int = minutes(10), date: String = "2026-10-22") =
        ClockState(dateKey = date, dayOfWeek = day, minuteOfDay = minute)

    private fun usage(
        totalMin: Long = 0,
        packages: Map<String, Long> = emptyMap(),
        categories: Map<String, Long> = emptyMap(),
        fgPackage: String? = null,
        fgCategory: String? = null
    ) = UsageSnapshot(
        totalMs = totalMin * 60_000L,
        packageMs = packages.mapValues { it.value * 60_000L },
        categoryMs = categories.mapValues { it.value * 60_000L },
        foregroundPackage = fgPackage,
        foregroundCategory = fgCategory
    )

    @Test
    fun `default config never locks`() {
        assertNull(LimitEnforcer.evaluate(LimitsConfig(), usage(totalMin = 999), clock()))
    }

    @Test
    fun `daily limit locks once total reaches limit`() {
        val config = LimitsConfig(dailyLimitEnabled = true, dailyLimitMinutes = 60)
        assertNull(LimitEnforcer.evaluate(config, usage(totalMin = 59), clock()))
        assertNotNull(LimitEnforcer.evaluate(config, usage(totalMin = 60), clock()))
    }

    @Test
    fun `disabled daily limit is ignored`() {
        val config = LimitsConfig(dailyLimitEnabled = false, dailyLimitMinutes = 1)
        assertNull(LimitEnforcer.evaluate(config, usage(totalMin = 500), clock()))
    }

    @Test
    fun `bonus only counts on its own date`() {
        val config = LimitsConfig(
            dailyLimitEnabled = true, dailyLimitMinutes = 60, bonusDate = "2026-10-22", bonusMinutes = 30
        )
        assertEquals(90, LimitEnforcer.effectiveDailyLimitMinutes(config, "2026-10-22"))
        assertEquals(60, LimitEnforcer.effectiveDailyLimitMinutes(config, "2026-10-23"))
        assertNull(LimitEnforcer.evaluate(config, usage(totalMin = 80), clock(date = "2026-10-22")))
        assertNotNull(LimitEnforcer.evaluate(config, usage(totalMin = 80), clock(date = "2026-10-23")))
    }

    @Test
    fun `window within same day`() {
        val w = TimeWindow(enabled = true, start = "14:00", end = "16:00")
        assertFalse(LimitEnforcer.isWindowActive(w, clock(minute = minutes(13, 59))))
        assertTrue(LimitEnforcer.isWindowActive(w, clock(minute = minutes(14))))
        assertTrue(LimitEnforcer.isWindowActive(w, clock(minute = minutes(15, 59))))
        assertFalse(LimitEnforcer.isWindowActive(w, clock(minute = minutes(16))))
    }

    @Test
    fun `window crossing midnight`() {
        val w = TimeWindow(enabled = true, start = "20:00", end = "06:00")
        assertTrue(LimitEnforcer.isWindowActive(w, clock(minute = minutes(21))))
        assertTrue(LimitEnforcer.isWindowActive(w, clock(minute = minutes(5, 59))))
        assertFalse(LimitEnforcer.isWindowActive(w, clock(minute = minutes(6))))
        assertFalse(LimitEnforcer.isWindowActive(w, clock(minute = minutes(12))))
    }

    @Test
    fun `midnight window respects day of the start date`() {
        // Hanya Jumat malam: Sabtu 05:00 masih termasuk jendela Jumat, Jumat 05:00 tidak.
        val w = TimeWindow(enabled = true, start = "20:00", end = "06:00", days = listOf(Calendar.FRIDAY))
        assertTrue(LimitEnforcer.isWindowActive(w, clock(day = Calendar.FRIDAY, minute = minutes(21))))
        assertTrue(LimitEnforcer.isWindowActive(w, clock(day = Calendar.SATURDAY, minute = minutes(5))))
        assertFalse(LimitEnforcer.isWindowActive(w, clock(day = Calendar.FRIDAY, minute = minutes(5))))
        assertFalse(LimitEnforcer.isWindowActive(w, clock(day = Calendar.SATURDAY, minute = minutes(21))))
    }

    @Test
    fun `sunday midnight window rolls back to saturday`() {
        val w = TimeWindow(enabled = true, start = "22:00", end = "06:00", days = listOf(Calendar.SATURDAY))
        assertTrue(LimitEnforcer.isWindowActive(w, clock(day = Calendar.SUNDAY, minute = minutes(2))))
    }

    @Test
    fun `invalid or empty window is inactive`() {
        assertFalse(LimitEnforcer.isWindowActive(TimeWindow(true, "xx", "06:00"), clock()))
        assertFalse(LimitEnforcer.isWindowActive(TimeWindow(true, "08:00", "08:00"), clock()))
        assertFalse(LimitEnforcer.isWindowActive(TimeWindow(true, "25:00", "06:00"), clock()))
    }

    @Test
    fun `bedtime locks regardless of foreground app`() {
        val config = LimitsConfig(bedtime = TimeWindow(true, "20:00", "06:00"))
        assertNotNull(LimitEnforcer.evaluate(config, usage(), clock(minute = minutes(21))))
    }

    @Test
    fun `study time blocks only entertainment and games`() {
        val config = LimitsConfig(study = TimeWindow(true, "14:00", "16:00", days = emptyList()))
        val c = clock(minute = minutes(15))
        assertNotNull(LimitEnforcer.evaluate(config, usage(fgCategory = "hiburan"), c))
        assertNotNull(LimitEnforcer.evaluate(config, usage(fgCategory = "game"), c))
        assertNull(LimitEnforcer.evaluate(config, usage(fgCategory = "edukasi"), c))
        assertNull(LimitEnforcer.evaluate(config, usage(fgCategory = null), c))
    }

    @Test
    fun `package rule locks only while that app is in foreground`() {
        val yt = "com.google.android.apps.youtube.kids"
        val config = LimitsConfig(
            appLimits = listOf(LimitRule("yt", "YouTube Kids", LimitRule.TYPE_PACKAGE, yt, 45))
        )
        val packages = mapOf(yt to 45L)
        assertNotNull(LimitEnforcer.evaluate(config, usage(packages = packages, fgPackage = yt), clock()))
        assertNull(LimitEnforcer.evaluate(config, usage(packages = packages, fgPackage = "other.app"), clock()))
        assertNull(LimitEnforcer.evaluate(config, usage(packages = mapOf(yt to 44L), fgPackage = yt), clock()))
    }

    @Test
    fun `category rule sums multiple categories`() {
        val config = LimitsConfig(
            appLimits = listOf(LimitRule("fun", "Game & Hiburan", LimitRule.TYPE_CATEGORY, "game,hiburan", 30))
        )
        val cats = mapOf("game" to 10L, "hiburan" to 20L)
        assertNotNull(LimitEnforcer.evaluate(config, usage(categories = cats, fgCategory = "game"), clock()))
        assertNull(LimitEnforcer.evaluate(config, usage(categories = cats, fgCategory = "edukasi"), clock()))
        assertNull(
            LimitEnforcer.evaluate(config, usage(categories = mapOf("game" to 10L, "hiburan" to 19L), fgCategory = "game"), clock())
        )
    }

    @Test
    fun `rule with zero minutes means unlimited`() {
        val config = LimitsConfig(
            appLimits = listOf(LimitRule("edu", "Aplikasi Edukasi", LimitRule.TYPE_CATEGORY, "edukasi", 0))
        )
        assertNull(LimitEnforcer.evaluate(config, usage(categories = mapOf("edukasi" to 600L), fgCategory = "edukasi"), clock()))
    }
}
