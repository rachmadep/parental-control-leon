package com.parentalcontrol.child.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockOverlayPolicyTest {

    @Test
    fun `nothing is evaluated while the screen is off`() {
        assertFalse(LockOverlayPolicy.shouldEvaluate(screenInteractive = false))
        assertTrue(LockOverlayPolicy.shouldEvaluate(screenInteractive = true))
    }

    @Test
    fun `overlay never wakes a sleeping screen`() {
        assertFalse(LockOverlayPolicy.shouldLaunchOverlay(violation = true, screenInteractive = false, overlayVisible = false))
    }

    @Test
    fun `overlay is launched when violating with the screen on and overlay not in front`() {
        assertTrue(LockOverlayPolicy.shouldLaunchOverlay(violation = true, screenInteractive = true, overlayVisible = false))
    }

    // Regresi: anak menekan Home -> overlay masih hidup tetapi tidak terlihat -> harus muncul lagi.
    @Test
    fun `overlay returns after the child leaves it with the home button`() {
        assertTrue(LockOverlayPolicy.shouldLaunchOverlay(violation = true, screenInteractive = true, overlayVisible = false))
    }

    @Test
    fun `overlay already in front is not launched again`() {
        assertFalse(LockOverlayPolicy.shouldLaunchOverlay(violation = true, screenInteractive = true, overlayVisible = true))
    }

    @Test
    fun `no violation means no overlay`() {
        assertFalse(LockOverlayPolicy.shouldLaunchOverlay(violation = false, screenInteractive = true, overlayVisible = false))
    }
}
