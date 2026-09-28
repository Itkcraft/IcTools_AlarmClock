package com.itkcraft.alarmclock.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.itkcraft.alarmclock.BuildConfig
import com.itkcraft.alarmclock.ui.components.RowDivider
import com.itkcraft.alarmclock.ui.components.SectionCard
import com.itkcraft.alarmclock.ui.components.SettingRow
import com.itkcraft.alarmclock.ui.components.SubPage
import com.itkcraft.alarmclock.ui.theme.MonoStyle

private data class Oss(val name: String, val owner: String, val license: String)

/** APK に同梱される外部ライブラリ（すべて Apache License 2.0） */
private val ossList = listOf(
    Oss("Kotlin Standard Library / kotlinx.coroutines", "JetBrains s.r.o. and Kotlin contributors", "Apache License 2.0"),
    Oss("AndroidX Core / Activity / Lifecycle", "The Android Open Source Project", "Apache License 2.0"),
    Oss("Jetpack Compose (UI / Foundation / Material3)", "The Android Open Source Project", "Apache License 2.0"),
    Oss("Material Icons (Compose)", "Google LLC / The Android Open Source Project", "Apache License 2.0"),
)

private const val TERMS = """利用規約

1. 本アプリは個人が非商用目的で作成したオープンソースソフトウェアであり、MIT License のもとで提供されます。
2. 本アプリは無保証で提供されます。端末の状態（電源・省電力設定・OS の制限・音量・イヤホンの状態等）により、アラームが鳴らない・遅れる可能性があります。重要な予定には複数の手段を併用してください。
3. 本アプリの利用により生じたいかなる損害についても、作者は責任を負いません。
4. 本規約は予告なく変更されることがあります。"""

private const val PRIVACY = """プライバシー

・本アプリはインターネット権限を持たず、外部との通信を一切行いません。
・アラーム・設定・ログは端末内のアプリ専用領域にのみ保存されます（クラウドバックアップ対象外）。
・ユーザーが追加した音声ファイルは、読み取り権限のみを取得して再生に使用します。ファイルの複製・送信は行いません。
・ログはユーザーがエクスポートした場合を除き端末外へ出ません。アラームのメモ内容はログに記録しません。"""

@Composable
fun AboutPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun asset(name: String) = runCatching { ctx.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("(読み込めませんでした)")

    SubPage("このアプリについて", onBack) {
        SectionCard {
            SettingRow("メディア音アラーム", "バージョン ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Icons.Rounded.Info)
            RowDivider()
            SettingRow("ソースコード", "github.com/itkcraft/ictools_alarmclock", Icons.Rounded.Code)
        }
        SectionCard(title = "規約") {
            SettingRow("利用規約", null, Icons.Rounded.Description, onClick = { dialog = "利用規約" to TERMS })
            RowDivider()
            SettingRow("プライバシー", null, Icons.Rounded.PrivacyTip, onClick = { dialog = "プライバシー" to PRIVACY })
            RowDivider()
            SettingRow("本アプリのライセンス", "MIT License", Icons.Rounded.Gavel, onClick = { dialog = "MIT License" to asset("licenses/app-mit.txt") })
        }
        SectionCard(title = "オープンソースライセンス") {
            ossList.forEachIndexed { i, o ->
                if (i > 0) RowDivider()
                SettingRow(o.name, "${o.owner}\n${o.license}", onClick = { dialog = o.name to asset("licenses/apache-2.0.txt") })
            }
        }
        Text(
            "内蔵アラーム音はアプリ内で波形を合成して生成しており、第三者の音源は含みません。",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    dialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(body, style = if (body.length > 1500) MonoStyle else androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("閉じる") } },
        )
    }
}
