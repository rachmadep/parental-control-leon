package com.parentalcontrol.child.model

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMs: Long,
    val lastTimeUsed: Long,
    val category: String = AppCategory.OTHER.key,
    val isYoutubeKids: Boolean = packageName == "com.google.android.apps.youtube.kids" ||
                                 packageName == "com.google.android.youtube" ||
                                 packageName.contains("youtube", ignoreCase = true)
) {
    // Custom getter agar nilai selalu dihitung ulang dari totalTimeInForegroundMs yang dinamis
    val durationMinutes: Long
        get() = totalTimeInForegroundMs / (1000 * 60)
}

data class DeviceUsageSummary(
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalScreenTimeMinutes: Long,
    val youtubeKidsMinutes: Long,
    val isLocked: Boolean,
    val currentVolumePercent: Int,
    val currentBrightnessPercent: Int,
    val appList: List<AppUsageInfo>,
    val categories: Map<String, Long> = emptyMap(), // key AppCategory -> menit
    val limitUsedMinutes: Long = 0L // menit yang dihitung ke batas harian (tanpa aplikasi child ini)
)
