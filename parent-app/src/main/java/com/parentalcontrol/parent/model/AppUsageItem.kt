package com.parentalcontrol.parent.model

data class AppUsageItem(
    val packageName: String = "",
    val appName: String = "",
    val totalTimeInForegroundMs: Long = 0L,
    val durationMinutes: Long = 0L,
    val lastTimeUsed: Long = 0L,
    val isYoutubeKids: Boolean = packageName == "com.google.android.apps.youtube.kids"
)

data class DeviceSummary(
    val deviceId: String = "",
    val timestamp: Long = 0L,
    val totalScreenTimeMinutes: Long = 0L,
    val youtubeKidsMinutes: Long = 0L,
    val isLocked: Boolean = false,
    val currentVolumePercent: Int = 50,
    val currentBrightnessPercent: Int = 60,
    val appList: List<AppUsageItem> = emptyList()
)
