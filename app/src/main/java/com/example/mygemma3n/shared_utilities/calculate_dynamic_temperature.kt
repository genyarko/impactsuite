package com.example.mygemma3n.shared_utilities

import kotlin.random.Random

private fun calculateDynamicTemperature(
    baseTemp: Float,
    attemptNumber: Int,
    previousSimilarityScore: Float
): Float {
    // Increase temperature if previous attempts were too similar
    val similarityBoost = if (previousSimilarityScore > 0.5f) 0.2f else 0f

    // Add variety based on attempt number
    val attemptVariety = (attemptNumber % 4) * 0.05f

    // Random component
    val randomness = Random.nextFloat() * 0.1f

    return (baseTemp + similarityBoost + attemptVariety + randomness).coerceIn(0.5f, 1.0f)
}