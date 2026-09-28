package com.itkcraft.alarmclock.ui.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.alarm.TimerActions
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.ui.formatDuration
import com.itkcraft.alarmclock.ui.formatTime
import kotlinx.coroutines.delay

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val timer by Repository.timer.collectAsStateWithLifecycle()
    val settings by Repository.settings.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(timer.running) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (timer.running) 200 else 1000)
        }
    }

    val remaining = timer.remaining(now)
    val total = when {
        timer.running || timer.paused -> maxOf(timer.lastDurationMs, remaining, 1L)
        else -> maxOf(remaining, 1L)
    }
    val progress by animateFloatAsState(
        targetValue = if (timer.running || timer.paused) remaining.toFloat() / total else if (remaining > 0) 1f else 0f,
        label = "progress",
    )
    val endAt = timer.endAt ?: (now + remaining)

    Column(Modifier.fillMaxSize()) {
    Text(
        "タイマー",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
    )
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
    val minH = maxHeight
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = minH)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {

        // 残り時間リング
        val ringColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(ringColor, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatDuration(remaining),
                    fontSize = if (remaining >= 3_600_000) 48.sp else 60.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    when {
                        timer.running -> "動作中"
                        timer.paused -> "一時停止中"
                        remaining > 0 -> "待機中"
                        else -> "時間を追加してください"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (remaining > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${formatTime(endAt, settings.use24h)} に鳴ります",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 加算ボタン
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1, 5, 10).forEach { min ->
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        TimerActions.add(ctx, min * 60_000L)
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) { Text("+${min}分", style = MaterialTheme.typography.titleLarge) }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 削除 / 開始・停止
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            OutlinedIconButton(
                onClick = { TimerActions.reset(ctx) },
                enabled = remaining > 0 || timer.running || timer.paused,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
            ) { Icon(Icons.Rounded.Delete, contentDescription = "削除（リセット）") }

            FilledIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    TimerActions.toggle(ctx)
                },
                enabled = remaining > 0,
                modifier = Modifier.size(92.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    if (timer.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (timer.running) "停止" else "開始",
                    modifier = Modifier.size(44.dp),
                )
            }
            OutlinedIconButton(
                onClick = { TimerActions.restoreLast() },
                enabled = !timer.running && !timer.paused && timer.lastDurationMs > 0 && timer.durationMs != timer.lastDurationMs,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
            ) { Icon(Icons.Rounded.History, contentDescription = "前回の時間に戻す") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            if (timer.lastDurationMs > 0) "前回: ${formatDuration(timer.lastDurationMs)}" else " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
    }
}
