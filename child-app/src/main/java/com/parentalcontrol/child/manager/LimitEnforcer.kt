package com.parentalcontrol.child.manager

import com.parentalcontrol.child.model.AppCategory
import com.parentalcontrol.child.model.LimitRule
import com.parentalcontrol.child.model.LimitsConfig
import com.parentalcontrol.child.model.TimeWindow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class UsageSnapshot(
    val totalMs: Long,
    val packageMs: Map<String, Long>,
    val categoryMs: Map<String, Long>,
    val foregroundPackage: String?,
    val foregroundCategory: String?
)

data class ClockState(val dateKey: String, val dayOfWeek: Int, val minuteOfDay: Int)

data class Violation(val reason: String)

/**
 * Logika murni (tanpa Android) untuk memutuskan apakah tablet harus dikunci sekarang.
 * Urutan prioritas: jam tidur → batas harian → waktu belajar → batas per aplikasi.
 */
object LimitEnforcer {

    fun evaluate(config: LimitsConfig, usage: UsageSnapshot, clock: ClockState): Violation? {
        if (isWindowActive(config.bedtime, clock)) {
            return Violation("Sudah waktunya tidur. Selamat istirahat, Leon!")
        }

        if (config.dailyLimitEnabled) {
            val limitMs = effectiveDailyLimitMinutes(config, clock.dateKey) * 60_000L
            if (usage.totalMs >= limitMs) {
                return Violation("Waktu layar hari ini sudah habis. Sampai jumpa besok, Leon!")
            }
        }

        val foregroundCategory = usage.foregroundCategory
        if (isWindowActive(config.study, clock) &&
            (foregroundCategory == AppCategory.ENTERTAINMENT.key || foregroundCategory == AppCategory.GAME.key)
        ) {
            return Violation("Sekarang waktu belajar. Hiburan dibuka lagi nanti ya!")
        }

        for (rule in config.appLimits) {
            if (rule.minutes <= 0) continue
            val (isForeground, usedMs) = ruleUsage(rule, usage)
            if (isForeground && usedMs >= rule.minutes * 60_000L) {
                return Violation("Batas ${rule.label} hari ini sudah habis.")
            }
        }
        return null
    }

    fun effectiveDailyLimitMinutes(config: LimitsConfig, dateKey: String): Int {
        val bonus = if (config.bonusDate == dateKey) config.bonusMinutes else 0
        return config.dailyLimitMinutes + bonus
    }

    fun isWindowActive(window: TimeWindow, clock: ClockState): Boolean {
        if (!window.enabled) return false
        val start = parseMinutes(window.start) ?: return false
        val end = parseMinutes(window.end) ?: return false
        if (start == end) return false

        val minute = clock.minuteOfDay
        return when {
            start < end -> dayMatches(window, clock.dayOfWeek) && minute in start until end
            // Jendela lewat tengah malam: bagian setelah 00:00 dihitung milik hari sebelumnya
            minute >= start -> dayMatches(window, clock.dayOfWeek)
            minute < end -> dayMatches(window, if (clock.dayOfWeek == Calendar.SUNDAY) Calendar.SATURDAY else clock.dayOfWeek - 1)
            else -> false
        }
    }

    private fun dayMatches(window: TimeWindow, dayOfWeek: Int) =
        window.days.isEmpty() || dayOfWeek in window.days

    private fun ruleUsage(rule: LimitRule, usage: UsageSnapshot): Pair<Boolean, Long> {
        return if (rule.type == LimitRule.TYPE_PACKAGE) {
            (usage.foregroundPackage == rule.target) to (usage.packageMs[rule.target] ?: 0L)
        } else {
            val keys = rule.target.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            (usage.foregroundCategory in keys) to keys.sumOf { usage.categoryMs[it] ?: 0L }
        }
    }

    private fun parseMinutes(hhmm: String): Int? {
        val parts = hhmm.split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun dateKey(offsetDays: Int = 0): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(cal.timeInMillis))
    }

    fun currentClock(): ClockState {
        val cal = Calendar.getInstance()
        return ClockState(
            dateKey = dateKey(),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        )
    }
}
