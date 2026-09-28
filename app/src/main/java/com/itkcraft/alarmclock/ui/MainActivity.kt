package com.itkcraft.alarmclock.ui

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.alarm.AlarmService
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.ui.alarms.AlarmEditScreen
import com.itkcraft.alarmclock.ui.alarms.AlarmListScreen
import com.itkcraft.alarmclock.ui.settings.SettingsPage
import com.itkcraft.alarmclock.ui.settings.SettingsScreen
import com.itkcraft.alarmclock.ui.theme.AppTheme
import com.itkcraft.alarmclock.ui.theme.isDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by Repository.settings.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme)
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                )
            }
            AppTheme(settings.theme) { AppRoot() }
        }
    }
}

/**
 * 画面構成: 下部タブ（タイマー / アラーム / 設定）＋ 1階層のみのサブページ。
 * overlay 文字列: "edit:<alarmId|new>:<groupId|->" / "settings:<page>"
 */
@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
    val message: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    // 初回起動時に通知権限をリクエスト
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        AppLog.i("Main", "POST_NOTIFICATIONS granted=$it")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = overlay != null) { overlay = null }

    AnimatedContent(
        targetState = overlay,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
            } else {
                fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "overlay",
    ) { route ->
        when {
            route == null -> MainTabs(tab, { tab = it }, snackbar, ringing, message) { overlay = it }
            route.startsWith("edit:") -> {
                val parts = route.split(":")
                AlarmEditScreen(
                    alarmId = parts.getOrNull(1)?.toLongOrNull(),
                    initialGroupId = parts.getOrNull(2)?.toLongOrNull(),
                    onClose = { overlay = null },
                    onMessage = message,
                )
            }
            route.startsWith("settings:") -> SettingsPage(route.removePrefix("settings:"), onBack = { overlay = null })
            else -> LaunchedEffect(route) { overlay = null }
        }
    }
}

@Composable
private fun MainTabs(
    tab: Int,
    onTab: (Int) -> Unit,
    snackbar: SnackbarHostState,
    ringing: AlarmService.RingInfo?,
    onMessage: (String) -> Unit,
    navigate: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val tabs = listOf("タイマー" to Icons.Rounded.Timer, "アラーム" to Icons.Rounded.Alarm, "設定" to Icons.Rounded.Settings)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (ringing != null) RingingBanner(ringing, onStop = { AlarmService.stop(ctx) }, onSnooze = { AlarmService.snooze(ctx) })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> com.itkcraft.alarmclock.ui.timer.TimerScreen()
                    1 -> AlarmListScreen(
                        onEdit = { id, group -> navigate("edit:${id ?: "new"}:${group ?: "-"}") },
                        onMessage = onMessage,
                    )
                    else -> SettingsScreen(onOpen = { navigate("settings:$it") })
                }
            }
        }
    }
}

@Composable
private fun RingingBanner(info: AlarmService.RingInfo, onStop: () -> Unit, onSnooze: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.onPrimary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(info.title, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                Text("鳴動中 ${info.subtitle}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall)
            }
            if (info.kind != AlarmService.KIND_TEST) {
                OutlinedButton(onClick = onSnooze) { Text("スヌーズ", color = MaterialTheme.colorScheme.onPrimary) }
            }
            Button(
                onClick = onStop,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("停止") }
        }
    }
}
