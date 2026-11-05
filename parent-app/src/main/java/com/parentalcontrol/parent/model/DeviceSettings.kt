package com.parentalcontrol.parent.model

/**
 * Pengaturan perangkat yang ditulis ke `devices/{UID}/settings` sebagai string JSON (Gson).
 * Kontrak ini diduplikasi di child-app (model/DeviceSettings.kt) — ubah keduanya bersamaan.
 */
data class DeviceSettings(
    val childName: String = "", // nama anak untuk teks layar istirahat; kosong = teks generik
    val locationEnabled: Boolean = false,
    // Peringatan jika Device Admin tablet dimatikan. Tidak memblokir uninstall (itu butuh Device Owner).
    val uninstallProtection: Boolean = false
)

/** Salt + hash PIN orang tua di `devices/{UID}/pin`. Null = masih PIN bawaan. */
data class PinCredential(val salt: String, val hash: String)

/** Lokasi terakhir tablet di `devices/{UID}/location`. */
data class DeviceLocation(
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val provider: String,
    val timestamp: Long
)

/** Peringatan terakhir dari tablet di `devices/{UID}/lastAlert`. */
data class DeviceAlert(val id: String, val type: String, val message: String, val timestamp: Long)

object AlertTypes {
    const val LIMIT_REACHED = "LIMIT_REACHED"
    const val ADMIN_DISABLED = "ADMIN_DISABLED"
}
