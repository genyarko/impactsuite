package com.example.mygemma3n.feature.tutor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mygemma3n.feature.progress.LearningProgressTracker

/**
 * Displays learning progress during tutoring sessions
 */
@Composable
fun TutorProgressDisplay(
    currentProgress: Float,
    currentStreak: Int,
    weeklyGoalProgress: Float,
    onViewDetails: () -> Unit,
    onMinimize: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Overall Progress
            ProgressIndicator(
                progress = currentProgress,
                icon = Icons.Default.School,
                label = "Progress",
                color = MaterialTheme.colorScheme.primary
            )

            // Streak
            StreakIndicator(
                streakDays = currentStreak,
                modifier = Modifier.weight(1f)
            )

            // Weekly Goal
            ProgressIndicator(
                progress = weeklyGoalProgress,
                icon = Icons.Default.Flag,
                label = "Weekly",
                color = MaterialTheme.colorScheme.secondary
            )

            // Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // View Details Button
                IconButton(
                    onClick = onViewDetails,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = "View Progress Details",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Minimize Button
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Minimize Progress Display",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressIndicator(
    progress: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = color,
                trackColor = color.copy(alpha = 0.3f),
                strokeWidth = 3.dp
            )
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = color
            )
        }
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StreakIndicator(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    val isActive = streakDays > 0
    val animatedColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "streak_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) animatedColor.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 2.dp,
                    color = animatedColor,
                    shape = CircleShape
                )
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = "Streak",
                modifier = Modifier.size(24.dp),
                tint = animatedColor
            )
        }
        Text(
            "$streakDays days",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = animatedColor
        )
        Text(
            "Streak",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Achievement notification dialog
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AchievementDialog(
    achievement: LearningProgressTracker.Achievement,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated icon
                val infiniteTransition = rememberInfiniteTransition(label = "achievement")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "icon_scale"
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .animateContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        achievement.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Text(
                    "Achievement Unlocked!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    achievement.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category badge
                AssistChip(
                    onClick = { },
                    label = { Text(achievement.category.name.replace('_', ' ')) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = getCategoryColor(achievement.category).copy(alpha = 0.2f)
                    )
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Awesome!")
                }
            }
        }
    }
}

/**
 * Floating progress summary that can be shown/hidden
 */
@Composable
fun FloatingProgressSummary(
    progressSummary: TutorProgressIntegrationService.ProgressSummary,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSelectConcept: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
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
                        "Your Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Progress overview
                LinearProgressIndicator(
                    progress = { progressSummary.overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${progressSummary.masteredConcepts}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Mastered",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${progressSummary.totalConcepts}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Total Concepts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${progressSummary.currentStreak}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (progressSummary.currentStreak > 0) Color(0xFFFF5722)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Day Streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Suggested next concept
                progressSummary.suggestedNextConcept?.let { concept ->
                    Card(
                        onClick = { onSelectConcept(concept) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
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
                                    "Suggested Next:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    concept,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Study this",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Recent achievements
                if (progressSummary.recentAchievements.isNotEmpty()) {
                    Text(
                        "Recent Achievements",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    progressSummary.recentAchievements.forEach { achievement ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                achievement.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = getCategoryColor(achievement.category)
                            )
                            Text(
                                achievement.title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryColor(category: LearningProgressTracker.AchievementCategory): Color {
    return when (category) {
        LearningProgressTracker.AchievementCategory.STREAK -> Color(0xFFFF5722)
        LearningProgressTracker.AchievementCategory.MASTERY -> Color(0xFF9C27B0)
        LearningProgressTracker.AchievementCategory.SPEED -> Color(0xFF2196F3)
        LearningProgressTracker.AchievementCategory.EXPLORATION -> Color(0xFF4CAF50)
        LearningProgressTracker.AchievementCategory.PERSISTENCE -> Color(0xFFFFC107)
    }
}