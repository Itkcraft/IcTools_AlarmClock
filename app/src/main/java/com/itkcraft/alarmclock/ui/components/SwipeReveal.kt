package com.itkcraft.alarmclock.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左→右へスワイプすると背面の削除ボタンが現れる。削除ボタン押下で onDelete。
 * （スワイプ＋押下の 2 アクションが確認を兼ねるため、確認ダイアログは出さない）
 */
@Composable
fun SwipeToReveal(
    onDelete: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { 96.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val progress = (offset.value / revealPx).coerceIn(0f, 1f)

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .alpha(progress)
                .clip(shape)
                .background(MaterialTheme.colorScheme.error),
        ) {
            Column(
                Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .clickable(enabled = progress > 0.9f) {
                        scope.launch { offset.snapTo(0f) }
                        onDelete()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onError)
                Text("削除", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch { offset.animateTo(if (offset.value > revealPx / 2) revealPx else 0f) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + dx).coerceIn(0f, revealPx)) }
                    }
                },
        ) { content() }
    }
}
