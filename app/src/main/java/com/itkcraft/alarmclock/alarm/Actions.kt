package com.itkcraft.alarmclock.alarm

import android.content.Context
import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.AlarmGroup
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.ui.formatNext

/** UI から呼ばれるアラーム操作。戻り値はユーザーへ表示するメッセージ（無ければ null） */
object AlarmActions {
    private const val TAG = "AlarmActions"

    private fun use24h() = Repository.settings.value.use24h

    fun save(ctx: Context, alarm: Alarm): String? {
        val old = Repository.alarms.value.firstOrNull { it.id == alarm.id }
        val now = System.currentTimeMillis()
        // 鳴動条件が変わった場合はスキップを解除
        val keepSkip = old != null && old.skipAt != null &&
            Schedule.nextTrigger(old.copy(skipAt = null), now) == Schedule.nextTrigger(alarm.copy(skipAt = null), now)
        val toSave = alarm.copy(skipAt = if (keepSkip) old!!.skipAt else null, enabled = true)
        Repository.upsertAlarm(toSave)
        AlarmScheduler.rescheduleAll(ctx)
        AppLog.i(TAG, "saved alarm ${alarm.id}")
        return nextMessage(toSave)
    }

    fun delete(ctx: Context, id: Long) {
        AlarmScheduler.cancel(ctx, id)
        Repository.deleteAlarm(id)
        AlarmScheduler.rescheduleAll(ctx)
        AppLog.i(TAG, "deleted alarm $id")
    }

    fun setEnabled(ctx: Context, alarm: Alarm, enabled: Boolean): String? {
        val updated = alarm.copy(enabled = enabled, skipAt = null)
        Repository.upsertAlarm(updated)
        AlarmScheduler.rescheduleAll(ctx)
        return if (enabled) nextMessage(updated) else null
    }

    private fun nextMessage(alarm: Alarm): String? {
        val now = System.currentTimeMillis()
        val groups = Repository.groups.value
        if (!Schedule.isActive(alarm, groups)) return if (alarm.enabled) "グループが OFF のため鳴りません" else null
        val next = Schedule.nextRing(alarm, now) ?: return "指定日時が過ぎているため鳴りません"
        return "${formatNext(next, use24h())} に鳴ります（${untilText(next - now)}）"
    }

    /** 長押し: 次回スキップの ON/OFF */
    fun toggleSkip(ctx: Context, alarm: Alarm): String {
        val now = System.currentTimeMillis()
        if (!Schedule.isActive(alarm, Repository.groups.value)) return "OFF のアラームはスキップできません"
        return if (Schedule.isSkipped(alarm, now)) {
            Repository.upsertAlarm(alarm.copy(skipAt = null))
            AlarmScheduler.rescheduleAll(ctx)
            "スキップを解除しました"
        } else {
            val t = Schedule.nextTrigger(alarm, now) ?: return "次回の予定がありません"
            Repository.upsertAlarm(alarm.copy(skipAt = t))
            AlarmScheduler.rescheduleAll(ctx)
            AppLog.i(TAG, "skip alarm ${alarm.id}")
            "${formatNext(t, use24h())} をスキップします"
        }
    }

    fun saveGroup(ctx: Context, group: AlarmGroup) {
        Repository.upsertGroup(group)
        AlarmScheduler.rescheduleAll(ctx)
    }

    fun deleteGroup(ctx: Context, group: AlarmGroup, deleteAlarms: Boolean) {
        if (deleteAlarms) Repository.alarms.value.filter { it.groupId == group.id }.forEach { AlarmScheduler.cancel(ctx, it.id) }
        Repository.deleteGroup(group.id, deleteAlarms)
        AlarmScheduler.rescheduleAll(ctx)
    }

    fun setGroupEnabled(ctx: Context, group: AlarmGroup, enabled: Boolean) {
        Repository.updateAlarms { a, g ->
            // グループを ON/OFF したらグループ内のスキップはリセット
            a.map { if (it.groupId == group.id) it.copy(skipAt = null) else it } to
                g.map { if (it.id == group.id) it.copy(enabled = enabled) else it }
        }
        AlarmScheduler.rescheduleAll(ctx)
    }

    fun toggleExpanded(group: AlarmGroup) = Repository.upsertGroup(group.copy(expanded = !group.expanded))

    /** 長押し: グループ内の有効なアラームを一括で次回スキップ / 解除 */
    fun toggleGroupSkip(ctx: Context, group: AlarmGroup): String {
        if (!group.enabled) return "OFF のグループはスキップできません"
        val now = System.currentTimeMillis()
        val targets = Repository.alarms.value.filter { it.groupId == group.id && it.enabled }
        if (targets.isEmpty()) return "ON のアラームがありません"
        val allSkipped = targets.all { Schedule.isSkipped(it, now) }
        Repository.updateAlarms { a, g ->
            a.map { al ->
                if (al.groupId != group.id || !al.enabled) al
                else if (allSkipped) al.copy(skipAt = null)
                else if (Schedule.isSkipped(al, now)) al
                else al.copy(skipAt = Schedule.nextTrigger(al, now))
            } to g
        }
        AlarmScheduler.rescheduleAll(ctx)
        AppLog.i(TAG, "group ${group.id} skip=${!allSkipped}")
        return if (allSkipped) "「${group.name}」のスキップを解除しました" else "「${group.name}」の次回をスキップします"
    }

    fun untilText(ms: Long): String {
        val min = (ms + 59_999) / 60_000
        val d = min / (60 * 24)
        val h = min % (60 * 24) / 60
        val m = min % 60
        return buildString {
            append("あと ")
            if (d > 0) append("${d}日")
            if (h > 0) append("${h}時間")
            if (d == 0L) append("${m}分")
        }
    }
}

/** タイマー操作 */
object TimerActions {
    private const val MAX_MS = 24 * 60 * 60_000L

    fun add(ctx: Context, ms: Long) {
        val now = System.currentTimeMillis()
        Repository.updateTimer { t ->
            when {
                t.endAt != null -> {
                    val remaining = (t.endAt - now).coerceAtLeast(0)
                    val added = (remaining + ms).coerceAtMost(MAX_MS) - remaining
                    t.copy(endAt = t.endAt + added, lastDurationMs = t.lastDurationMs + added)
                }
                t.pausedRemainingMs != null -> {
                    val newRemaining = (t.pausedRemainingMs + ms).coerceAtMost(MAX_MS)
                    t.copy(pausedRemainingMs = newRemaining, lastDurationMs = t.lastDurationMs + (newRemaining - t.pausedRemainingMs))
                }
                else -> t.copy(durationMs = (t.durationMs + ms).coerceAtMost(MAX_MS))
            }
        }
        Repository.timer.value.endAt?.let {
            AlarmScheduler.scheduleTimer(ctx, it)
            Notifications.showTimer(ctx, it)
        }
    }

    fun toggle(ctx: Context) {
        val now = System.currentTimeMillis()
        val t = Repository.timer.value
        when {
            t.endAt != null -> { // 一時停止
                Repository.updateTimer { it.copy(endAt = null, pausedRemainingMs = (t.endAt - now).coerceAtLeast(0)) }
                AlarmScheduler.cancelTimer(ctx)
                Notifications.cancelTimer(ctx)
                AppLog.i("Timer", "paused")
            }
            t.pausedRemainingMs != null -> { // 再開
                val end = now + t.pausedRemainingMs
                Repository.updateTimer { it.copy(endAt = end, pausedRemainingMs = null) }
                AlarmScheduler.scheduleTimer(ctx, end)
                Notifications.showTimer(ctx, end)
                AppLog.i("Timer", "resumed")
            }
            t.durationMs > 0 -> { // 開始
                val end = now + t.durationMs
                Repository.updateTimer { it.copy(endAt = end, lastDurationMs = t.durationMs) }
                AlarmScheduler.scheduleTimer(ctx, end)
                Notifications.showTimer(ctx, end)
                AppLog.i("Timer", "started ${t.durationMs / 60_000}min")
            }
        }
    }

    fun reset(ctx: Context) {
        Repository.updateTimer { it.copy(endAt = null, pausedRemainingMs = null, durationMs = 0) }
        AlarmScheduler.cancelTimer(ctx)
        Notifications.cancelTimer(ctx)
    }

    fun restoreLast() = Repository.updateTimer { it.copy(durationMs = it.lastDurationMs) }
}
