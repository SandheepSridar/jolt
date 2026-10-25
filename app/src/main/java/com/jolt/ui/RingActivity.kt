package com.jolt.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jolt.alarm.AlarmService
import com.jolt.data.Alarm
import com.jolt.jolt
import com.jolt.mission.Mission
import com.jolt.ui.components.HoldButton
import com.jolt.ui.theme.JoltTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Full-screen alarm shown over the lock screen. Lives exactly as long as AlarmService is ringing. */
class RingActivity : ComponentActivity() {
    companion object {
        fun intent(context: Context) = Intent(context, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** Volume keys would be an easy way to silence the alarm without getting up. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in VOLUME_KEYS || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode in VOLUME_KEYS || super.onKeyUp(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { /* Back doesn't get you out of a mission. */ }

        setContent {
            JoltTheme(darkTheme = true) {
                val ringing by AlarmService.ringing.collectAsState()
                var shown by remember { mutableStateOf<Alarm?>(null) }
                var done by remember { mutableStateOf(false) }

                LaunchedEffect(ringing) {
                    val alarm = ringing?.alarm
                    when {
                        alarm != null -> shown = alarm
                        done -> Unit
                        // Opened from a stale notification, or the alarm was stopped elsewhere.
                        // Opened at fire time, possibly before the service has loaded the alarm.
                        else -> { delay(if (shown == null) 4000 else 0); finish() }
                    }
                }
                LaunchedEffect(done) {
                    if (done) { delay(1800); finish() }
                }

                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val alarm = shown
                    when {
                        done -> GoodMorning()
                        alarm != null -> RingScreen(alarm, onDismiss = {
                            done = true
                            AlarmService.dismiss(this@RingActivity, alarm.id)
                        })
                    }
                }
            }
        }
    }
}

private val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE)

private enum class Phase { Wake, Mission }

@Composable
private fun RingScreen(alarm: Alarm, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var phase by remember { mutableStateOf(Phase.Wake) }
    val mission = remember(alarm) { Mission.of(alarm) }
    val missionLabel by produceState<String?>(null, mission) {
        value = when (mission) {
            is Mission.Generic -> "Scan ${mission.category.withArticle}"
            is Mission.Own -> context.jolt.repository.obj(mission.objectId)?.name?.let { "Scan your $it" }
            null -> null
        }
    }
    val hasMission = mission != null

    AnimatedContent(phase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phase") { current ->
        when (current) {
            Phase.Wake -> WakeScreen(
                alarm = alarm,
                missionLabel = missionLabel,
                hasMission = hasMission,
                onStart = {
                    if (hasMission) {
                        AlarmService.duck(context)
                        phase = Phase.Mission
                    } else {
                        onDismiss()
                    }
                },
                onSnooze = { AlarmService.snooze(context, alarm.id) },
            )
            Phase.Mission -> MissionView(
                mission = mission!!,
                onPassed = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                },
                onMatching = { AlarmService.duck(context) },
                fallback = { reason -> Fallback(reason, onDismiss) },
            )
        }
    }
}

@Composable
private fun WakeScreen(alarm: Alarm, missionLabel: String?, hasMission: Boolean, onStart: () -> Unit, onSnooze: () -> Unit) {
    val context = LocalContext.current
    val now by minuteTicker()
    val clock = remember(now) {
        val t = java.time.LocalTime.now()
        formatClock(context, t.hour, t.minute)
    }
    val date = remember(now) { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    val canSnooze = alarm.maxSnoozes > 0 && alarm.snoozeCount < alarm.maxSnoozes

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(clock.time, style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.3f))
            clock.suffix?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp, bottom = 18.dp))
            }
        }
        if (alarm.label.isNotBlank()) {
            Text(alarm.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1.4f))

        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp), shape = MaterialTheme.shapes.large) {
            if (hasMission) {
                Icon(Icons.Rounded.CameraAlt, null, Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text(missionLabel ?: "Start mission", style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Dismiss", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (canSnooze) {
            TextButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Snooze ${alarm.snoozeMinutes} min", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(56.dp))
        }
    }
}

/** Shown when the camera can't be used, so a broken camera never means an alarm you can't stop. */
@Composable
private fun Fallback(reason: String, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Can't scan right now", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "$reason Hold the button below for a full minute to turn the alarm off.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        HoldButton("Hold for 60 seconds", durationMs = 60_000, onComplete = onDismiss)
    }
}

@Composable
private fun GoodMorning() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.WbSunny, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Good morning", style = MaterialTheme.typography.displaySmall)
    }
}
