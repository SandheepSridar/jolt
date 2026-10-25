package com.jolt.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

const val MAX_RANDOM_SOUNDS = 5
private const val PREVIEW_MS = 3_000L

data class AlarmSound(val title: String, val uri: String)

/** The phone's built-in and downloaded alarm sounds. */
suspend fun loadAlarmSounds(context: Context): List<AlarmSound> = withContext(Dispatchers.IO) {
    val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
    val cursor = manager.cursor
    buildList {
        while (cursor.moveToNext()) {
            add(AlarmSound(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX), manager.getRingtoneUri(cursor.position).toString()))
        }
    }.distinctBy { it.uri }
}

/** Tick up to [MAX_RANDOM_SOUNDS] alarm sounds; one is picked at random each time the alarm rings. */
@Composable
fun RandomSoundsDialog(initial: List<String>, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val context = LocalContext.current
    val sounds by produceState<List<AlarmSound>?>(null) { value = loadAlarmSounds(context) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(initial.take(MAX_RANDOM_SOUNDS)) } }
    var previewing by remember { mutableStateOf<String?>(null) }
    val player = remember { PreviewPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    LaunchedEffect(previewing) {
        val uri = previewing ?: return@LaunchedEffect player.stop()
        player.play(Uri.parse(uri))
        delay(PREVIEW_MS)
        previewing = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Random sound") },
        text = {
            Column {
                Text(
                    "Pick up to $MAX_RANDOM_SOUNDS. Each time the alarm rings, one is chosen at random.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${picked.size} of $MAX_RANDOM_SOUNDS picked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(sounds.orEmpty(), key = { it.uri }) { sound ->
                        val checked = sound.uri in picked
                        val enabled = checked || picked.size < MAX_RANDOM_SOUNDS
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { if (checked) picked.remove(sound.uri) else picked.add(sound.uri) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
                            Text(sound.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { previewing = if (previewing == sound.uri) null else sound.uri }) {
                                Icon(
                                    if (previewing == sound.uri) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = "Preview",
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked.toList()) }, enabled = picked.isNotEmpty()) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Plays one sound at a time, on the alarm stream so it sounds like the real thing. */
private class PreviewPlayer(private val context: Context) {
    private var current: Ringtone? = null

    fun play(uri: Uri) {
        stop()
        current = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            play()
        }
    }

    fun stop() {
        current?.stop()
        current = null
    }
}
