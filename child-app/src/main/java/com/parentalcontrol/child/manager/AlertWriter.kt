package com.parentalcontrol.child.manager

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

/**
 * Menulis peringatan terbaru ke `devices/{UID}/lastAlert` agar ParentApp bisa menampilkan notifikasi.
 * Hanya satu peringatan terakhir yang disimpan (ditimpa), sehingga database tidak membengkak.
 */
object AlertWriter {

    private const val MIN_REPEAT_INTERVAL_MS = 5 * 60_000L
    private val lastSentAt = mutableMapOf<String, Long>()

    @Synchronized
    fun send(type: String, message: String) {
        // Database dilindungi aturan auth: tanpa akun masuk penulisan pasti ditolak
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val now = System.currentTimeMillis()
        // Cegah spam: jenis peringatan yang sama tidak dikirim ulang dalam 5 menit
        if (now - (lastSentAt[type] ?: 0L) < MIN_REPEAT_INTERVAL_MS) return
        lastSentAt[type] = now

        FirebaseDatabase.getInstance().getReference("devices").child(uid).child("lastAlert").setValue(
            mapOf(
                "id" to UUID.randomUUID().toString(),
                "type" to type,
                "message" to message,
                "timestamp" to now
            )
        )
    }
}
