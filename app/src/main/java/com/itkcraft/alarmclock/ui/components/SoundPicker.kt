package com.itkcraft.alarmclock.ui.components

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itkcraft.alarmclock.data.Repository
import com.itkcraft.alarmclock.data.SoundRef
import com.itkcraft.alarmclock.log.AppLog
import com.itkcraft.alarmclock.sound.Sounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 試聴用プレイヤー（メディア音で再生） */
class PreviewPlayer(private val ctx: Context) {
    private var player: MediaPlayer? = null
    var playingUri by mutableStateOf<String?>(null)
        private set

    fun toggle(ref: SoundRef) {
        if (playingUri == ref.uri) return stop()
        stop()
        try {
            val vol = Repository.settings.value.volume / 100f
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(ctx, Sounds.resolve(ctx, ref))
                isLooping = false
                setVolume(vol, vol)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playingUri = ref.uri
        } catch (e: Exception) {
            AppLog.e("Preview", "preview failed: ${ref.name}", e)
            Toast.makeText(ctx, "再生できませんでした", Toast.LENGTH_SHORT).show()
            stop()
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playingUri = null
    }
}

/** 音声ファイルの追加（読み取り専用の永続権限のみ取得） */
fun addCustomSound(ctx: Context, uri: Uri): SoundRef? {
    val type = ctx.contentResolver.getType(uri)
    if (type == null || !(type.startsWith("audio/") || type == "application/ogg")) {
        AppLog.w("Sound", "rejected non-audio file type=$type")
        Toast.makeText(ctx, "音声ファイルを選択してください", Toast.LENGTH_SHORT).show()
        return null
    }
    try {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: SecurityException) {
        AppLog.w("Sound", "persistable permission not granted", e)
    }
    val ref = SoundRef(uri.toString(), Sounds.displayName(ctx, uri))
    Repository.updateSettings { s ->
        if (s.customSounds.any { it.uri == ref.uri }) s else s.copy(customSounds = s.customSounds + ref)
    }
    AppLog.i("Sound", "custom sound added type=$type")
    return ref
}

fun removeCustomSound(ctx: Context, ref: SoundRef) {
    Repository.updateSettings { s ->
        s.copy(
            customSounds = s.customSounds.filterNot { it.uri == ref.uri },
            defaultSound = if (s.defaultSound.uri == ref.uri) Sounds.builtinRef("chime") else s.defaultSound,
        )
    }
    Repository.updateAlarms { a, g -> a.map { if (it.sound?.uri == ref.uri) it.copy(sound = null) else it } to g }
    runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(ref.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

/**
 * アラーム音選択シート。
 * @param allowDefault true の場合「既定の音を使う」(null) を選択肢に含める
 */
@Composable
fun SoundPickerSheet(
    current: SoundRef?,
    allowDefault: Boolean,
    onSelect: (SoundRef?) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings by Repository.settings.collectAsStateWithLifecycle()
    val preview = remember { PreviewPlayer(ctx) }
    var systemSounds by remember { mutableStateOf<List<SoundRef>>(emptyList()) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    LaunchedEffect(Unit) { systemSounds = withContext(Dispatchers.IO) { Sounds.systemAlarms(ctx) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addCustomSound(ctx, uri)?.let(onSelect)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item {
                Text("アラーム音を選択", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            if (allowDefault) {
                item {
                    SoundItem("既定の音（${settings.defaultSound.name}）", current == null, null, preview) { onSelect(null) }
                }
            }
            item { Header("内蔵") }
            items(Sounds.builtins) { b ->
                val ref = Sounds.builtinRef(b.id)
                SoundItem(ref.name, current?.uri == ref.uri, ref, preview) { onSelect(ref) }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Header("追加した音声ファイル", Modifier.weight(1f))
                    FilledTonalButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Rounded.Add, null)
                        Text("ファイルを追加")
                    }
                }
            }
            if (settings.customSounds.isEmpty()) {
                item {
                    Text(
                        "mp3 / m4a / ogg / wav などを追加できます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            items(settings.customSounds, key = { "c" + it.uri }) { ref ->
                SoundItem(ref.name, current?.uri == ref.uri, ref, preview, onRemove = { removeCustomSound(ctx, ref) }) { onSelect(ref) }
            }
            if (systemSounds.isNotEmpty()) {
                item { Header("端末のアラーム音") }
                items(systemSounds, key = { "s" + it.uri }) { ref ->
                    SoundItem(ref.name, current?.uri == ref.uri, ref, preview) { onSelect(ref) }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SoundItem(
    name: String,
    selected: Boolean,
    ref: SoundRef?,
    preview: PreviewPlayer,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (ref != null) {
            IconButton(onClick = { preview.toggle(ref) }) {
                Icon(
                    if (preview.playingUri == ref.uri) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = "試聴",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "削除") }
        }
    }
}

