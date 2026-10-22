package com.parentalcontrol.parent.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginValidatorTest {

    @Test
    fun `valid credentials pass`() {
        assertNull(LoginValidator.validate("keluarga@example.com", "rahasia123"))
        assertNull(LoginValidator.validate("  keluarga@example.com  ", "rahasia123"))
    }

    @Test
    fun `blank email is rejected`() {
        assertEquals("Isi email akun keluarga.", LoginValidator.validate("   ", "rahasia123"))
    }

    @Test
    fun `malformed emails are rejected`() {
        val msg = "Format email tidak valid."
        assertEquals(msg, LoginValidator.validate("keluarga", "rahasia123"))
        assertEquals(msg, LoginValidator.validate("@example.com", "rahasia123"))
        assertEquals(msg, LoginValidator.validate("a@b@example.com", "rahasia123"))
        assertEquals(msg, LoginValidator.validate("a@example", "rahasia123"))
        assertEquals(msg, LoginValidator.validate("a@example.", "rahasia123"))
    }

    @Test
    fun `password rules`() {
        assertEquals("Isi password.", LoginValidator.validate("a@example.com", ""))
        assertEquals("Password minimal 6 karakter.", LoginValidator.validate("a@example.com", "12345"))
        assertNull(LoginValidator.validate("a@example.com", "123456"))
    }
}
