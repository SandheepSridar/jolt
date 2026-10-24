package com.jolt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jolt.data.MissionObject
import com.jolt.jolt
import com.jolt.mission.MatchTracker
import com.jolt.mission.ScanSession
import com.jolt.mission.bestMatch
import com.jolt.ui.components.CameraView
import com.jolt.ui.components.Viewfinder
import com.jolt.ui.components.WithCameraPermission
import com.jolt.ui.components.describeCameraFailure

/** [obj] is null when the object was deleted. */
private class Target(val obj: MissionObject?, val references: List<FloatArray>)

/**
 * The photo mission: live camera, viewfinder and a ring that fills as the registered object
 * comes into view. Calls [onPassed] once, after several matching frames in a row.
 *
 * If the camera can't be used, [fallback] is shown instead so the alarm can always be stopped.
 */
@Composable
fun MissionView(
    objectId: Long,
    onPassed: () -> Unit,
    fallback: @Composable (reason: String) -> Unit,
    modifier: Modifier = Modifier,
    onMatching: () -> Unit = {},
    showScore: Boolean = false,
) {
    val context = LocalContext.current
    val target by produceState<Target?>(null, objectId) {
        val repo = context.jolt.repository
        val obj = repo.obj(objectId)
        value = Target(obj, if (obj == null) emptyList() else repo.shotsFor(objectId).map { it.vector() })
    }

    var error by remember { mutableStateOf<String?>(null) }
    val current = target
    WithCameraPermission(denied = { fallback("Jolt doesn't have camera access.") }) {
        when {
            error != null -> fallback(error!!)
            current == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            current.obj == null -> fallback("The object for this alarm was deleted.")
            current.references.isEmpty() -> fallback("This object has no reference photos.")
            else -> Scanner(current.obj, current.references, onPassed, onMatching, showScore, onError = { error = it }, modifier)
        }
    }
}

@Composable
private fun Scanner(
    obj: MissionObject,
    references: List<FloatArray>,
    onPassed: () -> Unit,
    onMatching: () -> Unit,
    showScore: Boolean,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val session = remember { ScanSession(context.applicationContext) }
    val tracker = remember(obj) { MatchTracker(obj.threshold) }
    // Written from the analysis thread; snapshot state is safe to update from any thread.
    var score by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var matching by remember { mutableStateOf(false) }
    var passed by remember { mutableStateOf(false) }
    val passedCallback by rememberUpdatedState(onPassed)
    val matchingCallback by rememberUpdatedState(onMatching)

    LaunchedEffect(passed) { if (passed) passedCallback() }
    LaunchedEffect(matching) { if (matching) matchingCallback() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        CameraView(
            onFrame = { frame ->
                if (passed) return@CameraView
                val vector = session.embed(frame) ?: return@CameraView
                val s = bestMatch(vector, references)
                val done = tracker.offer(s)
                score = s
                progress = tracker.progress(s)
                matching = tracker.streak > 0
                if (done) passed = true
            },
            onRelease = session::close,
            onError = { onError(describeCameraFailure(it)) },
            modifier = Modifier.fillMaxSize(),
        )
        Viewfinder(progress = if (passed) 1f else progress, locked = passed || matching, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Find your", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
            Text(obj.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            val hint = when {
                passed -> "Got it"
                matching -> "Hold steady…"
                else -> "Fit it inside the frame"
            }
            Text(hint, style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (showScore) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Similarity %.2f · needs %.2f".format(score, obj.threshold),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.fillMaxWidth().height(24.dp))
        }
    }
}
