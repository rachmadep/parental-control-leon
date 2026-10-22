package com.parentalcontrol.child.model

/**
 * Konfigurasi batas waktu yang dikirim ParentApp lewat `devices/leon_tablet/config` (string JSON).
 * Kontrak ini diduplikasi di parent-app (model/LimitsConfig.kt) — ubah keduanya bersamaan.
 */
data class TimeWindow(
    val enabled: Boolean = false,
    val start: String = "20:00", // HH:mm
    val end: String = "06:00",   // HH:mm, boleh lewat tengah malam
    val days: List<Int> = emptyList() // Calendar.DAY_OF_WEEK (1=Minggu..7=Sabtu); kosong = setiap hari
)

data class LimitRule(
    val id: String = "",
    val label: String = "",
    val type: String = TYPE_CATEGORY,
    val target: String = "", // package name, atau daftar key kategori dipisah koma
    val minutes: Int = 0     // 0 = tanpa batas
) {
    companion object {
        const val TYPE_PACKAGE = "PACKAGE"
        const val TYPE_CATEGORY = "CATEGORY"
    }
}

data class LimitsConfig(
    val dailyLimitEnabled: Boolean = false,
    val dailyLimitMinutes: Int = 120,
    val bonusDate: String = "", // yyyy-MM-dd, bonus hanya berlaku di tanggal ini
    val bonusMinutes: Int = 0,
    val appLimits: List<LimitRule> = emptyList(),
    val bedtime: TimeWindow = TimeWindow(enabled = false, start = "20:00", end = "06:00"),
    val study: TimeWindow = TimeWindow(
        enabled = false, start = "14:00", end = "16:00", days = listOf(2, 3, 4, 5, 6)
    )
)
