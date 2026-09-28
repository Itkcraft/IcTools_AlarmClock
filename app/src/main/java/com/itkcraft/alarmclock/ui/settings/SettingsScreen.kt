package com.itkcraft.alarmclock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.data.NoHeadphoneAction
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.data.ThemeMode
import com.itkcraft.alarmclock.ui.components.RowDivider
import com.itkcraft.alarmclock.ui.components.SectionCard
import com.itkcraft.alarmclock.ui.components.SettingRow

object SettingsPages {
    const val SOUND = "sound"
    const val SNOOZE = "snooze"
    const val BEHAVIOR = "behavior"
    const val DISPLAY = "display"
    const val PERMISSIONS = "permissions"
    const val DEBUG = "debug"
    const val ABOUT = "about"
}

fun NoHeadphoneAction.label() = when (this) {
    NoHeadphoneAction.SPEAKER -> "スピーカーから鳴らす"
    NoHeadphoneAction.VIBRATE_ONLY -> "バイブレーションのみ"
    NoHeadphoneAction.SILENT -> "鳴らさない"
}

fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "自動"
    ThemeMode.LIGHT -> "ライト"
    ThemeMode.DARK -> "ダーク"
}

fun silenceLabel(min: Int) = if (min == 0) "自動消音しない" else "${min}分後に消音"

@Composable
fun SettingsScreen(onOpen: (String) -> Unit) {
    val s by Repository.settings.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("設定", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))

        SectionCard(title = "アラーム設定") {
            SettingRow("アラーム音と音量", "${s.defaultSound.name} ・ 音量 ${s.volume}", Icons.AutoMirrored.Rounded.VolumeUp, onClick = { onOpen(SettingsPages.SOUND) })
            RowDivider()
            SettingRow("スヌーズ・消音", "スヌーズ ${s.snoozeMinutes}分 ・ ${silenceLabel(s.silenceMinutes)}", Icons.Rounded.Snooze, onClick = { onOpen(SettingsPages.SNOOZE) })
            RowDivider()
            SettingRow(
                "鳴動の動作",
                "音量可変 ${if (s.gradualVolume) "ON" else "OFF"} ・ 未接続時: ${s.noHeadphoneAction.label()} ・ バイブ ${if (s.vibration) "ON" else "OFF"}",
                Icons.Rounded.Headphones,
                onClick = { onOpen(SettingsPages.BEHAVIOR) },
            )
        }
        SectionCard(title = "表示") {
            SettingRow("時刻表示・テーマ", "${if (s.use24h) "24時間" else "12時間 (AM/PM)"} ・ ${s.theme.label()}", Icons.Rounded.Palette, onClick = { onOpen(SettingsPages.DISPLAY) })
        }
        SectionCard(title = "システム") {
            SettingRow("権限", "通知・正確なアラーム・バッテリー最適化など", Icons.Rounded.Security, onClick = { onOpen(SettingsPages.PERMISSIONS) })
            RowDivider()
            SettingRow("デバッグ", "ログコンソール・エクスポート・テスト鳴動", Icons.Rounded.BugReport, onClick = { onOpen(SettingsPages.DEBUG) })
        }
        SectionCard(title = "その他") {
            SettingRow("このアプリについて", "バージョン・利用規約・ライセンス", Icons.Rounded.Info, onClick = { onOpen(SettingsPages.ABOUT) })
        }
    }
}

@Composable
fun SettingsPage(page: String, onBack: () -> Unit) {
    when (page) {
        SettingsPages.SOUND -> SoundSettingsPage(onBack)
        SettingsPages.SNOOZE -> SnoozeSettingsPage(onBack)
        SettingsPages.BEHAVIOR -> BehaviorSettingsPage(onBack)
        SettingsPages.DISPLAY -> DisplaySettingsPage(onBack)
        SettingsPages.PERMISSIONS -> PermissionsPage(onBack)
        SettingsPages.DEBUG -> DebugPage(onBack)
        SettingsPages.ABOUT -> AboutPage(onBack)
        else -> androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
    }
}
