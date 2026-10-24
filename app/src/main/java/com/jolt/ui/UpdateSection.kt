package com.jolt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jolt.ui.theme.Amber
import com.jolt.ui.theme.OnAmber
import com.jolt.update.ReleaseInfo
import com.jolt.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    /** Downloaded, waiting for the user to allow "Install unknown apps". */
    data class NeedsPermission(val info: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val info: ReleaseInfo?, val message: String) : UpdateState
}

@Stable
class UpdateController(private val updater: Updater, private val scope: CoroutineScope) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    val versionName get() = updater.installedVersionName

    fun check() {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        scope.launch {
            state = updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
        }
    }

    fun update(info: ReleaseInfo) {
        state = UpdateState.Downloading(info, 0f)
        scope.launch {
            try {
                val file = updater.download(info) { state = UpdateState.Downloading(info, it) }
                if (updater.canInstall()) {
                    state = UpdateState.Available(info)
                    updater.install(file)
                } else {
                    state = UpdateState.NeedsPermission(info, file)
                    updater.openInstallPermissionSettings()
                }
            } catch (e: Exception) {
                state = UpdateState.Failed(info, e.message ?: "Update failed")
            }
        }
    }

    /** Back from the "Install unknown apps" screen: carry on if it was allowed. */
    fun onResume() {
        val pending = state as? UpdateState.NeedsPermission ?: return
        if (updater.canInstall()) {
            state = UpdateState.Available(pending.info)
            updater.install(pending.file)
        }
    }
}

/** Checks GitHub once each time the alarm list opens, never in the background. */
@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { UpdateController(Updater(context.applicationContext), scope) }
    LaunchedEffect(Unit) { controller.check() }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) controller.onResume()
    }
    return controller
}

/** Card shown at the top of the alarm list only while there's something to act on. */
@Composable
fun UpdateBanner(controller: UpdateController) {
    val info = when (val s = controller.state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.NeedsPermission -> s.info
        is UpdateState.Failed -> s.info
        else -> null
    } ?: return

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Update available · v${info.versionName}", style = MaterialTheme.typography.titleMedium)
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(info.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            when (val s = controller.state) {
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading… ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is UpdateState.NeedsPermission -> {
                    Text(
                        "Allow Jolt to install apps, then come back here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    if (s is UpdateState.Failed) {
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { controller.update(info) },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (s is UpdateState.Failed) "Try again" else "Update") }
                }
            }
        }
    }
}

/** Small footer: installed version plus a manual check. */
@Composable
fun VersionFooter(controller: UpdateController) {
    val status = when (val s = controller.state) {
        UpdateState.Checking -> "Checking…"
        UpdateState.UpToDate -> "Up to date"
        is UpdateState.Failed -> if (s.info == null) s.message else null
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOfNotNull("Jolt ${controller.versionName}", status).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = controller::check, enabled = controller.state !is UpdateState.Checking) {
            Text("Check for updates", style = MaterialTheme.typography.bodySmall)
        }
    }
}
