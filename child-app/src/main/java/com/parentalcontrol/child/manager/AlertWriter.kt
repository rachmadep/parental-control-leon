package com.parentalcontrol.child.manager

import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

/**
 * Menulis peringatan terbaru ke `devices/{id}/lastAlert` agar ParentApp bisa menampilkan notifikasi.
 * Hanya satu peringatan terakhir yang disimpan (ditimpa), sehingga database tidak membengkak.
 */
object AlertWriter {

    private const val MIN_REPEAT_INTERVAL_MS = 5 * 60_000L
    private val lastSentAt = mutableMapOf<String, Long>()

    @Synchronized
    fun send(deviceId: String, type: String, message: String) {
        val now = System.currentTimeMillis()
        // Cegah spam: jenis peringatan yang sama tidak dikirim ulang dalam 5 menit
        if (now - (lastSentAt[type] ?: 0L) < MIN_REPEAT_INTERVAL_MS) return
        lastSentAt[type] = now

        FirebaseDatabase.getInstance().getReference("devices").child(deviceId).child("lastAlert").setValue(
            mapOf(
                "id" to UUID.randomUUID().toString(),
                "type" to type,
                "message" to message,
                "timestamp" to now
            )
        )
    }
}
