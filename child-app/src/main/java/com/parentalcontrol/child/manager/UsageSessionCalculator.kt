package com.parentalcontrol.child.manager

/** Nilai `UsageEvents.Event.*` (disalin agar logika ini bebas dependensi Android). */
object UsageEventTypes {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val DEVICE_SHUTDOWN = 26
}

data class UsageEventRecord(
    val timestamp: Long,
    val packageName: String,
    val className: String?,
    val type: Int
)

data class UsageResult(
    val totalMs: Map<String, Long>,
    /** Terakhir kali aplikasi dipakai: waktu sesi terakhir berakhir (atau sekarang jika masih terbuka). */
    val lastUsed: Map<String, Long>,
    /** Aplikasi yang sedang di depan sekarang; null jika layar mati atau tidak ada. */
    val foregroundPackage: String?
)

/**
 * Menghitung durasi pemakaian per aplikasi dari urutan event UsageStats.
 *
 * Tidak memakai `queryAndAggregateUsageStats`, dan tidak percaya bahwa setiap `ACTIVITY_RESUMED` pasti
 * disusul `ACTIVITY_PAUSED`. Event PAUSED bisa hilang (layar mati, proses dimatikan sistem, perilaku OEM),
 * dan sesi yang "menggantung" akan terus bertambah tiap sinkronisasi sampai berjam-jam. Karena itu:
 *  - hanya satu aplikasi yang dianggap di depan; RESUMED aplikasi lain menutup sesi sebelumnya,
 *  - layar mati, layar kunci muncul, atau perangkat mati menutup sesi pada saat itu,
 *  - sesi yang masih terbuka di akhir hanya dihitung jika layar sedang menyala sekarang.
 * Dengan begitu jumlah semua aplikasi tidak pernah melebihi waktu yang benar-benar berlalu.
 */
object UsageSessionCalculator {

    fun compute(events: List<UsageEventRecord>, endTime: Long, screenInteractiveNow: Boolean): UsageResult {
        val totals = mutableMapOf<String, Long>()
        val lastUsed = mutableMapOf<String, Long>()

        var openPackage: String? = null
        var openSince = 0L
        val resumedActivities = mutableSetOf<String>()

        fun touch(pkg: String, at: Long) {
            lastUsed[pkg] = maxOf(lastUsed[pkg] ?: 0L, at)
        }

        fun closeSession(at: Long) {
            val pkg = openPackage ?: return
            totals[pkg] = (totals[pkg] ?: 0L) + (at - openSince).coerceAtLeast(0L)
            touch(pkg, at)
            openPackage = null
            resumedActivities.clear()
        }

        for (event in events.sortedBy { it.timestamp }) {
            val activityKey = "${event.packageName}/${event.className}"
            when (event.type) {
                UsageEventTypes.ACTIVITY_RESUMED -> {
                    if (openPackage != null && openPackage != event.packageName) closeSession(event.timestamp)
                    if (openPackage == null) {
                        openPackage = event.packageName
                        openSince = event.timestamp
                    }
                    resumedActivities.add(activityKey)
                    touch(event.packageName, event.timestamp)
                }

                UsageEventTypes.ACTIVITY_PAUSED -> {
                    // Pindah antar activity dalam satu aplikasi: PAUSED activity lama datang sebelum
                    // RESUMED activity baru, jadi sesi baru ditutup bila tidak ada activity yang resumed
                    if (openPackage == event.packageName) {
                        resumedActivities.remove(activityKey)
                        if (resumedActivities.isEmpty()) closeSession(event.timestamp)
                    }
                }

                UsageEventTypes.SCREEN_NON_INTERACTIVE,
                UsageEventTypes.KEYGUARD_SHOWN,
                UsageEventTypes.DEVICE_SHUTDOWN -> closeSession(event.timestamp)
            }
        }

        val foreground = if (screenInteractiveNow) openPackage else null
        if (foreground != null) closeSession(endTime)
        // Layar mati tapi sesi masih "terbuka" (event layar mati tidak tercatat): jangan dihitung

        return UsageResult(totals, lastUsed, foreground)
    }

    /** Pembulatan ke atas dari total milidetik, bukan per aplikasi, agar puluhan aplikasi sekejap tidak membengkak. */
    fun minutesCeil(ms: Long): Long = (ms.coerceAtLeast(0L) + 59_999L) / 60_000L
}
