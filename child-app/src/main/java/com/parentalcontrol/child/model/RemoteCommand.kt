package com.parentalcontrol.child.model

enum class CommandType {
    LOCK_NOW,          // Mengunci layar perangkat secara langsung
    UNLOCK,            // Membuka kunci overlay
    SET_VOLUME,        // Mengatur volume media (0 - 100)
    SET_BRIGHTNESS,    // Mengatur kecerahan layar (0 - 100)
    SHOW_LOCK_OVERLAY, // Menampilkan layar istirahat
    HIDE_LOCK_OVERLAY, // Menghilangkan layar istirahat
    REQUEST_SYNC       // Meminta tablet mengirim data statistik penggunaan terbaru
}

data class RemoteCommand(
    val type: CommandType,
    val intValue: Int? = null,
    val strValue: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
