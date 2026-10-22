package com.parentalcontrol.parent.analysis

import com.parentalcontrol.parent.model.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleFormatTest {

    @Test
    fun `empty or full week is every day`() {
        assertEquals("Setiap Hari", ScheduleFormat.daysLabel(emptyList()))
        assertEquals("Setiap Hari", ScheduleFormat.daysLabel(listOf(1, 2, 3, 4, 5, 6, 7)))
    }

    @Test
    fun `weekdays and weekend have names`() {
        assertEquals("Senin–Jumat", ScheduleFormat.daysLabel(listOf(2, 3, 4, 5, 6)))
        assertEquals("Akhir Pekan", ScheduleFormat.daysLabel(listOf(7, 1)))
    }

    @Test
    fun `other combinations are listed Monday first`() {
        assertEquals("Sen, Rab, Min", ScheduleFormat.daysLabel(listOf(1, 4, 2)))
    }

    @Test
    fun `summary includes days and times`() {
        assertEquals(
            "Setiap Hari • 20:00 – 06:00",
            ScheduleFormat.summary(TimeWindow(enabled = true, start = "20:00", end = "06:00"))
        )
    }
}
