package com.parentalcontrol.child.model

/**
 * Pengaturan perangkat dari ParentApp lewat `devices/leon_tablet/settings` (string JSON).
 * Kontrak ini diduplikasi di parent-app (model/DeviceSettings.kt) — ubah keduanya bersamaan.
 */
data class DeviceSettings(
    val locationEnabled: Boolean = false,
    // Beri tahu orang tua jika Device Admin dimatikan. Tidak memblokir uninstall (itu butuh Device Owner).
    val uninstallProtection: Boolean = false
)

object AlertTypes {
    const val LIMIT_REACHED = "LIMIT_REACHED"
    const val ADMIN_DISABLED = "ADMIN_DISABLED"
}
