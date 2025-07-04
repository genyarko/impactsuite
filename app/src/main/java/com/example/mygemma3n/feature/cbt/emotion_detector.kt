package com.example.mygemma3n.feature.cbt


import com.example.mygemma3n.data.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class EmotionDetector @Inject constructor(
    private val geminiApiService: GeminiApiService
) {

    /**
     * Detects emotion from audio features (simplified version)
     * In production, you'd use a proper audio emotion detection model
     */
    suspend fun detectFromAudio(audioData: FloatArray): Emotion {
        // Calculate audio features
        val energy = audioData.map { it * it }.average()
        val variance = audioData.map { (it - energy).let { d -> d * d } }.average()
        val zeroCrossingRate = calculateZeroCrossingRate(audioData)
        val spectralCentroid = calculateSpectralCentroid(audioData)

        // Basic rule-based detection (simplified)
        return when {
            energy > 0.7 && variance > 0.5 -> Emotion.ANGRY
            energy > 0.5 && variance < 0.3 && zeroCrossingRate > 0.3 -> Emotion.HAPPY
            energy < 0.3 && variance < 0.2 -> Emotion.SAD
            energy < 0.4 && variance > 0.6 -> Emotion.ANXIOUS
            spectralCentroid > 0.6 && energy > 0.5 -> Emotion.SURPRISED
            energy < 0.2 && zeroCrossingRate < 0.1 -> Emotion.FEARFUL
            else -> Emotion.NEUTRAL
        }
    }

    /**
     * Detects emotion from text using Gemma 3n
     */
    suspend fun detectFromText(text: String): Emotion = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Analyze the emotional tone of this text and classify it into ONE of these emotions:
                HAPPY, SAD, ANGRY, ANXIOUS, NEUTRAL, FEARFUL, SURPRISED, DISGUSTED
                
                Text: "$text"
                
                Respond with a JSON object containing:
                - emotion: the detected emotion (use exact uppercase labels above)
                - confidence: confidence score between 0 and 1
                - reasoning: brief explanation (max 20 words)
                
                Example response:
                {"emotion": "ANXIOUS", "confidence": 0.8, "reasoning": "Expresses worry about future uncertainty"}
            """.trimIndent()

            val response = geminiApiService.generateTextComplete(prompt)

            // Parse JSON response
            return@withContext try {
                val json = JSONObject(response)
                val emotionStr = json.getString("emotion")
                Emotion.valueOf(emotionStr)
            } catch (e: Exception) {
                Timber.w("Failed to parse emotion from response: $response")
                Emotion.NEUTRAL
            }

        } catch (e: Exception) {
            Timber.e(e, "Error detecting emotion from text")
            return@withContext Emotion.NEUTRAL
        }
    }

    /**
     * Detects emotion from both audio and text for better accuracy
     */
    suspend fun detectFromMultimodal(
        audioData: FloatArray?,
        text: String?
    ): EmotionDetectionResult = withContext(Dispatchers.IO) {
        val audioEmotion = audioData?.let { detectFromAudio(it) }
        val textEmotion = text?.let { detectFromText(it) }

        when {
            audioEmotion != null && textEmotion != null -> {
                // If both are available, use Gemma 3n to reconcile
                reconcileEmotions(audioEmotion, textEmotion, text)
            }
            audioEmotion != null -> {
                EmotionDetectionResult(audioEmotion, 0.7f, "Detected from voice tone")
            }
            textEmotion != null -> {
                EmotionDetectionResult(textEmotion, 0.8f, "Detected from text content")
            }
            else -> {
                EmotionDetectionResult(Emotion.NEUTRAL, 0.5f, "No input provided")
            }
        }
    }

    /**
     * Uses Gemma 3n to reconcile emotions detected from different modalities
     */
    private suspend fun reconcileEmotions(
        audioEmotion: Emotion,
        textEmotion: Emotion,
        text: String?
    ): EmotionDetectionResult {
        if (audioEmotion == textEmotion) {
            return EmotionDetectionResult(
                emotion = audioEmotion,
                confidence = 0.95f,
                reasoning = "Strong agreement between voice and text"
            )
        }

        // Use Gemma 3n to make final decision
        val prompt = """
            Voice analysis suggests: ${audioEmotion.name}
            Text analysis suggests: ${textEmotion.name}
            
            Text content: "${text ?: "No text available"}"
            
            Which emotion is more likely correct? Consider that voice tone can sometimes mask true feelings,
            while text content might be more deliberate. Choose the most accurate emotion.
            
            Respond with JSON: {"emotion": "EMOTION_NAME", "confidence": 0.0-1.0, "reasoning": "brief explanation"}
        """.trimIndent()

        return try {
            val response = geminiApiService.generateTextComplete(prompt)
            val json = JSONObject(response)

            EmotionDetectionResult(
                emotion = Emotion.valueOf(json.getString("emotion")),
                confidence = json.getDouble("confidence").toFloat(),
                reasoning = json.getString("reasoning")
            )
        } catch (e: Exception) {
            // Fallback to text emotion as it's usually more reliable
            EmotionDetectionResult(
                emotion = textEmotion,
                confidence = 0.6f,
                reasoning = "Mixed signals, defaulting to text analysis"
            )
        }
    }

    /**
     * Tracks emotion changes over time for a session
     */
    suspend fun analyzeEmotionTrajectory(
        messages: List<Message>
    ): EmotionTrajectoryAnalysis = withContext(Dispatchers.IO) {
        val emotionHistory = mutableListOf<Pair<Long, Emotion>>()

        // Analyze emotions for each message
        for (message in messages) {
            if (message is Message.User) {
                val emotion = detectFromText(message.content)
                emotionHistory.add(message.timestamp to emotion)
            }
        }

        if (emotionHistory.isEmpty()) {
            return@withContext EmotionTrajectoryAnalysis(
                startEmotion = Emotion.NEUTRAL,
                endEmotion = Emotion.NEUTRAL,
                peakEmotion = Emotion.NEUTRAL,
                improvement = false,
                summary = "No emotional data available"
            )
        }

        val startEmotion = emotionHistory.first().second
        val endEmotion = emotionHistory.last().second
        val peakEmotion = emotionHistory.maxByOrNull {
            getEmotionIntensity(it.second)
        }?.second ?: Emotion.NEUTRAL

        // Use Gemma 3n to analyze the trajectory
        val trajectoryPrompt = """
            Analyze this emotion trajectory from a therapy session:
            Start: ${startEmotion.name}
            End: ${endEmotion.name}
            Peak intensity: ${peakEmotion.name}
            
            All emotions: ${emotionHistory.map { it.second.name }.joinToString(" → ")}
            
            Provide a brief analysis (max 50 words) of the emotional journey and whether it shows improvement.
            Format: {"improvement": true/false, "summary": "analysis text"}
        """.trimIndent()

        return@withContext try {
            val response = geminiApiService.generateTextComplete(trajectoryPrompt)
            val json = JSONObject(response)

            EmotionTrajectoryAnalysis(
                startEmotion = startEmotion,
                endEmotion = endEmotion,
                peakEmotion = peakEmotion,
                improvement = json.getBoolean("improvement"),
                summary = json.getString("summary")
            )
        } catch (e: Exception) {
            EmotionTrajectoryAnalysis(
                startEmotion = startEmotion,
                endEmotion = endEmotion,
                peakEmotion = peakEmotion,
                improvement = getEmotionIntensity(endEmotion) < getEmotionIntensity(startEmotion),
                summary = "Emotion changed from $startEmotion to $endEmotion"
            )
        }
    }

    // Helper functions for audio analysis
    private fun calculateZeroCrossingRate(audioData: FloatArray): Float {
        var crossings = 0
        for (i in 1 until audioData.size) {
            if (audioData[i] * audioData[i - 1] < 0) {
                crossings++
            }
        }
        return crossings.toFloat() / audioData.size
    }

    private fun calculateSpectralCentroid(audioData: FloatArray): Float {
        // Simplified spectral centroid calculation
        val magnitude = audioData.map { abs(it) }
        val weightedSum = magnitude.mapIndexed { index, value ->
            index * value
        }.sum()
        val totalMagnitude = magnitude.sum()

        return if (totalMagnitude > 0) {
            (weightedSum / totalMagnitude) / audioData.size
        } else {
            0.5f
        }
    }

    private fun getEmotionIntensity(emotion: Emotion): Float {
        return when (emotion) {
            Emotion.ANGRY -> 0.9f
            Emotion.ANXIOUS -> 0.8f
            Emotion.FEARFUL -> 0.85f
            Emotion.SAD -> 0.7f
            Emotion.DISGUSTED -> 0.75f
            Emotion.SURPRISED -> 0.6f
            Emotion.HAPPY -> 0.3f
            Emotion.NEUTRAL -> 0.1f
        }
    }

    data class EmotionDetectionResult(
        val emotion: Emotion,
        val confidence: Float,
        val reasoning: String
    )

    data class EmotionTrajectoryAnalysis(
        val startEmotion: Emotion,
        val endEmotion: Emotion,
        val peakEmotion: Emotion,
        val improvement: Boolean,
        val summary: String
    )
}