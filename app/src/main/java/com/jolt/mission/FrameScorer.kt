package com.jolt.mission

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * Scores camera frames for one mission. Created on the UI thread but used, and closed, only on
 * the camera's analysis thread, so models load lazily there and need no locking.
 */
interface FrameScorer {
    /** Pass mark for [score]. */
    val threshold: Float

    /** Score below which the progress ring stays empty. */
    val floor: Float

    fun score(frame: Bitmap): Float
    fun close()
}

/** Your own photographed object: similarity to its reference shots. */
class ObjectScorer(
    context: Context,
    private val references: List<FloatArray>,
    override val threshold: Float,
) : FrameScorer {
    private val session = ScanSession(context)
    override val floor = 0.35f

    override fun score(frame: Bitmap): Float = session.embed(frame)?.let { bestMatch(it, references) } ?: 0f

    override fun close() = session.close()
}

/** Any object of a [Category], found by the on-device object detector. */
class CategoryScorer(private val context: Context, private val category: Category) : FrameScorer {
    private var detector: ObjectDetector? = null
    override val threshold = 0.5f
    override val floor = 0.2f

    override fun score(frame: Bitmap): Float {
        val result = (detector ?: create().also { detector = it }).detect(BitmapImageBuilder(frame).build())
        val frameArea = (frame.width * frame.height).toFloat()
        val detections = result.detections().flatMap { d ->
            val box = d.boundingBox()
            val area = (box.width() * box.height()) / frameArea
            d.categories().map { Detection(it.categoryName(), it.score(), area) }
        }
        return categoryScore(detections, category)
    }

    override fun close() {
        detector?.close()
        detector = null
    }

    private fun create(): ObjectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setCategoryAllowlist(listOf(category.label))
            .setScoreThreshold(floor)
            .setMaxResults(3)
            .build(),
    )

    private companion object {
        const val MODEL = "efficientdet_lite2.tflite"
    }
}
