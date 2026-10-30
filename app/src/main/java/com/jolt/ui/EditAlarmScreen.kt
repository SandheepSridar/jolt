package com.jolt.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jolt.alarm.AlarmTimes
import com.jolt.data.Alarm
import com.jolt.data.MissionObject
import com.jolt.jolt
import com.jolt.mission.Category
import com.jolt.mission.lowercaseUnlessAcronym
import com.jolt.ui.components.WheelPicker
import com.jolt.ui.theme.Amber
import com.jolt.ui.theme.OnAmber
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Create ([alarmId] = 0) or edit an alarm. [newObjectId] is set when the user has just
 * registered an object from here, so it can be selected automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Long, newObjectId: Long?, onDone: () -> Unit, onAddObject: () -> Unit) {
    val context = LocalContext.current
    val repo = context.jolt.repository
    val scope = rememberCoroutineScope()
    val objects by repo.objects.collectAsStateWithLifecycle(emptyList())
    val shots by repo.shots.collectAsStateWithLifecycle(emptyList())

    var loaded by rememberSaveable { mutableStateOf(alarmId == 0L) }
    var original by remember { mutableStateOf<Alarm?>(null) }
    var hour by rememberSaveable { mutableStateOf(7) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var days by rememberSaveable { mutableStateOf(WEEKDAYS) }
    var label by rememberSaveable { mutableStateOf("") }
    var soundUri by rememberSaveable { mutableStateOf<String?>(null) }
    var vibrate by rememberSaveable { mutableStateOf(true) }
    var snoozeMinutes by rememberSaveable { mutableStateOf(5) }
    var maxSnoozes by rememberSaveable { mutableStateOf(1) }
    var objectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var randomSounds by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteAfterDismiss by rememberSaveable { mutableStateOf(false) }
    var pickedDefaultMission by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingSoundMode by remember { mutableStateOf(false) }
    var choosingRandomSounds by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        if (alarmId == 0L) return@LaunchedEffect
        repo.alarm(alarmId)?.let {
            original = it
            if (!loaded) {
                hour = it.hour; minute = it.minute; days = it.days; label = it.label
                soundUri = it.soundUri; vibrate = it.vibrate; snoozeMinutes = it.snoozeMinutes
                maxSnoozes = it.maxSnoozes; objectId = it.objectId; category = it.category
                randomSounds = it.randomSounds; deleteAfterDismiss = it.deleteAfterDismiss
                loaded = true
            }
        }
    }
    // New alarms always start with a mission, because an alarm without one is easy to ignore:
    // your most recent object if you have one, otherwise any sink.
    LaunchedEffect(objects) {
        if (alarmId == 0L && !pickedDefaultMission) {
            if (objects.isNotEmpty()) objectId = objects.first().id else category = Category.Sink.name
            pickedDefaultMission = true
        }
    }
    LaunchedEffect(newObjectId) { newObjectId?.let { objectId = it; category = null } }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            soundUri = uri?.toString()
            randomSounds = null
        }
    }

    fun save() = scope.launch {
        val alarm = (original ?: Alarm(hour = hour, minute = minute)).copy(
            hour = hour, minute = minute, days = days, label = label.trim(), soundUri = soundUri,
            vibrate = vibrate, snoozeMinutes = snoozeMinutes, maxSnoozes = maxSnoozes,
            objectId = objectId, category = category, randomSounds = randomSounds, enabled = true,
            deleteAfterDismiss = deleteAfterDismiss && days == 0,
        )
        repo.saveAlarm(alarm)
        val next = AlarmTimes.nextOccurrence(hour, minute, days, ZonedDateTime.now())
        val inMillis = next.toInstant().toEpochMilli() - System.currentTimeMillis()
        Toast.makeText(context, "Alarm set for ${formatDuration(inMillis)} from now", Toast.LENGTH_SHORT).show()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId == 0L) "New alarm" else "Edit alarm") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, "Cancel") } },
                actions = {
                    Button(
                        onClick = { save() },
                        enabled = loaded,
                        modifier = Modifier.padding(end = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                    ) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            TimeWheels(hour, minute, onHour = { hour = it }, onMinute = { minute = it })
            val ringsIn = remember(hour, minute, days) {
                val next = AlarmTimes.nextOccurrence(hour, minute, days, ZonedDateTime.now())
                formatDuration(next.toInstant().toEpochMilli() - System.currentTimeMillis())
            }
            Text(
                "Rings in $ringsIn",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
            )

            Section("Repeat")
            DayToggles(days) { days = it }
            // Always shown so it's discoverable; only one-off alarms can use it.
            val oneOff = days == 0
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                ListItem(
                    headlineContent = { Text("Delete after dismissing") },
                    supportingContent = {
                        Text(if (oneOff) "Remove this alarm once you turn it off" else "One-time alarms only: turn off all repeat days to use it")
                    },
                    trailingContent = {
                        Switch(checked = oneOff && deleteAfterDismiss, onCheckedChange = { deleteAfterDismiss = it }, enabled = oneOff)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }

            Section("Mission")
            MissionPicker(
                objects = objects,
                photoFor = { id -> shots.firstOrNull { it.objectId == id }?.photoPath },
                selectedObject = objectId,
                selectedCategory = Category.fromName(category),
                onSelectObject = { objectId = it; category = null },
                onSelectCategory = { category = it?.name; objectId = null },
                onAdd = onAddObject,
            )

            Section("Label")
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = { Text("e.g. Gym") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Sound & snooze")
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    val randomCount = randomSounds?.lines()?.count { it.isNotBlank() } ?: 0
                    val soundName = remember(soundUri, randomCount) {
                        if (randomCount > 0) return@remember "Random · $randomCount ${if (randomCount == 1) "sound" else "sounds"}"
                        val uri = soundUri?.toUri() ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                        uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() } ?: "Default"
                    }
                    ListItem(
                        headlineContent = { Text("Sound") },
                        supportingContent = { Text(soundName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.clip(MaterialTheme.shapes.large).clickableItem { choosingSoundMode = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Vibrate") },
                        trailingContent = { Switch(checked = vibrate, onCheckedChange = { vibrate = it }) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(16.dp)) {
                        Text("Snooze", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(
                            options = listOf(0 to "Off", 5 to "5 min", 10 to "10 min"),
                            selected = if (maxSnoozes == 0) 0 else snoozeMinutes,
                            onSelect = {
                                if (it == 0) maxSnoozes = 0 else {
                                    snoozeMinutes = it
                                    if (maxSnoozes == 0) maxSnoozes = 1
                                }
                            },
                        )
                        if (maxSnoozes > 0) {
                            Spacer(Modifier.height(8.dp))
                            ChoiceRow(
                                options = listOf(1 to "Once", 2 to "Twice", 3 to "3 times"),
                                selected = maxSnoozes,
                                onSelect = { maxSnoozes = it },
                            )
                        }
                    }
                }
            }

            if (alarmId != 0L) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text("Delete alarm", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (choosingSoundMode) {
        AlertDialog(
            onDismissRequest = { choosingSoundMode = false },
            title = { Text("Alarm sound") },
            text = {
                Column {
                    SoundModeRow("One sound", "Always play the same sound") {
                        choosingSoundMode = false
                        soundPicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUri?.toUri()),
                        )
                    }
                    SoundModeRow("Random", "Pick up to $MAX_RANDOM_SOUNDS; a different one can play each time") {
                        choosingSoundMode = false
                        choosingRandomSounds = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingSoundMode = false }) { Text("Cancel") } },
        )
    }
    if (choosingRandomSounds) {
        RandomSoundsDialog(
            initial = randomSounds?.lines()?.filter { it.isNotBlank() }.orEmpty(),
            onDismiss = { choosingRandomSounds = false },
            onConfirm = {
                randomSounds = it.joinToString("\n")
                choosingRandomSounds = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this alarm?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    original?.let { scope.launch { repo.deleteAlarm(it); onDone() } }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit) = clickable(onClick = onClick)

@Composable
private fun SoundModeRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun TimeWheels(hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (is24) {
            WheelPicker(24, hour, onSelect = onHour, label = { "%02d".format(it) })
        } else {
            // Index i shows hour i + 1 (1…12); the AM/PM wheel decides the half of the day.
            val hour12 = if (hour % 12 == 0) 12 else hour % 12
            WheelPicker(12, hour12 - 1, onSelect = { i -> onHour((i + 1) % 12 + if (hour >= 12) 12 else 0) }, label = { "${it + 1}" })
        }
        Text(":", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 4.dp))
        WheelPicker(60, minute, onSelect = onMinute, label = { "%02d".format(it) })
        if (!is24) {
            WheelPicker(
                count = 2,
                initial = if (hour >= 12) 1 else 0,
                onSelect = { pm -> onHour(hour % 12 + pm * 12) },
                label = { if (it == 0) "AM" else "PM" },
                cyclic = false,
                width = 88.dp,
            )
        }
    }
}

@Composable
private fun DayToggles(days: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            val on = days and bit != 0
            Surface(
                onClick = { onChange(days xor bit) },
                shape = CircleShape,
                color = if (on) Amber else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (on) OnAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    Text(
        if (days == 0) "Rings once" else daysSummary(days),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun MissionPicker(
    objects: List<MissionObject>,
    photoFor: (Long) -> String?,
    selectedObject: Long?,
    selectedCategory: Category?,
    onSelectObject: (Long) -> Unit,
    onSelectCategory: (Category?) -> Unit,
    onAdd: () -> Unit,
) {
    Text("Anything like…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Category.entries.forEach { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onSelectCategory(category) },
                label = { Text("Any ${category.displayName.lowercaseUnlessAcronym()}") },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Your objects", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(objects, key = { it.id }) { obj ->
            val photo by thumbnail(photoFor(obj.id))
            MissionTile(obj.name, selectedObject == obj.id, onClick = { onSelectObject(obj.id) }) {
                photo?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        item {
            MissionTile("Add object", false, onClick = onAdd) {
                Icon(Icons.Rounded.Add, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            MissionTile("No mission", selectedObject == null && selectedCategory == null, onClick = { onSelectCategory(null) }) {
                Icon(Icons.Rounded.DoNotDisturbAlt, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (selectedObject == null && selectedCategory == null) {
        Text(
            "Without a mission, one tap turns this alarm off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MissionTile(name: String, selected: Boolean, onClick: () -> Unit, image: @Composable () -> Unit) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = if (selected) BorderStroke(3.dp, Amber) else null,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { image() }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, text) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) })
        }
    }
}
