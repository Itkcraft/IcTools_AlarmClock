package com.itkcraft.alarmclock.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.itkcraft.alarmclock.alarm.AlarmScheduler
import com.itkcraft.alarmclock.alarm.Notifications
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.ui.components.IconBadge
import com.itkcraft.alarmclock.ui.components.RowDivider
import com.itkcraft.alarmclock.ui.components.SectionCard
import com.itkcraft.alarmclock.ui.components.SubPage

object PermissionStatus {
    fun notifications(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ringChannel(ctx: Context): Boolean {
        val ch = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(Notifications.CH_RING)
        return ch == null || ch.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun exactAlarm(ctx: Context) = AlarmScheduler.canScheduleExact(ctx)

    fun fullScreen(ctx: Context) = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun battery(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun summary(ctx: Context): String =
        "notif=${notifications(ctx)} channel=${ringChannel(ctx)} exact=${exactAlarm(ctx)} fsi=${fullScreen(ctx)} battery=${battery(ctx)}"
}

private fun open(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppLog.w("Permissions", "cannot open ${intent.action}", e)
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@SuppressLint("BatteryLife")
@Composable
fun PermissionsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AppLog.i("Permissions", "POST_NOTIFICATIONS granted=$granted")
        if (!granted) {
            open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        tick++
    }
    val pkgUri = Uri.parse("package:${ctx.packageName}")

    SubPage("権限", onBack) {
        @Suppress("UNUSED_VARIABLE") val refresh = tick
        Text(
            "アラームを確実に鳴らすため、以下をすべて「OK」にすることをおすすめします。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SectionCard {
            PermissionRow(
                Icons.Rounded.Notifications, "通知", "鳴動画面・スヌーズ・タイマーの表示に必要です",
                PermissionStatus.notifications(ctx),
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            RowDivider()
            PermissionRow(
                Icons.Rounded.NotificationsActive, "鳴動通知チャネル", "「アラーム鳴動」通知の重要度が「高」以上である必要があります",
                PermissionStatus.ringChannel(ctx),
            ) {
                open(
                    ctx,
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CH_RING),
                )
            }
            RowDivider()
            PermissionRow(
                Icons.Rounded.AccessAlarm, "正確なアラーム", "指定時刻ぴったりに鳴らすために必要です",
                PermissionStatus.exactAlarm(ctx),
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
            }
            RowDivider()
            PermissionRow(
                Icons.Rounded.Fullscreen, "全画面通知", "ロック中に鳴動画面を表示するために必要です（Android 14 以降）",
                PermissionStatus.fullScreen(ctx),
            ) {
                if (Build.VERSION.SDK_INT >= 34) open(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
            }
            RowDivider()
            PermissionRow(
                Icons.Rounded.BatteryAlert, "バッテリー最適化の除外", "省電力機能によるアラーム遅延・停止を防ぎます",
                PermissionStatus.battery(ctx),
            ) {
                open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
            }
        }
        SectionCard {
            PermissionRow(Icons.Rounded.Apps, "アプリ情報", "その他の権限・通知・ストレージなどを確認", null) {
                open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
            }
        }
        Text(
            "このアプリはインターネット権限を持たず、データを外部へ送信しません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, desc: String, ok: Boolean?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, if (ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ok != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = (if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.15f),
                    ) {
                        Text(
                            if (ok) "OK" else "未許可",
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ok != true) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick) { Text(if (ok == null) "開く" else "設定") }
        }
    }
}
