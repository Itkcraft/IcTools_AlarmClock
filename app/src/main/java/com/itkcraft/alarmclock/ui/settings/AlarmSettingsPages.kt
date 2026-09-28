package com.itkcraft.alarmclock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.alarm.AudioRouting
import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.NoHeadphoneAction
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.data.ThemeMode
import com.itkcraft.alarmclock.ui.components.AppSlider
import com.itkcraft.alarmclock.ui.components.PreviewPlayer
import com.itkcraft.alarmclock.ui.components.RowDivider
import com.itkcraft.alarmclock.ui.components.SectionCard
import com.itkcraft.alarmclock.ui.components.SettingRow
import com.itkcraft.alarmclock.ui.components.SoundPickerSheet
import com.itkcraft.alarmclock.ui.components.SubPage
import com.itkcraft.alarmclock.ui.components.SwitchRow
import com.itkcraft.alarmclock.ui.formatHm
import com.itkcraft.alarmclock.ui.formatTime

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

// ---------------- アラーム音と音量 ----------------

@Composable
fun SoundSettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by Repository.settings.collectAsStateWithLifecycle()
    val alarms by Repository.alarms.collectAsStateWithLifecycle()
    val preview = remember { PreviewPlayer(ctx) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    var pickDefault by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<Alarm?>(null) }
    var volume by remember(s.volume) { mutableFloatStateOf(s.volume.toFloat()) }

    SubPage("アラーム音と音量", onBack) {
        SectionCard(title = "既定のアラーム音（一括設定）") {
            SettingRow("アラーム音", s.defaultSound.name, Icons.Rounded.MusicNote, onClick = { pickDefault = true })
            Hint("個別に音を指定していないアラームとタイマーはこの音で鳴ります。")
        }

        SectionCard(title = "音量（メディア音）") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                AppSlider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = { Repository.updateSettings { st -> st.copy(volume = volume.toInt().coerceIn(1, 100)) } },
                    valueRange = 1f..100f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${volume.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { preview.toggle(s.defaultSound) }) {
                    Icon(
                        if (preview.playingUri == s.defaultSound.uri) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = "試聴",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Hint("鳴動中は端末のメディア音量をこの値に合わせ、停止後に元の音量へ戻します。イヤホン接続中はイヤホンから鳴ります。")
        }

        SectionCard(title = "アラームごとの音") {
            if (alarms.isEmpty()) {
                Hint("アラームがありません。")
            }
            alarms.sortedWith(compareBy({ it.hour }, { it.minute })).forEachIndexed { i, a ->
                if (i > 0) RowDivider()
                val badge: (@Composable () -> Unit)? = if (a.sound != null) {
                    { Text("個別", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                } else null
                SettingRow(
                    title = formatHm(a.hour, a.minute, s.use24h) + if (a.memo.isNotBlank()) "  ${a.memo}" else "",
                    subtitle = a.sound?.name ?: "既定（${s.defaultSound.name}）",
                    icon = Icons.Rounded.Alarm,
                    onClick = { pickFor = a },
                    trailing = badge,
                )
            }
            if (alarms.any { it.sound != null }) {
                TextButton(
                    onClick = { Repository.updateAlarms { a, g -> a.map { it.copy(sound = null) } to g } },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("すべて既定の音に戻す") }
            }
        }
    }

    if (pickDefault) {
        SoundPickerSheet(
            current = s.defaultSound,
            allowDefault = false,
            onSelect = { ref -> if (ref != null) Repository.updateSettings { it.copy(defaultSound = ref) }; pickDefault = false },
            onDismiss = { pickDefault = false },
        )
    }
    pickFor?.let { alarm ->
        SoundPickerSheet(
            current = alarm.sound,
            allowDefault = true,
            onSelect = { ref ->
                Repository.alarms.value.firstOrNull { it.id == alarm.id }?.let { Repository.upsertAlarm(it.copy(sound = ref)) }
                pickFor = null
            },
            onDismiss = { pickFor = null },
        )
    }
}

// ---------------- スヌーズ・消音 ----------------

@Composable
fun SnoozeSettingsPage(onBack: () -> Unit) {
    val s by Repository.settings.collectAsStateWithLifecycle()
    var snooze by remember(s.snoozeMinutes) { mutableFloatStateOf(s.snoozeMinutes.toFloat()) }
    SubPage("スヌーズ・消音", onBack) {
        SectionCard(title = "スヌーズの長さ") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppSlider(
                    value = snooze,
                    onValueChange = { snooze = it },
                    onValueChangeFinished = { Repository.updateSettings { st -> st.copy(snoozeMinutes = snooze.toInt()) } },
                    valueRange = 1f..30f,
                    modifier = Modifier.weight(1f),
                )
                Text("${snooze.toInt()}分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            }
            ChipRow(listOf(3, 5, 10, 15), s.snoozeMinutes, { "${it}分" }) { v -> Repository.updateSettings { it.copy(snoozeMinutes = v) } }
        }
        SectionCard(title = "自動消音（鳴り始めから停止までの長さ）") {
            ChipRow(listOf(0, 1, 3, 5, 10, 15, 20, 30, 60), s.silenceMinutes, { if (it == 0) "しない" else "${it}分" }) { v ->
                Repository.updateSettings { it.copy(silenceMinutes = v) }
            }
            Hint("指定時間が経過すると自動で停止し、通知でお知らせします。")
        }
    }
}

@Composable
private fun ChipRow(values: List<Int>, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { v ->
            FilterChip(
                selected = v == selected,
                onClick = { onSelect(v) },
                label = { Text(label(v)) },
                shape = MaterialTheme.shapes.large,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

// ---------------- 鳴動の動作 ----------------

@Composable
fun BehaviorSettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by Repository.settings.collectAsStateWithLifecycle()
    var headphones by remember { mutableStateOf(AudioRouting.describe(ctx)) }
    LifecycleResumeEffect(Unit) {
        headphones = AudioRouting.describe(ctx)
        onPauseOrDispose { }
    }
    var ramp by remember(s.gradualSeconds) { mutableFloatStateOf(s.gradualSeconds.toFloat()) }

    SubPage("鳴動の動作", onBack) {
        SectionCard(title = "音量可変") {
            SwitchRow(
                "音量を徐々に上げる",
                s.gradualVolume,
                { v -> Repository.updateSettings { it.copy(gradualVolume = v) } },
                subtitle = "小さな音から設定音量まで上げていきます",
                icon = Icons.Rounded.TrendingUp,
            )
            if (s.gradualVolume) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppSlider(
                        value = ramp,
                        onValueChange = { ramp = it },
                        onValueChangeFinished = { Repository.updateSettings { st -> st.copy(gradualSeconds = ramp.toInt()) } },
                        valueRange = 5f..120f,
                        step = 5f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${ramp.toInt()}秒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        SectionCard(title = "イヤホン未接続時の動作") {
            NoHeadphoneAction.entries.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Repository.updateSettings { it.copy(noHeadphoneAction = a) } }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.noHeadphoneAction == a, onClick = { Repository.updateSettings { it.copy(noHeadphoneAction = a) } })
                    Column {
                        Text(a.label(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when (a) {
                                NoHeadphoneAction.SPEAKER -> "端末のスピーカーからメディア音で鳴らします"
                                NoHeadphoneAction.VIBRATE_ONLY -> "音は出さず振動のみで知らせます"
                                NoHeadphoneAction.SILENT -> "音も振動も出さず、画面と通知のみ表示します"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            RowDivider()
            SettingRow("現在のイヤホン", headphones, Icons.Rounded.Headphones)
            Hint("鳴動中にイヤホンを抜き差しした場合も、この設定に従って切り替わります。")
        }

        SectionCard(title = "バイブレーション") {
            SwitchRow(
                "アラーム音とともに振動",
                s.vibration,
                { v -> Repository.updateSettings { it.copy(vibration = v) } },
                subtitle = "OFF にすると全アラームで振動しません（「バイブのみ」動作時を除く）",
                icon = Icons.Rounded.Vibration,
            )
        }
    }
}

// ---------------- 表示 ----------------

@Composable
fun DisplaySettingsPage(onBack: () -> Unit) {
    val s by Repository.settings.collectAsStateWithLifecycle()
    val now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    SubPage("時刻表示・テーマ", onBack) {
        SectionCard(title = "時刻表示形式") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                listOf(true to "24時間", false to "12時間").forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = s.use24h == v,
                        onClick = { Repository.updateSettings { it.copy(use24h = v) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label, maxLines = 1) }
                }
            }
            Hint("表示例: ${formatTime(now, s.use24h)}")
        }
        SectionCard(title = "テーマ") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.theme == m,
                        onClick = { Repository.updateSettings { it.copy(theme = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(m.label(), maxLines = 1) }
                }
            }
        }
    }
}
