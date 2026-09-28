package com.itkcraft.alarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** AlarmManager への登録・解除を一手に引き受ける */
object AlarmScheduler {
    private const val TAG = "Scheduler"
    const val ACTION_ALARM = "com.itkcraft.alarmclock.action.ALARM"
    const val ACTION_TIMER = "com.itkcraft.alarmclock.action.TIMER"
    const val ACTION_SNOOZE = "com.itkcraft.alarmclock.action.SNOOZE"
    const val ACTION_CANCEL_SNOOZE = "com.itkcraft.alarmclock.action.CANCEL_SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_TRIGGER_AT = "triggerAt"
    const val EXTRA_KIND = "kind"

    /** スキップ済み時刻の猶予（受信処理との競合を避けるため） */
    private const val STALE_GRACE_MS = 5 * 60_000L

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    private fun alarmIntent(ctx: Context, action: String, data: String) =
        Intent(ctx, AlarmReceiver::class.java).setAction(action).setData(Uri.parse(data))

    private fun pending(ctx: Context, intent: Intent, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(ctx, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    private fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val manager = am(ctx)
        if (canScheduleExact(ctx)) {
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            AppLog.w(TAG, "exact alarm not permitted; using inexact fallback")
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** 全アラームを再登録（起動時・変更時・時刻変更時に呼ぶ） */
    fun rescheduleAll(ctx: Context) {
        Repository.init(ctx)
        val now = System.currentTimeMillis()
        // 期限切れのスキップフラグを整理（端末電源 OFF 中に予定時刻を過ぎた場合など）
        Repository.updateAlarms { alarms, groups ->
            alarms.map { a ->
                if (a.skipAt != null && a.skipAt < now - STALE_GRACE_MS) {
                    val oneShot = !a.repeat || a.date != null
                    a.copy(skipAt = null, enabled = if (oneShot) false else a.enabled)
                } else a
            } to groups
        }
        val groups = Repository.groups.value
        Repository.alarms.value.forEach { alarm ->
            if (Schedule.isActive(alarm, groups)) schedule(ctx, alarm, now) else cancel(ctx, alarm.id)
        }
        val t = Repository.timer.value
        if (t.endAt != null && t.endAt > now) scheduleTimer(ctx, t.endAt) else cancelTimer(ctx)
    }

    private fun schedule(ctx: Context, alarm: Alarm, now: Long) {
        // スキップ中でも「その時刻」に一度受信し、そこでフラグを下げる
        val at = Schedule.nextTrigger(alarm, now)
        if (at == null) {
            cancel(ctx, alarm.id)
            return
        }
        val intent = alarmIntent(ctx, ACTION_ALARM, "alarm://a/${alarm.id}")
            .putExtra(EXTRA_ID, alarm.id).putExtra(EXTRA_TRIGGER_AT, at)
        pending(ctx, intent)?.let { setExact(ctx, at, it) }
        AppLog.d(TAG, "scheduled alarm ${alarm.id} at ${fmt(at)}${if (alarm.skipAt == at) " (skip)" else ""}")
    }

    fun cancel(ctx: Context, id: Long) {
        pending(ctx, alarmIntent(ctx, ACTION_ALARM, "alarm://a/$id"), PendingIntent.FLAG_NO_CREATE)?.let {
            am(ctx).cancel(it); it.cancel()
        }
    }

    fun scheduleTimer(ctx: Context, at: Long) {
        pending(ctx, alarmIntent(ctx, ACTION_TIMER, "alarm://timer"))?.let { setExact(ctx, at, it) }
        AppLog.d(TAG, "scheduled timer at ${fmt(at)}")
    }

    fun cancelTimer(ctx: Context) {
        pending(ctx, alarmIntent(ctx, ACTION_TIMER, "alarm://timer"), PendingIntent.FLAG_NO_CREATE)?.let {
            am(ctx).cancel(it); it.cancel()
        }
    }

    fun scheduleSnooze(ctx: Context, kind: String, id: Long, at: Long) {
        val intent = alarmIntent(ctx, ACTION_SNOOZE, "alarm://snooze").putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id)
        pending(ctx, intent)?.let { setExact(ctx, at, it) }
        AppLog.i(TAG, "snooze $kind/$id until ${fmt(at)}")
    }

    fun cancelSnooze(ctx: Context) {
        pending(ctx, alarmIntent(ctx, ACTION_SNOOZE, "alarm://snooze"), PendingIntent.FLAG_NO_CREATE)?.let {
            am(ctx).cancel(it); it.cancel()
        }
    }

    fun fmt(t: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(t))
}
