package com.parentalcontrol.parent.model

/**
 * Pengaturan perangkat yang ditulis ke `devices/leon_tablet/settings` sebagai string JSON (Gson).
 * Kontrak ini diduplikasi di child-app (model/DeviceSettings.kt) — ubah keduanya bersamaan.
 */
data class DeviceSettings(
    val locationEnabled: Boolean = false,
    // Peringatan jika Device Admin tablet dimatikan. Tidak memblokir uninstall (itu butuh Device Owner).
    val uninstallProtection: Boolean = false
)

/** Salt + hash PIN orang tua di `devices/leon_tablet/pin`. Null = masih PIN bawaan. */
data class PinCredential(val salt: String, val hash: String)

/** Lokasi terakhir tablet di `devices/leon_tablet/location`. */
data class DeviceLocation(
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val provider: String,
    val timestamp: Long
)

/** Peringatan terakhir dari tablet di `devices/leon_tablet/lastAlert`. */
data class DeviceAlert(val id: String, val type: String, val message: String, val timestamp: Long)

object AlertTypes {
    const val LIMIT_REACHED = "LIMIT_REACHED"
    const val ADMIN_DISABLED = "ADMIN_DISABLED"
}
