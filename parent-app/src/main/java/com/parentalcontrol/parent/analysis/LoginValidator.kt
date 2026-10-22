package com.parentalcontrol.parent.analysis

object LoginValidator {

    private const val MIN_PASSWORD_LENGTH = 6 // batas minimum Firebase Auth

    /** Pesan error untuk form login, atau null jika input layak dikirim ke Firebase. */
    fun validate(email: String, password: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Isi email akun keluarga."
            !isPlausibleEmail(trimmed) -> "Format email tidak valid."
            password.isEmpty() -> "Isi password."
            password.length < MIN_PASSWORD_LENGTH -> "Password minimal $MIN_PASSWORD_LENGTH karakter."
            else -> null
        }
    }

    private fun isPlausibleEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at > 0 && at == email.lastIndexOf('@') && email.indexOf('.', at) > at + 1 && !email.endsWith(".")
    }
}
