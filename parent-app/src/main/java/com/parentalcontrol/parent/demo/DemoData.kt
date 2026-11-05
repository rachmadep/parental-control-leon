package com.parentalcontrol.parent.demo

import com.parentalcontrol.parent.model.AppUsageItem
import com.parentalcontrol.parent.model.DayHistory
import com.parentalcontrol.parent.model.DeviceSettings
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitsConfig
import com.parentalcontrol.parent.model.TimeWindow
import java.time.LocalDate

/**
 * Data contoh untuk mode demo (build debug saja, lihat DemoMode): dipakai untuk merekam video/screenshot
 * tanpa akun Firebase dan tanpa data anak sungguhan. Semua angka dibuat konsisten satu sama lain.
 */
object DemoData {

    const val CHILD_NAME = "Raka"
    private const val MIN = 60_000L

    private fun app(pkg: String, name: String, minutes: Long, category: String, lastUsed: Long) = AppUsageItem(
        packageName = pkg,
        appName = name,
        totalTimeInForegroundMs = minutes * MIN,
        durationMinutes = minutes,
        lastTimeUsed = lastUsed,
        category = category
    )

    fun summary(now: Long): DeviceSummary {
        val apps = listOf(
            app(LimitsConfig.YOUTUBE_KIDS_PACKAGE, "YouTube Kids", 52, "hiburan", now - 4 * MIN),
            app("com.lingokids.demo", "Lingokids", 28, "edukasi", now - 65 * MIN),
            app("com.studio.coloring.demo", "Coloring Games", 9, "game", now - 130 * MIN),
            app("com.android.vending", "Google Play Store", 5, "lainnya", now - 190 * MIN)
        )
        val total = apps.sumOf { it.durationMinutes }
        return DeviceSummary(
            deviceId = "demo",
            timestamp = now,
            totalScreenTimeMinutes = total,
            youtubeKidsMinutes = 52,
            limitUsedMinutes = total,
            currentVolumePercent = 40,
            currentBrightnessPercent = 60,
            appList = apps,
            categories = apps.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.durationMinutes } },
            deviceModel = "Tablet Demo",
            androidVersion = "14",
            batteryPercent = 72,
            deviceAdminActive = true
        )
    }

    /** Enam hari sebelum [todayKey], urut dari yang terlama. */
    fun history(todayKey: String): List<DayHistory> {
        val totals = listOf(88L, 120L, 64L, 150L, 97L, 110L)
        val today = LocalDate.parse(todayKey)
        return totals.mapIndexed { index, total ->
            val edu = total * 30 / 100
            val game = total * 10 / 100
            val other = total * 5 / 100
            val fun_ = total - edu - game - other
            DayHistory(
                date = today.minusDays((totals.size - index).toLong()).toString(),
                totalMinutes = total,
                youtubeKidsMinutes = fun_ * 80 / 100,
                categories = mapOf("edukasi" to edu, "hiburan" to fun_, "game" to game, "lainnya" to other)
            )
        }
    }

    fun config() = LimitsConfig(
        dailyLimitEnabled = true,
        dailyLimitMinutes = 120,
        appLimits = LimitsConfig.defaultRules().map { if (it.id == "youtube_kids") it.copy(minutes = 60) else it },
        bedtime = TimeWindow(enabled = true, start = "20:00", end = "06:00"),
        study = TimeWindow(enabled = false, start = "14:00", end = "16:00", days = listOf(2, 3, 4, 5, 6))
    )

    fun settings() = DeviceSettings(childName = CHILD_NAME, locationEnabled = false, uninstallProtection = true)
}
