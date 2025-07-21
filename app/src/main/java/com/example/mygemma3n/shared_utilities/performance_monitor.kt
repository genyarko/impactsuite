package com.example.mygemma3n.shared_utilities

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class PerformanceMonitor @Inject constructor(
    private val context: Context,
    private val analytics: FirebaseAnalytics,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MAX_METRICS_HISTORY = 100

        // Performance thresholds for Gemma 3n competition requirements
        private const val TARGET_CAPTION_LATENCY_MS = 300L
        private const val TARGET_DECODE_MS_PER_TOKEN = 50L
        private const val TARGET_MEMORY_MB = 2300 // 2.3 GB
        private const val TARGET_PREFILL_MS = 200L
    }

    private val metrics = ConcurrentLinkedQueue<InferenceMetric>()
    private val _performanceState = MutableStateFlow(PerformanceState())
    val performanceState: StateFlow<PerformanceState> = _performanceState

    data class InferenceMetric(
        val timestamp: Long = System.currentTimeMillis(),
        val prefillTimeMs: Long,
        val decodeTimeMs: Long,
        val tokensGenerated: Int,
        val modelSize: String,
        val feature: String,
        val memoryUsedMB: Int,
        val cacheHitRate: Float = 0f,
        val delegateType: String = "CPU",
        val quantizationType: String = "INT8"
    )

    data class PerformanceSummary(
        val avgPrefillMs: Double,
        val avgDecodeMs: Double,
        val avgTokensPerSecond: Double,
        val p95PrefillMs: Double,
        val p95DecodeMs: Double,
        val avgMemoryMB: Double,
        val totalInferences: Int,
        val successRate: Double,
        val avgCacheHitRate: Double
    )

    data class PerformanceState(
        val isPerformanceOptimal: Boolean = true,
        val currentMemoryMB: Int = 0,
        val recentLatencyMs: Long = 0,
        val warnings: List<PerformanceWarning> = emptyList()
    )

    data class PerformanceWarning(
        val type: WarningType,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class WarningType {
        HIGH_LATENCY,
        HIGH_MEMORY,
        LOW_CACHE_HIT,
        THERMAL_THROTTLING,
        DELEGATE_FALLBACK
    }

    fun startMonitoring() {
        scope.launch(Dispatchers.IO) {
            while (true) {
                updateMemoryMetrics()
                checkPerformanceHealth()
                kotlinx.coroutines.delay(5000) // Check every 5 seconds
            }
        }
    }

    fun recordInference(
        prefillTime: Long,
        decodeTime: Long,
        tokens: Int,
        model: String,
        feature: String,
        cacheHitRate: Float = 0f,
        delegateType: String = "CPU"
    ) {
        val memoryMB = getCurrentMemoryUsageMB()

        val metric = InferenceMetric(
            prefillTimeMs = prefillTime,
            decodeTimeMs = decodeTime,
            tokensGenerated = tokens,
            modelSize = model,
            feature = feature,
            memoryUsedMB = memoryMB,
            cacheHitRate = cacheHitRate,
            delegateType = delegateType,
            quantizationType = if (model.contains("2b")) "INT8" else "INT4"
        )

        metrics.add(metric)

        // Keep only recent metrics
        while (metrics.size > MAX_METRICS_HISTORY) {
            metrics.poll()
        }

        // Log to Firebase for competition metrics
        analytics.logEvent("gemma3n_inference") {
            param("prefill_ms", prefillTime)
            param("decode_ms", decodeTime)
            param("tokens_per_second", calculateTokensPerSecond(decodeTime, tokens))
            param("model", model)
            param("feature", feature)
            param("memory_mb", memoryMB.toLong())
            param("cache_hit_rate", (cacheHitRate * 100).roundToInt().toLong())
            param("delegate", delegateType)
        }

        // Check performance against competition targets
        checkCompetitionTargets(metric)

        // Update state
        updatePerformanceState(metric)
    }

    private fun calculateTokensPerSecond(decodeTimeMs: Long, tokens: Int): Long {
        return if (decodeTimeMs > 0) {
            (tokens * 1000.0 / decodeTimeMs).toLong()
        } else {
            0L
        }
    }

    fun trackEvent(name: String, params: Map<String, String>) {
        analytics.logEvent(name) {
            params.forEach { (key, value) ->
                param(key, value)
            }
        }
    }


    private fun checkCompetitionTargets(metric: InferenceMetric) {
        val warnings = mutableListOf<PerformanceWarning>()

        // Check caption latency for live caption feature
        if (metric.feature == "live_caption" &&
            metric.prefillTimeMs + metric.decodeTimeMs > TARGET_CAPTION_LATENCY_MS) {
            warnings.add(
                PerformanceWarning(
                    type = WarningType.HIGH_LATENCY,
                    message = "Caption latency ${metric.prefillTimeMs + metric.decodeTimeMs}ms exceeds target ${TARGET_CAPTION_LATENCY_MS}ms"
                )
            )
        }

        // Check decode time per token
        val msPerToken = if (metric.tokensGenerated > 0) {
            metric.decodeTimeMs / metric.tokensGenerated
        } else 0

        if (msPerToken > TARGET_DECODE_MS_PER_TOKEN) {
            warnings.add(
                PerformanceWarning(
                    type = WarningType.HIGH_LATENCY,
                    message = "Decode speed ${msPerToken}ms/token exceeds target ${TARGET_DECODE_MS_PER_TOKEN}ms/token"
                )
            )
        }

        // Check memory usage
        if (metric.memoryUsedMB > TARGET_MEMORY_MB) {
            warnings.add(
                PerformanceWarning(
                    type = WarningType.HIGH_MEMORY,
                    message = "Memory usage ${metric.memoryUsedMB}MB exceeds target ${TARGET_MEMORY_MB}MB"
                )
            )
        }

        // Check cache performance for PLE optimization
        if (metric.cacheHitRate < 0.7f && metric.modelSize != "2b") {
            warnings.add(
                PerformanceWarning(
                    type = WarningType.LOW_CACHE_HIT,
                    message = "Low cache hit rate ${(metric.cacheHitRate * 100).roundToInt()}% for PLE optimization"
                )
            )
        }

        // Log warnings
        warnings.forEach { warning ->
            logPerformanceWarning(metric, warning)
        }
    }

    private fun logPerformanceWarning(metric: InferenceMetric, warning: PerformanceWarning) {
        Log.w(TAG, "${warning.type}: ${warning.message}")

        analytics.logEvent("performance_warning") {
            param("warning_type", warning.type.name)
            param("feature", metric.feature)
            param("model", metric.modelSize)
            param("message", warning.message.take(100)) // Firebase has length limits
        }

        // Add to state warnings
        _performanceState.value = _performanceState.value.copy(
            warnings = (_performanceState.value.warnings + warning).takeLast(10)
        )
    }

    fun getAverageMetrics(): PerformanceSummary {
        val metricsList = metrics.toList()

        if (metricsList.isEmpty()) {
            return PerformanceSummary(
                avgPrefillMs = 0.0,
                avgDecodeMs = 0.0,
                avgTokensPerSecond = 0.0,
                p95PrefillMs = 0.0,
                p95DecodeMs = 0.0,
                avgMemoryMB = 0.0,
                totalInferences = 0,
                successRate = 0.0,
                avgCacheHitRate = 0.0
            )
        }

        val prefillTimes = metricsList.map { it.prefillTimeMs }.sorted()
        val decodeTimes = metricsList.map { it.decodeTimeMs }.sorted()

        return PerformanceSummary(
            avgPrefillMs = prefillTimes.average(),
            avgDecodeMs = decodeTimes.average(),
            avgTokensPerSecond = metricsList.map {
                it.tokensGenerated * 1000.0 / it.decodeTimeMs.coerceAtLeast(1)
            }.average(),
            p95PrefillMs = percentile(prefillTimes, 0.95),
            p95DecodeMs = percentile(decodeTimes, 0.95),
            avgMemoryMB = metricsList.map { it.memoryUsedMB }.average(),
            totalInferences = metricsList.size,
            successRate = calculateSuccessRate(metricsList),
            avgCacheHitRate = metricsList.map { it.cacheHitRate.toDouble() }.average()
        )
    }

    private fun percentile(sortedList: List<Long>, percentile: Double): Double {
        if (sortedList.isEmpty()) return 0.0
        val index = (sortedList.size * percentile).toInt().coerceIn(0, sortedList.size - 1)
        return sortedList[index].toDouble()
    }

    private fun calculateSuccessRate(metrics: List<InferenceMetric>): Double {
        if (metrics.isEmpty()) return 0.0

        val successful = metrics.count { metric ->
            val totalTime = metric.prefillTimeMs + metric.decodeTimeMs
            when (metric.feature) {
                "live_caption" -> totalTime <= TARGET_CAPTION_LATENCY_MS
                else -> metric.decodeTimeMs / metric.tokensGenerated.coerceAtLeast(1) <= TARGET_DECODE_MS_PER_TOKEN
            }
        }

        return successful.toDouble() / metrics.size
    }

    private fun getCurrentMemoryUsageMB(): Int {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)

        // Total PSS (Proportional Set Size) in MB
        return memInfo.totalPss / 1024
    }

    private fun updateMemoryMetrics() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val appMemoryMB = getCurrentMemoryUsageMB()

        _performanceState.value = _performanceState.value.copy(
            currentMemoryMB = appMemoryMB
        )

        // Log memory metrics periodically
        analytics.logEvent("memory_snapshot") {
            param("app_memory_mb", appMemoryMB.toLong())
            param("available_memory_mb", (memoryInfo.availMem / (1024 * 1024)).toLong())
            param("low_memory", if (memoryInfo.lowMemory) 1L else 0L)
        }
    }

    private fun checkPerformanceHealth() {
        val recent = metrics.toList().takeLast(10)
        if (recent.isEmpty()) return

        val avgLatency = recent.map { it.prefillTimeMs + it.decodeTimeMs }.average()
        val isOptimal = avgLatency < TARGET_CAPTION_LATENCY_MS &&
                _performanceState.value.currentMemoryMB < TARGET_MEMORY_MB

        _performanceState.value = _performanceState.value.copy(
            isPerformanceOptimal = isOptimal,
            recentLatencyMs = avgLatency.toLong()
        )
    }

    private fun updatePerformanceState(metric: InferenceMetric) {
        scope.launch {
            _performanceState.value = _performanceState.value.copy(
                recentLatencyMs = metric.prefillTimeMs + metric.decodeTimeMs
            )
        }
    }

    // Optimization suggestions based on metrics
    fun getOptimizationSuggestions(): List<String> {
        val summary = getAverageMetrics()
        val suggestions = mutableListOf<String>()

        if (summary.avgPrefillMs > TARGET_PREFILL_MS) {
            suggestions.add("Consider using 2B model for lower prefill latency")
        }

        if (summary.avgCacheHitRate < 0.7) {
            suggestions.add("Enable PLE cache reuse for better performance")
        }

        if (summary.avgMemoryMB > TARGET_MEMORY_MB * 0.9) {
            suggestions.add("Memory usage approaching limit - consider model quantization")
        }

        val gpuMetrics = metrics.filter { it.delegateType == "GPU" }
        if (gpuMetrics.isEmpty()) {
            suggestions.add("Enable GPU delegate for faster inference")
        }

        return suggestions
    }

    // Export metrics for competition submission
    fun exportMetricsReport(): String {
        val summary = getAverageMetrics()
        val modelBreakdown = metrics.groupBy { it.modelSize }
            .mapValues { (_, metrics) ->
                metrics.map { it.prefillTimeMs + it.decodeTimeMs }.average()
            }

        return """
            Gemma 3n Performance Report
            ==========================
            
            Overall Performance:
            - Total Inferences: ${summary.totalInferences}
            - Success Rate: ${(summary.successRate * 100).roundToInt()}%
            - Avg Prefill Time: ${summary.avgPrefillMs.roundToInt()}ms
            - Avg Decode Time: ${summary.avgDecodeMs.roundToInt()}ms
            - Avg Tokens/Second: ${summary.avgTokensPerSecond.roundToInt()}
            - P95 Latency: ${(summary.p95PrefillMs + summary.p95DecodeMs).roundToInt()}ms
            - Avg Memory Usage: ${summary.avgMemoryMB.roundToInt()}MB
            - Cache Hit Rate: ${(summary.avgCacheHitRate * 100).roundToInt()}%
            
            Model Performance:
            ${modelBreakdown.entries.joinToString("\n") { (model, avgLatency) ->
            "- $model: ${avgLatency.roundToInt()}ms avg latency"
        }}
            
            Competition Targets:
            - Caption Latency: ${if (summary.avgPrefillMs + summary.avgDecodeMs <= TARGET_CAPTION_LATENCY_MS) "✓ PASS" else "✗ FAIL"}
            - Memory Usage: ${if (summary.avgMemoryMB <= TARGET_MEMORY_MB) "✓ PASS" else "✗ FAIL"}
            - Decode Speed: ${if (summary.avgDecodeMs / summary.avgTokensPerSecond <= TARGET_DECODE_MS_PER_TOKEN) "✓ PASS" else "✗ FAIL"}
        """.trimIndent()
    }
}