package com.example.mygemma3n.embedder

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import java.io.Closeable

class Embedder(
    ctx: Context,
    assetFile: String,         // "mobile_bert.tflite" or "average_word_embedder.tflite"
    useGpu: Boolean = false
) : Closeable {

    private val textEmbedder: TextEmbedder

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath(assetFile)
            .setDelegate(if (useGpu) Delegate.GPU else Delegate.CPU)
            .build()

        val opts = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(base)
            .build()

        textEmbedder = TextEmbedder.createFromOptions(ctx, opts)
    }

    fun embed(text: String): FloatArray =
        textEmbedder.embed(text).embeddingResult().embeddings().first().floatEmbedding()

    override fun close() = textEmbedder.close()
}
