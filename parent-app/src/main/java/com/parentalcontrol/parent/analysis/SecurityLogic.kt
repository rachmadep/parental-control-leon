package com.parentalcontrol.parent.analysis

import com.parentalcontrol.parent.model.DeviceAlert
import com.parentalcontrol.parent.model.PinCredential
import java.security.MessageDigest

/**
 * Hash PIN dengan salt (SHA-256). PIN 4 digit hanya punya 10.000 kemungkinan, jadi ini mencegah PIN
 * terbaca polos di database, bukan melawan brute force. Diduplikasi di child-app (manager/PinHasher.kt).
 */
object PinHasher {

    fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())
}

object PinChange {

    const val DEFAULT_PIN = "1234" // sama dengan ChildApp.DEFAULT_PIN

    /** Pesan error untuk dialog ganti PIN, atau null jika semua input valid. */
    fun validate(old: String, new: String, confirm: String, credential: PinCredential?): String? {
        val oldOk = if (credential != null) {
            PinHasher.verify(old, credential.salt, credential.hash)
        } else {
            old == DEFAULT_PIN
        }
        return when {
            !oldOk -> "PIN lama salah"
            new.length != 4 || !new.all { it.isDigit() } -> "PIN baru harus 4 digit angka"
            new != confirm -> "Konfirmasi PIN tidak cocok"
            new == old -> "PIN baru harus berbeda dari PIN lama"
            else -> null
        }
    }
}

/** Memutuskan apakah peringatan dari tablet perlu ditampilkan sebagai notifikasi. */
object AlertTracker {

    data class Decision(val notify: Boolean, val newLastSeen: Long)

    /**
     * @param lastSeen timestamp peringatan terakhir yang sudah dilihat; negatif = belum pernah
     * (peringatan pertama yang teramati hanya jadi acuan, supaya peringatan lama tidak membanjir).
     */
    fun decide(alert: DeviceAlert, lastSeen: Long, enabled: Boolean): Decision = Decision(
        notify = enabled && lastSeen >= 0 && alert.timestamp > lastSeen,
        // Tetap maju saat notifikasi dimatikan, agar mengaktifkannya kembali tidak memunculkan peringatan lama
        newLastSeen = maxOf(lastSeen, alert.timestamp)
    )
}

/** Kunci aplikasi parent: kapan perlu autentikasi ulang. */
object AppLockPolicy {

    const val RELOCK_AFTER_MS = 60_000L

    fun shouldLock(enabled: Boolean, unlockedOnce: Boolean, lastActiveAt: Long, now: Long): Boolean =
        enabled && (!unlockedOnce || now - lastActiveAt > RELOCK_AFTER_MS)
}

object RelativeTime {

    fun format(nowMs: Long, thenMs: Long): String {
        val minutes = (nowMs - thenMs) / 60_000L
        return when {
            minutes < 1 -> "baru saja"
            minutes < 60 -> "$minutes menit lalu"
            minutes < 24 * 60 -> "${minutes / 60} jam lalu"
            else -> "${minutes / (24 * 60)} hari lalu"
        }
    }
}
