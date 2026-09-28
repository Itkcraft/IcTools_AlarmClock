package com.itkcraft.alarmclock

import android.app.Application
import com.itkcraft.alarmclock.alarm.AlarmScheduler
import com.itkcraft.alarmclock.alarm.Notifications
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.sound.Sounds

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.installCrashHandler()
        AppLog.i("App", "start v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) sdk=${android.os.Build.VERSION.SDK_INT}")
        Repository.init(this)
        Notifications.createChannels(this)
        Thread {
            runCatching { Sounds.ensureGenerated(this) }.onFailure { AppLog.e("App", "sound generation failed", it) }
        }.start()
        runCatching { AlarmScheduler.rescheduleAll(this) }.onFailure { AppLog.e("App", "reschedule failed", it) }
    }
}
