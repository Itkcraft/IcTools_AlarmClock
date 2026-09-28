package com.itkcraft.alarmclock.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.itkcraft.alarmclock.data.NoHeadphoneAction
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.data.SoundRef
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.sound.Sounds
import com.itkcraft.alarmclock.ui.formatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 鳴動サービス。
 * アラーム音は「メディア音 (USAGE_MEDIA)」として再生するため、イヤホン接続中はイヤホンから鳴る。
 * メディアの音声フォーカスを取得するので、YouTube 等の再生は停止される。
 */
class AlarmService : Service() {

    data class RingInfo(val kind: String, val alarmId: Long, val title: String, val subtitle: String, val startedAt: Long)

    private enum class Output { SOUND, VIBRATE, NONE }

    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_START = "com.itkcraft.alarmclock.action.RING_START"
        const val ACTION_STOP = "com.itkcraft.alarmclock.action.RING_STOP"
        const val ACTION_SNOOZE = "com.itkcraft.alarmclock.action.RING_SNOOZE"
        const val KIND_ALARM = "alarm"
        const val KIND_TIMER = "timer"
        const val KIND_TEST = "test"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"

        private val _ringing = MutableStateFlow<RingInfo?>(null)
        val ringing: StateFlow<RingInfo?> = _ringing.asStateFlow()

        fun start(ctx: Context, kind: String, alarmId: Long) {
            val i = Intent(ctx, AlarmService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, alarmId)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                AppLog.e(TAG, "startForegroundService failed", e)
            }
        }

        fun stop(ctx: Context) = send(ctx, ACTION_STOP)
        fun snooze(ctx: Context) = send(ctx, ACTION_SNOOZE)

        private fun send(ctx: Context, action: String) {
            try {
                ctx.startService(Intent(ctx, AlarmService::class.java).setAction(action))
            } catch (e: Exception) {
                AppLog.e(TAG, "send $action failed", e)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var originalVolume: Int? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var current: RingInfo? = null
    private var sound: SoundRef? = null
    private var alarmVibrate = true
    private var output = Output.NONE
    private var rampStep = 0

    private val mediaAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any(AudioRouting::isHeadphone)) handler.post { applyOutput("headphones added") }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any(AudioRouting::isHeadphone)) handler.post { applyOutput("headphones removed") }
        }
    }

    private val autoSilence = Runnable {
        val info = current ?: return@Runnable
        AppLog.i(TAG, "auto silenced ${info.kind}/${info.alarmId}")
        Notifications.showInfo(this, "アラームを自動停止しました", "${info.title}（${info.subtitle}）")
        stopRinging()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Repository.init(this)
        when (intent?.action) {
            ACTION_START -> {
                val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_ALARM
                startRinging(kind, intent.getLongExtra(EXTRA_ID, 0))
            }
            ACTION_SNOOZE -> snoozeRinging()
            ACTION_STOP -> stopRinging()
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(kind: String, alarmId: Long) {
        if (current != null) releaseOutputs()
        val settings = Repository.settings.value
        val alarm = if (kind == KIND_ALARM) Repository.alarms.value.firstOrNull { it.id == alarmId } else null
        val now = System.currentTimeMillis()
        val title = when (kind) {
            KIND_TIMER -> "タイマー終了"
            KIND_TEST -> "テスト鳴動"
            else -> alarm?.memo?.takeIf { it.isNotBlank() } ?: "アラーム"
        }
        val subtitle = formatTime(now, settings.use24h)
        val info = RingInfo(kind, alarmId, title, subtitle, now)
        current = info
        sound = alarm?.sound ?: settings.defaultSound
        alarmVibrate = alarm?.vibrate ?: true

        val notification = Notifications.ringNotification(this, title, subtitle, canSnooze = kind != KIND_TEST)
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_RING, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "startForeground failed", e)
        }

        acquireWakeLock(settings.silenceMinutes)
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        _ringing.value = info
        applyOutput("start")

        handler.removeCallbacks(autoSilence)
        if (settings.silenceMinutes > 0) handler.postDelayed(autoSilence, settings.silenceMinutes * 60_000L)
        AppLog.i(TAG, "ringing $kind/$alarmId output=$output headphones=${AudioRouting.describe(this)}")
    }

    /** イヤホン接続状態と設定から出力方法を決定して反映する（鳴動中の抜き差しにも追従） */
    private fun applyOutput(reason: String) {
        if (current == null) return
        val settings = Repository.settings.value
        val headphones = AudioRouting.headphonesConnected(this)
        val newOutput = if (headphones) Output.SOUND else when (settings.noHeadphoneAction) {
            NoHeadphoneAction.SPEAKER -> Output.SOUND
            NoHeadphoneAction.VIBRATE_ONLY -> Output.VIBRATE
            NoHeadphoneAction.SILENT -> Output.NONE
        }
        val vibrate = newOutput == Output.VIBRATE || (newOutput == Output.SOUND && settings.vibration && alarmVibrate)
        AppLog.d(TAG, "applyOutput($reason): headphones=$headphones output=$newOutput vibrate=$vibrate")

        if (newOutput == Output.SOUND && player == null) startPlayer()
        if (newOutput != Output.SOUND) stopPlayer()
        if (vibrate) startVibration() else vibrator?.cancel()
        output = newOutput
    }

    private fun startPlayer() {
        val settings = Repository.settings.value
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(mediaAttrs)
            .setOnAudioFocusChangeListener { AppLog.d(TAG, "focus change $it") }
            .build()
        focusRequest = req
        val focus = audio.requestAudioFocus(req)
        AppLog.d(TAG, "audio focus result=$focus")

        // メディア音量を設定値に合わせる（停止時に元へ戻す）
        try {
            if (originalVolume == null) originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = max(1, (maxVol * settings.volume / 100f).roundToInt())
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (e: Exception) {
            AppLog.w(TAG, "setStreamVolume failed", e)
        }

        val ref = sound ?: settings.defaultSound
        val p = createPlayer(Sounds.resolve(this, ref)) ?: run {
            AppLog.w(TAG, "fallback to builtin sound (was ${ref.name})")
            createPlayer(Sounds.resolve(this, Sounds.fallback))
        }
        if (p == null) {
            AppLog.e(TAG, "no playable sound; vibrating instead")
            startVibration()
            return
        }
        player = p
        rampStep = 0
        if (settings.gradualVolume) {
            p.setVolume(0.02f, 0.02f)
            handler.post(ramp)
        } else {
            p.setVolume(1f, 1f)
        }
        p.start()
    }

    private val ramp = object : Runnable {
        override fun run() {
            val p = player ?: return
            val totalSteps = Repository.settings.value.gradualSeconds * 4
            rampStep++
            val f = (rampStep.toFloat() / totalSteps).coerceIn(0.02f, 1f)
            val v = f * f // 聴感上なめらかになるよう二乗カーブ
            runCatching { p.setVolume(v, v) }
            if (rampStep < totalSteps) handler.postDelayed(this, 250)
        }
    }

    private fun createPlayer(uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(mediaAttrs)
            setDataSource(this@AlarmService, uri)
            isLooping = true
            setOnErrorListener { _, what, extra ->
                AppLog.e(TAG, "MediaPlayer error what=$what extra=$extra")
                true
            }
            prepare()
        }
    } catch (e: Exception) {
        AppLog.e(TAG, "cannot play $uri", e)
        null
    }

    private fun stopPlayer() {
        handler.removeCallbacks(ramp)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
        originalVolume?.let { runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) } }
        originalVolume = null
    }

    private fun startVibration() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "vibrate failed", e)
        }
    }

    private fun acquireWakeLock(silenceMinutes: Int) {
        wakeLock?.let { if (it.isHeld) it.release() }
        val timeout = (if (silenceMinutes > 0) silenceMinutes + 1 else 60) * 60_000L
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MediaAlarm:ring")
            .apply { acquire(timeout) }
    }

    private fun releaseOutputs() {
        handler.removeCallbacks(autoSilence)
        stopPlayer()
        vibrator?.cancel()
        runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
        output = Output.NONE
    }

    private fun snoozeRinging() {
        val info = current ?: return stopRinging()
        if (info.kind == KIND_TEST) return stopRinging()
        val settings = Repository.settings.value
        val at = System.currentTimeMillis() + settings.snoozeMinutes * 60_000L
        AlarmScheduler.scheduleSnooze(this, info.kind, info.alarmId, at)
        Notifications.showSnooze(this, formatTime(at, settings.use24h))
        stopRinging()
    }

    private fun stopRinging() {
        current?.let { AppLog.i(TAG, "stop ${it.kind}/${it.alarmId}") }
        releaseOutputs()
        current = null
        _ringing.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (current != null) {
            releaseOutputs()
            current = null
            _ringing.value = null
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
