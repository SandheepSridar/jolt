package com.jolt.mission

import android.content.Context
import android.graphics.Bitmap

/**
 * Owns the embedder for one camera session. Loading the model is deferred to the first frame so
 * it happens on the analysis thread, never the UI thread; [close] frees it when the camera closes.
 */
class ScanSession(private val context: Context) {
    private var embedder: ObjectEmbedder? = null

    fun embed(bitmap: Bitmap): FloatArray? =
        (embedder ?: ObjectEmbedder.create(context).also { embedder = it }).embed(bitmap)

    fun close() {
        embedder?.close()
        embedder = null
    }
}
