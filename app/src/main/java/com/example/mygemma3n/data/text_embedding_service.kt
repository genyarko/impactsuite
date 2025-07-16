package com.example.mygemma3n.data

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class TextEmbeddingService @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    companion object {
        private const val MODEL_ASSET = "models/universal_sentence_encoder.tflite"
    }

    // ✅ Correct direct assignment of TextEmbedder, not inside a lambda
    private val embedder: TextEmbedder by lazy {
        val baseOpts = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val opts = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOpts)
            .setL2Normalize(false)
            .build()
        TextEmbedder.createFromOptions(context, opts)
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        // ✅ Use correct API to retrieve embeddings list
        val firstEmbedding = embedder
            .embed(text)
            .embeddingResult()
            .embeddings()
            .first()
        val raw = firstEmbedding.floatEmbedding()

        // ✅ Explicit lambda types to avoid inference errors
        val norm = sqrt(raw.fold(0f) { acc: Float, v: Float -> acc + v * v })
        FloatArray(raw.size) { i -> raw[i] / norm }
    }
}
