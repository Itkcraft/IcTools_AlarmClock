package com.itkcraft.alarmclock.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.alarm.AlarmService
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** ロック画面上にも表示される鳴動画面 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val settings by Repository.settings.collectAsStateWithLifecycle()
            val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(ringing) {
                if (ringing == null) {
                    delay(300)
                    if (AlarmService.ringing.value == null) finish()
                }
            }
            AppTheme(settings.theme) {
                RingContent(
                    info = ringing,
                    use24h = settings.use24h,
                    snoozeMinutes = settings.snoozeMinutes,
                    onStop = { AlarmService.stop(this); finish() },
                    onSnooze = { AlarmService.snooze(this); finish() },
                )
            }
        }
    }
}

@Composable
private fun RingContent(
    info: AlarmService.RingInfo?,
    use24h: Boolean,
    snoozeMinutes: Int,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .scale(pulse)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Alarm, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text(formatTime(now, use24h), fontSize = 72.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onBackground)
            Text(
                info?.title ?: "",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(64.dp))
            Button(
                onClick = onStop,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) { Text("停止", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            if (info != null && info.kind != AlarmService.KIND_TEST) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Icon(Icons.Rounded.Snooze, null)
                    Text("  スヌーズ（${snoozeMinutes}分）", fontSize = 18.sp)
                }
            }
        }
    }
}
