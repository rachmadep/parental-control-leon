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

    /** Hanya email (untuk "Lupa password"). */
    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Isi email akun keluarga."
            !isPlausibleEmail(trimmed) -> "Format email tidak valid."
            else -> null
        }
    }

    /** Validasi pendaftaran: sama seperti masuk, ditambah konfirmasi password agar salah ketik tidak mengunci akun. */
    fun validateSignUp(email: String, password: String, confirm: String): String? =
        validate(email, password) ?: if (password != confirm) "Konfirmasi password tidak cocok." else null

    private fun isPlausibleEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at > 0 && at == email.lastIndexOf('@') && email.indexOf('.', at) > at + 1 && !email.endsWith(".")
    }
}
