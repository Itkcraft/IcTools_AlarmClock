package com.itkcraft.alarmclock.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged

/** 角丸カードのセクション */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp, top = 4.dp),
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

@Composable
fun RowDivider() = HorizontalDivider(
    modifier = Modifier.padding(horizontal = 20.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
)

/** 丸いアイコン背景 */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f), modifier = Modifier.size(38.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (showChevron) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingRow(title, subtitle, icon, enabled, onClick = { onCheckedChange(!checked) }, showChevron = false) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 第2階層ページ用の共通レイアウト（戻る付き） */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る") }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * アプリ共通のスライダー（全画面で同じ見た目に統一）。
 * step 刻みでスナップする。タップ・ドラッグ両対応。
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = 1f,
    onValueChangeFinished: () -> Unit = {},
) {
    val onChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val onFinished by androidx.compose.runtime.rememberUpdatedState(onValueChangeFinished)
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumbInner = MaterialTheme.colorScheme.surface
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbRadiusPx = with(density) { 11.dp.toPx() }
    var widthPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    fun toValue(x: Float): Float {
        val usable = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)
        val frac = ((x - thumbRadiusPx) / usable).coerceIn(0f, 1f)
        val raw = valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
        val snapped = valueRange.start + kotlin.math.round((raw - valueRange.start) / step) * step
        return snapped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(valueRange, step) {
                detectTapGestures { onChange(toValue(it.x)); onFinished() }
            }
            .pointerInput(valueRange, step) {
                detectHorizontalDragGestures(
                    onDragEnd = { onFinished() },
                    onDragCancel = { onFinished() },
                ) { change, _ ->
                    change.consume()
                    onChange(toValue(change.position.x))
                }
            },
    ) {
        val cy = size.height / 2
        val start = thumbRadiusPx
        val end = size.width - thumbRadiusPx
        val frac = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val x = start + (end - start) * frac
        val h = 6.dp.toPx()
        drawLine(track, androidx.compose.ui.geometry.Offset(start, cy), androidx.compose.ui.geometry.Offset(end, cy), h, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(active, androidx.compose.ui.geometry.Offset(start, cy), androidx.compose.ui.geometry.Offset(x, cy), h, androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(active, thumbRadiusPx, androidx.compose.ui.geometry.Offset(x, cy))
        drawCircle(thumbInner, thumbRadiusPx * 0.4f, androidx.compose.ui.geometry.Offset(x, cy))
    }
}
