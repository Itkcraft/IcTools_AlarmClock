package com.itkcraft.alarmclock.alarm

import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.AlarmGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // 2026-09-28 は月曜日
    private val now = ms(2026, 9, 28, 8, 0)

    @Test fun laterToday() {
        val a = Alarm(1, 9, 30)
        assertEquals(ms(2026, 9, 28, 9, 30), Schedule.nextTrigger(a, now, zone))
    }

    @Test fun earlierTimeGoesTomorrow() {
        val a = Alarm(1, 7, 0)
        assertEquals(ms(2026, 9, 29, 7, 0), Schedule.nextTrigger(a, now, zone))
    }

    @Test fun sameMinuteIsNotNow() {
        val a = Alarm(1, 8, 0)
        assertEquals(ms(2026, 9, 29, 8, 0), Schedule.nextTrigger(a, now, zone))
    }

    @Test fun weekdaysOnly() {
        val a = Alarm(1, 7, 0, days = setOf(DayOfWeek.SATURDAY))
        assertEquals(ms(2026, 10, 3, 7, 0), Schedule.nextTrigger(a, now, zone))
    }

    @Test fun sameWeekdayNextWeek() {
        val a = Alarm(1, 7, 0, days = setOf(DayOfWeek.MONDAY))
        assertEquals(ms(2026, 10, 5, 7, 0), Schedule.nextTrigger(a, now, zone))
    }

    @Test fun specificDate() {
        val a = Alarm(1, 6, 15, date = LocalDate.of(2026, 12, 24))
        assertEquals(ms(2026, 12, 24, 6, 15), Schedule.nextTrigger(a, now, zone))
    }

    @Test fun pastDateIsNull() {
        val a = Alarm(1, 6, 15, date = LocalDate.of(2026, 9, 1))
        assertNull(Schedule.nextTrigger(a, now, zone))
    }

    @Test fun skipRepeatingMovesToFollowing() {
        val t = ms(2026, 9, 29, 7, 0)
        val a = Alarm(1, 7, 0, repeat = true, skipAt = t)
        assertTrue(Schedule.isSkipped(a, now))
        assertEquals(t, Schedule.nextTrigger(a, now, zone))
        assertEquals(ms(2026, 9, 30, 7, 0), Schedule.nextRing(a, now, zone))
    }

    @Test fun skipOneShotHasNoRing() {
        val t = ms(2026, 9, 29, 7, 0)
        val a = Alarm(1, 7, 0, repeat = false, skipAt = t)
        assertNull(Schedule.nextRing(a, now, zone))
    }

    @Test fun skipExpiresAfterTime() {
        val t = ms(2026, 9, 28, 7, 0)
        val a = Alarm(1, 7, 0, skipAt = t)
        assertFalse(Schedule.isSkipped(a, now))
    }

    @Test fun groupOffDisablesAlarm() {
        val g = AlarmGroup(10, "g", enabled = false)
        assertFalse(Schedule.isActive(Alarm(1, 7, 0, groupId = 10), listOf(g)))
        assertTrue(Schedule.isActive(Alarm(1, 7, 0, groupId = 10), listOf(g.copy(enabled = true))))
        assertFalse(Schedule.isActive(Alarm(1, 7, 0, enabled = false), emptyList()))
    }

    @Test fun dstSafeInOtherZone() {
        val ny = ZoneId.of("America/New_York")
        // 2026-03-08 02:30 は存在しない（DST 開始）。例外にならず何らかの時刻を返すこと
        val before = LocalDateTime.of(2026, 3, 7, 12, 0).atZone(ny).toInstant().toEpochMilli()
        val r = Schedule.nextTrigger(Alarm(1, 2, 30), before, ny)
        assertTrue(r != null && r > before)
    }
}
