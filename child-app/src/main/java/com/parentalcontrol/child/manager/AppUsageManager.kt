package com.parentalcontrol.child.manager

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.parentalcontrol.child.model.AppUsageInfo
import java.util.Calendar

class AppUsageManager(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager: PackageManager = context.packageManager

    fun isPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getDailyUsageStats(): List<AppUsageInfo> {
        if (!isPermissionGranted() || usageStatsManager == null) {
            return emptyList()
        }

        // Hitung range waktu dari jam 00:00 hari ini sampai sekarang
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val totalTimeMap = mutableMapOf<String, Long>()
        val lastUsedMap = mutableMapOf<String, Long>()

        for ((pkgName, usageStat) in statsMap) {
            totalTimeMap[pkgName] = usageStat.totalTimeInForeground
            lastUsedMap[pkgName] = usageStat.lastTimeUsed
        }

        // Hitung durasi sesi aktif real-time dari aplikasi yang sedang terbuka saat ini menggunakan UsageEvents
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val foregroundStartTimes = mutableMapOf<String, Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    foregroundStartTimes[pkg] = event.timeStamp
                    lastUsedMap[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    foregroundStartTimes.remove(pkg)
                }
            }
        }

        // Jika ada aplikasi yang saat ini sedang aktif terbuka (belum dipause)
        for ((pkg, fgStartTime) in foregroundStartTimes) {
            val activeMs = (endTime - fgStartTime).coerceAtLeast(0L)
            val currentTotal = totalTimeMap[pkg] ?: 0L
            totalTimeMap[pkg] = currentTotal + activeMs
        }

        val resultList = mutableListOf<AppUsageInfo>()

        for ((pkgName, totalTimeMs) in totalTimeMap) {
            // Saring aplikasi yang durasi pemakaiannya lebih dari 1 detik dan merupakan aplikasi pengguna
            if (totalTimeMs >= 1000L && isLaunchableApp(pkgName)) {
                val appName = getAppName(pkgName)
                val lastUsed = lastUsedMap[pkgName] ?: 0L
                resultList.add(
                    AppUsageInfo(
                        packageName = pkgName,
                        appName = appName,
                        totalTimeInForegroundMs = totalTimeMs,
                        lastTimeUsed = lastUsed
                    )
                )
            }
        }

        // Urutkan dari aplikasi yang paling lama digunakan
        return resultList.sortedByDescending { it.totalTimeInForegroundMs }
    }

    fun getYoutubeKidsUsageMinutes(): Long {
        val stats = getDailyUsageStats()
        return stats.filter { it.isYoutubeKids }.sumOf { Math.ceil(it.totalTimeInForegroundMs / 60000.0).toLong() }
    }

    fun getTotalScreenTimeMinutes(): Long {
        val stats = getDailyUsageStats()
        // Menggunakan Math.ceil agar pemakaian beberapa detik/milidetik tetap dihitung sebagai 1 menit
        return stats.sumOf { Math.ceil(it.totalTimeInForegroundMs / 60000.0).toLong() }
    }

    private fun isLaunchableApp(packageName: String): Boolean {
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
