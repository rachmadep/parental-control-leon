package com.parentalcontrol.child.manager

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import com.parentalcontrol.child.model.AppCategoryClassifier
import com.parentalcontrol.child.model.AppUsageInfo
import java.util.Calendar

class AppUsageManager(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager: PackageManager = context.packageManager

    /** Aplikasi yang sedang di depan menurut pemanggilan getDailyUsageStats() terakhir. */
    @Volatile
    var foregroundPackage: String? = null
        private set

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

    /**
     * Pemakaian per aplikasi sejak 00:00, dihitung dari event UsageStats oleh [UsageSessionCalculator].
     * Aplikasi ini sendiri (layanan latar belakang dan overlay) tidak ikut dihitung atau ditampilkan.
     */
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

        val events = mutableListOf<UsageEventRecord>()
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            events.add(UsageEventRecord(event.timeStamp, event.packageName, event.className, event.eventType))
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val result = UsageSessionCalculator.compute(events, endTime, powerManager.isInteractive)
        foregroundPackage = result.foregroundPackage?.takeIf { it != context.packageName }

        return result.totalMs
            // Saring aplikasi yang dipakai lebih dari 1 detik, bisa dibuka pengguna, dan bukan aplikasi ini
            .filter { (pkg, ms) -> pkg != context.packageName && ms >= 1000L && isLaunchableApp(pkg) }
            .map { (pkg, ms) ->
                AppUsageInfo(
                    packageName = pkg,
                    appName = getAppName(pkg),
                    totalTimeInForegroundMs = ms,
                    lastTimeUsed = result.lastUsed[pkg] ?: 0L,
                    category = getCategory(pkg)
                )
            }
            // Urutkan dari aplikasi yang paling lama digunakan
            .sortedByDescending { it.totalTimeInForegroundMs }
    }

    private fun isLaunchableApp(packageName: String): Boolean {
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }

    private fun getCategory(packageName: String): String {
        val androidCategory = try {
            packageManager.getApplicationInfo(packageName, 0).category
        } catch (e: Exception) {
            -1
        }
        return AppCategoryClassifier.classify(packageName, androidCategory).key
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
