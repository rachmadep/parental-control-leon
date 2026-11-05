package com.parentalcontrol.child.manager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BleFamilyTagTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `tag is four bytes and stable`() {
        assertEquals(4, BleFamilyTag.of("uid-test").size)
        assertArrayEquals(BleFamilyTag.of("uid-test"), BleFamilyTag.of("uid-test"))
    }

    @Test
    fun `tag matches known value shared with parent-app`() {
        // Kontrak dengan parent-app (SecurityLogicTest memeriksa nilai yang sama): SHA-256("uid-test")[0..4]
        assertEquals("62a5cbb7", hex(BleFamilyTag.of("uid-test")))
    }

    @Test
    fun `different families get different tags`() {
        assertFalse(BleFamilyTag.of("family-a").contentEquals(BleFamilyTag.of("family-b")))
    }
}
