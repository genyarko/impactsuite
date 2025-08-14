package com.mygemma3n.aiapp.feature.tutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FloatingTopicBubbles(
    topics: List<String>,
    onTopicSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxVisibleTopics: Int = 3
) {
    // Take only a subset of topics to avoid overcrowding
    val visibleTopics = remember(topics) {
        topics.shuffled().take(maxVisibleTopics)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        visibleTopics.forEachIndexed { index, topic ->
            FloatingBubble(
                text = topic,
                index = index,
                totalCount = visibleTopics.size,
                onClick = { onTopicSelected(topic) }
            )
        }
    }
}

@Composable
private fun FloatingBubble(
    text: String,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val lift = 25f   // raise everything ~20 dp
    // Stagger the appearance of bubbles
    LaunchedEffect(index) {
        kotlinx.coroutines.delay(index * 150L)
        isVisible = true
    }

    // Create floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "float")

    // Vertical floating movement
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000 + (index * 200),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    // Horizontal sway
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000 + (index * 300),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    // Calculate position in a semi-circle arrangement
    val angle = (index.toFloat() / (totalCount - 1)) * 120f - 60f // -60 to 60 degrees
    val radiusX = 120f // Use float instead of dp
    val radiusY = 80f  // Use float instead of dp

    val xPosition = radiusX * sin(Math.toRadians(angle.toDouble())).toFloat()
    val yPosition = radiusY * (1 - cos(Math.toRadians(angle.toDouble()))).toFloat()

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            initialScale = 0.3f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = scaleOut(targetScale = 0.5f) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = (xPosition + offsetX).dp,
                    y = (yPosition + offsetY - lift).dp   // ▼ subtract lift
                )
        ) {
            TopicChip(
                text = text,
                onClick = {
                    isVisible = false
                    onClick()
                }
            )
        }
    }
}

@Composable
private fun TopicChip(
    text: String,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )

    val backgroundColor = remember { colors.random() }
    
    // Scale animation on press
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "chipScale"
    )

    Surface(
        modifier = Modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null // Using Surface's built-in Material3 ripple
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// Alternative overlay version
@Composable
fun FloatingTopicOverlay(
    topics: List<String>,
    onTopicSelected: (String) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            FloatingTopicBubbles(
                topics = topics,
                onTopicSelected = onTopicSelected,
                modifier = Modifier.padding(bottom = 100.dp)
            )
        }
    }
}