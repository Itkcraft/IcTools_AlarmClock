package com.itkcraft.alarmclock.ui

import com.itkcraft.alarmclock.data.Alarm
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayLabels = mapOf(
    DayOfWeek.MONDAY to "月", DayOfWeek.TUESDAY to "火", DayOfWeek.WEDNESDAY to "水",
    DayOfWeek.THURSDAY to "木", DayOfWeek.FRIDAY to "金", DayOfWeek.SATURDAY to "土", DayOfWeek.SUNDAY to "日",
)

/** 表示順（月〜日） */
val weekOrder = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

fun dayLabel(d: DayOfWeek) = dayLabels.getValue(d)

fun formatHm(hour: Int, minute: Int, use24h: Boolean): String =
    if (use24h) "%02d:%02d".format(hour, minute)
    else "${if (hour < 12) "AM" else "PM"} %d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute)

fun formatTime(epochMs: Long, use24h: Boolean): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return formatHm(t.hour, t.minute, use24h)
}

/** 「明日 07:00」「9/30(火) 07:00」のような次回表示 */
fun formatNext(epochMs: Long, use24h: Boolean): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val d = t.toLocalDate()
    val day = when (d) {
        today -> "今日"
        today.plusDays(1) -> "明日"
        else -> "${d.monthValue}/${d.dayOfMonth}(${dayLabel(d.dayOfWeek)})"
    }
    return "$day ${formatHm(t.hour, t.minute, use24h)}"
}

fun formatDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.JAPAN)) + "(${dayLabel(d.dayOfWeek)})"

/** 残り時間 h:mm:ss / mm:ss */
fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun repeatSummary(a: Alarm): String = when {
    a.date != null -> formatDate(a.date)
    a.days.isEmpty() -> if (a.repeat) "毎日" else "1回のみ"
    a.days.size == 7 -> if (a.repeat) "毎日" else "1回のみ"
    a.days == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) ->
        if (a.repeat) "平日" else "平日（1回のみ）"
    a.days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> if (a.repeat) "土日" else "土日（1回のみ）"
    else -> weekOrder.filter { it in a.days }.joinToString("・") { dayLabel(it) } + if (a.repeat) "" else "（1回のみ）"
}

/** 大きな時刻表示用: (AM/PM または null, "7:00") */
fun hmParts(hour: Int, minute: Int, use24h: Boolean): Pair<String?, String> =
    if (use24h) null to "%02d:%02d".format(hour, minute)
    else (if (hour < 12) "AM" else "PM") to "%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute)
