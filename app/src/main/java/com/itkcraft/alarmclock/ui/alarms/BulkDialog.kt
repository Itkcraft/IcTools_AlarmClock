package com.itkcraft.alarmclock.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.itkcraft.alarmclock.alarm.AlarmActions
import com.itkcraft.alarmclock.data.Alarm
import com.itkcraft.alarmclock.data.AlarmGroup
import com.itkcraft.alarmclock.ui.formatHm
import com.itkcraft.alarmclock.ui.repeatSummary

/** アラームの一括操作（ON/OFF・グループ移動・削除） */
@Composable
fun BulkDialog(
    alarms: List<Alarm>,
    groups: List<AlarmGroup>,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var moveMenu by remember { mutableStateOf(false) }
    val allSelected = alarms.isNotEmpty() && selected.size == alarms.size
    val has = selected.isNotEmpty()

    fun done(msg: String) {
        onMessage(msg)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text("一括操作", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = if (allSelected) emptySet() else alarms.map { it.id }.toSet() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = allSelected, onCheckedChange = { selected = if (it) alarms.map { a -> a.id }.toSet() else emptySet() })
                    Text("すべて選択", modifier = Modifier.weight(1f))
                    Text("${selected.size}件選択中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(alarms, key = { it.id }) { a ->
                        val checked = a.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (checked) selected - a.id else selected + a.id }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + a.id else selected - a.id })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    formatHm(a.hour, a.minute, use24h) + if (a.memo.isNotBlank()) "  ${a.memo}" else "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        repeatSummary(a),
                                        groups.firstOrNull { it.id == a.groupId }?.name,
                                        if (a.enabled) "ON" else "OFF",
                                    ).joinToString(" ・ "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { AlarmActions.bulkEnable(ctx, selected, true); done("${selected.size}件を ON にしました") },
                            enabled = has,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.weight(1f),
                        ) { Text("ON") }
                        FilledTonalButton(
                            onClick = { AlarmActions.bulkEnable(ctx, selected, false); done("${selected.size}件を OFF にしました") },
                            enabled = has,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.weight(1f),
                        ) { Text("OFF") }
                    }
                    Box {
                        FilledTonalButton(
                            onClick = { moveMenu = true },
                            enabled = has,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.DriveFileMove, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("グループへ移動")
                        }
                        DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                            DropdownMenuItem(text = { Text("グループなし") }, onClick = {
                                moveMenu = false
                                AlarmActions.bulkMove(ctx, selected, null); done("${selected.size}件をグループなしへ移動しました")
                            })
                            groups.forEach { g ->
                                DropdownMenuItem(text = { Text(g.name) }, onClick = {
                                    moveMenu = false
                                    AlarmActions.bulkMove(ctx, selected, g.id); done("${selected.size}件を「${g.name}」へ移動しました")
                                })
                            }
                        }
                    }
                    Button(
                        onClick = { AlarmActions.bulkDelete(ctx, selected); done("${selected.size}件を削除しました") },
                        enabled = has,
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("削除")
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("閉じる") }
                }
            }
        }
    }
}
