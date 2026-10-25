package com.jolt.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jolt.alarm.AlarmTimes
import com.jolt.data.Alarm
import com.jolt.jolt
import com.jolt.mission.Category
import com.jolt.mission.lowercaseUnlessAcronym
import com.jolt.ui.theme.Amber
import com.jolt.ui.theme.OnAmber
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(onEdit: (Long) -> Unit, onObjects: () -> Unit) {
    val context = LocalContext.current
    val repo = context.jolt.repository
    val alarms by repo.alarms.collectAsStateWithLifecycle(null)
    val objects by repo.objects.collectAsStateWithLifecycle(emptyList())
    val now by minuteTicker()
    val scope = rememberCoroutineScope()
    val next = remember(alarms, now) { alarms?.let { AlarmTimes.earliest(it, now, ZoneId.systemDefault()) } }
    val updates = rememberUpdateController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jolt") },
                actions = {
                    IconButton(onClick = onObjects) { Icon(Icons.Rounded.CenterFocusStrong, "Scan objects") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(0) },
                containerColor = Amber,
                contentColor = OnAmber,
            ) { Icon(Icons.Rounded.Add, "New alarm") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NextAlarmHeader(next?.second, now) }
            item { PermissionBanners() }
            item { UpdateBanner(updates) }
            val list = alarms
            if (list != null && list.isEmpty()) {
                item { EmptyAlarms() }
            }
            items(list.orEmpty(), key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    missionLabel = Category.fromName(alarm.category)?.let { "Scan any ${it.displayName.lowercaseUnlessAcronym()}" }
                        ?: objects.firstOrNull { it.id == alarm.objectId }?.name?.let { "Scan $it" },
                    onClick = { onEdit(alarm.id) },
                    onToggle = { scope.launch { repo.setEnabled(alarm, it) } },
                )
            }
            item { VersionFooter(updates) }
        }
    }
}

@Composable
private fun NextAlarmHeader(nextAt: Long?, now: Long) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp)) {
        if (nextAt == null) {
            Text("No alarms set", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Jolt sleeps too. It uses no battery until an alarm goes off.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val at = Instant.ofEpochMilli(nextAt).atZone(ZoneId.systemDefault())
        val clock = formatClock(context, at.hour, at.minute)
        val today = LocalDate.now()
        val day = when (at.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        Text("Next alarm · $day", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(clock.time, style = MaterialTheme.typography.displayLarge)
            clock.suffix?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp, bottom = 14.dp))
            }
        }
        Text(
            "Rings in ${formatDuration(nextAt - now)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, missionLabel: String?, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val clock = formatClock(context, alarm.hour, alarm.minute)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.45f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(clock.time, style = MaterialTheme.typography.displaySmall)
                    clock.suffix?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    }
                }
                Text(
                    listOf(alarm.label.ifBlank { null }, daysSummary(alarm.days)).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                MissionTag(missionLabel)
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun MissionTag(label: String?) {
    val (icon, text, color) = if (label != null) {
        Triple(Icons.Rounded.CameraAlt, label, MaterialTheme.colorScheme.primary)
    } else {
        Triple(Icons.Rounded.ErrorOutline, "No mission", MaterialTheme.colorScheme.error)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun EmptyAlarms() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("How Jolt works", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "1. Register an object far from your bed, like the bathroom sink or the coffee machine.",
                "2. Set an alarm and choose that object as its mission.",
                "3. To turn the alarm off, you have to get up and scan it.",
            ).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

/** Only the permissions an alarm can't work without. Re-checked every time the screen resumes. */
@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState == Lifecycle.State.RESUMED
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val missingNotifications = remember(resumed) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val missingFullScreen = remember(resumed) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val missingExact = remember(resumed) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val packageUri = "package:${context.packageName}".toUri()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (missingExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Banner("Alarms can't ring on time", "Allow Jolt to set alarms.") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
            }
        }
        if (missingNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Banner("Notifications are off", "Jolt needs them to show the alarm screen.") {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missingFullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Banner("Alarm screen is blocked", "Allow full-screen alerts so the alarm shows over your lock screen.") {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
            }
        }
    }
}

@Composable
private fun Banner(title: String, body: String, onFix: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onFix)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
            }
            TextButton(onClick = onFix) { Text("Fix", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}
