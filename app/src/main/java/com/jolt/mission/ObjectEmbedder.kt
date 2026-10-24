package com.jolt.mission

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder

/**
 * Turns a photo into a feature vector using an on-device MobileNet model.
 * Photos of the same object give vectors pointing the same way, so cosine similarity measures the match.
 *
 * Not thread safe: create it, use it from a single thread, close it when the camera closes.
 */
class ObjectEmbedder private constructor(private val embedder: ImageEmbedder) : AutoCloseable {

    fun embed(bitmap: Bitmap): FloatArray? {
        val result = embedder.embed(BitmapImageBuilder(bitmap).build())
        return result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
    }

    override fun close() = embedder.close()

    companion object {
        private const val MODEL = "mobilenet_v3_small.tflite"

        fun create(context: Context): ObjectEmbedder {
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                .setRunningMode(RunningMode.IMAGE)
                .setL2Normalize(true)
                .build()
            return ObjectEmbedder(ImageEmbedder.createFromOptions(context, options))
        }
    }
}

/** Cosine similarity of two L2-normalised vectors. */
fun similarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    for (i in a.indices) dot += a[i] * b[i]
    return dot
}

/** Best similarity between [frame] and any of the object's reference shots. */
fun bestMatch(frame: FloatArray, references: List<FloatArray>): Float =
    references.maxOfOrNull { similarity(frame, it) } ?: 0f
