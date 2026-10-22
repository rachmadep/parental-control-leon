package com.parentalcontrol.parent.analysis

import com.parentalcontrol.parent.model.TimeWindow

object ScheduleFormat {

    // Urutan tampil Senin..Minggu; nilai = Calendar.DAY_OF_WEEK
    val DAY_ORDER = listOf(2, 3, 4, 5, 6, 7, 1)

    private val SHORT_NAMES = mapOf(
        2 to "Sen", 3 to "Sel", 4 to "Rab", 5 to "Kam", 6 to "Jum", 7 to "Sab", 1 to "Min"
    )

    fun dayName(day: Int): String = SHORT_NAMES[day] ?: "?"

    fun daysLabel(days: List<Int>): String {
        val set = days.toSet()
        return when {
            set.isEmpty() || set.size == 7 -> "Setiap Hari"
            set == setOf(2, 3, 4, 5, 6) -> "Senin–Jumat"
            set == setOf(1, 7) -> "Akhir Pekan"
            else -> DAY_ORDER.filter { it in set }.joinToString(", ") { dayName(it) }
        }
    }

    fun summary(window: TimeWindow): String = "${daysLabel(window.days)} • ${window.start} – ${window.end}"
}
