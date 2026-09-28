package com.itkcraft.alarmclock.ui.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.BuildConfig
import com.itkcraft.alarmclock.alarm.AlarmScheduler
import com.itkcraft.alarmclock.alarm.AlarmService
import com.itkcraft.alarmclock.alarm.AudioRouting
import com.itkcraft.alarmclock.alarm.Schedule
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.ui.components.SectionCard
import com.itkcraft.alarmclock.ui.components.SubPage
import com.itkcraft.alarmclock.ui.theme.MonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun deviceInfo(ctx: Context): String = buildString {
    appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    appendLine("Headphones: ${AudioRouting.describe(ctx)}")
    appendLine("Permissions: ${PermissionStatus.summary(ctx)}")
    append("Alarms: ${Repository.alarms.value.size}, Groups: ${Repository.groups.value.size}")
}

private fun scheduleDump(): String {
    val now = System.currentTimeMillis()
    val groups = Repository.groups.value
    val lines = Repository.alarms.value.map { a ->
        val active = Schedule.isActive(a, groups)
        val next = Schedule.nextTrigger(a, now)?.let(AlarmScheduler::fmt) ?: "-"
        val ring = Schedule.nextRing(a, now)?.let(AlarmScheduler::fmt) ?: "-"
        "#${a.id % 100000} %02d:%02d active=$active repeat=${a.repeat} days=${a.days.size} date=${a.date ?: "-"}\n  trigger=$next ring=$ring skip=${a.skipAt?.let(AlarmScheduler::fmt) ?: "-"}"
            .format(a.hour, a.minute)
    }
    val t = Repository.timer.value
    return (lines + "Timer: endAt=${t.endAt?.let(AlarmScheduler::fmt) ?: "-"} paused=${t.pausedRemainingMs}").joinToString("\n")
}

@Composable
fun DebugPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<AppLog.Level?>(null) }
    var dump by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf(deviceInfo(ctx)) }
    val listState = rememberLazyListState()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(AppLog.exportText(deviceInfo(ctx) + "\n\nSchedule:\n" + scheduleDump()).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(ctx, "ログを保存しました", Toast.LENGTH_SHORT).show()
            AppLog.i("Debug", "log exported")
        } catch (e: Exception) {
            AppLog.e("Debug", "export failed", e)
            Toast.makeText(ctx, "保存に失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    val shown = remember(entries, filter) {
        val f = filter
        if (f == null) entries else entries.filter { it.level.ordinal >= f.ordinal && it.time != 0L }
    }
    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) listState.scrollToItem(shown.size - 1) }

    SubPage("デバッグ", onBack) {
        SectionCard(title = "端末情報") {
            SelectionContainer {
                Text(info, style = MonoStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            TextButton(onClick = { info = deviceInfo(ctx) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Rounded.Refresh, null); Text(" 更新")
            }
        }

        SectionCard(title = "ツール") {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { AlarmService.start(ctx, AlarmService.KIND_TEST, 0) }) {
                    Icon(Icons.Rounded.Alarm, null); Text(" 今すぐテスト鳴動")
                }
                FilledTonalButton(onClick = {
                    AlarmScheduler.scheduleSnooze(ctx, AlarmService.KIND_TEST, 0, System.currentTimeMillis() + 10_000)
                    Toast.makeText(ctx, "10秒後に鳴ります（画面を消して確認できます）", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.Timer, null); Text(" 10秒後にテスト") }
                FilledTonalButton(onClick = { dump = scheduleDump() }) { Icon(Icons.Rounded.List, null); Text(" 予定一覧") }
                FilledTonalButton(onClick = {
                    AlarmScheduler.rescheduleAll(ctx)
                    Toast.makeText(ctx, "再登録しました", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.Refresh, null); Text(" 再登録") }
            }
        }

        SectionCard(title = "ログコンソール") {
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(start = 12.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("すべて") })
                    listOf(AppLog.Level.I to "情報", AppLog.Level.W to "警告", AppLog.Level.E to "エラー").forEach { (l, name) ->
                        FilterChip(selected = filter == l, onClick = { filter = l }, label = { Text("$name以上") })
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(12.dp)
                    .background(Color(0xFF0B0B0D), MaterialTheme.shapes.medium),
            ) {
                SelectionContainer {
                    LazyColumn(state = listState, modifier = Modifier.padding(8.dp)) {
                        items(shown) { e ->
                            Text(
                                if (e.time == 0L) e.message else e.format(),
                                style = MonoStyle,
                                color = when (e.level) {
                                    AppLog.Level.E -> Color(0xFFFF6B6B)
                                    AppLog.Level.W -> Color(0xFFFFB35C)
                                    AppLog.Level.I -> Color(0xFFE8E6E3)
                                    AppLog.Level.D -> Color(0xFF9A969E)
                                },
                            )
                        }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exporter.launch("mediaalarm-log-$stamp.txt")
                }) { Icon(Icons.Rounded.FileDownload, null); Text(" エクスポート") }
                TextButton(onClick = { AppLog.clear() }) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
                    Text(" ログ消去", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "ログは端末内にのみ保存され、エクスポートした場合を除き外部へ出ることはありません。メモの内容は記録しません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    dump?.let { text ->
        AlertDialog(
            onDismissRequest = { dump = null },
            title = { Text("予定一覧") },
            text = {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    SelectionContainer { Text(text.ifBlank { "なし" }, style = MonoStyle) }
                }
            },
            confirmButton = { TextButton(onClick = { dump = null }) { Text("閉じる") } },
        )
    }
}
