package com.example.mygemma3n.feature.analytics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class KnowledgeGapAnalyzer @Inject constructor(
    private val interactionDao: LearningInteractionDao,
    private val masteryDao: TopicMasteryDao,
    private val progressDao: SubjectProgressDao,
    private val gapDao: KnowledgeGapDao
) {

    /**
     * Comprehensive knowledge gap analysis using multiple algorithms
     */
    suspend fun analyzeKnowledgeGaps(studentId: String): List<KnowledgeGapEntity> {
        val gaps = mutableListOf<KnowledgeGapEntity>()
        
        try {
            // Algorithm 1: Performance Decline Detection
            gaps.addAll(detectPerformanceDeclines(studentId))
            
            // Algorithm 2: Missing Prerequisites Analysis
            gaps.addAll(detectMissingPrerequisites(studentId))
            
            // Algorithm 3: Conceptual Clustering Analysis
            gaps.addAll(detectConceptualMisunderstandings(studentId))
            
            // Algorithm 4: Temporal Learning Pattern Analysis
            gaps.addAll(detectLearningPatternAnomalies(studentId))
            
            // Algorithm 5: Knowledge Fragmentation Detection
            gaps.addAll(detectKnowledgeFragmentation(studentId))
            
            // Filter and prioritize gaps
            val prioritizedGaps = prioritizeAndFilterGaps(gaps)
            
            // Save gaps to database
            gapDao.insertGaps(prioritizedGaps)
            
            Timber.d("Identified ${prioritizedGaps.size} knowledge gaps for student $studentId")
            return prioritizedGaps
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze knowledge gaps")
            return emptyList()
        }
    }

    /**
     * Algorithm 1: Detect performance declines over time
     */
    private suspend fun detectPerformanceDeclines(studentId: String): List<KnowledgeGapEntity> {
        val gaps = mutableListOf<KnowledgeGapEntity>()
        
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            val recentInteractions = interactionDao.getInteractionsSince(studentId, thirtyDaysAgo)
            
            // Group by subject and topic
            val performanceByTopic = recentInteractions
                .filter { it.responseQuality != null }
                .groupBy { "${it.subject}-${it.topic}" }
            
            performanceByTopic.forEach { (topicKey, interactions) ->
                if (interactions.size >= 3) { // Need minimum interactions for trend analysis
                    val trend = calculatePerformanceTrend(interactions.map { it.responseQuality!! })
                    
                    if (trend < -0.2f) { // Significant decline threshold
                        val parts = topicKey.split("-")
                        gaps.add(
                            KnowledgeGapEntity(
                                id = UUID.randomUUID().toString(),
                                studentId = studentId,
                                subject = parts[0],
                                topic = parts[1],
                                concept = "Overall understanding",
                                gapType = GapType.DECLINING_PERFORMANCE,
                                priority = when {
                                    trend < -0.4f -> GapPriority.CRITICAL
                                    trend < -0.3f -> GapPriority.HIGH
                                    else -> GapPriority.MEDIUM
                                },
                                description = "Performance declining in ${parts[1]} (${(trend * 100).roundToInt()}% trend)",
                                suggestedActions = """["Review fundamental concepts", "Practice more exercises", "Seek additional help"]""",
                                prerequisiteTopics = getPrerequisiteTopics(parts[0], parts[1])
                            )
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect performance declines")
        }
        
        return gaps
    }

    /**
     * Algorithm 2: Detect missing prerequisites based on mastery patterns
     */
    private suspend fun detectMissingPrerequisites(studentId: String): List<KnowledgeGapEntity> {
        val gaps = mutableListOf<KnowledgeGapEntity>()
        
        try {
            val masteryData = masteryDao.getMasteryForStudent(studentId)
            
            masteryData.collect { masteries ->
                val masteryBySubject = masteries.groupBy { it.subject }
                
                masteryBySubject.forEach { (subject, subjectMasteries) ->
                    val prerequisiteMap = getSubjectPrerequisites(subject)
                    
                    subjectMasteries.forEach { mastery ->
                        if (mastery.masteryLevel in listOf(MasteryLevel.INTRODUCED, MasteryLevel.DEVELOPING)) {
                            val prerequisites = prerequisiteMap[mastery.topic] ?: emptyList()
                            
                            prerequisites.forEach { prerequisite ->
                                val prereqMastery = subjectMasteries.find { it.topic == prerequisite }
                                
                                if (prereqMastery == null || prereqMastery.masteryLevel in listOf(
                                        MasteryLevel.NOT_STARTED, 
                                        MasteryLevel.INTRODUCED
                                    )) {
                                    gaps.add(
                                        KnowledgeGapEntity(
                                            id = UUID.randomUUID().toString(),
                                            studentId = studentId,
                                            subject = subject,
                                            topic = prerequisite,
                                            concept = "Foundation knowledge",
                                            gapType = GapType.MISSING_PREREQUISITE,
                                            priority = GapPriority.HIGH,
                                            description = "Missing prerequisite: $prerequisite needed for ${mastery.topic}",
                                            prerequisiteTopics = """["$prerequisite"]"""
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect missing prerequisites")
        }
        
        return gaps
    }

    /**
     * Algorithm 3: Detect conceptual misunderstandings using error pattern analysis
     */
    private suspend fun detectConceptualMisunderstandings(studentId: String): List<KnowledgeGapEntity> {
        val gaps = mutableListOf<KnowledgeGapEntity>()
        
        try {
            val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            val recentInteractions = interactionDao.getInteractionsSince(studentId, sevenDaysAgo)
            
            // Group by concept and analyze error patterns
            val interactionsByConcept = recentInteractions
                .filter { it.wasCorrect != null }
                .groupBy { "${it.subject}-${it.topic}-${it.concept}" }
            
            interactionsByConcept.forEach { (conceptKey, interactions) ->
                val incorrectCount = interactions.count { it.wasCorrect == false }
                val totalCount = interactions.size
                val errorRate = if (totalCount > 0) incorrectCount.toFloat() / totalCount else 0f
                
                // High error rate indicates conceptual misunderstanding
                if (totalCount >= 3 && errorRate > 0.6f) {
                    val parts = conceptKey.split("-")
                    val attemptsPattern = interactions.map { it.attemptsNeeded }
                    val avgAttempts = attemptsPattern.average()
                    
                    gaps.add(
                        KnowledgeGapEntity(
                            id = UUID.randomUUID().toString(),
                            studentId = studentId,
                            subject = parts[0],
                            topic = parts[1],
                            concept = parts[2],
                            gapType = GapType.CONCEPTUAL_MISUNDERSTANDING,
                            priority = when {
                                errorRate > 0.8f -> GapPriority.CRITICAL
                                errorRate > 0.7f -> GapPriority.HIGH
                                else -> GapPriority.MEDIUM
                            },
                            description = "Conceptual difficulty with ${parts[2]} (${(errorRate * 100).roundToInt()}% error rate, avg ${avgAttempts.roundToInt()} attempts)",
                            suggestedActions = """["Review concept fundamentals", "Try alternative explanations", "Practice with simpler examples"]"""
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect conceptual misunderstandings")
        }
        
        return gaps
    }

    /**
     * Algorithm 4: Detect learning pattern anomalies
     */
    private suspend fun detectLearningPatternAnomalies(studentId: String): List<KnowledgeGapEntity> {
        val gaps = mutableListOf<KnowledgeGapEntity>()
        
        try {
            val fourteenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
            val interactions = interactionDao.getInteractionsSince(studentId, fourteenDaysAgo)
            
            val bySubject = interactions.groupBy { it.subject }
            
            bySubject.forEach { (subject, subjectInteractions) ->
                // Analyze session duration patterns
                val sessionDurations = subjectInteractions.map { it.sessionDurationMs }
                val avgDuration = sessionDurations.average()
                val durationVariance = calculateVariance(sessionDurations.map { it.toDouble() })
                
                // Detect unusual patterns
                val recentDurations = sessionDurations.takeLast(5)
                val recentAvgDuration = if (recentDurations.isNotEmpty()) recentDurations.average() else avgDuration
                
                // Significant drop in engagement
                if (recentAvgDuration < avgDuration * 0.5 && durationVariance > avgDuration * 0.3) {
                    gaps.add(
                        KnowledgeGapEntity(
                            id = UUID.randomUUID().toString(),
                            studentId = studentId,
                            subject = subject,
                            topic = "General engagement",
                            concept = "Study patterns",
                            gapType = GapType.INSUFFICIENT_PRACTICE,
                            priority = GapPriority.MEDIUM,
                            description = "Declining engagement pattern detected in $subject",
                            suggestedActions = """["Take a break", "Try different learning approach", "Set smaller goals"]"""
                        )
                    )
                }
                
                // Analyze help-seeking patterns
                val helpRequests = subjectInteractions.count { it.helpRequested }
                val helpRate = if (subjectInteractions.isNotEmpty()) helpRequests.toFloat() / subjectInteractions.size else 0f
                
                if (helpRate > 0.4f) { // High help-seeking rate
                    gaps.add(
                        KnowledgeGapEntity(
                            id = UUID.randomUUID().toString(),
                            studentId = studentId,
                            subject = subject,
                            topic = "Self-sufficiency",
                            concept = "Independent learning",
                            gapType = GapType.PROCEDURAL_ERROR,
                            priority = GapPriority.MEDIUM,
                            description = "High dependency on help in $subject (${(helpRate * 100).roundToInt()}% help rate)",
                            suggestedActions = """["Build foundational skills", "Practice problem-solving strategies", "Develop confidence"]"""
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect learning pattern anomalies")
        }
        
        return gaps
    }

    /**
     * Algorithm 5: Detect knowledge fragmentation
     */
    private suspend fun detectKnowledgeFragmentation(studentId: String): List<KnowledgeGapEntity> {
        val gaps = mutableListOf<KnowledgeGapEntity>()
        
        try {
            val masteryData = masteryDao.getMasteryForStudent(studentId)
            
            masteryData.collect { masteries ->
                val masteryBySubject = masteries.groupBy { it.subject }
                
                masteryBySubject.forEach { (subject, subjectMasteries) ->
                    // Look for inconsistent mastery levels within related topics
                    val relatedTopicGroups = getRelatedTopicGroups(subject)
                    
                    relatedTopicGroups.forEach { topicGroup ->
                        val groupMasteries = subjectMasteries.filter { it.topic in topicGroup }
                        
                        if (groupMasteries.size >= 2) {
                            val masteryLevels = groupMasteries.map { it.masteryLevel.ordinal }
                            val maxLevel = masteryLevels.maxOrNull() ?: 0
                            val minLevel = masteryLevels.minOrNull() ?: 0
                            
                            // Significant gap between mastery levels in related topics
                            if (maxLevel - minLevel >= 3) {
                                val weakTopics = groupMasteries
                                    .filter { it.masteryLevel.ordinal <= minLevel + 1 }
                                    .map { it.topic }
                                
                                weakTopics.forEach { weakTopic ->
                                    gaps.add(
                                        KnowledgeGapEntity(
                                            id = UUID.randomUUID().toString(),
                                            studentId = studentId,
                                            subject = subject,
                                            topic = weakTopic,
                                            concept = "Topic connections",
                                            gapType = GapType.KNOWLEDGE_FRAGMENTATION,
                                            priority = GapPriority.MEDIUM,
                                            description = "Knowledge fragmentation: $weakTopic lags behind related topics",
                                            suggestedActions = """["Connect to stronger topics", "Review relationships", "Practice integration"]""",
                                            prerequisiteTopics = topicGroup.filter { it != weakTopic }.take(2).toString()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect knowledge fragmentation")
        }
        
        return gaps
    }

    /**
     * Prioritize and filter gaps to avoid overwhelming the student
     */
    private fun prioritizeAndFilterGaps(gaps: List<KnowledgeGapEntity>): List<KnowledgeGapEntity> {
        return gaps
            .distinctBy { "${it.subject}-${it.topic}-${it.concept}" } // Remove duplicates
            .sortedWith(
                compareByDescending<KnowledgeGapEntity> { it.priority.ordinal }
                    .thenBy { it.subject }
                    .thenBy { it.topic }
            )
            .take(10) // Limit to top 10 gaps
    }

    // Helper functions

    private fun calculatePerformanceTrend(values: List<Float>): Float {
        if (values.size < 2) return 0f
        
        val n = values.size
        val sumX = (0 until n).sum()
        val sumY = values.sum()
        val sumXY = values.mapIndexed { index, value -> index * value }.sum()
        val sumX2 = (0 until n).map { it * it }.sum()
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        return slope
    }

    private fun calculateVariance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average()
    }

    private fun getPrerequisiteTopics(subject: String, topic: String): String {
        // Simplified prerequisite mapping - in real implementation, this would be more comprehensive
        val prerequisites = when (subject.uppercase()) {
            "MATHEMATICS" -> when (topic.lowercase()) {
                "algebra" -> listOf("arithmetic", "basic_operations")
                "geometry" -> listOf("basic_shapes", "measurements")
                "calculus" -> listOf("algebra", "functions")
                else -> emptyList()
            }
            "SCIENCE" -> when (topic.lowercase()) {
                "chemistry" -> listOf("atoms", "elements")
                "physics" -> listOf("mathematics", "measurements")
                else -> emptyList()
            }
            else -> emptyList()
        }
        return prerequisites.toString()
    }

    private fun getSubjectPrerequisites(subject: String): Map<String, List<String>> {
        // Simplified prerequisite mapping
        return when (subject.uppercase()) {
            "MATHEMATICS" -> mapOf(
                "algebra" to listOf("arithmetic", "basic_operations"),
                "geometry" to listOf("basic_shapes", "measurements"),
                "calculus" to listOf("algebra", "functions", "limits"),
                "statistics" to listOf("arithmetic", "graphs")
            )
            "SCIENCE" -> mapOf(
                "chemistry" to listOf("atoms", "elements", "periodic_table"),
                "physics" to listOf("mathematics", "measurements", "forces"),
                "biology" to listOf("cells", "organisms", "life_processes")
            )
            else -> emptyMap()
        }
    }

    private fun getRelatedTopicGroups(subject: String): List<List<String>> {
        // Groups of related topics that should have similar mastery levels
        return when (subject.uppercase()) {
            "MATHEMATICS" -> listOf(
                listOf("addition", "subtraction", "multiplication", "division"),
                listOf("fractions", "decimals", "percentages"),
                listOf("algebra", "equations", "variables"),
                listOf("geometry", "shapes", "area", "perimeter")
            )
            "SCIENCE" -> listOf(
                listOf("atoms", "molecules", "compounds"),
                listOf("forces", "motion", "energy"),
                listOf("cells", "tissues", "organs")
            )
            "ENGLISH" -> listOf(
                listOf("grammar", "punctuation", "spelling"),
                listOf("reading", "comprehension", "vocabulary"),
                listOf("writing", "essays", "paragraphs")
            )
            else -> emptyList()
        }
    }
}