package com.jolt.ui.components

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jolt.mission.FrameAnalyzer
import java.util.concurrent.Executors

/**
 * Back-camera preview plus a low-resolution, throttled analysis stream.
 * The camera is bound only while this is on screen and released as soon as it leaves.
 *
 * [onFrame] and [onRelease] run on a single background thread, so per-session state such as the
 * embedder can live there without locking.
 */
@Composable
fun CameraView(
    onFrame: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
    onRelease: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val frameCallback by rememberUpdatedState(onFrame)
    val releaseCallback by rememberUpdatedState(onRelease)
    val errorCallback by rememberUpdatedState(onError)

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor, FrameAnalyzer { bitmap ->
                    try {
                        frameCallback(bitmap)
                    } catch (e: Throwable) {
                        // Includes LinkageError from a broken native library, so the mission
                        // falls back to hold-to-dismiss instead of hanging.
                        Log.e(TAG, "Frame processing failed", e)
                        ContextCompat.getMainExecutor(context).execute { errorCallback(FrameProcessingException(e)) }
                    }
                })
                // Wait for layout so the viewport (the visible part of the preview) is known. With
                // it, each frame's cropRect matches what's on screen, so the viewfinder box is honest.
                previewView.post {
                    if (disposed) return@post
                    try {
                        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
                        previewView.viewPort?.let(group::setViewPort)
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera bind failed", e)
                        errorCallback(e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider failed", e)
                errorCallback(e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.execute { releaseCallback() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private const val TAG = "JoltCamera"

/** The camera ran, but processing a frame (e.g. the image model) failed. */
class FrameProcessingException(cause: Throwable) : Exception(cause)

/** One user-facing line saying which step failed, plus the root cause for bug reports. */
fun describeCameraFailure(e: Throwable): String {
    val step = if (e is FrameProcessingException) "The scanner couldn't start." else "The camera couldn't start."
    var root: Throwable = e
    while (root.cause != null && root.cause !== root) root = root.cause!!
    val detail = listOfNotNull(root.javaClass.simpleName.ifBlank { root.javaClass.name }, root.message?.take(120)).joinToString(": ")
    return "$step\n($detail)"
}
