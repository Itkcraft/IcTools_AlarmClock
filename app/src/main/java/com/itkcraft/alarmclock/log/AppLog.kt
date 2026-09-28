package com.itkcraft.alarmclock.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * アプリ内ロガー。
 * - logcat に出力しつつ、アプリ専用領域 (filesDir/logs) にローテーション付きで保存する
 * - 画面のデバッグコンソール向けに直近のログを StateFlow で公開する
 * - 個人情報（メモ本文など）は記録しない方針
 */
object AppLog {
    enum class Level(val tag: String) { D("D"), I("I"), W("W"), E("E") }

    data class Entry(val time: Long, val level: Level, val tag: String, val message: String) {
        fun format(): String = "${fmt.get()!!.format(Date(time))} ${level.tag}/$tag: $message"
    }

    private const val TAG = "MediaAlarm"
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val MAX_MEMORY = 1500

    private val fmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
    private val lock = Any()
    private var logDir: File? = null
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            logDir = dir
            // 前回までのログを読み込み、コンソールに表示できるようにする
            val restored = runCatching {
                currentFile(dir).takeIf { it.exists() }?.readLines()?.takeLast(MAX_MEMORY)
            }.getOrNull().orEmpty().map { Entry(0, Level.D, "", it) }
            _entries.value = restored
        }
    }

    private fun currentFile(dir: File) = File(dir, "app.log")
    private fun oldFile(dir: File) = File(dir, "app.log.1")

    fun d(tag: String, msg: String) = write(Level.D, tag, msg, null)
    fun i(tag: String, msg: String) = write(Level.I, tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = write(Level.W, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = write(Level.E, tag, msg, t)

    private fun write(level: Level, tag: String, msg: String, t: Throwable?) {
        val full = if (t != null) "$msg\n${Log.getStackTraceString(t)}" else msg
        when (level) {
            Level.D -> Log.d(TAG, "$tag: $full")
            Level.I -> Log.i(TAG, "$tag: $full")
            Level.W -> Log.w(TAG, "$tag: $full")
            Level.E -> Log.e(TAG, "$tag: $full")
        }
        val entry = Entry(System.currentTimeMillis(), level, tag, full)
        synchronized(lock) {
            val list = _entries.value
            _entries.value = if (list.size >= MAX_MEMORY) list.drop(list.size - MAX_MEMORY + 1) + entry else list + entry
            val dir = logDir ?: return
            runCatching {
                val f = currentFile(dir)
                if (f.length() > MAX_FILE_BYTES) {
                    oldFile(dir).delete()
                    f.renameTo(oldFile(dir))
                }
                currentFile(dir).appendText(entry.format() + "\n")
            }.onFailure { Log.e(TAG, "log write failed", it) }
        }
    }

    /** エクスポート用: 旧ファイル + 現行ファイルの全文 */
    fun exportText(header: String): String = synchronized(lock) {
        val dir = logDir ?: return header
        buildString {
            appendLine(header)
            appendLine("----")
            oldFile(dir).takeIf { it.exists() }?.let { append(it.readText()) }
            currentFile(dir).takeIf { it.exists() }?.let { append(it.readText()) }
        }
    }

    fun clear() = synchronized(lock) {
        logDir?.let { currentFile(it).delete(); oldFile(it).delete() }
        _entries.value = emptyList()
    }

    /** 未捕捉例外をログに残してから既定のハンドラ（クラッシュ）へ委譲する */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { e("Crash", "Uncaught exception in thread ${thread.name}", throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
