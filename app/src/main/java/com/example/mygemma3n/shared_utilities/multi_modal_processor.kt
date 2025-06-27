package com.example.mygemma3n.shared_utilities

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ImageProcessor
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.mygemma3n.gemma.GemmaModelManager
import com.google.android.gms.common.Feature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

// MultimodalProcessor.kt
class MultimodalProcessor @OptIn(UnstableApi::class)
@Inject constructor(
    private val modelManager: GemmaModelManager,
    private val audioProcessor: AudioProcessor,
    private val imageProcessor: ImageProcessor
) {
    data class MultimodalInput(
        val text: String? = null,
        val image: Bitmap? = null,
        val audio: FloatArray? = null
    )

    suspend fun process(input: MultimodalInput, feature: Feature): String {
        val model = selectOptimalModel(feature, input)

        // Prepare interleaved inputs as per Gemma 3n requirements
        val interleavedInput = prepareInterleavedInput(input)

        return withContext(Dispatchers.Default) {
            model.run(interleavedInput)
        }
    }

    private fun selectOptimalModel(feature: Feature, input: MultimodalInput): LiteRTInterpreter {
        return when {
            feature == Feature.CRISIS && input.audio != null ->
                modelManager.getModel(GemmaModelManager.ModelConfig.Fast2B) // Speed critical
            feature == Feature.PLANT && input.image != null ->
                modelManager.getModel(GemmaModelManager.ModelConfig.Quality4B) // Accuracy critical
            else -> modelManager.getModel(GemmaModelManager.ModelConfig.Balanced3B)
        }
    }
}