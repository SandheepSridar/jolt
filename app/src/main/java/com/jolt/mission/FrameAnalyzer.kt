package com.jolt.mission

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Throttles camera frames to a few per second and hands over an upright square: the part of
 * the scene inside the on-screen viewfinder.
 */
class FrameAnalyzer(
    private val intervalMs: Long = 250,
    private val onFrame: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        image.use {
            val now = SystemClock.elapsedRealtime()
            if (now - last < intervalMs) return
            last = now
            onFrame(it.toBitmap().uprightSquare(it.cropRect, it.imageInfo.rotationDegrees))
        }
    }
}

/** Centre square of the visible area ([visible] is the frame's crop rect), rotated upright. */
private fun Bitmap.uprightSquare(visible: Rect, rotation: Int): Bitmap {
    val side = (minOf(visible.width(), visible.height()) * VIEWFINDER_FRACTION).toInt()
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val left = (visible.centerX() - side / 2).coerceIn(0, width - side)
    val top = (visible.centerY() - side / 2).coerceIn(0, height - side)
    return Bitmap.createBitmap(this, left, top, side, side, matrix, true)
}

/** Share of the visible preview's short side covered by the viewfinder square drawn on screen. */
const val VIEWFINDER_FRACTION = 0.8f
