package com.itkcraft.alarmclock.sound

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import com.itkcraft.alarmclock.data.SoundRef
import com.itkcraft.alarmclock.log.AppLog
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 内蔵アラーム音。
 * ライセンス問題を避けるため、音源ファイルは同梱せずコードで波形を合成して WAV を生成する。
 */
object Sounds {
    private const val TAG = "Sounds"
    private const val RATE = 44100
    private const val GEN_VERSION = 1

    data class Builtin(val id: String, val name: String)

    val builtins = listOf(
        Builtin("chime", "チャイム"),
        Builtin("gentle", "やさしい和音"),
        Builtin("marimba", "マリンバ風"),
        Builtin("beep", "ビープ"),
        Builtin("digital", "デジタル"),
    )

    fun builtinRef(id: String) = builtins.first { it.id == id }.let { SoundRef("builtin:${it.id}", it.name) }

    val fallback get() = builtinRef("beep")

    private fun dir(context: Context) = File(context.filesDir, "sounds")

    /** 起動時に内蔵音 WAV を生成（生成済みならスキップ） */
    fun ensureGenerated(context: Context) {
        val d = dir(context).apply { mkdirs() }
        val marker = File(d, ".v$GEN_VERSION")
        if (marker.exists() && builtins.all { File(d, "${it.id}.wav").exists() }) return
        builtins.forEach { b ->
            runCatching { writeWav(File(d, "${b.id}.wav"), synth(b.id)) }
                .onFailure { AppLog.e(TAG, "generate ${b.id} failed", it) }
        }
        marker.createNewFile()
        AppLog.i(TAG, "builtin sounds generated")
    }

    /** SoundRef を再生可能な Uri へ解決 */
    fun resolve(context: Context, ref: SoundRef): Uri =
        if (ref.uri.startsWith("builtin:")) {
            val id = ref.uri.removePrefix("builtin:")
            val f = File(dir(context), "$id.wav")
            if (!f.exists()) ensureGenerated(context)
            Uri.fromFile(f)
        } else Uri.parse(ref.uri)

    /** 端末のシステムアラーム音一覧 */
    fun systemAlarms(context: Context): List<SoundRef> = runCatching {
        val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val c = rm.cursor
        val list = mutableListOf<SoundRef>()
        while (c.moveToNext()) {
            val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = rm.getRingtoneUri(c.position)
            list += SoundRef(uri.toString(), title)
        }
        list
    }.onFailure { AppLog.w(TAG, "system alarms query failed", it) }.getOrDefault(emptyList())

    /** ユーザーが選んだファイルの表示名 */
    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "音声ファイル"

    // ---- 波形合成 ----

    private fun tone(freq: Double, t: Double, harmonics: DoubleArray = doubleArrayOf(1.0)): Double {
        var v = 0.0
        harmonics.forEachIndexed { i, a -> v += a * sin(2 * PI * freq * (i + 1) * t) }
        return v / harmonics.sum()
    }

    /** notes: (開始秒, 周波数, 長さ秒, 減衰係数) */
    private fun render(totalSec: Double, notes: List<DoubleArray>, harmonics: DoubleArray, gain: Double = 0.8): ShortArray {
        val n = (totalSec * RATE).toInt()
        val buf = DoubleArray(n)
        for (note in notes) {
            val (start, freq, len, decay) = note.toList()
            val s0 = (start * RATE).toInt()
            val s1 = minOf(n, ((start + len) * RATE).toInt())
            for (i in s0 until s1) {
                val t = (i - s0).toDouble() / RATE
                val attack = minOf(1.0, t / 0.005)
                val release = minOf(1.0, (s1 - i).toDouble() / (0.01 * RATE))
                buf[i] += tone(freq, t, harmonics) * exp(-decay * t) * attack * release
            }
        }
        val peak = buf.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        return ShortArray(n) { (buf[it] / peak * gain * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun note(start: Double, freq: Double, len: Double, decay: Double) = doubleArrayOf(start, freq, len, decay)

    private fun synth(id: String): ShortArray = when (id) {
        "chime" -> render(
            3.2,
            listOf(note(0.0, 1318.5, 1.5, 2.5), note(0.4, 1046.5, 1.5, 2.5), note(0.8, 784.0, 1.5, 2.5), note(1.2, 523.3, 1.9, 2.0)),
            doubleArrayOf(1.0, 0.5, 0.25, 0.1),
        )
        "gentle" -> render(
            3.6,
            listOf(note(0.0, 523.3, 3.4, 0.8), note(0.3, 659.3, 3.1, 0.8), note(0.6, 784.0, 2.8, 0.8), note(0.9, 1046.5, 2.5, 0.9)),
            doubleArrayOf(1.0, 0.2),
            0.6,
        )
        "marimba" -> {
            val seq = listOf(784.0, 988.0, 1175.0, 988.0, 784.0, 1175.0, 1568.0, 1175.0)
            render(2.4, seq.mapIndexed { i, f -> note(i * 0.25, f, 0.5, 9.0) }, doubleArrayOf(1.0, 0.0, 0.0, 0.3))
        }
        "beep" -> render(1.0, listOf(note(0.0, 880.0, 0.18, 0.0), note(0.25, 880.0, 0.18, 0.0), note(0.5, 880.0, 0.18, 0.0)), doubleArrayOf(1.0, 0.3), 0.7)
        else -> render(1.0, listOf(note(0.0, 2000.0, 0.08, 0.0), note(0.12, 2000.0, 0.08, 0.0)), doubleArrayOf(1.0), 0.55)
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            fun le32(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff); out.write(v shr 16 and 0xff); out.write(v shr 24 and 0xff) }
            fun le16(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff) }
            out.writeBytes("RIFF"); le32(36 + dataLen); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(RATE); le32(RATE * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(dataLen)
            pcm.forEach { le16(it.toInt()) }
        }
    }
}
