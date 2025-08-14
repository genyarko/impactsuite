package com.mygemma3n.aiapp.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mygemma3n.aiapp.shared_utilities.OfflineRAG
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LearningProgressTracker @Inject constructor() {
    
    data class LearningProgress(
        val studentId: String,
        val subject: OfflineRAG.Subject,
        val overallProgress: Float, // 0.0 to 1.0
        val conceptMasteries: Map<String, ConceptMastery>,
        val streakDays: Int,
        val totalSessionTime: Long, // in milliseconds
        val achievements: List<Achievement>,
        val weeklyGoal: WeeklyGoal,
        val lastUpdated: Long = System.currentTimeMillis()
    )
    
    data class ConceptMastery(
        val concept: String,
        val level: MasteryLevel,
        val progressPercentage: Float, // 0.0 to 1.0
        val lastPracticed: Long,
        val practiceCount: Int,
        val strengthAreas: List<String> = emptyList(),
        val improvementAreas: List<String> = emptyList()
    )
    
    enum class MasteryLevel(val displayName: String, val color: Color, val icon: ImageVector) {
        NOT_STARTED("Not Started", Color(0xFFE0E0E0), Icons.Default.RadioButtonUnchecked),
        LEARNING("Learning", Color(0xFFFFC107), Icons.Default.School),
        PRACTICING("Practicing", Color(0xFF2196F3), Icons.Default.Repeat),
        PROFICIENT("Proficient", Color(0xFF4CAF50), Icons.Default.CheckCircle),
        MASTERED("Mastered", Color(0xFF9C27B0), Icons.Default.EmojiEvents)
    }
    
    data class Achievement(
        val id: String,
        val title: String,
        val description: String,
        val icon: ImageVector,
        val unlockedAt: Long,
        val category: AchievementCategory
    )
    
    enum class AchievementCategory {
        STREAK, MASTERY, SPEED, EXPLORATION, PERSISTENCE
    }
    
    data class WeeklyGoal(
        val targetSessionMinutes: Int,
        val currentSessionMinutes: Int,
        val targetTopicsCount: Int,
        val currentTopicsCount: Int,
        val weekStartTime: Long
    ) {
        val sessionProgress: Float get() = (currentSessionMinutes.toFloat() / targetSessionMinutes).coerceAtMost(1f)
        val topicsProgress: Float get() = (currentTopicsCount.toFloat() / targetTopicsCount).coerceAtMost(1f)
        val isComplete: Boolean get() = sessionProgress >= 1f && topicsProgress >= 1f
    }
    
    fun calculateMasteryLevel(progressPercentage: Float, practiceCount: Int): MasteryLevel {
        return when {
            progressPercentage == 0f -> MasteryLevel.NOT_STARTED
            progressPercentage < 0.3f -> MasteryLevel.LEARNING
            progressPercentage < 0.7f -> MasteryLevel.PRACTICING
            progressPercentage < 0.9f -> MasteryLevel.PROFICIENT
            else -> MasteryLevel.MASTERED
        }
    }
    
    fun updateProgress(
        current: LearningProgress,
        sessionDuration: Long,
        topicsStudied: List<String>,
        conceptScores: Map<String, Float>
    ): LearningProgress {
        val updatedMasteries = current.conceptMasteries.toMutableMap()
        
        // Update concept masteries based on session performance
        conceptScores.forEach { (concept, score) ->
            val existing = updatedMasteries[concept] ?: ConceptMastery(
                concept = concept,
                level = MasteryLevel.NOT_STARTED,
                progressPercentage = 0f,
                lastPracticed = System.currentTimeMillis(),
                practiceCount = 0
            )
            
            // Calculate new progress (weighted average of current and new score)
            val newProgress = if (existing.practiceCount == 0) {
                score
            } else {
                (existing.progressPercentage * 0.7f + score * 0.3f).coerceIn(0f, 1f)
            }
            
            updatedMasteries[concept] = existing.copy(
                level = calculateMasteryLevel(newProgress, existing.practiceCount + 1),
                progressPercentage = newProgress,
                lastPracticed = System.currentTimeMillis(),
                practiceCount = existing.practiceCount + 1
            )
        }
        
        // Check for new achievements
        val newAchievements = checkForAchievements(current, sessionDuration, topicsStudied, conceptScores)
        
        // Update streak
        val newStreak = calculateStreak(current.streakDays, current.lastUpdated)
        
        // Update overall progress
        val newOverallProgress = if (updatedMasteries.isNotEmpty()) {
            updatedMasteries.values.map { it.progressPercentage }.average().toFloat()
        } else 0f
        
        return current.copy(
            overallProgress = newOverallProgress,
            conceptMasteries = updatedMasteries,
            streakDays = newStreak,
            totalSessionTime = current.totalSessionTime + sessionDuration,
            achievements = current.achievements + newAchievements,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    private fun calculateStreak(currentStreak: Int, lastUpdated: Long): Int {
        val now = System.currentTimeMillis()
        val daysSinceLastUpdate = (now - lastUpdated) / (24 * 60 * 60 * 1000)
        
        return when {
            daysSinceLastUpdate <= 1 -> currentStreak + 1 // Same day or next day
            daysSinceLastUpdate <= 2 -> currentStreak // Grace period
            else -> 0 // Streak broken
        }
    }
    
    private fun checkForAchievements(
        current: LearningProgress,
        sessionDuration: Long,
        topicsStudied: List<String>,
        conceptScores: Map<String, Float>
    ): List<Achievement> {
        val achievements = mutableListOf<Achievement>()
        val now = System.currentTimeMillis()
        
        // Streak achievements
        val newStreak = calculateStreak(current.streakDays, current.lastUpdated)
        if (newStreak >= 7 && current.streakDays < 7) {
            achievements.add(Achievement(
                id = "streak_7",
                title = "Week Warrior",
                description = "Studied for 7 days in a row!",
                icon = Icons.Default.LocalFireDepartment,
                unlockedAt = now,
                category = AchievementCategory.STREAK
            ))
        }
        
        // Mastery achievements
        val masteredConcepts = current.conceptMasteries.values.count { it.level == MasteryLevel.MASTERED }

        // Speed achievements
        val sessionMinutes = sessionDuration / (60 * 1000)
        if (sessionMinutes >= 30) {
            achievements.add(Achievement(
                id = "session_30_${now}",
                title = "Focused Learner",
                description = "Studied for 30+ minutes in one session!",
                icon = Icons.Default.Timer,
                unlockedAt = now,
                category = AchievementCategory.PERSISTENCE
            ))
        }
        
        // Perfect score achievement
        if (conceptScores.values.all { it >= 0.9f } && conceptScores.isNotEmpty()) {
            achievements.add(Achievement(
                id = "perfect_${now}",
                title = "Perfect Performance",
                description = "Scored 90%+ on all concepts this session!",
                icon = Icons.Default.Star,
                unlockedAt = now,
                category = AchievementCategory.MASTERY
            ))
        }
        
        return achievements
    }
}

@Composable
fun LearningProgressCard(
    progress: LearningProgressTracker.LearningProgress,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with overall progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${progress.subject.name} Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "${(progress.overallProgress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Overall progress bar
            LinearProgressIndicator(
            progress = { progress.overallProgress },
            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProgressStat(
                    icon = Icons.Default.LocalFireDepartment,
                    value = progress.streakDays.toString(),
                    label = "Day Streak",
                    color = if (progress.streakDays > 0) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                ProgressStat(
                    icon = Icons.Default.EmojiEvents,
                    value = progress.achievements.size.toString(),
                    label = "Achievements",
                    color = Color(0xFFFFC107)
                )
                
                ProgressStat(
                    icon = Icons.Default.Timer,
                    value = "${progress.totalSessionTime / (60 * 1000)}m",
                    label = "Study Time",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProgressStat(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConceptMasteryGrid(
    conceptMasteries: Map<String, LearningProgressTracker.ConceptMastery>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Concept Mastery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Mastery level legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LearningProgressTracker.MasteryLevel.entries.forEach { level ->
                    MasteryLevelIndicator(
                        level = level,
                        count = conceptMasteries.values.count { it.level == level }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Concept progress items
            conceptMasteries.values.take(5).forEach { mastery ->
                ConceptProgressItem(mastery = mastery)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (conceptMasteries.size > 5) {
                Text(
                    text = "... and ${conceptMasteries.size - 5} more concepts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MasteryLevelIndicator(
    level: LearningProgressTracker.MasteryLevel,
    count: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(level.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = level.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun ConceptProgressItem(
    mastery: LearningProgressTracker.ConceptMastery
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = mastery.level.icon,
            contentDescription = mastery.level.displayName,
            tint = mastery.level.color,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mastery.concept,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            LinearProgressIndicator(
            progress = { mastery.progressPercentage },
            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
            color = mastery.level.color,
            trackColor = mastery.level.color.copy(alpha = 0.2f),
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "${(mastery.progressPercentage * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = mastery.level.color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun WeeklyGoalCard(
    weeklyGoal: LearningProgressTracker.WeeklyGoal,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (weeklyGoal.isComplete) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
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
                    text = "Weekly Goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (weeklyGoal.isComplete) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Goal Complete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Session time goal
            GoalProgressItem(
                icon = Icons.Default.Timer,
                label = "Study Time",
                current = weeklyGoal.currentSessionMinutes,
                target = weeklyGoal.targetSessionMinutes,
                unit = "min",
                progress = weeklyGoal.sessionProgress
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Topics goal
            GoalProgressItem(
                icon = Icons.Default.Topic,
                label = "Topics Explored",
                current = weeklyGoal.currentTopicsCount,
                target = weeklyGoal.targetTopicsCount,
                unit = "topics",
                progress = weeklyGoal.topicsProgress
            )
        }
    }
}

@Composable
private fun GoalProgressItem(
    icon: ImageVector,
    label: String,
    current: Int,
    target: Int,
    unit: String,
    progress: Float
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Text(
                text = "$current/$target $unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
        color = if (progress >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}