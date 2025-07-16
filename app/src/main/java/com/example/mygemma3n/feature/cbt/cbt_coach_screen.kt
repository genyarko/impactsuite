package com.example.mygemma3n.feature.cbt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val audioPermission = rememberAudioPermissionState()

    var userInput  by remember { mutableStateOf("") }
    var showThoughtRecord by remember { mutableStateOf(false) }

    /* Update userInput when state changes */
    LaunchedEffect(sessionState.userTypedInput) {
        userInput = sessionState.userTypedInput
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        /* ───────── Header ───────── */
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text       = "CBT Coach",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                AnimatedVisibility(visible = sessionState.currentEmotion != null) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "Detected Emotion: ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        EmotionChip(
                            text       = sessionState.currentEmotion?.name ?: "",
                            onClick    = { /* no‑op */ },
                            labelColor = getEmotionColor(sessionState.currentEmotion)
                        )
                    }
                }

                AnimatedVisibility(visible = sessionState.suggestedTechnique != null) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text       = "Current Technique: ${sessionState.suggestedTechnique?.name}",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        sessionState.suggestedTechnique?.let {
                            LinearProgressIndicator(
                                progress = { (sessionState.currentStep + 1f) / it.steps.size },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                color = ProgressIndicatorDefaults.linearColor,
                                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                            )
                        }
                    }
                }
            }
        }

        /* ───────── Conversation ───────── */
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            reverseLayout = true
        ) {
            items(sessionState.conversation.reversed()) { message ->
                MessageBubble(
                    message       = message,
                    cbtTechniques = viewModel.cbtTechniques
                )
            }
        }

        /* ───────── Error Display (if any) ───────── */
        sessionState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        /* ───────── Action Buttons ───────── */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sessionState.isActive) {
                OutlinedButton(
                    onClick  = { showThoughtRecord = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Thought Record") }

                OutlinedButton(
                    onClick  = { viewModel.progressToNextStep() },
                    modifier = Modifier.weight(1f),
                    enabled  = sessionState.suggestedTechnique != null
                ) { Text("Next Step") }

                OutlinedButton(
                    onClick  = { viewModel.analyzeEmotionTrajectory() },
                    modifier = Modifier.weight(1f)
                ) { Text("Analyze Progress") }

                OutlinedButton(
                    onClick  = {
                        val lastIssue = viewModel.sessionState.value
                            .conversation
                            .filterIsInstance<Message.User>()
                            .lastOrNull()
                            ?.content ?: ""
                        viewModel.getPersonalizedRecommendations(lastIssue)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Recommendations") }
            }
        }

        /* ───────── Input Area ───────── */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (sessionState.isActive) {
                OutlinedTextField(
                    value         = userInput,
                    onValueChange = { new ->
                        // Let the user type only when not recording
                        if (!sessionState.isRecording) {
                            userInput = new
                            viewModel.updateUserTypedInput(new)
                        }
                    },
                    modifier      = Modifier.weight(1f),
                    placeholder   = {
                        Text(
                            when {
                                sessionState.isRecording -> "Recording... Speak now"
                                userInput == "Transcribing..." -> "Transcribing..."
                                else -> "Share your thoughts…"
                            }
                        )
                    },
                    enabled       = !isLoading,  // Allow viewing while recording
                    readOnly      = sessionState.isRecording,  // Make read-only while recording
                    shape         = RoundedCornerShape(24.dp),
                    colors        = if (sessionState.isRecording) {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        OutlinedTextFieldDefaults.colors()
                    }
                )

                IconButton(
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
                    enabled = !isLoading
                ) {
                    if (sessionState.isRecording) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = if (audioPermission.hasPermission)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick  = {
                        if (userInput.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.processTextInput(userInput)
                                userInput = ""
                                viewModel.updateUserTypedInput("")
                            }
                        }
                    },
                    enabled = userInput.isNotBlank() && !isLoading && !sessionState.isRecording
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier   = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            } else {
                /* Guidance text replacing disabled input */
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "Let's begin",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { viewModel.startSession() },
                    shape   = RoundedCornerShape(24.dp)
                ) { Text("Start Session") }
            }
        }

        // End session button
        AnimatedVisibility(visible = sessionState.isActive) {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.generateSessionSummary()
                        viewModel.endSession()
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("End Session")
            }
        }
    }

    // Thought Record Dialog
    if (showThoughtRecord) {
        ThoughtRecordDialog(
            onDismiss = { showThoughtRecord = false },
            onSubmit = { situation, thought, intensity ->
                viewModel.createThoughtRecord(situation, thought, intensity)
                showThoughtRecord = false
            }
        )
    }

    // Session Insights Dialog
    sessionState.sessionInsights?.let { insights ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Session Summary") },
            text = {
                Column {
                    Text(insights.summary)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Key Insights:", fontWeight = FontWeight.Bold)
                    insights.keyInsights.forEach { insight ->
                        Text("• $insight", modifier = Modifier.padding(start = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Homework:", fontWeight = FontWeight.Bold)
                    insights.homework.forEach { hw ->
                        Text("• $hw", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSessionInsights()
                }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    SuggestionChip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = text,
                color = labelColor
            )
        }
    )
}

@Composable
fun MessageBubble(
    message: Message,
    cbtTechniques: CBTTechniques  // Add parameter
) {
    val isUser = message is Message.User

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )

                if (message is Message.AI && message.techniqueId != null) {
                    val techniqueName = message.getTechniqueName(cbtTechniques)
                    techniqueName?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Technique: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThoughtRecordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Float) -> Unit
) {
    var situation by remember { mutableStateOf("") }
    var thought by remember { mutableStateOf("") }
    var intensity by remember { mutableFloatStateOf(0.5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Thought Record") },
        text = {
            Column {
                OutlinedTextField(
                    value = situation,
                    onValueChange = { situation = it },
                    label = { Text("Situation") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = thought,
                    onValueChange = { thought = it },
                    label = { Text("Automatic Thought") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Emotion Intensity: ${(intensity * 100).toInt()}%")
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
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
