package com.mygemma3n.aiapp.feature.tutor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mygemma3n.aiapp.data.StudentProfileEntity
import com.mygemma3n.aiapp.data.TutorSessionType
import com.mygemma3n.aiapp.shared_utilities.OfflineRAG
import com.mygemma3n.aiapp.components.*
import com.mygemma3n.aiapp.feature.tutor.TutorProgressDisplay
import com.mygemma3n.aiapp.feature.tutor.FloatingProgressSummary
import com.mygemma3n.aiapp.feature.tutor.TutorProgressIntegrationService
import com.mygemma3n.aiapp.feature.tutor.AchievementDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TutorScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToChatList: () -> Unit = {},
    viewModel: TutorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val accessibilityManager = LocalAccessibilityManager.current

    // Handle system back button
    BackHandler(enabled = state.currentSubject != null) {
        viewModel.clearCurrentSubject()
    }

    // Save state when navigating away
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveConversationState()
        }
    }

    Scaffold(
        topBar = {
            TutorTopBar(
                studentName = state.studentProfile?.name ?: "Student",
                subject = state.currentSubject,
                onBackClick = {
                    if (state.currentSubject != null) {
                        viewModel.clearCurrentSubject()
                    } else {
                        onNavigateBack()
                    }
                },
                onProfileClick = { viewModel.showStudentProfileDialog() },
                onChatListClick = onNavigateToChatList
            )
        },
        bottomBar = {
            if (state.currentSubject != null) {
                Column {
                    // Auto-speak controls
                    AnimatedVisibility(
                        visible = state.autoSpeak,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        AutoSpeakControls(
                            speechRate = state.speechRate,
                            onSpeechRateChange = viewModel::setSpeechRate,
                            onClose = { viewModel.toggleAutoSpeak() }
                        )
                    }

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
                        onToggleAutoSpeak = { viewModel.toggleAutoSpeak() },
                        isLoading = state.isLoading,
                        isRecording = state.isRecording,
                        isTranscribing = state.isTranscribing,
                        showFloatingTopics = state.showFloatingTopics,
                        hasSuggestedTopics = state.suggestedTopics.isNotEmpty(),
                        autoSpeakEnabled = state.autoSpeak
                    )
                }
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
                        scrollState = scrollState,
                        currentProgress = state.currentProgress,
                        currentStreak = state.currentStreak,
                        weeklyGoalProgress = state.weeklyGoalProgress,
                        showFloatingProgressSummary = state.showFloatingProgressSummary,
                        showConceptMasteryCard = state.showConceptMasteryCard,
                        showTutorProgressDisplay = state.showTutorProgressDisplay,
                        isUsingOnlineService = state.isUsingOnlineService,
                        onMessageDoubleTap = { viewModel.speakText(it) },
                        onTopicSelected = { topic ->
                            viewModel.selectTopic(topic)
                        },
                        onBookmarkMessage = { messageId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.bookmarkMessage(messageId)
                        },
                        onViewProgressDetails = {
                            viewModel.showFloatingProgressSummary()
                        },
                        onDismissProgressSummary = {
                            viewModel.hideFloatingProgressSummary()
                        },
                        onSelectConceptFromProgress = { concept ->
                            viewModel.selectConceptFromProgress(concept)
                        },
                        onToggleConceptMasteryCard = {
                            viewModel.toggleConceptMasteryCard()
                        },
                        onToggleTutorProgressDisplay = {
                            viewModel.toggleTutorProgressDisplay()
                        },
                        progressSummary = viewModel.getProgressSummary()
                    )
                }
            }

            // Error snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }

        // Auto-scroll to bottom when new message arrives
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) {
                scrollState.animateScrollToItem(0)
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

    // Achievement Dialog
    if (state.showAchievementDialog && state.achievementToShow != null) {
        AchievementDialog(
            achievement = state.achievementToShow!!,
            onDismiss = { viewModel.dismissAchievementDialog() }
        )
    }
}

@Composable
private fun TutorChatInterface(
    messages: List<TutorViewModel.TutorMessage>,
    isLoading: Boolean,
    conceptMastery: Map<String, Float>,
    suggestedTopics: List<String>,
    showFloatingTopics: Boolean,
    scrollState: LazyListState,
    currentProgress: Float,
    currentStreak: Int,
    weeklyGoalProgress: Float,
    showFloatingProgressSummary: Boolean,
    showConceptMasteryCard: Boolean,
    showTutorProgressDisplay: Boolean,
    isUsingOnlineService: Boolean,
    onMessageDoubleTap: (String) -> Unit,
    onTopicSelected: (String) -> Unit,
    onBookmarkMessage: (String) -> Unit,
    onViewProgressDetails: () -> Unit,
    onDismissProgressSummary: () -> Unit,
    onSelectConceptFromProgress: (String) -> Unit,
    onToggleConceptMasteryCard: () -> Unit,
    onToggleTutorProgressDisplay: () -> Unit,
    progressSummary: TutorProgressIntegrationService.ProgressSummary?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Progress display
            AnimatedVisibility(
                visible = (currentProgress > 0f || currentStreak > 0 || weeklyGoalProgress > 0f) && showTutorProgressDisplay,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    TutorProgressDisplay(
                        currentProgress = currentProgress,
                        currentStreak = currentStreak,
                        weeklyGoalProgress = weeklyGoalProgress,
                        onViewDetails = onViewProgressDetails,
                        onMinimize = onToggleTutorProgressDisplay,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider()
                }
            }
            
            // Minimized progress display
            AnimatedVisibility(
                visible = (currentProgress > 0f || currentStreak > 0 || weeklyGoalProgress > 0f) && !showTutorProgressDisplay,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(8.dp),
                    onClick = onToggleTutorProgressDisplay
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (currentStreak > 0) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$currentStreak",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentStreak > 0) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "Show progress details",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Service mode indicator
            AnimatedVisibility(
                visible = true, // Always show when in a session
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                ServiceModeIndicator(
                    isOnline = isUsingOnlineService,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            
            // Concept mastery indicator
            AnimatedVisibility(
                visible = conceptMastery.isNotEmpty() && showConceptMasteryCard,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    ConceptMasterySection(
                        conceptMastery = conceptMastery,
                        onToggle = onToggleConceptMasteryCard
                    )
                    HorizontalDivider()
                }
            }
            
            // Show progress button when card is hidden
            AnimatedVisibility(
                visible = conceptMastery.isNotEmpty() && !showConceptMasteryCard,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(8.dp),
                    onClick = onToggleConceptMasteryCard
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Show Progress (${(conceptMastery.values.average() * 100).toInt()}% avg)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "Show progress",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Messages with skeleton loading
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = scrollState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                reverseLayout = true
            ) {
                if (isLoading) {
                    item {
                        TutorTypingIndicator()
                    }
                }

                items(
                    items = messages.reversed(),
                    key = { it.id }
                ) { message ->
                    TutorMessageBubble(
                        message = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(), // Updated from animateItemPlacement()
                        onDoubleTap = onMessageDoubleTap,
                        onBookmark = { onBookmarkMessage(message.id) }
                    )
                }

                if (showFloatingTopics && messages.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(200.dp))
                    }
                }
            }
        }

        // Floating topic bubbles
        AnimatedVisibility(
            visible = showFloatingTopics && suggestedTopics.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            FloatingTopicBubbles(
                topics = suggestedTopics,
                onTopicSelected = onTopicSelected
            )
        }
        
        // Floating progress summary
        progressSummary?.let { summary ->
            FloatingProgressSummary(
                progressSummary = summary,
                isVisible = showFloatingProgressSummary,
                onDismiss = onDismissProgressSummary,
                onSelectConcept = onSelectConceptFromProgress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun ConceptMasterySection(
    conceptMastery: Map<String, Float>,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
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
                    text = "Your Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${(conceptMastery.values.average() * 100).toInt()}% Average",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Hide progress card",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            conceptMastery.entries.take(3).forEach { (concept, mastery) ->
                LabeledProgressBar(
                    progress = mastery,
                    label = concept,
                    modifier = Modifier.padding(vertical = 4.dp),
                    showPercentage = true,
                    color = when {
                        mastery >= 0.8f -> Color(0xFF4CAF50)
                        mastery >= 0.6f -> Color(0xFFFFC107)
                        else -> Color(0xFFFF5252)
                    }
                )
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TutorMessageBubble(
    message: TutorViewModel.TutorMessage,
    modifier: Modifier = Modifier,
    onDoubleTap: (String) -> Unit = {},
    onBookmark: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .semantics {
                contentDescription = buildString {
                    append(if (message.isUser) "You said: " else "Tutor said: ")
                    append(message.content)
                    if (!message.isUser) {
                        append(". Double tap to hear this message.")
                    }
                }
            }
            .combinedClickable(
                onClick = { },
                onDoubleClick = {
                    if (!message.isUser) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDoubleTap(message.content)
                    }
                },
                onLongClick = {
                    if (!message.isUser) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBookmark()
                    }
                }
            ),
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
                containerColor = if (message.isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 16.dp else 4.dp,
                topEnd = if (message.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Bookmark indicator
                if (message.isBookmarked) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Bookmarked",
                            modifier = Modifier.size(16.dp),
                            tint = if (message.isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Metadata and timestamp
                message.metadata?.let { metadata ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        metadata.concept?.let {
                            TagChip(
                                text = it,
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isUser) {
                        MessageStatusIcon(status = message.status)
                        Spacer(modifier = Modifier.width(4.dp))
                    }

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
private fun AutoSpeakControls(
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Speech Rate",
                style = MaterialTheme.typography.labelLarge
            )

            Slider(
                value = speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.5f..2.0f,
                steps = 5,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Text(
                text = "${(speechRate * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(40.dp)
            )

            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close speech controls"
                )
            }
        }
    }
}


@Composable
private fun TutorInputBar(
    onSendMessage: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onToggleTopics: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    isLoading: Boolean,
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    showFloatingTopics: Boolean = false,
    hasSuggestedTopics: Boolean = false,
    autoSpeakEnabled: Boolean = false
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
                        // Auto-speak toggle
                        IconButton(
                            onClick = onToggleAutoSpeak,
                            enabled = !isLoading
                        ) {
                            Icon(
                                if (autoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = if (autoSpeakEnabled) "Disable auto-speak" else "Enable auto-speak",
                                tint = if (autoSpeakEnabled)
                                    MaterialTheme.colorScheme.primary
                                else LocalContentColor.current
                            )
                        }
                        
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
                                tint = if (isRecording) 
                                    MaterialTheme.colorScheme.error 
                                else LocalContentColor.current
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
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        color = Color(0xFF795548),
        defaultSessionType = TutorSessionType.CONCEPT_EXPLANATION
    ),
    SubjectCard(
        subject = OfflineRAG.Subject.COMPUTER_SCIENCE,
        displayName = "Computer Science",
        description = "Programming, Algorithms, Data Structures, Web Development",
        icon = Icons.Default.Computer,
        color = Color(0xFF607D8B),
        defaultSessionType = TutorSessionType.PRACTICE_PROBLEMS
    )
)

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
        OfflineRAG.Subject.COMPUTER_SCIENCE -> listOf(
            "Programming fundamentals",
            "Data structures and algorithms",
            "Web development basics",
            "Database design"
        )
        else -> listOf("General topics", "Basic concepts", "Fundamentals")
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "now"
        diff < 3600000 -> "${diff / 60000}m"
        diff < 86400000 -> "${diff / 3600000}h"
        else -> {
            val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
            formatter.format(Date(timestamp))
        }
    }
}

@Composable
private fun ServiceModeIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(6.dp),
        color = if (isOnline) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Cloud else Icons.Default.OfflineBolt,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isOnline) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
            
            Text(
                text = if (isOnline) "Online Mode" else "Offline Mode",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOnline) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = if (isOnline) "Gemini API" else "On-device AI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isOnline) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
        }
    }
}