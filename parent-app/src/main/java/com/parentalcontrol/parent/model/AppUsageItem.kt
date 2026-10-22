package com.parentalcontrol.parent.model

data class AppUsageItem(
    val packageName: String = "",
    val appName: String = "",
    val totalTimeInForegroundMs: Long = 0L,
    val durationMinutes: Long = 0L,
    val lastTimeUsed: Long = 0L,
    val category: String = "lainnya",
    val isYoutubeKids: Boolean = packageName == "com.google.android.apps.youtube.kids"
)

data class DeviceSummary(
    val deviceId: String = "",
    val timestamp: Long = 0L,
    val totalScreenTimeMinutes: Long = 0L,
    val youtubeKidsMinutes: Long = 0L,
    /** Menit yang dihitung ke batas harian di tablet (tanpa pemakaian aplikasi child itu sendiri). */
    val limitUsedMinutes: Long = 0L,
    val isLocked: Boolean = false,
    val currentVolumePercent: Int = 50,
    val currentBrightnessPercent: Int = 60,
    val appList: List<AppUsageItem> = emptyList(),
    val categories: Map<String, Long> = emptyMap() // key kategori -> menit
)

/** Ringkasan satu hari dari `devices/leon_tablet/history/{yyyy-MM-dd}`. */
data class DayHistory(
    val date: String = "",
    val totalMinutes: Long = 0L,
    val youtubeKidsMinutes: Long = 0L,
    val categories: Map<String, Long> = emptyMap()
)

object CategoryKeys {
    const val EDUCATION = "edukasi"
    const val ENTERTAINMENT = "hiburan"
    const val GAME = "game"
    const val OTHER = "lainnya"
    val ALL = listOf(EDUCATION, ENTERTAINMENT, GAME, OTHER)
}
