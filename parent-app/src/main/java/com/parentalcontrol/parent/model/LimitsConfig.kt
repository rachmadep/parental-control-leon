package com.parentalcontrol.parent.model

/**
 * Konfigurasi batas waktu yang ditulis ke `devices/leon_tablet/config` sebagai satu string JSON (Gson).
 * Kontrak ini diduplikasi di child-app (model/LimitsConfig.kt) — ubah keduanya bersamaan.
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
    val appLimits: List<LimitRule> = defaultRules(),
    val bedtime: TimeWindow = TimeWindow(enabled = false, start = "20:00", end = "06:00"),
    val study: TimeWindow = TimeWindow(
        enabled = false, start = "14:00", end = "16:00", days = listOf(2, 3, 4, 5, 6)
    )
) {
    /** Batas harian efektif untuk [dateKey], sudah termasuk bonus waktu hari itu. */
    fun effectiveDailyMinutes(dateKey: String): Int =
        dailyLimitMinutes + if (bonusDate == dateKey) bonusMinutes else 0

    companion object {
        const val YOUTUBE_KIDS_PACKAGE = "com.google.android.apps.youtube.kids"

        // Semua default "tanpa batas" agar tampilan tidak menjanjikan batas yang belum aktif di tablet
        fun defaultRules() = listOf(
            LimitRule("youtube_kids", "YouTube Kids", LimitRule.TYPE_PACKAGE, YOUTUBE_KIDS_PACKAGE, 0),
            LimitRule("fun", "Game & Hiburan", LimitRule.TYPE_CATEGORY, "game,hiburan", 0),
            LimitRule("edu", "Aplikasi Edukasi", LimitRule.TYPE_CATEGORY, "edukasi", 0)
        )
    }
}
