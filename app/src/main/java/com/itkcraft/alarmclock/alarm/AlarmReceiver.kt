package com.itkcraft.alarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.log.AppLog

/** AlarmManager からの通知を受けて鳴動・スキップ解除を行う（非公開レシーバー） */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Repository.init(context)
            when (intent.action) {
                AlarmScheduler.ACTION_ALARM -> onAlarm(context, intent)
                AlarmScheduler.ACTION_TIMER -> {
                    AppLog.i(TAG, "timer finished")
                    Repository.updateTimer { it.copy(endAt = null, pausedRemainingMs = null, durationMs = it.lastDurationMs) }
                    Notifications.cancelTimer(context)
                    AlarmService.start(context, AlarmService.KIND_TIMER, 0)
                }
                AlarmScheduler.ACTION_SNOOZE -> {
                    Notifications.cancelSnooze(context)
                    val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: AlarmService.KIND_ALARM
                    AlarmService.start(context, kind, intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0))
                }
                AlarmScheduler.ACTION_CANCEL_SNOOZE -> {
                    AlarmScheduler.cancelSnooze(context)
                    Notifications.cancelSnooze(context)
                    AppLog.i(TAG, "snooze cancelled")
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "onReceive failed: ${intent.action}", e)
        }
    }

    private fun onAlarm(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1)
        val triggerAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
        val alarm = Repository.alarms.value.firstOrNull { it.id == id }
        if (alarm == null || !Schedule.isActive(alarm, Repository.groups.value)) {
            AppLog.w(TAG, "alarm $id not active; ignored")
            AlarmScheduler.rescheduleAll(context)
            return
        }
        val oneShot = !alarm.repeat || alarm.date != null
        if (alarm.skipAt != null && alarm.skipAt <= triggerAt + 60_000) {
            // 今回分はスキップ。ここでフラグを下げる
            AppLog.i(TAG, "alarm $id skipped at ${AlarmScheduler.fmt(triggerAt)}")
            Repository.upsertAlarm(alarm.copy(skipAt = null, enabled = if (oneShot) false else alarm.enabled))
        } else {
            AppLog.i(TAG, "alarm $id ring")
            if (oneShot) Repository.upsertAlarm(alarm.copy(enabled = false))
            AlarmService.start(context, AlarmService.KIND_ALARM, id)
        }
        AlarmScheduler.rescheduleAll(context)
    }

    companion object { private const val TAG = "Receiver" }
}
