package com.itkcraft.alarmclock.alarm

import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.AlarmGroup
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 鳴動時刻の計算（純粋関数。単体テスト対象） */
object Schedule {

    fun isActive(alarm: Alarm, groups: List<AlarmGroup>): Boolean {
        if (!alarm.enabled) return false
        val g = alarm.groupId?.let { id -> groups.firstOrNull { it.id == id } }
        return g?.enabled ?: true
    }

    /** now より後で最初に条件に合う鳴動時刻。無ければ null */
    fun nextTrigger(alarm: Alarm, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val time = LocalTime.of(alarm.hour, alarm.minute)
        alarm.date?.let { d ->
            val t = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
            return if (t > now) t else null
        }
        for (offset in 0..8) {
            val day = nowDt.toLocalDate().plusDays(offset.toLong())
            if (alarm.days.isNotEmpty() && day.dayOfWeek !in alarm.days) continue
            val t = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
            if (t > now) return t
        }
        return null
    }

    fun isSkipped(alarm: Alarm, now: Long): Boolean = alarm.skipAt != null && alarm.skipAt > now

    /** スキップを考慮した「実際に鳴る」次回時刻 */
    fun nextRing(alarm: Alarm, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val t = nextTrigger(alarm, now, zone) ?: return null
        if (alarm.skipAt == t) {
            if (!alarm.repeat || alarm.date != null) return null
            return nextTrigger(alarm, t, zone)
        }
        return t
    }
}
