package com.itkcraft.alarmclock.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.alarm.AlarmActions
import com.itkcraft.alarmclock.alarm.Schedule
import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.ui.components.RowDivider
import com.itkcraft.alarmclock.ui.components.SectionCard
import com.itkcraft.alarmclock.ui.components.SettingRow
import com.itkcraft.alarmclock.ui.components.SoundPickerSheet
import com.itkcraft.alarmclock.ui.components.SubPage
import com.itkcraft.alarmclock.ui.components.SwitchRow
import com.itkcraft.alarmclock.ui.dayLabel
import com.itkcraft.alarmclock.ui.formatDate
import com.itkcraft.alarmclock.ui.formatNext
import com.itkcraft.alarmclock.ui.weekOrder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AlarmEditScreen(
    alarmId: Long?,
    initialGroupId: Long?,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val settings by Repository.settings.collectAsStateWithLifecycle()
    val groups by Repository.groups.collectAsStateWithLifecycle()
    val original = remember(alarmId) { Repository.alarms.value.firstOrNull { it.id == alarmId } }
    val isNew = original == null
    var draft by remember(alarmId) {
        mutableStateOf(
            original ?: Alarm(
                id = Repository.newId(),
                hour = 7,
                minute = 0,
                repeat = true,
                vibrate = true,
                groupId = initialGroupId,
            ),
        )
    }
    val timeState = rememberTimePickerState(draft.hour, draft.minute, settings.use24h)
    var keyboardInput by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showSound by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun current() = draft.copy(hour = timeState.hour, minute = timeState.minute)

    fun save() {
        AlarmActions.save(ctx, current())?.let(onMessage)
        onClose()
    }

    SubPage(
        title = if (isNew) "新規アラーム" else "アラーム編集",
        onBack = onClose,
        actions = {
            IconButton(onClick = ::save) { Icon(Icons.Rounded.Check, contentDescription = "保存", tint = MaterialTheme.colorScheme.primary) }
        },
    ) {
        // 時刻
        SectionCard {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                )
                if (keyboardInput) TimeInput(state = timeState, colors = colors) else TimePicker(state = timeState, colors = colors)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { keyboardInput = !keyboardInput }) {
                        Icon(if (keyboardInput) Icons.Rounded.Schedule else Icons.Rounded.Keyboard, contentDescription = "入力方法の切替")
                    }
                    val next = Schedule.nextTrigger(current().copy(skipAt = null), System.currentTimeMillis())
                    Text(
                        if (next != null) "次回: ${formatNext(next, settings.use24h)}（${AlarmActions.untilText(next - System.currentTimeMillis())}）"
                        else "指定日時が過去です",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (next != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.memo,
            onValueChange = { draft = draft.copy(memo = it.take(50)) },
            label = { Text("メモ") },
            placeholder = { Text("例: 仕事の日") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        // 繰り返し・曜日・日付
        SectionCard(title = "鳴らす日") {
            val dateMode = draft.date != null
            SwitchRow(
                title = "毎週繰り返す",
                subtitle = if (dateMode) "日付指定中は無効です" else if (draft.repeat) "選んだ曜日に毎週鳴ります（未選択なら毎日）" else "1回鳴ったら OFF になります",
                checked = draft.repeat,
                onCheckedChange = { draft = draft.copy(repeat = it) },
                icon = Icons.Rounded.Repeat,
                enabled = !dateMode,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekOrder.forEach { d -> DayToggle(d, d in draft.days, !dateMode) { on ->
                    draft = draft.copy(days = if (on) draft.days + d else draft.days - d)
                } }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                QuickChip("毎日", draft.days.isEmpty() || draft.days.size == 7, !dateMode) { draft = draft.copy(days = emptySet()) }
                QuickChip("平日", draft.days == weekdays, !dateMode) { draft = draft.copy(days = weekdays) }
                QuickChip("土日", draft.days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), !dateMode) {
                    draft = draft.copy(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
                }
            }
            RowDivider()
            val clearDate: (@Composable () -> Unit)? = if (draft.date != null) {
                { IconButton(onClick = { draft = draft.copy(date = null) }) { Icon(Icons.Rounded.Close, "日付指定を解除") } }
            } else null
            SettingRow(
                title = "日付指定",
                subtitle = draft.date?.let { "${formatDate(it)} に1回だけ鳴ります" } ?: "指定なし",
                icon = Icons.Rounded.Event,
                onClick = { showDate = true },
                trailing = clearDate,
            )
        }

        SectionCard(title = "サウンド・バイブ") {
            SettingRow(
                title = "アラーム音",
                subtitle = draft.sound?.name ?: "既定の音（${settings.defaultSound.name}）",
                icon = Icons.Rounded.MusicNote,
                onClick = { showSound = true },
            )
            RowDivider()
            SwitchRow(
                title = "バイブレーション",
                subtitle = if (!settings.vibration) "設定でバイブレーションが OFF のため振動しません" else null,
                checked = draft.vibrate,
                onCheckedChange = { draft = draft.copy(vibrate = it) },
                icon = Icons.Rounded.Vibration,
            )
        }

        SectionCard(title = "グループ") {
            Box {
                SettingRow(
                    title = "所属グループ",
                    subtitle = groups.firstOrNull { it.id == draft.groupId }?.name ?: "なし",
                    icon = Icons.Rounded.Folder,
                    onClick = { showGroupMenu = true },
                )
                DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                    DropdownMenuItem(text = { Text("なし") }, onClick = { draft = draft.copy(groupId = null); showGroupMenu = false })
                    groups.forEach { g ->
                        DropdownMenuItem(text = { Text(g.name) }, onClick = { draft = draft.copy(groupId = g.id); showGroupMenu = false })
                    }
                }
            }
        }

        Button(
            onClick = ::save,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("保存", style = MaterialTheme.typography.titleMedium) }

        if (!isNew) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(6.dp))
                Text("このアラームを削除", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDate) {
        val today = LocalDate.now()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (draft.date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        draft = draft.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("キャンセル") } },
        ) { DatePicker(state = dateState) }
    }

    if (showSound) {
        SoundPickerSheet(
            current = draft.sound,
            allowDefault = true,
            onSelect = { draft = draft.copy(sound = it); showSound = false },
            onDismiss = { showSound = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("アラームを削除") },
            text = { Text("このアラームを削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    alarmId?.let { AlarmActions.delete(ctx, it) }
                    confirmDelete = false
                    onClose()
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when {
        selected && enabled -> MaterialTheme.colorScheme.onPrimary
        day == DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = { onChange(!selected) },
        enabled = enabled,
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(dayLabel(day), color = textColor.copy(alpha = if (enabled) 1f else 0.4f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        shape = MaterialTheme.shapes.large,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
