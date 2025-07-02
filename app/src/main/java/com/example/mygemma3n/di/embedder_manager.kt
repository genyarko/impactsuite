package com.example.mygemma3n.di


import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads either Mobile-BERT (~100 MB) or Average-Word (~4 MB)
 * from /assets and exposes a single embed() call.
 */
@Singleton
class EmbedderManager @Inject constructor(          // ← annotate
    @ApplicationContext private val context: Context
) {
    enum class Model(val assetName: String) {
        MOBILE_BERT("mobile_bert.tflite"),
        AVG_WORD("average_word_embedder.tflite");
    }

    private val mutex = Mutex()
    private var embedder: TextEmbedder? = null
    private var loadedModel: Model? = null

    /** Initialise (or switch) the model lazily. */
    suspend fun load(model: Model, useGpu: Boolean = false) = mutex.withLock {
        if (loadedModel == model) return
        embedder?.close()

        val baseOpts = BaseOptions.builder()
            .setModelAssetPath(model.assetName)
            .setDelegate(if (useGpu) Delegate.GPU else Delegate.CPU)
            .build()

        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOpts)
            .build()

        embedder = TextEmbedder.createFromOptions(context, options)
        loadedModel = model
    }

    /** Returns the L2-normalised embedding for a sentence. */
    fun embed(text: String): FloatArray {
        val vector = embedder
            ?: throw IllegalStateException("Call load() first").also { return FloatArray(0) }
        val raw = vector.embed(text).embeddingResult().embeddings().first().floatEmbedding()

        // Normalise in-place
        var norm = 0f
        raw.forEach { norm += it * it }
        val scale = 1f / sqrt(norm)
        raw.indices.forEach { raw[it] *= scale }
        return raw
    }

    suspend fun close() = mutex.withLock {
        embedder?.close(); embedder = null; loadedModel = null
    }
}
