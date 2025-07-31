package com.example.mygemma3n.feature.cbt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.mygemma3n.feature.caption.rememberAudioPermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CBTCoachScreen(
    viewModel: CBTCoachViewModel = hiltViewModel()
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val audioPermission = rememberAudioPermissionState()

    var userInput by remember { mutableStateOf("") }
    var showThoughtRecord by remember { mutableStateOf(false) }

    // Animation states
    val headerExpanded by remember { derivedStateOf { sessionState.isActive } }
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    /* Update userInput when state changes */
    LaunchedEffect(sessionState.userTypedInput) {
        userInput = sessionState.userTypedInput
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        /* ───────── Enhanced Header ───────── */
        AnimatedContent(
            targetState = headerExpanded,
            transitionSpec = {
                slideInVertically { -it } + fadeIn() togetherWith
                        slideOutVertically { -it } + fadeOut()
            },
            label = "header"
        ) { expanded ->
            if (expanded) {
                // Active session header
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CBT Coach",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Active Session",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // Service mode indicator
                            ServiceModeIndicatorCBT(
                                isOnline = sessionState.isUsingOnlineService
                            )
                        }

                        AnimatedVisibility(
                            visible = sessionState.currentEmotion != null,
                            enter = slideInVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mood,
                                        contentDescription = null,
                                        tint = getEmotionColor(sessionState.currentEmotion)
                                    )
                                    Text(
                                        text = "Current Emotion:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    EnhancedEmotionChip(
                                        emotion = sessionState.currentEmotion,
                                        onClick = { /* no-op */ }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = sessionState.suggestedTechnique != null,
                            enter = slideInVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = sessionState.suggestedTechnique?.name ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    sessionState.suggestedTechnique?.let { technique ->
                                        val progress = (sessionState.currentStep + 1f) / technique.steps.size

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Step ${sessionState.currentStep + 1} of ${technique.steps.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Welcome header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .scale(pulseScale),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "CBT Coach",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Your personal cognitive behavioral therapy companion",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        /* ───────── Conversation with Enhanced Messages ───────── */
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = sessionState.conversation.reversed(),
                key = { it.hashCode() }
            ) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    EnhancedMessageBubble(
                        message = message,
                        cbtTechniques = viewModel.cbtTechniques
                    )
                }
            }
        }

        /* ───────── Error Display ───────── */
        AnimatedVisibility(
            visible = sessionState.error != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            sessionState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        /* ───────── Enhanced Action Buttons ───────── */
        AnimatedVisibility(
            visible = sessionState.isActive,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AssistChip(
                        onClick = { showThoughtRecord = true },
                        label = { Text("Thought Record") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Note,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                item {
                    AssistChip(
                        onClick = { viewModel.progressToNextStep() },
                        label = { Text("Next Step") },
                        enabled = sessionState.suggestedTechnique != null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.NavigateNext,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                item {
                    AssistChip(
                        onClick = { viewModel.analyzeEmotionTrajectory() },
                        label = { Text("Progress") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                item {
                    AssistChip(
                        onClick = {
                            val lastIssue = viewModel.sessionState.value
                                .conversation
                                .filterIsInstance<Message.User>()
                                .lastOrNull()
                                ?.content ?: ""
                            viewModel.getPersonalizedRecommendations(lastIssue)
                        },
                        label = { Text("Tips") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        /* ───────── Enhanced Input Area ───────── */
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column {
                if (sessionState.isActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { new ->
                                if (!sessionState.isRecording) {
                                    userInput = new
                                    viewModel.updateUserTypedInput(new)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    when {
                                        sessionState.isRecording -> "🎤 Recording... Speak now"
                                        userInput == "Transcribing..." -> "✨ Transcribing..."
                                        else -> "💭 Share your thoughts…"
                                    }
                                )
                            },
                            enabled = !isLoading,
                            readOnly = sessionState.isRecording,
                            shape = RoundedCornerShape(28.dp),
                            colors = if (sessionState.isRecording) {
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                OutlinedTextFieldDefaults.colors()
                            },
                            minLines = 1,
                            maxLines = 4
                        )

                        // Voice button with enhanced animation
                        FloatingActionButton(
                            onClick = {
                                if (sessionState.isRecording) {
                                    viewModel.stopRecording()
                                } else {
                                    if (audioPermission.hasPermission) {
                                        viewModel.startRecording()
                                    } else {
                                        audioPermission.launchPermissionRequest()
                                    }
                                }
                            },
                            containerColor = if (sessionState.isRecording)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .scale(if (sessionState.isRecording) pulseScale else 1f)
                                .size(56.dp),
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = if (sessionState.isRecording) 12.dp else 6.dp
                            )
                        ) {
                            if (sessionState.isRecording) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Recording",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record",
                                    tint = if (audioPermission.hasPermission)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Send button
                        FloatingActionButton(
                            onClick = {
                                if (userInput.isNotBlank()) {
                                    coroutineScope.launch {
                                        viewModel.processTextInput(userInput)
                                        userInput = ""
                                        viewModel.updateUserTypedInput("")
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp),
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = if (userInput.isNotBlank()) 8.dp else 2.dp
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(
                                        alpha = if (userInput.isNotBlank() && !sessionState.isRecording) 1f else 0.5f
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Welcome state input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.startSession()
                                }
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Your Session",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                // End session button with enhanced styling
                AnimatedVisibility(
                    visible = sessionState.isActive,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.generateSessionSummary()
                                    viewModel.endSession()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("End Session")
                        }
                    }
                }
            }
        }
    }

    // Enhanced Thought Record Dialog
    if (showThoughtRecord) {
        EnhancedThoughtRecordDialog(
            onDismiss = { showThoughtRecord = false },
            onSubmit = { situation, thought, intensity ->
                viewModel.createThoughtRecord(situation, thought, intensity)
                showThoughtRecord = false
            }
        )
    }

    // Enhanced Session Insights Dialog
    sessionState.sessionInsights?.let { insights ->
        EnhancedSessionInsightsDialog(
            insights = insights,
            onDismiss = { viewModel.clearSessionInsights() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedEmotionChip(
    emotion: Emotion?,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    emotion?.let {
        SuggestionChip(
            onClick = onClick,
            enabled = enabled,
            label = {
                Text(
                    text = it.name,
                    color = getEmotionColor(it),
                    fontWeight = FontWeight.Medium
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = getEmotionColor(it).copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun EnhancedMessageBubble(
    message: Message,
    cbtTechniques: CBTTechniques
) {
    val isUser = message is Message.User

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 300.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = message.content,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (message is Message.AI && message.techniqueId != null) {
                    val techniqueName = message.getTechniqueName(cbtTechniques)
                    techniqueName?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = if (isUser)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isUser)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isUser)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedThoughtRecordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Float) -> Unit
) {
    var situation by remember { mutableStateOf("") }
    var thought by remember { mutableStateOf("") }
    var intensity by remember { mutableFloatStateOf(0.5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Note,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Create Thought Record")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = situation,
                    onValueChange = { situation = it },
                    label = { Text("Situation") },
                    placeholder = { Text("Describe what happened...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = thought,
                    onValueChange = { thought = it },
                    label = { Text("Automatic Thought") },
                    placeholder = { Text("What went through your mind?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                Column {
                    Text(
                        "Emotion Intensity: ${(intensity * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = getIntensityColor(intensity),
                            activeTrackColor = getIntensityColor(intensity)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(situation, thought, intensity) },
                enabled = situation.isNotBlank() && thought.isNotBlank()
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



fun getIntensityColor(intensity: Float): Color {
    val t = intensity.coerceIn(0f, 1f)
    val start = Color(0xFF4CAF50)
    val end   = Color(0xFFF44336)

    fun mix(a: Float, b: Float) = a + (b - a) * t

    return Color(
        red   = mix(start.red,   end.red),
        green = mix(start.green, end.green),
        blue  = mix(start.blue,  end.blue),
        alpha = 1f               // fully opaque
    )
}



@Composable
fun EnhancedSessionInsightsDialog(
    insights: CBTSessionManager.SessionInsights,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Session Summary")
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                /* ── Overall summary ── */
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = insights.summary,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                /* ── Key insights ── */
                item {
                    Column {
                        Text(
                            "Key Insights",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        insights.keyInsights.forEach { insight ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text("• $insight", Modifier.padding(8.dp))
                            }
                        }
                    }
                }

                /* ── Progress ── */
                item {
                    Column {
                        Text(
                            "Progress",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            insights.progress,
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }

                /* ── Homework ── */
                item {
                    Column {
                        Text(
                            "Homework",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        insights.homework.forEach { hw ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text("• $hw", Modifier.padding(8.dp))
                            }
                        }
                    }
                }

                /* ── Next steps ── */
                item {
                    Column {
                        Text(
                            "Next Steps",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            insights.nextSteps,
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    )
}
fun getEmotionColor(emotion: Emotion?): Color {
    return when (emotion) {
        Emotion.HAPPY -> Color(0xFF4CAF50)
        Emotion.SAD -> Color(0xFF2196F3)
        Emotion.ANGRY -> Color(0xFFF44336)
        Emotion.ANXIOUS -> Color(0xFFFF9800)
        Emotion.FEARFUL -> Color(0xFF9C27B0)
        Emotion.SURPRISED -> Color(0xFFFFEB3B)
        Emotion.DISGUSTED -> Color(0xFF795548)
        Emotion.NEUTRAL -> Color(0xFF607D8B)
        null -> Color.Gray
    }
}

@Composable
private fun ServiceModeIndicatorCBT(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = if (isOnline) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Cloud else Icons.Default.OfflineBolt,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isOnline) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
            
            Text(
                text = if (isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOnline) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
        }
    }
}
