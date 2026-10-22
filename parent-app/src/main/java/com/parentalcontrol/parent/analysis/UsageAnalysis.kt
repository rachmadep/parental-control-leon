package com.parentalcontrol.parent.analysis

import com.parentalcontrol.parent.model.CategoryKeys
import com.parentalcontrol.parent.model.DayHistory
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitsConfig
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class RangeStats(
    val totalMinutes: Long,
    val averagePerDay: Long,
    val categories: Map<String, Long>,
    val daysWithData: Int
)

enum class InsightLevel { OK, INFO, WARNING }

data class Insight(val level: InsightLevel, val title: String, val detail: String)

object UsageFormat {
    /** 150 -> "2j 30m", 45 -> "45m", 120 -> "2j 00m". */
    fun duration(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        return if (m >= 60) "${m / 60}j ${(m % 60).toString().padStart(2, '0')}m" else "${m}m"
    }
}

object UsageAnalysis {

    /** Ganti entri hari ini di riwayat dengan data live dari ringkasan agar selalu terbaru. */
    fun withLiveToday(history: List<DayHistory>, today: DeviceSummary, todayKey: String): List<DayHistory> {
        val live = DayHistory(
            date = todayKey,
            totalMinutes = today.totalScreenTimeMinutes,
            youtubeKidsMinutes = today.youtubeKidsMinutes,
            categories = today.categories
        )
        return (history.filter { it.date != todayKey } + live).sortedBy { it.date }
    }

    /** Entri dalam [days] hari kalender terakhir sampai [todayKey] (inklusif). */
    fun lastDays(history: List<DayHistory>, days: Int, todayKey: String): List<DayHistory> {
        val cutoff = LocalDate.parse(todayKey).minusDays((days - 1).toLong()).toString()
        return history.filter { it.date >= cutoff && it.date <= todayKey }.sortedBy { it.date }
    }

    /** Total per hari selama [days] hari terakhir; hari tanpa data bernilai 0. */
    fun dailyTotals(history: List<DayHistory>, days: Int, todayKey: String): List<Pair<String, Long>> {
        val byDate = history.associateBy { it.date }
        val end = LocalDate.parse(todayKey)
        return (days - 1 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong()).toString()
            date to (byDate[date]?.totalMinutes ?: 0L)
        }
    }

    fun stats(entries: List<DayHistory>): RangeStats {
        val total = entries.sumOf { it.totalMinutes }
        val categories = CategoryKeys.ALL.associateWith { key -> entries.sumOf { it.categories[key] ?: 0L } }
        return RangeStats(
            totalMinutes = total,
            averagePerDay = if (entries.isEmpty()) 0L else total / entries.size,
            categories = categories,
            daysWithData = entries.size
        )
    }

    /** Perubahan persen hari ini dibanding rata-rata hari sebelumnya; null jika tidak ada pembanding. */
    fun trendPercent(history: List<DayHistory>, todayKey: String, todayMinutes: Long): Int? {
        val previous = history.filter { it.date < todayKey && it.totalMinutes > 0 }
        if (previous.isEmpty()) return null
        val average = previous.sumOf { it.totalMinutes }.toDouble() / previous.size
        return (((todayMinutes - average) / average) * 100).roundToInt()
    }

    fun buildInsights(
        history: List<DayHistory>,
        today: DeviceSummary,
        config: LimitsConfig,
        todayKey: String
    ): List<Insight> {
        val insights = mutableListOf<Insight>()

        trendPercent(history, todayKey, today.totalScreenTimeMinutes)?.let { pct ->
            val level = when {
                pct >= 20 -> InsightLevel.WARNING
                pct <= -10 -> InsightLevel.OK
                else -> InsightLevel.INFO
            }
            val title = when {
                pct >= 10 -> "Waktu layar naik $pct%"
                pct <= -10 -> "Waktu layar turun ${abs(pct)}%"
                else -> "Waktu layar stabil"
            }
            insights.add(Insight(level, title, "Dibanding rata-rata hari-hari sebelumnya di riwayat."))
        }

        if (config.dailyLimitEnabled) {
            val remaining = config.effectiveDailyMinutes(todayKey) - today.limitUsedMinutes
            insights.add(
                when {
                    remaining <= 0 -> Insight(
                        InsightLevel.WARNING, "Batas harian tercapai",
                        "Tablet dikunci sampai besok, kecuali Anda menambah waktu."
                    )
                    remaining <= 15 -> Insight(
                        InsightLevel.WARNING, "Batas harian hampir habis",
                        "Sisa ${UsageFormat.duration(remaining)} hari ini."
                    )
                    else -> Insight(
                        InsightLevel.OK, "Dalam batas harian",
                        "Sisa ${UsageFormat.duration(remaining)} hari ini."
                    )
                }
            )
        }
        return insights
    }
}
