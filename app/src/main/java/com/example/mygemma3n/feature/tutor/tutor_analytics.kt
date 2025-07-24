package com.example.mygemma3n.feature.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mygemma3n.data.TutorDao
import com.example.mygemma3n.shared_utilities.OfflineRAG
import kotlin.time.Duration.Companion.days
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TutorAnalytics @Inject constructor(
    private val tutorDao: TutorDao,
    private val promptManager: TutorPromptManager
) {

    data class PromptEffectiveness(
        val promptType: String,
        val averageComprehension: Float,
        val averageAttempts: Float,
        val studentSatisfaction: Float,
        val useCount: Int
    )

    data class ConceptDifficulty(
        val concept: String,
        val gradeLevel: Int,
        val averageAttempts: Float,
        val successRate: Float,
        val commonMisconceptions: List<String>
    )

    /**
     * Track which teaching approaches work best for different concepts
     */
    suspend fun getPromptEffectiveness(
        subject: OfflineRAG.Subject,
        timeRange: LongRange
    ): List<PromptEffectiveness> {
        // In a real implementation, this would query detailed interaction logs
        return listOf(
            PromptEffectiveness(
                promptType = "Socratic Method",
                averageComprehension = 0.82f,
                averageAttempts = 1.8f,
                studentSatisfaction = 0.91f,
                useCount = 245
            ),
            PromptEffectiveness(
                promptType = "Direct Explanation",
                averageComprehension = 0.75f,
                averageAttempts = 1.2f,
                studentSatisfaction = 0.78f,
                useCount = 189
            ),
            PromptEffectiveness(
                promptType = "Problem Solving",
                averageComprehension = 0.88f,
                averageAttempts = 2.1f,
                studentSatisfaction = 0.85f,
                useCount = 312
            )
        )
    }

    /**
     * Identify concepts that students struggle with most
     */
    suspend fun getConceptDifficulties(
        subject: OfflineRAG.Subject,
        gradeLevel: Int
    ): List<ConceptDifficulty> {
        val weakConcepts = tutorDao.getConceptsForReview(
            studentId = "", // Aggregate across all students
            gradeLevel = gradeLevel,
            limit = 10
        )

        return weakConcepts.map { concept ->
            ConceptDifficulty(
                concept = concept.concept,
                gradeLevel = concept.gradeLevel,
                averageAttempts = 3.2f, // Would calculate from real data
                successRate = concept.masteryLevel,
                commonMisconceptions = listOf(
                    "Thinks photosynthesis happens in roots",
                    "Confuses respiration with photosynthesis"
                )
            )
        }
    }

    /**
     * Generate recommendations for prompt improvements
     */
    fun generatePromptRecommendations(
        effectiveness: List<PromptEffectiveness>,
        difficulties: List<ConceptDifficulty>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // Check for low-performing prompts
        effectiveness.filter { it.averageComprehension < 0.7f }.forEach { prompt ->
            recommendations.add(
                "Consider revising ${prompt.promptType} prompts - " +
                        "comprehension is only ${(prompt.averageComprehension * 100).toInt()}%"
            )
        }

        // Check for high-attempt concepts
        difficulties.filter { it.averageAttempts > 3 }.forEach { concept ->
            recommendations.add(
                "Students struggle with '${concept.concept}' - " +
                        "consider adding more scaffolding or examples"
            )
        }

        // Check satisfaction vs effectiveness
        effectiveness.forEach { prompt ->
            if (prompt.studentSatisfaction > prompt.averageComprehension + 0.1f) {
                recommendations.add(
                    "${prompt.promptType} is liked but not effective - " +
                            "balance engagement with learning outcomes"
                )
            }
        }

        return recommendations
    }
}

// Composable UI for educators to view analytics
@Composable
fun EducatorDashboard(
    analytics: TutorAnalytics,
    onExportReport: () -> Unit
) {
    var selectedSubject by remember { mutableStateOf(OfflineRAG.Subject.MATHEMATICS) }
    var selectedGrade by remember { mutableIntStateOf(6) }

    val effectiveness by produceState<List<TutorAnalytics.PromptEffectiveness>>(emptyList()) {
        value = analytics.getPromptEffectiveness(
            selectedSubject,
            System.currentTimeMillis() - 7.days.inWholeMilliseconds..System.currentTimeMillis()
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                "Tutor Analytics Dashboard",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Subject selector
                FilterChip(
                    selected = true,
                    onClick = { /* Show subject menu */ },
                    label = { Text(selectedSubject.name) }
                )

                // Grade selector
                FilterChip(
                    selected = true,
                    onClick = { /* Show grade menu */ },
                    label = { Text("Grade $selectedGrade") }
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Prompt Effectiveness",
                        style = MaterialTheme.typography.titleMedium
                    )

                    effectiveness.forEach { metric ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(metric.promptType)
                            Text(
                                "${(metric.averageComprehension * 100).toInt()}% comprehension",
                                color = when {
                                    metric.averageComprehension > 0.8f -> Color.Green
                                    metric.averageComprehension > 0.6f -> Color.Yellow
                                    else -> Color.Red
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onExportReport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Detailed Report")
            }
        }
    }
}