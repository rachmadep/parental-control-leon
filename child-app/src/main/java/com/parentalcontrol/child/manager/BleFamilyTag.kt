package com.parentalcontrol.child.manager

import java.security.MessageDigest

/**
 * Penanda 4 byte yang diiklankan tablet lewat BLE (service data) agar HP orang tua hanya
 * menyambung ke tablet keluarganya sendiri saat banyak pasangan berdekatan. Diturunkan dari UID akun
 * keluarga. Ini penyaring, bukan autentikasi. Diduplikasi di parent-app (analysis/SecurityLogic.kt).
 */
object BleFamilyTag {

    fun of(uid: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8)).copyOf(4)
}
