package com.example.mygemma3n.feature.quiz

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Data classes for analytics
data class QuizAnalytics(
    val overallAccuracy: Float,
    val totalQuestionsAnswered: Int,
    val questionsToday: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val subjectPerformance: Map<Subject, SubjectAnalytics>,
    val frequentlyMissedQuestions: List<MissedQuestionInfo>,
    val topicCoverage: Map<String, TopicCoverageInfo>,
    val difficultyBreakdown: Map<Difficulty, DifficultyStats>,
    val weeklyProgress: List<DailyProgress>,
    val conceptMastery: Map<String, ConceptMasteryInfo>
)

data class SubjectAnalytics(
    val accuracy: Float,
    val questionsAnswered: Int,
    val averageTimePerQuestion: Float,
    val lastAttempted: Long,
    val topicBreakdown: Map<String, Float> // topic -> accuracy
)

data class MissedQuestionInfo(
    val questionText: String,
    val timesAttempted: Int,
    val timesCorrect: Int,
    val lastAttempted: Long,
    val concepts: List<String>,
    val difficulty: Difficulty,
    val subject: Subject
)

data class TopicCoverageInfo(
    val topicName: String,
    val totalQuestions: Int,
    val questionsAttempted: Int,
    val coveragePercentage: Float,
    val lastCovered: Long?,
    val accuracy: Float
)

data class DifficultyStats(
    val questionsAttempted: Int,
    val accuracy: Float,
    val averageTime: Float
)

data class DailyProgress(
    val date: Long,
    val questionsAnswered: Int,
    val accuracy: Float,
    val subjects: Set<Subject>
)

data class ConceptMasteryInfo(
    val concept: String,
    val masteryLevel: Float, // 0-1
    val questionsAnswered: Int,
    val lastSeen: Long,
    val trend: MasteryTrend
)

enum class MasteryTrend { IMPROVING, STABLE, DECLINING }

// Analytics Screen Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizAnalyticsScreen(
    analytics: QuizAnalytics,
    onNavigateBack: () -> Unit,
    onSelectMissedQuestion: (MissedQuestionInfo) -> Unit,
    onSelectTopic: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz Analytics Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overview Card
            OverviewCard(analytics)

            // Weekly Progress Chart
            WeeklyProgressChart(analytics.weeklyProgress)

            // Subject Performance
            SubjectPerformanceSection(analytics.subjectPerformance)

            // Frequently Missed Questions
            FrequentlyMissedSection(
                missedQuestions = analytics.frequentlyMissedQuestions,
                onSelectQuestion = onSelectMissedQuestion
            )

            // Topic Coverage
            TopicCoverageSection(
                topicCoverage = analytics.topicCoverage,
                onSelectTopic = onSelectTopic
            )

            // Concept Mastery
            ConceptMasterySection(analytics.conceptMastery)

            // Difficulty Breakdown
            DifficultyBreakdownSection(analytics.difficultyBreakdown)
        }
    }
}

@Composable
fun OverviewCard(analytics: QuizAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Learning Overview",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.School,
                    value = "${analytics.totalQuestionsAnswered}",
                    label = "Total Questions"
                )
                StatItem(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    value = "${(analytics.overallAccuracy * 100).roundToInt()}%",
                    label = "Accuracy"
                )
                StatItem(
                    icon = Icons.Default.LocalFireDepartment,
                    value = "${analytics.currentStreak}",
                    label = "Day Streak"
                )
            }

            if (analytics.questionsToday > 0) {
                LinearProgressIndicator(
                    progress = { analytics.questionsToday / 20f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "${analytics.questionsToday}/20 questions today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WeeklyProgressChart(weeklyProgress: List<DailyProgress>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Weekly Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                drawWeeklyProgress(weeklyProgress)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weeklyProgress.takeLast(7).forEach { day ->
                    Text(
                        SimpleDateFormat("EEE", Locale.getDefault()).format(Date(day.date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun DrawScope.drawWeeklyProgress(weeklyProgress: List<DailyProgress>) {
    if (weeklyProgress.isEmpty()) return

    val padding = 40f
    val graphWidth = size.width - 2 * padding
    val graphHeight = size.height - 2 * padding
    val maxQuestions = weeklyProgress.maxOf { it.questionsAnswered }.coerceAtLeast(1)
    val dataPoints = weeklyProgress.takeLast(7)

    // Draw grid lines
    for (i in 0..4) {
        val y = padding + (i * graphHeight / 4)
        drawLine(
            color = Color.Gray.copy(alpha = 0.2f),
            start = Offset(padding, y),
            end = Offset(size.width - padding, y),
            strokeWidth = 1f
        )
    }

    // Draw data
    if (dataPoints.size > 1) {
        val path = Path()
        val accuracyPath = Path()

        dataPoints.forEachIndexed { index, day ->
            val x = padding + (index * graphWidth / (dataPoints.size - 1))
            val y = padding + graphHeight - (day.questionsAnswered.toFloat() / maxQuestions * graphHeight)
            val accuracyY = padding + graphHeight - (day.accuracy * graphHeight)

            if (index == 0) {
                path.moveTo(x, y)
                accuracyPath.moveTo(x, accuracyY)
            } else {
                path.lineTo(x, y)
                accuracyPath.lineTo(x, accuracyY)
            }

            // Draw points
            drawCircle(
                color = Color(0xFF4CAF50),
                radius = 6f,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color(0xFF2196F3),
                radius = 6f,
                center = Offset(x, accuracyY)
            )
        }

        // Draw lines
        drawPath(
            path = path,
            color = Color(0xFF4CAF50),
            style = Stroke(width = 3f)
        )
        drawPath(
            path = accuracyPath,
            color = Color(0xFF2196F3),
            style = Stroke(width = 3f)
        )
    }
}

@Composable
fun FrequentlyMissedSection(
    missedQuestions: List<MissedQuestionInfo>,
    onSelectQuestion: (MissedQuestionInfo) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Frequently Missed Questions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (missedQuestions.isEmpty()) {
                Text(
                    "Great job! No frequently missed questions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                missedQuestions.take(5).forEach { question ->
                    MissedQuestionItem(
                        question = question,
                        onClick = { onSelectQuestion(question) }
                    )
                    if (question != missedQuestions.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedQuestionItem(
    question: MissedQuestionInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                question.questionText.take(100) + if (question.questionText.length > 100) "..." else "",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { },
                        label = { Text("${question.timesCorrect}/${question.timesAttempted} correct") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = { },
                        label = { Text(question.difficulty.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (question.difficulty) {
                                Difficulty.EASY -> Color.Green.copy(alpha = 0.2f)
                                Difficulty.MEDIUM -> Color.Yellow.copy(alpha = 0.2f)
                                Difficulty.HARD -> Color.Red.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                }
                Text(
                    question.subject.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SubjectPerformanceSection(subjectPerformance: Map<Subject, SubjectAnalytics>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Subject Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            subjectPerformance.forEach { (subject, analytics) ->
                SubjectPerformanceItem(subject, analytics)
                if (subject != subjectPerformance.keys.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SubjectPerformanceItem(
    subject: Subject,
    analytics: SubjectAnalytics
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                subject.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${(analytics.accuracy * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    analytics.accuracy > 0.8f -> Color(0xFF4CAF50)
                    analytics.accuracy > 0.6f -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
            )
        }
        LinearProgressIndicator(
            progress = { analytics.accuracy },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                analytics.accuracy > 0.8f -> Color(0xFF4CAF50)
                analytics.accuracy > 0.6f -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            "${analytics.questionsAnswered} questions",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TopicCoverageSection(
    topicCoverage: Map<String, TopicCoverageInfo>,
    onSelectTopic: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Topic Coverage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.DonutLarge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Show topics that need more coverage first
            val sortedTopics = topicCoverage.values
                .sortedBy { it.coveragePercentage }
                .take(10)

            sortedTopics.forEach { topic ->
                TopicCoverageItem(
                    topic = topic,
                    onClick = { onSelectTopic(topic.topicName) }
                )
                if (topic != sortedTopics.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCoverageItem(
    topic: TopicCoverageInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    topic.topicName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${topic.questionsAttempted}/${topic.totalQuestions} questions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(60.dp)
            ) {
                CircularProgressIndicator(
                    progress = { topic.coveragePercentage },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 4.dp,
                    color = when {
                        topic.coveragePercentage > 0.8f -> Color(0xFF4CAF50)
                        topic.coveragePercentage > 0.5f -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "${(topic.coveragePercentage * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConceptMasterySection(conceptMastery: Map<String, ConceptMasteryInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Concept Mastery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val topConcepts = conceptMastery.values
                .sortedByDescending { it.masteryLevel }
                .take(5)

            topConcepts.forEach { concept ->
                ConceptMasteryItem(concept)
                if (concept != topConcepts.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ConceptMasteryItem(concept: ConceptMasteryInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (concept.trend) {
                    MasteryTrend.IMPROVING -> Icons.Default.TrendingUp
                    MasteryTrend.STABLE -> Icons.Default.Remove
                    MasteryTrend.DECLINING -> Icons.AutoMirrored.Filled.TrendingDown
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (concept.trend) {
                    MasteryTrend.IMPROVING -> Color(0xFF4CAF50)
                    MasteryTrend.STABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    MasteryTrend.DECLINING -> Color(0xFFF44336)
                }
            )
            Column {
                Text(
                    concept.concept,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${concept.questionsAnswered} questions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        concept.masteryLevel > 0.8f -> Color(0xFF4CAF50)
                        concept.masteryLevel > 0.6f -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }.copy(alpha = 0.2f)
                )
        ) {
            Text(
                "${(concept.masteryLevel * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    concept.masteryLevel > 0.8f -> Color(0xFF4CAF50)
                    concept.masteryLevel > 0.6f -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
            )
        }
    }
}

@Composable
fun DifficultyBreakdownSection(difficultyBreakdown: Map<Difficulty, DifficultyStats>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Performance by Difficulty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            difficultyBreakdown.forEach { (difficulty, stats) ->
                DifficultyStatsItem(difficulty, stats)
                if (difficulty != difficultyBreakdown.keys.last()) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun DifficultyStatsItem(
    difficulty: Difficulty,
    stats: DifficultyStats
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when (difficulty) {
                                Difficulty.EASY -> Color(0xFF4CAF50)
                                Difficulty.MEDIUM -> Color(0xFFFFC107)
                                Difficulty.HARD -> Color(0xFFF44336)
                                Difficulty.ADAPTIVE -> MaterialTheme.colorScheme.primary
                            }
                        )
                )
                Text(
                    difficulty.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                "${(stats.accuracy * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "${stats.questionsAttempted} questions • ${stats.averageTime.roundToInt()}s avg",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}