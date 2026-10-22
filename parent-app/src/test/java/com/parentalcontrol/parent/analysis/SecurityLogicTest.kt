package com.parentalcontrol.parent.analysis

import com.parentalcontrol.parent.model.DeviceAlert
import com.parentalcontrol.parent.model.PinCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLogicTest {

    private fun credential(pin: String, salt: String = "s") = PinCredential(salt, PinHasher.hash(pin, salt))

    private fun alert(ts: Long) = DeviceAlert("id", "LIMIT_REACHED", "msg", ts)

    // --- PinHasher (harus sama dengan child-app) ---

    @Test
    fun `pin hash is salted sha256 hex`() {
        val hash = PinHasher.hash("1234", "abc")
        assertEquals(64, hash.length)
        assertTrue(PinHasher.verify("1234", "abc", hash))
        assertFalse(PinHasher.verify("1235", "abc", hash))
        assertFalse(PinHasher.verify("1234", "abd", hash))
    }

    @Test
    fun `hash matches known value shared with child-app`() {
        // Kontrak dengan child-app (PinHasherTest memeriksa nilai yang sama): SHA-256("salt:1234")
        assertEquals("35c56314f89066056408d6d269daf9e69217b9953215f286eec86cec3076271a", PinHasher.hash("1234", "salt"))
    }

    // --- PinChange ---

    @Test
    fun `default pin accepted as old pin when no credential`() {
        assertNull(PinChange.validate("1234", "4321", "4321", null))
        assertEquals("PIN lama salah", PinChange.validate("0000", "4321", "4321", null))
    }

    @Test
    fun `old pin verified against stored credential`() {
        val c = credential("5555")
        assertNull(PinChange.validate("5555", "4321", "4321", c))
        assertEquals("PIN lama salah", PinChange.validate("1234", "4321", "4321", c))
    }

    @Test
    fun `new pin must be four digits`() {
        assertEquals("PIN baru harus 4 digit angka", PinChange.validate("1234", "123", "123", null))
        assertEquals("PIN baru harus 4 digit angka", PinChange.validate("1234", "12345", "12345", null))
        assertEquals("PIN baru harus 4 digit angka", PinChange.validate("1234", "12a4", "12a4", null))
    }

    @Test
    fun `confirmation must match and pin must change`() {
        assertEquals("Konfirmasi PIN tidak cocok", PinChange.validate("1234", "4321", "4322", null))
        assertEquals("PIN baru harus berbeda dari PIN lama", PinChange.validate("1234", "1234", "1234", null))
    }

    // --- AlertTracker ---

    @Test
    fun `first observed alert only becomes the baseline`() {
        val d = AlertTracker.decide(alert(1000), lastSeen = -1, enabled = true)
        assertFalse(d.notify)
        assertEquals(1000L, d.newLastSeen)
    }

    @Test
    fun `newer alert notifies and older does not`() {
        assertTrue(AlertTracker.decide(alert(2000), lastSeen = 1000, enabled = true).notify)
        assertFalse(AlertTracker.decide(alert(1000), lastSeen = 1000, enabled = true).notify)
        assertFalse(AlertTracker.decide(alert(500), lastSeen = 1000, enabled = true).notify)
    }

    @Test
    fun `disabled notifications still advance last seen`() {
        val d = AlertTracker.decide(alert(3000), lastSeen = 1000, enabled = false)
        assertFalse(d.notify)
        assertEquals(3000L, d.newLastSeen)
    }

    // --- AppLockPolicy ---

    @Test
    fun `lock policy`() {
        val now = 1_000_000L
        assertFalse(AppLockPolicy.shouldLock(enabled = false, unlockedOnce = false, lastActiveAt = 0, now = now))
        assertTrue(AppLockPolicy.shouldLock(enabled = true, unlockedOnce = false, lastActiveAt = 0, now = now))
        assertFalse(AppLockPolicy.shouldLock(true, true, lastActiveAt = now - 30_000, now = now))
        assertTrue(AppLockPolicy.shouldLock(true, true, lastActiveAt = now - 61_000, now = now))
    }

    // --- RelativeTime ---

    @Test
    fun `relative time buckets`() {
        val now = 10_000_000_000L
        assertEquals("baru saja", RelativeTime.format(now, now - 30_000))
        assertEquals("baru saja", RelativeTime.format(now, now + 5_000))
        assertEquals("12 menit lalu", RelativeTime.format(now, now - 12 * 60_000L))
        assertEquals("3 jam lalu", RelativeTime.format(now, now - 3 * 3_600_000L))
        assertEquals("2 hari lalu", RelativeTime.format(now, now - 2 * 86_400_000L))
    }
}
