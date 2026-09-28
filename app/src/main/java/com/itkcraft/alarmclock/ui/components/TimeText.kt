package com.itkcraft.alarmclock.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.itkcraft.alarmclock.ui.hmParts

/** 時刻の大表示。12時間表示では AM/PM を小さく添える */
@Composable
fun TimeText(
    hour: Int,
    minute: Int,
    use24h: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val (period, time) = hmParts(hour, minute, use24h)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (period != null) {
            Text(
                period,
                fontSize = fontSize * 0.36f,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.75f),
                modifier = Modifier.padding(end = 6.dp, bottom = (fontSize.value * 0.16f).dp),
            )
        }
        Text(time, fontSize = fontSize, fontWeight = FontWeight.Light, color = color, maxLines = 1)
    }
}
