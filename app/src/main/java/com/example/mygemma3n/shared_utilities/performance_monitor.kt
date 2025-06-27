package com.example.mygemma3n.shared_utilities

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceMonitor @Inject constructor(
    private val analytics: FirebaseAnalytics
) {
    private val metrics = mutableListOf<InferenceMetric>()

    data class InferenceMetric(
        val prefillTimeMs: Long,
        val decodeTimeMs: Long,
        val tokensGenerated: Int,
        val modelSize: String,
        val feature: String
    )

    fun recordInference(
        prefillTime: Long,
        decodeTime: Long,
        tokens: Int,
        model: String,
        feature: String
    ) {
        val metric = InferenceMetric(
            prefillTimeMs = prefillTime,
            decodeTimeMs = decodeTime,
            tokensGenerated = tokens,
            modelSize = model,
            feature = feature
        )

        metrics.add(metric)

        // Log to Firebase for competition metrics
        analytics.logEvent("ai_inference") {
            param("prefill_ms", prefillTime)
            param("decode_ms", decodeTime)
            param("tokens_per_second", (tokens * 1000.0 / decodeTime).toLong())
            param("model", model)
            param("feature", feature)
        }

        // Alert if performance degrades
        if (prefillTime > 300 || decodeTime / tokens > 50) {
            logPerformanceWarning(metric)
        }
    }

    fun getAverageMetrics(): PerformanceSummary {
        return PerformanceSummary(
            avgPrefillMs = metrics.map { it.prefillTimeMs }.average(),
            avgDecodeMs = metrics.map { it.decodeTimeMs }.average(),
            avgTokensPerSecond = metrics.map {
                it.tokensGenerated * 1000.0 / it.decodeTimeMs
            }.average()
        )
    }
}