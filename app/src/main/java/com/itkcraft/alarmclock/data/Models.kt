package com.itkcraft.alarmclock.data

import java.time.DayOfWeek
import java.time.LocalDate

/** 音源の参照。uri は builtin:xxx / content:// / file:// のいずれか */
data class SoundRef(val uri: String, val name: String)

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val memo: String = "",
    /** 鳴らす曜日。空なら「毎日（繰り返し時）/ 次に来る時刻（単発時）」 */
    val days: Set<DayOfWeek> = emptySet(),
    /** true: 毎週繰り返し / false: 1回鳴ったら OFF */
    val repeat: Boolean = true,
    /** 日付指定（指定時はその日1回のみ。曜日・繰り返しより優先） */
    val date: LocalDate? = null,
    val vibrate: Boolean = true,
    /** null の場合は設定画面の既定アラーム音を使用 */
    val sound: SoundRef? = null,
    val groupId: Long? = null,
    /** スキップ対象となっている鳴動予定時刻 (epoch millis)。null ならスキップなし */
    val skipAt: Long? = null,
)

data class AlarmGroup(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    val expanded: Boolean = true,
)

enum class NoHeadphoneAction { SPEAKER, VIBRATE_ONLY, SILENT }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val defaultSound: SoundRef = SoundRef("builtin:chime", "チャイム"),
    /** メディア音量 1〜100 */
    val volume: Int = 60,
    val snoozeMinutes: Int = 5,
    /** 鳴り始めから自動消音までの分数。0 は自動消音しない */
    val silenceMinutes: Int = 10,
    val gradualVolume: Boolean = true,
    val gradualSeconds: Int = 30,
    val noHeadphoneAction: NoHeadphoneAction = NoHeadphoneAction.SPEAKER,
    val vibration: Boolean = true,
    val use24h: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** ユーザーが追加した音声ファイル */
    val customSounds: List<SoundRef> = emptyList(),
)

data class TimerState(
    /** 待機中にセットされている時間 */
    val durationMs: Long = 0,
    /** 前回スタートした時間（前回記憶） */
    val lastDurationMs: Long = 0,
    /** 動作中の終了時刻 (epoch millis)。null なら停止中 */
    val endAt: Long? = null,
    /** 一時停止中の残り時間。null なら一時停止していない */
    val pausedRemainingMs: Long? = null,
) {
    val running get() = endAt != null
    val paused get() = pausedRemainingMs != null
    fun remaining(now: Long): Long = when {
        endAt != null -> (endAt - now).coerceAtLeast(0)
        pausedRemainingMs != null -> pausedRemainingMs
        else -> durationMs
    }
}
