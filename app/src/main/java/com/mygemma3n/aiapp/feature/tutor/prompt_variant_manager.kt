package com.mygemma3n.aiapp.feature.tutor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PromptVariantManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        val PROMPT_VARIANT_KEY = stringPreferencesKey("prompt_variant")
        val PROMPT_PERFORMANCE_KEY = stringPreferencesKey("prompt_performance")
    }

    data class PromptVariant(
        val id: String,
        val name: String,
        val modifiers: Map<String, String>
    )

    // Define different prompt variants for A/B testing
    private val variants = listOf(
        PromptVariant(
            id = "standard",
            name = "Standard Prompts",
            modifiers = emptyMap()
        ),
        PromptVariant(
            id = "conversational",
            name = "More Conversational",
            modifiers = mapOf(
                "tone" to "Be extra friendly and conversational, like talking to a friend.",
                "style" to "Use 'we' and 'let's' frequently. Add encouraging phrases."
            )
        ),
        PromptVariant(
            id = "structured",
            name = "Highly Structured",
            modifiers = mapOf(
                "format" to "Always use numbered lists and clear headers.",
                "style" to "Be very organized. Use 'First:', 'Second:', etc."
            )
        ),
        PromptVariant(
            id = "gamified",
            name = "Gamified Learning",
            modifiers = mapOf(
                "tone" to "Make it feel like a fun game or adventure!",
                "style" to "Use points, levels, and achievement language.",
                "examples" to "Frame problems as quests or challenges."
            )
        )
    )

    suspend fun getCurrentVariant(): PromptVariant {
        val variantId = dataStore.data.map { prefs ->
            prefs[PROMPT_VARIANT_KEY]
        }.first()

        return if (variantId != null) {
            variants.find { it.id == variantId } ?: assignRandomVariant()
        } else {
            assignRandomVariant()
        }
    }

    private suspend fun assignRandomVariant(): PromptVariant {
        // For A/B testing, randomly assign a variant
        val variant = variants[Random.nextInt(variants.size)]

        dataStore.edit { prefs ->
            prefs[PROMPT_VARIANT_KEY] = variant.id
        }

        return variant
    }

    suspend fun applyVariantToPrompt(basePrompt: String): String {
        val variant = getCurrentVariant()

        if (variant.modifiers.isEmpty()) {
            return basePrompt
        }

        val modifierText = variant.modifiers.entries.joinToString("\n") { (key, value) ->
            "${key.uppercase()}: $value"
        }

        return """
            $basePrompt
            
            STYLE VARIANT: ${variant.name}
            $modifierText
        """.trimIndent()
    }

    suspend fun trackPerformance(
        variantId: String,
        metric: PerformanceMetric,
        value: Float
    ) {
        // Track how well each variant performs
        val currentData = dataStore.data.map { prefs ->
            prefs[PROMPT_PERFORMANCE_KEY] ?: "{}"
        }.first()

        // In production, this would be sent to analytics
        // For now, just log it
        timber.log.Timber.d(
            "Prompt variant performance: $variantId - ${metric.name} = $value"
        )
    }

    enum class PerformanceMetric {
        STUDENT_COMPREHENSION,
        RESPONSE_HELPFULNESS,
        ENGAGEMENT_DURATION,
        CONCEPT_MASTERY_RATE
    }
}

// Extension to integrate with TutorPromptManager
suspend fun TutorPromptManager.getPromptWithVariant(
    basePromptGenerator: () -> String,
    variantManager: PromptVariantManager
): String {
    val basePrompt = basePromptGenerator()
    return variantManager.applyVariantToPrompt(basePrompt)
}