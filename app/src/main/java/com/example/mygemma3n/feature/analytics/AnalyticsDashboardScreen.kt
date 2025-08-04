package com.example.mygemma3n.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: LearningAnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedSubject by viewModel.selectedSubject.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = { 
                Text(
                    "Learning Analytics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                ) 
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, "Navigate back")
                }
            },
            actions = {
                // Demo data button - commented out for production use
                // IconButton(
                //     onClick = { viewModel.initializeDemoData() },
                //     enabled = !uiState.isLoading
                // ) {
                //     Icon(Icons.Default.DatasetLinked, "Initialize demo data")
                // }
                IconButton(
                    onClick = { viewModel.refreshAnalytics() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(Icons.Default.Refresh, "Refresh analytics")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        when {
            uiState.isLoading -> {
                LoadingState()
            }
            uiState.error != null -> {
                ErrorState(
                    error = uiState.error!!,
                    onRetry = { viewModel.refreshAnalytics() }
                )
            }
            uiState.analytics != null -> {
                AnalyticsContent(
                    analytics = uiState.analytics!!,
                    selectedTimeframe = selectedTimeframe,
                    selectedSubject = selectedSubject,
                    onTimeframeSelected = viewModel::selectTimeframe,
                    onSubjectSelected = viewModel::selectSubject,
                    onGenerateRecommendations = viewModel::generateRecommendations,
                    isGeneratingRecommendations = uiState.isGeneratingRecommendations,
                    onRecommendationCompleted = viewModel::markRecommendationCompleted,
                    onRecommendationDismissed = viewModel::dismissRecommendation
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                "Loading your learning analytics...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

@Composable
private fun AnalyticsContent(
    analytics: LearningAnalytics,
    selectedTimeframe: AnalyticsTimeframe,
    selectedSubject: String?,
    onTimeframeSelected: (AnalyticsTimeframe) -> Unit,
    onSubjectSelected: (String?) -> Unit,
    onGenerateRecommendations: () -> Unit,
    isGeneratingRecommendations: Boolean,
    onRecommendationCompleted: (String) -> Unit,
    onRecommendationDismissed: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timeframe Selector
        item {
            TimeframeSelector(
                selectedTimeframe = selectedTimeframe,
                onTimeframeSelected = onTimeframeSelected
            )
        }

        // Overall Progress Card
        item {
            OverallProgressCard(analytics = analytics)
        }

        // Subject Progress Grid
        item {
            SubjectProgressSection(
                subjectProgress = analytics.subjectProgress,
                selectedSubject = selectedSubject,
                onSubjectSelected = onSubjectSelected
            )
        }

        // Weekly Stats
        item {
            WeeklyStatsCard(weeklyStats = analytics.weeklyStats)
        }

        // Learning Trends
        item {
            LearningTrendsCard(trends = analytics.trends)
        }

        // Knowledge Gaps
        item {
            KnowledgeGapsSection(knowledgeGaps = analytics.knowledgeGaps)
        }

        // Recommendations
        item {
            RecommendationsSection(
                recommendations = analytics.recommendations,
                isGenerating = isGeneratingRecommendations,
                onGenerate = onGenerateRecommendations,
                onCompleted = onRecommendationCompleted,
                onDismissed = onRecommendationDismissed
            )
        }
    }
}

@Composable
private fun TimeframeSelector(
    selectedTimeframe: AnalyticsTimeframe,
    onTimeframeSelected: (AnalyticsTimeframe) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(AnalyticsTimeframe.values()) { timeframe ->
            FilterChip(
                onClick = { onTimeframeSelected(timeframe) },
                label = { Text(timeframe.displayName) },
                selected = timeframe == selectedTimeframe
            )
        }
    }
}

@Composable
private fun OverallProgressCard(analytics: LearningAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Overall Progress",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${(analytics.overallProgress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            LinearProgressIndicator(
                progress = analytics.overallProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun SubjectProgressSection(
    subjectProgress: Map<String, SubjectAnalytics>,
    selectedSubject: String?,
    onSubjectSelected: (String?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Subject Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(subjectProgress.entries.toList()) { (subject, analytics) ->
                    SubjectProgressCard(
                        subject = subject,
                        analytics = analytics,
                        isSelected = subject == selectedSubject,
                        onClick = { 
                            onSubjectSelected(if (selectedSubject == subject) null else subject)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectProgressCard(
    subject: String,
    analytics: SubjectAnalytics,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.tertiaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "${(analytics.masteryScore * 100).roundToInt()}% Mastery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            LinearProgressIndicator(
                progress = analytics.masteryScore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            
            Text(
                "${analytics.topicsExplored}/${analytics.totalTopics} Topics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeeklyStatsCard(weeklyStats: WeeklyStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "This Week",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Schedule,
                    value = "${weeklyStats.totalTimeSpent / (1000 * 60)}min",
                    label = "Study Time"
                )
                StatItem(
                    icon = Icons.Default.PlayCircle,
                    value = "${weeklyStats.sessionsCompleted}",
                    label = "Sessions"
                )
                StatItem(
                    icon = Icons.Default.Book,
                    value = "${weeklyStats.topicsExplored}",
                    label = "Topics"
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LearningTrendsCard(trends: LearningTrends) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Learning Trends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendItem("Mastery", trends.masteryTrend)
                TrendItem("Engagement", trends.engagementTrend)
                TrendItem("Accuracy", trends.accuracyTrend)
                TrendItem("Focus", trends.focusTrend)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Consistency Score",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${(trends.consistencyScore * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TrendItem(label: String, trend: TrendDirection) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val (icon, color, text) = when (trend) {
                TrendDirection.IMPROVING -> Triple(
                    Icons.Default.TrendingUp,
                    Color(0xFF4CAF50),
                    "Improving"
                )
                TrendDirection.DECLINING -> Triple(
                    Icons.Default.TrendingDown,
                    Color(0xFFF44336),
                    "Declining"
                )
                TrendDirection.STABLE -> Triple(
                    Icons.Default.TrendingFlat,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "Stable"
                )
                TrendDirection.INSUFFICIENT_DATA -> Triple(
                    Icons.Default.Help,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "N/A"
                )
            }
            
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
private fun KnowledgeGapsSection(knowledgeGaps: List<KnowledgeGapEntity>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Knowledge Gaps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (knowledgeGaps.isEmpty()) {
                Text(
                    "Great! No significant knowledge gaps detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                knowledgeGaps.take(3).forEach { gap ->
                    GapItem(gap = gap)
                }
            }
        }
    }
}

@Composable
private fun GapItem(gap: KnowledgeGapEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val priorityColor = when (gap.priority) {
            GapPriority.CRITICAL -> Color(0xFFD32F2F)
            GapPriority.HIGH -> Color(0xFFFF9800)
            GapPriority.MEDIUM -> Color(0xFFFFC107)
            GapPriority.LOW -> Color(0xFF4CAF50)
        }
        
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(priorityColor, RoundedCornerShape(6.dp))
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${gap.subject} - ${gap.topic}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                gap.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationsSection(
    recommendations: List<StudyRecommendationEntity>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onCompleted: (String) -> Unit,
    onDismissed: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Study Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedButton(
                    onClick = onGenerate,
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate")
                }
            }
            
            if (recommendations.isEmpty()) {
                Text(
                    "No recommendations available. Generate some based on your current progress!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recommendations.take(3).forEach { recommendation ->
                    RecommendationItem(
                        recommendation = recommendation,
                        onCompleted = { onCompleted(recommendation.id) },
                        onDismissed = { onDismissed(recommendation.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationItem(
    recommendation: StudyRecommendationEntity,
    onCompleted: () -> Unit,
    onDismissed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    recommendation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    "${recommendation.estimatedTimeMinutes}min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                recommendation.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismissed) {
                    Text("Dismiss")
                }
                Button(onClick = onCompleted) {
                    Text("Complete")
                }
            }
        }
    }
}