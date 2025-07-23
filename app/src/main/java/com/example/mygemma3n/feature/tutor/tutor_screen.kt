package com.example.mygemma3n.feature.tutor


import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mygemma3n.data.StudentProfileEntity
import com.example.mygemma3n.data.TutorSessionType
import com.example.mygemma3n.shared_utilities.OfflineRAG

// feature/tutor/TutorScreen.kt

@Composable
fun TutorScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: TutorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showProfileDialog by remember { mutableStateOf(false) }
    var showSubjectSelector by remember { mutableStateOf(false) }

    // Check if student profile exists
    LaunchedEffect(Unit) {
        if (state.studentProfile == null) {
            showProfileDialog = true
        }
    }

    Scaffold(
        topBar = {
            TutorTopBar(
                studentName = state.studentProfile?.name ?: "Student",
                subject = state.currentSubject,
                onBackClick = onNavigateBack,
                onProfileClick = { showProfileDialog = true }
            )
        },
        bottomBar = {
            if (state.currentSubject != null) {
                TutorInputBar(
                    onSendMessage = { viewModel.processUserInput(it) },
                    onVoiceInput = { /* Voice input */ },
                    isLoading = state.isLoading
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
                        conceptMastery = state.conceptMastery
                    )
                }
            }
        }
    }

    // Dialogs
    if (showProfileDialog) {
        StudentProfileDialog(
            existingProfile = state.studentProfile,
            onDismiss = { showProfileDialog = false },
            onConfirm = { name, grade ->
                viewModel.initializeStudent(name, grade)
                showProfileDialog = false
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
    onProfileClick: () -> Unit
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TutorChatInterface(
    messages: List<TutorViewModel.TutorMessage>,
    isLoading: Boolean,
    conceptMastery: Map<String, Float>
) {
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TutorMessageBubble(
    message: TutorViewModel.TutorMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
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
                    }
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
    isLoading: Boolean
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
                placeholder = { Text("Ask your tutor...") },
                maxLines = 4,
                enabled = !isLoading,
                trailingIcon = {
                    IconButton(
                        onClick = onVoiceInput,
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Mic, "Voice input")
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
                enabled = textInput.isNotBlank() && !isLoading
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
    )
)

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