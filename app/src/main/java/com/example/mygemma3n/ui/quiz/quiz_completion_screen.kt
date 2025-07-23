package com.example.mygemma3n.ui.quiz

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mygemma3n.feature.quiz.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Enhanced quiz completion screen with animations and statistics
 */
@Composable
fun QuizCompletionScreen(
    quiz: Quiz,
    onReviewAnswers: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onNewQuiz: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Calculate statistics
    val stats = remember(quiz) { calculateQuizStatistics(quiz) }

    // Animation states
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Animated score circle
            AnimatedVisibility(
                visible = showContent,
                enter = scaleIn() + fadeIn()
            ) {
                ScoreCircle(
                    score = stats.scorePercentage,
                    modifier = Modifier.size(200.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Result message
            AnimatedVisibility(
                visible = showContent,
                enter = slideInVertically { it } + fadeIn()
            ) {
                ResultMessage(
                    score = stats.scorePercentage,
                    totalQuestions = stats.totalQuestions
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Statistics cards
            AnimatedVisibility(
                visible = showContent,
                enter = slideInVertically { it } + fadeIn()
            ) {
                StatisticsGrid(stats = stats)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Achievements
            if (stats.achievements.isNotEmpty()) {
                AnimatedVisibility(
                    visible = showContent,
                    enter = slideInVertically { it } + fadeIn()
                ) {
                    AchievementsSection(achievements = stats.achievements)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Action buttons
            AnimatedVisibility(
                visible = showContent,
                enter = slideInVertically { it } + fadeIn()
            ) {
                ActionButtons(
                    score = stats.scorePercentage,
                    onReviewAnswers = onReviewAnswers,
                    onRetakeQuiz = onRetakeQuiz,
                    onNewQuiz = onNewQuiz,
                    onShare = onShare
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Animated circular score display
 */
@Composable
private fun ScoreCircle(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "score"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "progress"
    )

    val color = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Background circle
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(
                color = color.copy(alpha = 0.1f),
                style = Stroke(width = 20.dp.toPx())
            )

            // Progress arc
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(
                    width = 20.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        // Score text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$animatedScore%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "Score",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Celebration particles for high scores
        if (score >= 80) {
            repeat(8) { index ->
                val rotation by rememberInfiniteTransition(label = "rotation").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(10000 + index * 1000),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "particle_rotation"
                )

                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .offset(y = (-100).dp)
                        .rotate(rotation + index * 45f)
                )
            }
        }
    }
}

/**
 * Result message based on score
 */
@Composable
private fun ResultMessage(
    score: Int,
    totalQuestions: Int,
    modifier: Modifier = Modifier
) {
    val message = when {
        score >= 90 -> "Outstanding! 🌟"
        score >= 80 -> "Excellent Work! 🎉"
        score >= 70 -> "Great Job! 👏"
        score >= 60 -> "Good Effort! 👍"
        score >= 50 -> "Keep Practicing! 💪"
        else -> "Don't Give Up! 🌱"
    }

    val encouragement = when {
        score >= 90 -> "You've mastered this topic!"
        score >= 80 -> "You're doing amazingly well!"
        score >= 70 -> "You're on the right track!"
        score >= 60 -> "You're making good progress!"
        score >= 50 -> "You're halfway there!"
        else -> "Every expert was once a beginner!"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = encouragement,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Statistics grid showing detailed performance
 */
@Composable
private fun StatisticsGrid(
    stats: QuizStatistics,
    modifier: Modifier = Modifier
) {
    val statsItems = listOf(
        StatItem(
            icon = Icons.Default.CheckCircle,
            label = "Correct",
            value = "${stats.correctAnswers}",
            color = Color(0xFF4CAF50)
        ),
        StatItem(
            icon = Icons.Default.Cancel,
            label = "Incorrect",
            value = "${stats.incorrectAnswers}",
            color = MaterialTheme.colorScheme.error
        ),
        StatItem(
            icon = Icons.Default.Timer,
            label = "Time",
            value = stats.formattedTime,
            color = MaterialTheme.colorScheme.primary
        ),
        StatItem(
            icon = Icons.Default.Speed,
            label = "Avg Time",
            value = "${stats.averageTimePerQuestion}s",
            color = MaterialTheme.colorScheme.tertiary
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(statsItems) { stat ->
            StatCard(stat = stat)
        }
    }
}

@Composable
private fun StatCard(
    stat: StatItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = stat.color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = stat.icon,
                contentDescription = null,
                tint = stat.color,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = stat.color
            )

            Text(
                text = stat.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Achievements section
 */
@Composable
private fun AchievementsSection(
    achievements: List<Achievement>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Achievements Unlocked!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        achievements.forEach { achievement ->
            AchievementBadge(
                achievement = achievement,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AchievementBadge(
    achievement: Achievement,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = achievement.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.Stars,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Action buttons
 */
@Composable
private fun ActionButtons(
    score: Int,
    onReviewAnswers: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onNewQuiz: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary action based on score
        if (score < 70) {
            Button(
                onClick = onRetakeQuiz,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retake Quiz")
            }
        } else {
            Button(
                onClick = onNewQuiz,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Quiz")
            }
        }

        // Secondary actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReviewAnswers,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RateReview, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Review")
            }

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        }
    }
}

// Data classes
private data class StatItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val color: Color
)

private data class Achievement(
    val title: String,
    val description: String,
    val icon: ImageVector
)

private data class QuizStatistics(
    val scorePercentage: Int,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val incorrectAnswers: Int,
    val totalTimeSeconds: Long,
    val averageTimePerQuestion: Int,
    val formattedTime: String,
    val achievements: List<Achievement>
)

// Helper function to calculate statistics
private fun calculateQuizStatistics(quiz: Quiz): QuizStatistics {
    val questions = quiz.questions
    val correctAnswers = questions.count { it.userAnswer == it.correctAnswer }
    val scorePercentage = ((correctAnswers.toFloat() / questions.size) * 100).roundToInt()

    // Calculate time (this is mock data - you'd get real time from tracking)
    val totalTimeSeconds = 300L // 5 minutes mock
    val averageTime = (totalTimeSeconds / questions.size).toInt()

    // Format time
    val minutes = totalTimeSeconds / 60
    val seconds = totalTimeSeconds % 60
    val formattedTime = "${minutes}m ${seconds}s"

    // Calculate achievements
    val achievements = mutableListOf<Achievement>()

    if (scorePercentage == 100) {
        achievements.add(
            Achievement(
                "Perfect Score!",
                "Answered all questions correctly",
                Icons.Default.EmojiEvents
            )
        )
    }

    if (averageTime < 20) {
        achievements.add(
            Achievement(
                "Speed Demon",
                "Averaged under 20 seconds per question",
                Icons.Default.Speed
            )
        )
    }

    if (correctAnswers >= 5 && correctAnswers == questions.count { it.difficulty == Difficulty.HARD }) {
        achievements.add(
            Achievement(
                "Challenge Master",
                "Aced all hard questions",
                Icons.Default.Psychology
            )
        )
    }

    return QuizStatistics(
        scorePercentage = scorePercentage,
        totalQuestions = questions.size,
        correctAnswers = correctAnswers,
        incorrectAnswers = questions.size - correctAnswers,
        totalTimeSeconds = totalTimeSeconds,
        averageTimePerQuestion = averageTime,
        formattedTime = formattedTime,
        achievements = achievements
    )
}