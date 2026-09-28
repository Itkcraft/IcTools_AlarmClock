package com.itkcraft.alarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.itkcraft.alarmclock.log.AppLog

/** 再起動・時刻変更・アップデート後にアラームを再登録する */
class BootReceiver : BroadcastReceiver() {
    private val allowed = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in allowed) return
        AppLog.i("Boot", "reschedule by ${intent.action}")
        runCatching { AlarmScheduler.rescheduleAll(context) }.onFailure { AppLog.e("Boot", "reschedule failed", it) }
    }
}
