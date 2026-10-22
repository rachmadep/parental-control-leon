package com.parentalcontrol.child.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `correct pin verifies`() {
        val hash = PinHasher.hash("1234", "abc")
        assertTrue(PinHasher.verify("1234", "abc", hash))
    }

    @Test
    fun `wrong pin or wrong salt fails`() {
        val hash = PinHasher.hash("1234", "abc")
        assertFalse(PinHasher.verify("1235", "abc", hash))
        assertFalse(PinHasher.verify("1234", "abd", hash))
    }

    @Test
    fun `same pin with different salts gives different hashes`() {
        assertNotEquals(PinHasher.hash("1234", "a"), PinHasher.hash("1234", "b"))
    }

    @Test
    fun `hash matches known value shared with parent-app`() {
        // Kontrak dengan parent-app (SecurityLogicTest memeriksa nilai yang sama): SHA-256("salt:1234")
        assertEquals("35c56314f89066056408d6d269daf9e69217b9953215f286eec86cec3076271a", PinHasher.hash("1234", "salt"))
    }
}
