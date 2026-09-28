package com.itkcraft.alarmclock.ui.alarms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.FilledTonalButton
import com.itkcraft.alarmclock.ui.components.SwipeToReveal
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.alarm.AlarmActions
import com.itkcraft.alarmclock.alarm.Schedule
import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.AlarmGroup
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.ui.components.TimeText
import com.itkcraft.alarmclock.ui.formatNext
import com.itkcraft.alarmclock.ui.repeatSummary
import kotlinx.coroutines.delay

@Composable
fun AlarmListScreen(
    onEdit: (alarmId: Long?, groupId: Long?) -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val alarms by Repository.alarms.collectAsStateWithLifecycle()
    val groups by Repository.groups.collectAsStateWithLifecycle()
    val settings by Repository.settings.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<AlarmGroup?>(null) }
    var bulkOpen by remember { mutableStateOf(false) }

    val sorted = remember(alarms) { alarms.sortedWith(compareBy({ it.hour }, { it.minute })) }
    val nextRing = remember(alarms, groups, now) {
        alarms.filter { Schedule.isActive(it, groups) }.mapNotNull { Schedule.nextRing(it, now) }.minOrNull()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("アラーム", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        if (alarms.isNotEmpty()) {
                            FilledTonalButton(onClick = { bulkOpen = true }, shape = MaterialTheme.shapes.large) {
                                Icon(Icons.Rounded.Checklist, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("一括操作")
                            }
                        }
                    }
                    Text(
                        if (nextRing != null) "次のアラーム: ${formatNext(nextRing, settings.use24h)}（${AlarmActions.untilText(nextRing - now)}）"
                        else "ON のアラームはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (nextRing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "タップで編集 ・ 長押しで次回スキップ ・ 右へスワイプで削除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (alarms.isEmpty() && groups.isEmpty()) {
                item { EmptyState() }
            }

            groups.forEach { group ->
                val members = sorted.filter { it.groupId == group.id }
                item(key = "g${group.id}") {
                    GroupCard(
                        group = group,
                        members = members,
                        now = now,
                        use24h = settings.use24h,
                        onToggleExpand = { AlarmActions.toggleExpanded(group) },
                        onEdit = { editGroup = group },
                        onSkip = { onMessage(AlarmActions.toggleGroupSkip(ctx, group)) },
                        onEnable = { AlarmActions.setGroupEnabled(ctx, group, it) },
                        onEditAlarm = { onEdit(it.id, group.id) },
                        onSkipAlarm = { onMessage(AlarmActions.toggleSkip(ctx, it)) },
                        onEnableAlarm = { a, on -> AlarmActions.setEnabled(ctx, a, on)?.let(onMessage) },
                        onDeleteAlarm = { AlarmActions.delete(ctx, it.id); onMessage("削除しました") },
                        onAddAlarm = { onEdit(null, group.id) },
                    )
                }
            }

            val ungrouped = sorted.filter { a -> a.groupId == null || groups.none { it.id == a.groupId } }
            if (groups.isNotEmpty() && ungrouped.isNotEmpty()) {
                item {
                    Text(
                        "グループなし",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                }
            }
            items(ungrouped, key = { "a${it.id}" }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    active = alarm.enabled,
                    now = now,
                    use24h = settings.use24h,
                    onClick = { onEdit(alarm.id, null) },
                    onLongClick = { onMessage(AlarmActions.toggleSkip(ctx, alarm)) },
                    onEnable = { AlarmActions.setEnabled(ctx, alarm, it)?.let(onMessage) },
                    onDelete = { AlarmActions.delete(ctx, alarm.id); onMessage("削除しました") },
                )
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(
                onClick = { menuOpen = true },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Rounded.Add, contentDescription = "追加") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("アラームを追加") },
                    leadingIcon = { Icon(Icons.Rounded.AlarmAdd, null) },
                    onClick = { menuOpen = false; onEdit(null, null) },
                )
                DropdownMenuItem(
                    text = { Text("グループを追加") },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                    onClick = { menuOpen = false; editGroup = AlarmGroup(id = 0, name = "") },
                )
            }
        }
    }

    if (bulkOpen) {
        BulkDialog(
            alarms = sorted,
            groups = groups,
            use24h = settings.use24h,
            onDismiss = { bulkOpen = false },
            onMessage = onMessage,
        )
    }

    editGroup?.let { g ->
        GroupDialog(
            group = g,
            onDismiss = { editGroup = null },
            onSave = { name ->
                AlarmActions.saveGroup(ctx, if (g.id == 0L) AlarmGroup(Repository.newId(), name) else g.copy(name = name))
                editGroup = null
            },
            onDelete = { deleteAlarms ->
                AlarmActions.deleteGroup(ctx, g, deleteAlarms)
                editGroup = null
            },
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.AlarmAdd, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Text("アラームがありません", style = MaterialTheme.typography.titleMedium)
        Text("右下の ＋ から追加できます", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AlarmCard(
    alarm: Alarm,
    active: Boolean,
    now: Long,
    use24h: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEnable: (Boolean) -> Unit,
    onDelete: () -> Unit,
    inGroup: Boolean = false,
) {
    SwipeToReveal(onDelete = onDelete, shape = MaterialTheme.shapes.large) {
        AlarmCardBody(alarm, active, now, use24h, onClick, onLongClick, onEnable, inGroup)
    }
}

@Composable
private fun AlarmCardBody(
    alarm: Alarm,
    active: Boolean,
    now: Long,
    use24h: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEnable: (Boolean) -> Unit,
    inGroup: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val skipped = Schedule.isSkipped(alarm, now)
    val alpha by animateFloatAsState(if (active) 1f else 0.45f, label = "alpha")
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (inGroup) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (skipped) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(alpha)) {
                TimeText(alarm.hour, alarm.minute, use24h, 38.sp)
                if (alarm.memo.isNotBlank()) {
                    Text(alarm.memo, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(repeatSummary(alarm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (alarm.vibrate) Icon(Icons.Rounded.Vibration, "バイブ", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (alarm.sound != null) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(alarm.sound.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (skipped) {
                    Spacer(Modifier.height(4.dp))
                    SkipBadge(
                        Schedule.nextRing(alarm, now)?.let { "スキップ中 → 次回 ${formatNext(it, use24h)}" } ?: "スキップ中（次回なし）",
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onEnable)
        }
    }
}

@Composable
private fun SkipBadge(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SkipNext, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GroupCard(
    group: AlarmGroup,
    members: List<Alarm>,
    now: Long,
    use24h: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
    onEnable: (Boolean) -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onSkipAlarm: (Alarm) -> Unit,
    onEnableAlarm: (Alarm, Boolean) -> Unit,
    onDeleteAlarm: (Alarm) -> Unit,
    onAddAlarm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(if (group.expanded) 180f else 0f, label = "rot")
    val onCount = members.count { it.enabled }
    val skippedAll = group.enabled && members.any { it.enabled } && members.filter { it.enabled }.all { Schedule.isSkipped(it, now) }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, if (group.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onEdit,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkip()
                    },
                )
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleExpand) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = if (group.expanded) "折りたたむ" else "展開", modifier = Modifier.rotate(rotation))
            }
            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${members.size}件（ON ${onCount}件）" + if (skippedAll) " ・ 次回スキップ中" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (skippedAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = group.enabled, onCheckedChange = onEnable)
        }
        AnimatedVisibility(visible = group.expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEach { a ->
                    AlarmCard(
                        alarm = a,
                        active = a.enabled && group.enabled,
                        now = now,
                        use24h = use24h,
                        onClick = { onEditAlarm(a) },
                        onLongClick = { onSkipAlarm(a) },
                        onEnable = { onEnableAlarm(a, it) },
                        onDelete = { onDeleteAlarm(a) },
                        inGroup = true,
                    )
                }
                TextButton(onClick = onAddAlarm, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("このグループにアラームを追加")
                }
            }
        }
    }
}

@Composable
private fun GroupDialog(
    group: AlarmGroup,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (deleteAlarms: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = group.id == 0L
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("グループを削除") },
            text = { Text("「${group.name}」を削除します。所属するアラームはどうしますか？") },
            confirmButton = { TextButton(onClick = { onDelete(false) }) { Text("アラームは残す") } },
            dismissButton = {
                TextButton(onClick = { onDelete(true) }) { Text("アラームも削除", color = MaterialTheme.colorScheme.error) }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "グループを追加" else "グループを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("グループ名") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("このグループを削除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { "グループ" }) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
