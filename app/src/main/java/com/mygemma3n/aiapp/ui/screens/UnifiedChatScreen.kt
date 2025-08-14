package com.mygemma3n.aiapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mygemma3n.aiapp.feature.chat.ChatMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class FeatureShortcut(
    val name: String,
    val command: String,
    val icon: ImageVector,
    val route: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedChatScreen(
    navController: NavHostController,
    viewModel: UnifiedChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var showFeatureMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Feature shortcuts
    val features = remember {
        listOf(
            FeatureShortcut("AI Tutor", "tutor", Icons.Default.School, "tutor", "Get personalized lessons"),
            FeatureShortcut("Quiz", "quiz", Icons.Default.Quiz, "quiz_generator", "Generate practice quizzes"),
            FeatureShortcut("Live Caption", "caption", Icons.Default.ClosedCaption, "live_caption", "Real-time transcription"),
            FeatureShortcut("CBT Coach", "cbt", Icons.Default.Psychology, "cbt_coach", "Mental wellness support"),
            FeatureShortcut("Summarizer", "summarizer", Icons.Default.Summarize, "summarizer", "Document summarization"),
            FeatureShortcut("Image Scan", "scan", Icons.Default.PhotoCamera, "plant_scanner", "Identify images"),
            FeatureShortcut("Crisis Help", "crisis", Icons.Default.LocalHospital, "crisis_handbook", "Emergency resources"),
            FeatureShortcut("Analytics", "analytics", Icons.Default.Analytics, "analytics", "Learning insights"),
            FeatureShortcut("Story Mode", "story", Icons.AutoMirrored.Filled.MenuBook, "story_mode", "Interactive stories")
        )
    }

    // Keep input text in sync with viewModel state
    LaunchedEffect(state.userTypedInput) {
        inputText = state.userTypedInput
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.conversation.size) {
        if (state.conversation.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Command parsing for feature activation
    LaunchedEffect(inputText) {
        features.forEach { feature ->
            if (inputText.lowercase().trim() == feature.command ||
                inputText.lowercase().startsWith("/${feature.command}") ||
                inputText.lowercase().startsWith("open ${feature.command}") ||
                inputText.lowercase().startsWith("start ${feature.command}")
            ) {
                // Clear input and navigate
                viewModel.updateUserTypedInput("")
                navController.navigate(feature.route)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            UnifiedChatTopBar(
                showFeatureMenu = showFeatureMenu,
                onToggleFeatureMenu = { showFeatureMenu = !showFeatureMenu },
                features = features,
                onFeatureClick = { feature ->
                    showFeatureMenu = false
                    navController.navigate(feature.route)
                },
                navController = navController
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Feature menu overlay
            AnimatedVisibility(
                visible = showFeatureMenu,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                FeatureMenuOverlay(
                    features = features,
                    onFeatureClick = { feature ->
                        showFeatureMenu = false
                        navController.navigate(feature.route)
                    },
                    onDismiss = { showFeatureMenu = false }
                )
            }

            // Error message display
            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Recording indicator
            if (state.isRecording) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording audio...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Speaking indicator
            if (state.isSpeaking) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Speaking: ${state.speakingText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }

            // Messages area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.conversation.isEmpty()) {
                    UnifiedEmptyState(features = features, onFeatureClick = { feature ->
                        navController.navigate(feature.route)
                    })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Show typing indicator if loading
                        if (state.isLoading) {
                            item {
                                TypingIndicator()
                            }
                        }

                        // Messages
                        items(
                            items = state.conversation.reversed(),
                            key = { "${it.timestamp}_${it.content.take(10)}" }
                        ) { message ->
                            AnimatedMessage(
                                message = message,
                                onDoubleTap = { text ->
                                    viewModel.speakText(text)
                                }
                            )
                        }
                    }
                }

                // Gradient fade at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                )
                            )
                        )
                )
            }


            // Input area with enhanced command hints
            EnhancedChatInputArea(
                inputText = inputText,
                isRecording = state.isRecording,
                isLoading = state.isLoading,
                features = features,
                onInputChange = { newText ->
                    if (!state.isRecording) {
                        inputText = newText
                        viewModel.updateUserTypedInput(newText)
                    }
                },
                onSendClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage()
                        inputText = ""
                    }
                },
                onRecordClick = {
                    if (state.isRecording) {
                        viewModel.stopRecording()
                    } else {
                        viewModel.startRecording()
                    }
                },
                onCameraClick = {
                    navController.navigate("plant_scanner")
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedChatTopBar(
    showFeatureMenu: Boolean,
    onToggleFeatureMenu: () -> Unit,
    features: List<FeatureShortcut>,
    onFeatureClick: (FeatureShortcut) -> Unit,
    navController: NavHostController
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

            }
        },
        navigationIcon = {
            IconButton(onClick = onToggleFeatureMenu) {
                Icon(
                    if (showFeatureMenu) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = if (showFeatureMenu) "Close menu" else "Open menu"
                )
            }
        },
        actions = {
            IconButton(onClick = { 
                // Navigate to api settings
                navController.navigate("api_settings")
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = { 
                // Navigate to original home screen for model download etc
                navController.navigate("home")
            }) {
                Icon(Icons.Default.Home, contentDescription = "Home")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.shadow(4.dp)
    )
}

@Composable
private fun FeatureMenuOverlay(
    features: List<FeatureShortcut>,
    onFeatureClick: (FeatureShortcut) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(features) { feature ->
                Card(
                    onClick = { onFeatureClick(feature) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = feature.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = feature.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        
                        Text(
                            text = "/${feature.command}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedEmptyState(
    features: List<FeatureShortcut>,
    onFeatureClick: (FeatureShortcut) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Welcome to G3N AI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Start chatting or activate features:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Command examples
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(features.take(5)) { feature ->
                AssistChip(
                    onClick = { onFeatureClick(feature) },
                    label = { Text("/${feature.command}") },
                    leadingIcon = {
                        Icon(
                            feature.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Or use the menu button above for all features",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedChatInputArea(
    inputText: String,
    isRecording: Boolean,
    isLoading: Boolean,
    features: List<FeatureShortcut>,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onRecordClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    // Command suggestion
    val currentSuggestion = remember(inputText) {
        if (inputText.startsWith("/") && inputText.length > 1) {
            features.find { 
                it.command.startsWith(inputText.drop(1).lowercase()) 
            }
        } else null
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.shadow(8.dp)
    ) {
        Column {
            // Command suggestion
            if (currentSuggestion != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            currentSuggestion.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "/${currentSuggestion.command} - ${currentSuggestion.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Full-width input field with integrated buttons
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Input field takes most space
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            enabled = !isRecording,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 12.dp
                                        )
                                ) {
                                    if (inputText.isEmpty()) {
                                        Column {
                                            Text(
                                                text = when {
                                                    isRecording -> "Recording... Speak now"
                                                    else -> "Chat with AI or type commands below..."
                                                },
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            if (!isRecording) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "/quiz • /tutor • /scan • /caption • /cbt",
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                    }

                    // Integrated buttons row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera button
                        IconButton(
                            onClick = onCameraClick,
                            enabled = !isLoading && !isRecording,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Image Scanner",
                                modifier = Modifier.size(20.dp),
                                tint = if (!isLoading && !isRecording) 
                                    MaterialTheme.colorScheme.onSurfaceVariant 
                                else 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        // Voice button (simplified)
                        IconButton(
                            onClick = onRecordClick,
                            enabled = !isLoading,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                                modifier = Modifier.size(20.dp),
                                tint = if (isRecording) 
                                    MaterialTheme.colorScheme.error 
                                else if (!isLoading) 
                                    MaterialTheme.colorScheme.onSurfaceVariant 
                                else 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        // Send button (simplified)
                        IconButton(
                            onClick = onSendClick,
                            enabled = inputText.isNotBlank() && !isRecording && !isLoading,
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (inputText.isNotBlank() && !isRecording && !isLoading)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Reuse existing components from ChatScreen
@Composable
private fun AnimatedMessage(
    message: ChatMessage,
    onDoubleTap: (String) -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { it / 4 }
        )
    ) {
        when (message) {
            is ChatMessage.User -> UserMessageBubble(message)
            is ChatMessage.AI -> AIMessageBubble(
                message = message,
                onDoubleTap = onDoubleTap,
                isCurrentlySpeaking = false // We could track which message is being spoken if needed
            )
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage.User) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 4.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                modifier = Modifier.shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatMessageTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AIMessageBubble(
    message: ChatMessage.AI,
    onDoubleTap: (String) -> Unit = {},
    isCurrentlySpeaking: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // AI Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7C4DFF),
                            Color(0xFF536DFE)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                modifier = Modifier
                    .shadow(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = Color.Black.copy(alpha = 0.05f)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                onDoubleTap(message.content)
                            }
                        )
                    }
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatMessageTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(start = 40.dp)
                .shadow(
                    elevation = 1.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color.Black.copy(alpha = 0.05f)
                )
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition()

                for (i in 0..2) {
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(i * 200)
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                    )
                }
            }
        }
    }
}


private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}