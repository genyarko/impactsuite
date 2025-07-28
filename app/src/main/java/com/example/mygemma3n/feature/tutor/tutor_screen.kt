package com.example.mygemma3n.feature.tutor


import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mygemma3n.data.StudentProfileEntity
import com.example.mygemma3n.data.TutorSessionType
import com.example.mygemma3n.shared_utilities.OfflineRAG
import com.example.mygemma3n.feature.tutor.FloatingTopicBubbles
// feature/tutor/TutorScreen.kt

@Composable
fun TutorScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToChatList: () -> Unit = {},
    viewModel: TutorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showSubjectSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TutorTopBar(
                studentName = state.studentProfile?.name ?: "Student",
                subject = state.currentSubject,
                onBackClick = {
                    if (state.currentSubject != null) {
                        // If in a subject session, go back to subject selection
                        viewModel.clearCurrentSubject()
                    } else {
                        // If in subject selection, go back to main
                        onNavigateBack()
                    }
                },
                onProfileClick = { viewModel.showStudentProfileDialog() },
                onChatListClick = onNavigateToChatList
            )
        },
        bottomBar = {
            if (state.currentSubject != null) {
                TutorInputBar(
                    onSendMessage = { viewModel.processUserInput(it) },
                    onVoiceInput = {
                        if (state.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            viewModel.startRecording()
                        }
                    },
                    onToggleTopics = { viewModel.toggleFloatingTopics() },
                    isLoading = state.isLoading,
                    isRecording = state.isRecording,
                    isTranscribing = state.isTranscribing,
                    showFloatingTopics = state.showFloatingTopics,
                    hasSuggestedTopics = state.suggestedTopics.isNotEmpty()
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.currentSubject == null -> {
                    TutorWelcomeScreen(
                        studentProfile = state.studentProfile,
                        onSelectSubject = { subject, sessionType ->
                            viewModel.startTutorSession(
                                subject = subject,
                                sessionType = sessionType,
                                topic = "General"
                            )
                        }
                    )
                }
                else -> {
                    TutorChatInterface(
                        messages = state.messages,
                        isLoading = state.isLoading,
                        conceptMastery = state.conceptMastery,
                        suggestedTopics = state.suggestedTopics,
                        showFloatingTopics = state.showFloatingTopics,
                        onMessageDoubleTap = { viewModel.speakText(it) },
                        onTopicSelected = { topic ->
                            viewModel.selectTopic(topic)
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (state.showStudentDialog) {
        StudentProfileDialog(
            existingProfile = state.studentProfile,
            onDismiss = { viewModel.dismissStudentDialog() },
            onConfirm = { name, grade ->
                viewModel.initializeStudent(name, grade)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TutorTopBar(
    studentName: String,
    subject: OfflineRAG.Subject?,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onChatListClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "AI Tutor",
                    style = MaterialTheme.typography.titleMedium
                )
                if (subject != null) {
                    Text(
                        text = "$studentName - ${subject.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(onClick = onChatListClick) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, "Chat List")
            }
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Default.Person, "Profile")
            }
        }
    )
}

@Composable
private fun TutorWelcomeScreen(
    studentProfile: StudentProfileEntity?,
    onSelectSubject: (OfflineRAG.Subject, TutorSessionType) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Welcome, ${studentProfile?.name ?: "Student"}!",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Grade ${studentProfile?.gradeLevel ?: ""}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            Text(
                text = "What would you like to work on today?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }

        // Subject cards
        items(getSubjectCards()) { subjectCard ->
            SubjectSelectionCard(
                subjectCard = subjectCard,
                onClick = { onSelectSubject(subjectCard.subject, subjectCard.defaultSessionType) }
            )
        }
    }
}

@Composable
private fun SubjectSelectionCard(
    subjectCard: SubjectCard,
    onClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        onClick = { 
            isExpanded = !isExpanded
        },
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) 
                subjectCard.color.copy(alpha = 0.05f) 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Main content row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = subjectCard.color.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = subjectCard.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = subjectCard.color
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subjectCard.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subjectCard.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expanded preview content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = subjectCard.color.copy(alpha = 0.2f))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Sample Topics:",
                        style = MaterialTheme.typography.labelLarge,
                        color = subjectCard.color,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Sample topics based on subject
                    getSampleTopics(subjectCard.subject).take(3).forEach { topic ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(6.dp),
                                tint = subjectCard.color.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = topic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Start learning button
                    Button(
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = subjectCard.color
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Learning ${subjectCard.displayName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorChatInterface(
    messages: List<TutorViewModel.TutorMessage>,
    isLoading: Boolean,
    conceptMastery: Map<String, Float>,
    suggestedTopics: List<String>,
    showFloatingTopics: Boolean,
    onMessageDoubleTap: (String) -> Unit,
    onTopicSelected: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Concept mastery indicator
            if (conceptMastery.isNotEmpty()) {
                ConceptMasteryBar(conceptMastery)
                HorizontalDivider()
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                reverseLayout = true
            ) {
                if (isLoading) {
                    item {
                        TutorTypingIndicator()
                    }
                }

                items(messages.reversed()) { message ->
                    TutorMessageBubble(
                        message = message,
                        modifier = Modifier.fillMaxWidth(),
                        onDoubleTap = onMessageDoubleTap
                    )
                }

                // Add space at the top for floating topics
                if (showFloatingTopics && messages.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(200.dp))
                    }
                }
            }
        }

        // Floating topic bubbles
        if (showFloatingTopics && suggestedTopics.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp) // Above the input bar
            ) {
                FloatingTopicBubbles(
                    topics = suggestedTopics,
                    onTopicSelected = onTopicSelected
                )
            }
        }
    }
}
@Composable
fun SuggestedTopicsSection(topics: List<String>) {
    if (topics.isNotEmpty()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Suggested Topics",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(topics) { topic ->
                    AssistChip(
                        onClick = { /* Handle topic click */ },
                        label = { Text(topic) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorMessageBubble(
    message: TutorViewModel.TutorMessage,
    modifier: Modifier = Modifier,
    onDoubleTap: (String) -> Unit = {}
) {
    Row(
        modifier = modifier.combinedClickable(
            onClick = { /* consume single tap – no TTS */ },
            onDoubleClick = {
                if (!message.isUser) onDoubleTap(message.content)
            }
        ),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ){
        if (!message.isUser) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = "Tutor",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(4.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 16.dp else 4.dp,
                topEnd = if (message.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Message metadata chips
                message.metadata?.let { metadata ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        metadata.concept?.let {
                            AssistChip(
                                onClick = { },
                                label = { Text(it, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        
                        metadata.responseTime?.let { responseTime ->
                            AssistChip(
                                onClick = { },
                                label = { Text("${responseTime}ms", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }

                // Timestamp and status row
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Message status icon
                    if (message.isUser) {
                        MessageStatusIcon(status = message.status)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    // Timestamp
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isUser) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
            }
        }

        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Person,
                contentDescription = "Student",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(4.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun TutorInputBar(
    onSendMessage: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onToggleTopics: () -> Unit,  // Add this parameter
    isLoading: Boolean,
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    showFloatingTopics: Boolean = false,  // Add this parameter
    hasSuggestedTopics: Boolean = false   // Add this parameter
) {
    var textInput by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(when {
                        isTranscribing -> "Transcribing..."
                        isRecording -> "Listening..."
                        else -> "Ask your tutor..."
                    })
                },
                maxLines = 4,
                enabled = !isLoading && !isRecording && !isTranscribing,
                trailingIcon = {
                    Row {
                        // Topics button
                        if (hasSuggestedTopics) {
                            IconButton(
                                onClick = onToggleTopics,
                                enabled = !isLoading && !isRecording && !isTranscribing
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = "Show topics",
                                    tint = if (showFloatingTopics)
                                        MaterialTheme.colorScheme.primary
                                    else LocalContentColor.current
                                )
                            }
                        }

                        // Voice input button
                        IconButton(
                            onClick = onVoiceInput,
                            enabled = !isLoading && !isTranscribing
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Voice input",
                                tint = if (isRecording) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                enabled = textInput.isNotBlank() && !isLoading && !isRecording && !isTranscribing
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun ConceptMasteryBar(conceptMastery: Map<String, Float>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        conceptMastery.entries.take(3).forEach { (concept, mastery) ->
            val bg = when {
                mastery < 0.4f -> MaterialTheme.colorScheme.errorContainer
                mastery < 0.7f -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }

            SuggestionChip(
                onClick = { /* no-op or show details */ },
                label = {
                    Text(
                        text = "$concept ${(mastery * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.height(32.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = bg)
            )
        }
    }
}

@Composable
private fun TutorTypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 100)
                ),
                label = "dot_alpha"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }
    }
}

// Helper data classes
data class SubjectCard(
    val subject: OfflineRAG.Subject,
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val defaultSessionType: TutorSessionType
)

private fun getSubjectCards() = listOf(
    SubjectCard(
        subject = OfflineRAG.Subject.MATHEMATICS,
        displayName = "Mathematics",
        description = "Algebra, Geometry, Calculus, and more",
        icon = Icons.Default.Calculate,
        color = Color(0xFF2196F3),
        defaultSessionType = TutorSessionType.PRACTICE_PROBLEMS
    ),
    SubjectCard(
        subject = OfflineRAG.Subject.SCIENCE,
        displayName = "Science",
        description = "Physics, Chemistry, Biology",
        icon = Icons.Default.Science,
        color = Color(0xFF4CAF50),
        defaultSessionType = TutorSessionType.CONCEPT_EXPLANATION
    ),
    SubjectCard(
        subject = OfflineRAG.Subject.ENGLISH,
        displayName = "English",
        description = "Grammar, Writing, Literature",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        color = Color(0xFFFF9800),
        defaultSessionType = TutorSessionType.HOMEWORK_HELP
    ),
    SubjectCard(
        subject = OfflineRAG.Subject.HISTORY,
        displayName = "History",
        description = "World History, Ancient civilizations, Modern history",
        icon = Icons.Default.HistoryEdu,
        color = Color(0xFF9C27B0),
        defaultSessionType = TutorSessionType.CONCEPT_EXPLANATION
    ),
    SubjectCard(
        subject = OfflineRAG.Subject.GEOGRAPHY,
        displayName = "Geography",
        description = "Physical geography, Human geography, Maps",
        icon = Icons.Default.Public,
        color = Color(0xFF00BCD4),
        defaultSessionType = TutorSessionType.CONCEPT_EXPLANATION
    ),
    SubjectCard(
        subject = OfflineRAG.Subject.ECONOMICS,
        displayName = "Economics",
        description = "Microeconomics, Macroeconomics, Markets",
        icon = Icons.Default.TrendingUp,
        color = Color(0xFF795548),
        defaultSessionType = TutorSessionType.CONCEPT_EXPLANATION
    )
)

@Composable
private fun TutorInputBar(
    onSendMessage: (String) -> Unit,
    onVoiceInput: () -> Unit,
    isLoading: Boolean,
    isRecording: Boolean = false,  // Add this parameter
    isTranscribing: Boolean = false  // Add this parameter
) {
    var textInput by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(when {
                        isTranscribing -> "Transcribing..."
                        isRecording -> "Listening..."
                        else -> "Ask your tutor..."
                    })
                },
                maxLines = 4,
                enabled = !isLoading && !isRecording && !isTranscribing,
                trailingIcon = {
                    IconButton(
                        onClick = onVoiceInput,
                        enabled = !isLoading && !isTranscribing
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Voice input",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                enabled = textInput.isNotBlank() && !isLoading && !isRecording && !isTranscribing
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

private fun getSampleTopics(subject: OfflineRAG.Subject): List<String> {
    return when (subject) {
        OfflineRAG.Subject.MATHEMATICS -> listOf(
            "Linear equations and functions",
            "Geometric shapes and angles",
            "Probability and statistics",
            "Calculus fundamentals"
        )
        OfflineRAG.Subject.SCIENCE -> listOf(
            "Forces and motion",
            "Chemical reactions",
            "Cell biology and genetics",
            "Energy and waves"
        )
        OfflineRAG.Subject.ENGLISH -> listOf(
            "Grammar and sentence structure",
            "Poetry analysis and writing",
            "Literature comprehension",
            "Essay writing techniques"
        )
        OfflineRAG.Subject.HISTORY -> listOf(
            "Ancient civilizations",
            "World wars and conflicts",
            "Cultural revolutions",
            "Modern democracy"
        )
        OfflineRAG.Subject.GEOGRAPHY -> listOf(
            "Climate and weather patterns",
            "Physical landscapes",
            "Population and urbanization",
            "Natural resources"
        )
        OfflineRAG.Subject.ECONOMICS -> listOf(
            "Supply and demand",
            "Market structures",
            "Government policies",
            "International trade"
        )
        else -> listOf("General topics", "Basic concepts", "Fundamentals")
    }
}

@Composable
private fun StudentProfileDialog(
    existingProfile: StudentProfileEntity?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var gradeLevel by remember { mutableStateOf(existingProfile?.gradeLevel?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Student Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = gradeLevel,
                    onValueChange = { gradeLevel = it.filter { char -> char.isDigit() } },
                    label = { Text("Grade Level (1-12)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val grade = gradeLevel.toIntOrNull() ?: 0
                    if (name.isNotBlank() && grade in 1..12) {
                        onConfirm(name, grade)
                    }
                },
                enabled = name.isNotBlank() && gradeLevel.toIntOrNull() in 1..12
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MessageStatusIcon(status: TutorViewModel.TutorMessage.MessageStatus) {
    val (icon, tint) = when (status) {
        TutorViewModel.TutorMessage.MessageStatus.SENDING -> 
            Icons.Default.Schedule to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
        TutorViewModel.TutorMessage.MessageStatus.SENT -> 
            Icons.Default.Check to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
        TutorViewModel.TutorMessage.MessageStatus.DELIVERED -> 
            Icons.Default.DoneAll to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        TutorViewModel.TutorMessage.MessageStatus.FAILED -> 
            Icons.Default.Error to MaterialTheme.colorScheme.error
        TutorViewModel.TutorMessage.MessageStatus.TYPING -> 
            Icons.Default.MoreHoriz to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
    }
    
    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(12.dp),
        tint = tint
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "now" // Less than 1 minute
        diff < 3600000 -> "${diff / 60000}m" // Less than 1 hour, show minutes
        diff < 86400000 -> "${diff / 3600000}h" // Less than 1 day, show hours
        else -> {
            // More than 1 day, show date
            val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
            formatter.format(Date(timestamp))
        }
    }
}