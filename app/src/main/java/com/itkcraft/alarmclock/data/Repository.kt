package com.itkcraft.alarmclock.data

import android.content.Context
import android.util.AtomicFile
import com.itkcraft.alarmclock.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * アプリデータの永続化（アプリ専用領域に JSON で保存。外部ストレージは使わない）。
 * 書き込みは AtomicFile により途中で壊れないようにしている。
 */
object Repository {
    private const val TAG = "Repository"
    private lateinit var dir: File
    private val lock = Any()

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    private val _groups = MutableStateFlow<List<AlarmGroup>>(emptyList())
    val groups: StateFlow<List<AlarmGroup>> = _groups.asStateFlow()
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    @Volatile private var loaded = false

    fun init(context: Context): Unit = synchronized(lock) {
        if (loaded) return
        dir = File(context.filesDir, "data").apply { mkdirs() }
        readJson("alarms.json")?.let { root ->
            _alarms.value = root.optJSONArray("alarms").objects().mapNotNull { runCatching { it.toAlarm() }.getOrNull() }
            _groups.value = root.optJSONArray("groups").objects().mapNotNull { runCatching { it.toGroup() }.getOrNull() }
        }
        readJson("settings.json")?.let { _settings.value = runCatching { it.toSettings() }.getOrDefault(Settings()) }
        readJson("timer.json")?.let { _timer.value = runCatching { it.toTimer() }.getOrDefault(TimerState()) }
        loaded = true
        AppLog.i(TAG, "loaded alarms=${_alarms.value.size} groups=${_groups.value.size}")
    }

    // ---- 更新 API ----

    fun updateAlarms(transform: (List<Alarm>, List<AlarmGroup>) -> Pair<List<Alarm>, List<AlarmGroup>>) = synchronized(lock) {
        val (a, g) = transform(_alarms.value, _groups.value)
        _alarms.value = a
        _groups.value = g
        writeJson("alarms.json", JSONObject().apply {
            put("version", 1)
            put("alarms", JSONArray(a.map { it.toJson() }))
            put("groups", JSONArray(g.map { it.toJson() }))
        })
    }

    fun upsertAlarm(alarm: Alarm) = updateAlarms { a, g ->
        (if (a.any { it.id == alarm.id }) a.map { if (it.id == alarm.id) alarm else it } else a + alarm) to g
    }

    fun deleteAlarm(id: Long) = updateAlarms { a, g -> a.filterNot { it.id == id } to g }

    fun upsertGroup(group: AlarmGroup) = updateAlarms { a, g ->
        a to (if (g.any { it.id == group.id }) g.map { if (it.id == group.id) group else it } else g + group)
    }

    /** グループ削除。所属アラームは「グループなし」に移す（アラーム自体は消さない） */
    fun deleteGroup(id: Long, deleteAlarms: Boolean) = updateAlarms { a, g ->
        val newAlarms = if (deleteAlarms) a.filterNot { it.groupId == id } else a.map { if (it.groupId == id) it.copy(groupId = null) else it }
        newAlarms to g.filterNot { it.id == id }
    }

    fun updateSettings(transform: (Settings) -> Settings) = synchronized(lock) {
        val s = transform(_settings.value)
        _settings.value = s
        writeJson("settings.json", s.toJson())
    }

    fun updateTimer(transform: (TimerState) -> TimerState) = synchronized(lock) {
        val t = transform(_timer.value)
        _timer.value = t
        writeJson("timer.json", t.toJson())
    }

    fun newId(): Long = System.currentTimeMillis() * 10 + (0..9).random()

    // ---- ファイル I/O ----

    private fun readJson(name: String): JSONObject? = try {
        val f = AtomicFile(File(dir, name))
        if (!f.baseFile.exists()) null else JSONObject(String(f.readFully(), Charsets.UTF_8))
    } catch (e: Exception) {
        AppLog.e(TAG, "read $name failed", e)
        null
    }

    private fun writeJson(name: String, json: JSONObject) {
        val f = AtomicFile(File(dir, name))
        val out = try { f.startWrite() } catch (e: Exception) { AppLog.e(TAG, "open $name failed", e); return }
        try {
            out.write(json.toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            AppLog.e(TAG, "write $name failed", e)
        }
    }

    // ---- JSON 変換 ----

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun SoundRef.toJson() = JSONObject().put("uri", uri).put("name", name)
    private fun JSONObject.toSound() = SoundRef(getString("uri"), optString("name", ""))

    private fun Alarm.toJson() = JSONObject().apply {
        put("id", id); put("hour", hour); put("minute", minute); put("enabled", enabled)
        put("memo", memo); put("days", JSONArray(days.map { it.value })); put("repeat", repeat)
        date?.let { put("date", it.toEpochDay()) }
        put("vibrate", vibrate)
        sound?.let { put("sound", it.toJson()) }
        groupId?.let { put("groupId", it) }
        skipAt?.let { put("skipAt", it) }
    }

    private fun JSONObject.toAlarm() = Alarm(
        id = getLong("id"),
        hour = getInt("hour").coerceIn(0, 23),
        minute = getInt("minute").coerceIn(0, 59),
        enabled = optBoolean("enabled", true),
        memo = optString("memo", ""),
        days = optJSONArray("days")?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optInt(i).takeIf { it in 1..7 }?.let(DayOfWeek::of) }.toSet() } ?: emptySet(),
        repeat = optBoolean("repeat", true),
        date = if (has("date")) LocalDate.ofEpochDay(getLong("date")) else null,
        vibrate = optBoolean("vibrate", true),
        sound = optJSONObject("sound")?.toSound(),
        groupId = if (has("groupId")) getLong("groupId") else null,
        skipAt = if (has("skipAt")) getLong("skipAt") else null,
    )

    private fun AlarmGroup.toJson() = JSONObject().put("id", id).put("name", name).put("enabled", enabled).put("expanded", expanded)
    private fun JSONObject.toGroup() = AlarmGroup(getLong("id"), optString("name", "グループ"), optBoolean("enabled", true), optBoolean("expanded", true))

    private fun Settings.toJson() = JSONObject().apply {
        put("defaultSound", defaultSound.toJson()); put("volume", volume); put("snoozeMinutes", snoozeMinutes)
        put("silenceMinutes", silenceMinutes); put("gradualVolume", gradualVolume); put("gradualSeconds", gradualSeconds)
        put("noHeadphoneAction", noHeadphoneAction.name); put("vibration", vibration); put("use24h", use24h)
        put("theme", theme.name); put("customSounds", JSONArray(customSounds.map { it.toJson() }))
    }

    private fun JSONObject.toSettings(): Settings {
        val d = Settings()
        return Settings(
            defaultSound = optJSONObject("defaultSound")?.toSound() ?: d.defaultSound,
            volume = optInt("volume", d.volume).coerceIn(1, 100),
            snoozeMinutes = optInt("snoozeMinutes", d.snoozeMinutes).coerceIn(1, 60),
            silenceMinutes = optInt("silenceMinutes", d.silenceMinutes).coerceIn(0, 120),
            gradualVolume = optBoolean("gradualVolume", d.gradualVolume),
            gradualSeconds = optInt("gradualSeconds", d.gradualSeconds).coerceIn(5, 300),
            noHeadphoneAction = runCatching { NoHeadphoneAction.valueOf(optString("noHeadphoneAction")) }.getOrDefault(d.noHeadphoneAction),
            vibration = optBoolean("vibration", d.vibration),
            use24h = optBoolean("use24h", d.use24h),
            theme = runCatching { ThemeMode.valueOf(optString("theme")) }.getOrDefault(d.theme),
            customSounds = optJSONArray("customSounds").objects().mapNotNull { runCatching { it.toSound() }.getOrNull() },
        )
    }

    private fun TimerState.toJson() = JSONObject().apply {
        put("durationMs", durationMs); put("lastDurationMs", lastDurationMs)
        endAt?.let { put("endAt", it) }
        pausedRemainingMs?.let { put("pausedRemainingMs", it) }
    }

    private fun JSONObject.toTimer() = TimerState(
        durationMs = optLong("durationMs", 0),
        lastDurationMs = optLong("lastDurationMs", 0),
        endAt = if (has("endAt")) getLong("endAt") else null,
        pausedRemainingMs = if (has("pausedRemainingMs")) getLong("pausedRemainingMs") else null,
    )
}
