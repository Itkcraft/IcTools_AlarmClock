package com.itkcraft.alarmclock.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.itkcraft.alarmclock.R
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.ui.MainActivity
import com.itkcraft.alarmclock.ui.RingActivity

object Notifications {
    const val CH_RING = "ring"
    const val CH_TIMER = "timer"
    const val CH_INFO = "info"

    const val ID_RING = 1001
    private const val ID_TIMER = 1002
    private const val ID_SNOOZE = 1003
    private const val ID_INFO = 1004

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RING, "アラーム鳴動", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "アラーム・タイマー鳴動中の表示（音はアプリがメディア音で再生します）"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER, "タイマー動作中", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "お知らせ（スヌーズ・自動停止）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
            },
        )
    }

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ringNotification(ctx: Context, title: String, text: String, canSnooze: Boolean): Notification {
        val full = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            ctx, 2, Intent(ctx, AlarmService::class.java).setAction(AlarmService.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getService(
            ctx, 3, Intent(ctx, AlarmService::class.java).setAction(AlarmService.ACTION_SNOOZE), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setColor(0xFFFF6A00.toInt())
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, "停止", stop)
            .apply { if (canSnooze) addAction(0, "スヌーズ", snooze) }
            .build()
    }

    fun showTimer(ctx: Context, endAt: Long) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("タイマー動作中")
            .setContentText("終了予定 ${android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(endAt))}")
            .setWhen(endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setColor(0xFFFF6A00.toInt())
            .setContentIntent(openApp(ctx))
            .build()
        notify(ctx, ID_TIMER, n)
    }

    fun cancelTimer(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID_TIMER)

    fun showSnooze(ctx: Context, untilText: String) {
        val cancel = PendingIntent.getBroadcast(
            ctx, 4, Intent(ctx, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_CANCEL_SNOOZE), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("スヌーズ中")
            .setContentText("$untilText に再度鳴ります")
            .setOngoing(true)
            .setColor(0xFFFF6A00.toInt())
            .setContentIntent(openApp(ctx))
            .addAction(0, "スヌーズ解除", cancel)
            .build()
        notify(ctx, ID_SNOOZE, n)
    }

    fun cancelSnooze(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID_SNOOZE)

    fun showInfo(ctx: Context, title: String, text: String) {
        val n = NotificationCompat.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setColor(0xFFFF6A00.toInt())
            .setContentIntent(openApp(ctx))
            .build()
        notify(ctx, ID_INFO, n)
    }

    private fun notify(ctx: Context, id: Int, n: Notification) {
        if (!canPost(ctx)) {
            AppLog.w("Notify", "notification permission not granted")
            return
        }
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) {
            AppLog.e("Notify", "notify failed", e)
        }
    }
}
