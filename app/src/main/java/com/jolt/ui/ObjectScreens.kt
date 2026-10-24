package com.jolt.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jolt.data.MissionObject
import com.jolt.data.Strictness
import com.jolt.jolt
import com.jolt.mission.ScanSession
import com.jolt.ui.components.CameraView
import com.jolt.ui.components.Viewfinder
import com.jolt.ui.components.WithCameraPermission
import com.jolt.ui.components.describeCameraFailure
import com.jolt.ui.theme.Amber
import com.jolt.ui.theme.OnAmber
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectsScreen(onBack: () -> Unit, onAdd: () -> Unit, onTest: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.jolt.repository
    val scope = rememberCoroutineScope()
    val objects by repo.objects.collectAsStateWithLifecycle(null)
    val shots by repo.shots.collectAsStateWithLifecycle(emptyList())
    var deleting by remember { mutableStateOf<MissionObject?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan objects") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add object") },
                containerColor = Amber,
                contentColor = OnAmber,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (objects?.isEmpty() == true) {
                item {
                    Text(
                        "Register something you can only reach by getting out of bed: the bathroom sink, the coffee machine, a poster in the hallway.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(objects.orEmpty(), key = { it.id }) { obj ->
                val objShots = shots.filter { it.objectId == obj.id }
                ObjectCard(
                    obj = obj,
                    photoPath = objShots.firstOrNull()?.photoPath,
                    photoCount = objShots.size,
                    onStrictness = { scope.launch { repo.setStrictness(obj, it) } },
                    onTest = { onTest(obj.id) },
                    onDelete = { deleting = obj },
                )
            }
        }
    }

    deleting?.let { obj ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${obj.name}?") },
            text = { Text("Alarms that use it will have no mission until you pick another object.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { repo.deleteObject(obj) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ObjectCard(
    obj: MissionObject,
    photoPath: String?,
    photoCount: Int,
    onStrictness: (Strictness) -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val photo by thumbnail(photoPath)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    photo?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(obj.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (photoCount == 1) "1 photo" else "$photoCount photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(14.dp))
            Text("Strictness", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            StrictnessPicker(Strictness.closestTo(obj.threshold), onStrictness)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Test scan")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrictnessPicker(selected: Strictness, onSelect: (Strictness) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Strictness.entries.forEachIndexed { i, s ->
            SegmentedButton(
                selected = s == selected,
                onClick = { onSelect(s) },
                shape = SegmentedButtonDefaults.itemShape(i, Strictness.entries.size),
                label = { Text(s.label) },
            )
        }
    }
}

private const val MAX_SHOTS = 3

/** Take 1–3 reference photos of an object and name it. Returns the new object's id via [onSaved]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterObjectScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.jolt.repository
    val scope = rememberCoroutineScope()
    val session = remember { ScanSession(context.applicationContext) }
    val captureNext = remember { AtomicBoolean(false) }
    val shots = remember { mutableStateListOf<Pair<Bitmap, FloatArray>>() }
    var name by remember { mutableStateOf("") }
    var strictness by remember { mutableStateOf(Strictness.Normal) }
    var saving by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            WithCameraPermission(denied = { CameraMessage("Jolt needs the camera to photograph your object.") }) {
                if (cameraError != null) {
                    CameraMessage(cameraError!!)
                } else {
                    CameraView(
                        onFrame = { frame ->
                            if (captureNext.compareAndSet(true, false)) {
                                val vector = session.embed(frame) ?: return@CameraView
                                mainExecutor.execute { if (shots.size < MAX_SHOTS) shots.add(frame to vector) }
                            }
                        },
                        onRelease = session::close,
                        onError = { cameraError = describeCameraFailure(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Viewfinder(progress = null, modifier = Modifier.fillMaxSize())
                }
            }
            IconButton(onClick = onBack, modifier = Modifier.safeDrawingPadding().padding(8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                Text(
                    when (shots.size) {
                        0 -> "Frame your object and take a photo"
                        MAX_SHOTS -> "All set. Name it and save."
                        else -> "Nice. Add another angle, or name it and save."
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Pick something that stays put. Shots from a couple of angles make the morning scan easier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(MAX_SHOTS) { i ->
                        val shot = shots.getOrNull(i)
                        val bitmap = remember(shot) { shot?.first?.asImageBitmap() }
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small),
                        ) {
                            bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = { captureNext.set(true) },
                        enabled = shots.size < MAX_SHOTS && cameraError == null,
                        shape = CircleShape,
                        color = if (shots.size < MAX_SHOTS) Amber else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(4.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier.size(72.dp).border(2.dp, Amber, CircleShape),
                    ) {}
                }

                if (shots.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Coffee machine") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    StrictnessPicker(strictness) { strictness = it }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            saving = true
                            scope.launch { onSaved(repo.createObject(name.trim(), strictness, shots.toList())) }
                        },
                        enabled = name.isNotBlank() && !saving,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Save object") }
                }
            }
        }
    }
}

@Composable
private fun CameraMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Try the mission without an alarm ringing, with the live similarity score shown. */
@Composable
fun TestScanScreen(objectId: Long, onBack: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var passed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (passed) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(72.dp), tint = Amber)
                Spacer(Modifier.height(16.dp))
                Text("Matched", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(
                    "This would have turned your alarm off.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { passed = false; attempt++ }) { Text("Scan again", color = Color.White) }
            }
        } else {
            key(attempt) {
                MissionView(
                    objectId = objectId,
                    onPassed = { passed = true },
                    showScore = true,
                    fallback = { CameraMessage(it) },
                )
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.safeDrawingPadding().padding(8.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
        }
    }
}
