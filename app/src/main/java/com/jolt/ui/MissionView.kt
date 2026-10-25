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
import com.jolt.jolt
import com.jolt.mission.CategoryScorer
import com.jolt.mission.FrameScorer
import com.jolt.mission.MatchTracker
import com.jolt.mission.Mission
import com.jolt.mission.ObjectScorer
import com.jolt.ui.components.CameraView
import com.jolt.ui.components.Viewfinder
import com.jolt.ui.components.WithCameraPermission
import com.jolt.ui.components.describeCameraFailure

/** Everything the scanner needs, loaded up front. [problem] is set if the mission can't run. */
private class Prepared(
    val title: String,
    val subject: String,
    val newScorer: (() -> FrameScorer)?,
    val problem: String? = null,
)

/**
 * The photo mission: live camera, viewfinder and a ring that fills as the target comes into
 * view. Calls [onPassed] once, after several matching frames in a row.
 *
 * If the camera can't be used, [fallback] is shown instead so the alarm can always be stopped.
 */
@Composable
fun MissionView(
    mission: Mission,
    onPassed: () -> Unit,
    fallback: @Composable (reason: String) -> Unit,
    modifier: Modifier = Modifier,
    onMatching: () -> Unit = {},
    showScore: Boolean = false,
) {
    val context = LocalContext.current
    val prepared by produceState<Prepared?>(null, mission) {
        val app = context.applicationContext
        value = when (mission) {
            is Mission.Generic -> Prepared("Find", mission.category.withArticle, { CategoryScorer(app, mission.category) })
            is Mission.Own -> {
                val repo = context.jolt.repository
                val obj = repo.obj(mission.objectId)
                val references = if (obj == null) emptyList() else repo.shotsFor(obj.id).map { it.vector() }
                when {
                    obj == null -> Prepared("", "", null, "The object for this alarm was deleted.")
                    references.isEmpty() -> Prepared("", "", null, "This object has no reference photos.")
                    else -> Prepared("Find your", obj.name, { ObjectScorer(app, references, obj.threshold) })
                }
            }
        }
    }

    var error by remember { mutableStateOf<String?>(null) }
    val current = prepared
    WithCameraPermission(denied = { fallback("Jolt doesn't have camera access.") }) {
        when {
            error != null -> fallback(error!!)
            current == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            current.newScorer == null -> fallback(current.problem ?: "This mission can't run.")
            else -> Scanner(current, current.newScorer, onPassed, onMatching, showScore, onError = { error = it }, modifier)
        }
    }
}

@Composable
private fun Scanner(
    prepared: Prepared,
    newScorer: () -> FrameScorer,
    onPassed: () -> Unit,
    onMatching: () -> Unit,
    showScore: Boolean,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val scorer = remember(prepared) { newScorer() }
    val tracker = remember(scorer) { MatchTracker(scorer.threshold, floor = scorer.floor) }
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
                val s = scorer.score(frame)
                val done = tracker.offer(s)
                score = s
                progress = tracker.progress(s)
                matching = tracker.streak > 0
                if (done) passed = true
            },
            onRelease = scorer::close,
            onError = { onError(describeCameraFailure(it)) },
            modifier = Modifier.fillMaxSize(),
        )
        Viewfinder(progress = if (passed) 1f else progress, locked = passed || matching, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(prepared.title, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
            Text(prepared.subject, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
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
                    "Score %.2f · needs %.2f".format(score, scorer.threshold),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.fillMaxWidth().height(24.dp))
        }
    }
}
