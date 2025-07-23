// File: app/src/main/java/com/example/mygemma3n/embedder/Embedder.kt
package com.example.mygemma3n.embedder

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import java.io.Closeable
import kotlin.math.sqrt

/**
 * Thin wrapper around MediaPipe Text Embedder (.tflite task model).
 *
 * @param ctx        Android context
 * @param assetFile  model filename under assets/  (e.g. "mobile_bert.tflite")
 * @param useGpu     true → GPU delegate, else CPU
 * @param l2Norm     true → return L2-normalised vector
 */
class Embedder(
    ctx: Context,
    assetFile: String,
    useGpu: Boolean = false,
    private val l2Norm: Boolean = true
) : Closeable {

    private val mp: TextEmbedder

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath(assetFile)
            .setDelegate(if (useGpu) Delegate.GPU else Delegate.CPU)
            .build()

        val opts = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(base)
            .build()

        mp = TextEmbedder.createFromOptions(ctx, opts)
    }

    /** Returns a float-array embedding; optionally L2-normalised. */
    fun embed(text: String): FloatArray {
        val v = mp.embed(text)
            .embeddingResult()
            .embeddings()
            .first()
            .floatEmbedding()

        if (!l2Norm) return v

        var sum = 0f
        v.forEach { sum += it * it }
        if (sum > 0f) {
            val inv = 1f / sqrt(sum)
            for (i in v.indices) v[i] *= inv
        }
        return v
    }

    override fun close() = mp.close()
}
