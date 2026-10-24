package com.parentalcontrol.child.manager

import com.parentalcontrol.child.manager.UsageEventTypes.ACTIVITY_PAUSED
import com.parentalcontrol.child.manager.UsageEventTypes.ACTIVITY_RESUMED
import com.parentalcontrol.child.manager.UsageEventTypes.DEVICE_SHUTDOWN
import com.parentalcontrol.child.manager.UsageEventTypes.KEYGUARD_SHOWN
import com.parentalcontrol.child.manager.UsageEventTypes.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSessionCalculatorTest {

    private val min = 60_000L
    private val hour = 60 * min

    private fun resumed(at: Long, pkg: String, cls: String = "Main") = UsageEventRecord(at, pkg, cls, ACTIVITY_RESUMED)
    private fun paused(at: Long, pkg: String, cls: String = "Main") = UsageEventRecord(at, pkg, cls, ACTIVITY_PAUSED)
    private fun screenOff(at: Long) = UsageEventRecord(at, "android", null, SCREEN_NON_INTERACTIVE)

    private fun compute(events: List<UsageEventRecord>, end: Long = 24 * hour, interactive: Boolean = false) =
        UsageSessionCalculator.compute(events, end, interactive)

    @Test
    fun `simple resumed and paused pair`() {
        val r = compute(listOf(resumed(1 * hour, "a"), paused(1 * hour + 10 * min, "a")))
        assertEquals(10 * min, r.totalMs["a"])
        assertNull(r.foregroundPackage)
    }

    @Test
    fun `sessions of different apps are summed separately`() {
        val r = compute(
            listOf(
                resumed(1 * hour, "a"), paused(1 * hour + 10 * min, "a"),
                resumed(2 * hour, "b"), paused(2 * hour + 5 * min, "b"),
                resumed(3 * hour, "a"), paused(3 * hour + 20 * min, "a")
            )
        )
        assertEquals(30 * min, r.totalMs["a"])
        assertEquals(5 * min, r.totalMs["b"])
    }

    // Regresi bug "Lingokids 8 jam padahal dibuka 1 jam": PAUSED hilang dan layar mati.
    @Test
    fun `screen off closes a session whose paused event is missing`() {
        val r = compute(
            listOf(
                resumed(10 * hour, "lingokids"),
                screenOff(11 * hour),
                resumed(18 * hour, "other"), paused(18 * hour + min, "other")
            ),
            end = 20 * hour
        )
        assertEquals(1 * hour, r.totalMs["lingokids"])
        assertEquals(1 * min, r.totalMs["other"])
    }

    @Test
    fun `resume of another app closes a stuck session`() {
        val r = compute(
            listOf(resumed(1 * hour, "a"), resumed(1 * hour + 30 * min, "b"), paused(2 * hour, "b"))
        )
        assertEquals(30 * min, r.totalMs["a"])
        assertEquals(30 * min, r.totalMs["b"])
    }

    @Test
    fun `keyguard and shutdown also close sessions`() {
        val keyguard = compute(listOf(resumed(1 * hour, "a"), UsageEventRecord(2 * hour, "android", null, KEYGUARD_SHOWN)))
        assertEquals(1 * hour, keyguard.totalMs["a"])

        val shutdown = compute(listOf(resumed(1 * hour, "a"), UsageEventRecord(3 * hour, "android", null, DEVICE_SHUTDOWN)))
        assertEquals(2 * hour, shutdown.totalMs["a"])
    }

    @Test
    fun `open session at the end counts only while the screen is on`() {
        val events = listOf(resumed(10 * hour, "a"))

        val on = compute(events, end = 10 * hour + 25 * min, interactive = true)
        assertEquals(25 * min, on.totalMs["a"])
        assertEquals("a", on.foregroundPackage)

        val off = compute(events, end = 18 * hour, interactive = false)
        assertNull(off.totalMs["a"])
        assertNull(off.foregroundPackage)
    }

    @Test
    fun `moving between activities of the same app is one continuous session`() {
        val r = compute(
            listOf(
                resumed(1 * hour, "a", "Main"),
                paused(1 * hour + 10 * min, "a", "Main"), resumed(1 * hour + 10 * min, "a", "Detail"),
                paused(1 * hour + 30 * min, "a", "Detail")
            )
        )
        assertEquals(30 * min, r.totalMs["a"])
    }

    @Test
    fun `stale paused event for another activity does not end the session`() {
        val r = compute(
            listOf(
                resumed(1 * hour, "a", "Main"),
                paused(1 * hour + 5 * min, "a", "Ghost"),
                paused(1 * hour + 20 * min, "a", "Main")
            )
        )
        assertEquals(20 * min, r.totalMs["a"])
    }

    @Test
    fun `paused without an open session is ignored`() {
        val r = compute(listOf(paused(1 * hour, "a"), resumed(2 * hour, "b"), paused(2 * hour + min, "b")))
        assertNull(r.totalMs["a"])
        assertEquals(min, r.totalMs["b"])
    }

    @Test
    fun `last used is the end of the latest session`() {
        val r = compute(
            listOf(resumed(1 * hour, "a"), paused(1 * hour + 10 * min, "a"), resumed(3 * hour, "a"), paused(3 * hour + 5 * min, "a"))
        )
        assertEquals(3 * hour + 5 * min, r.lastUsed["a"])
    }

    @Test
    fun `unsorted events are handled chronologically`() {
        val r = compute(listOf(paused(1 * hour + 10 * min, "a"), resumed(1 * hour, "a")))
        assertEquals(10 * min, r.totalMs["a"])
    }

    // Jumlah semua aplikasi tidak boleh melebihi waktu yang berlalu, apa pun kondisi event-nya.
    @Test
    fun `total across apps never exceeds elapsed time even with missing paused events`() {
        val events = mutableListOf<UsageEventRecord>()
        var t = 0L
        val apps = listOf("a", "b", "c", "d")
        for (i in 0 until 200) {
            t += (i % 7 + 1) * min
            events.add(resumed(t, apps[i % apps.size])) // tanpa pasangan PAUSED sama sekali
            if (i % 25 == 24) events.add(screenOff(t + min))
        }
        val end = t + 3 * hour
        val r = compute(events, end = end, interactive = false)
        assertTrue(r.totalMs.values.sum() <= end)
    }

    @Test
    fun `minutes are rounded up from the total not per app`() {
        assertEquals(0L, UsageSessionCalculator.minutesCeil(0))
        assertEquals(1L, UsageSessionCalculator.minutesCeil(1))
        assertEquals(1L, UsageSessionCalculator.minutesCeil(60_000))
        assertEquals(2L, UsageSessionCalculator.minutesCeil(60_001))
        // 30 aplikasi x 5 detik = 150 detik = 3 menit (bukan 30 menit)
        assertEquals(3L, UsageSessionCalculator.minutesCeil(30 * 5_000L))
    }
}
