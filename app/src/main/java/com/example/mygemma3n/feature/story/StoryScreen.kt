package com.example.mygemma3n.feature.story

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    viewModel: StoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handle system back button to ensure TTS is stopped
    BackHandler(enabled = !state.showStoryList) {
        viewModel.backToStoryList()
    }

    // Ensure TTS is stopped when the screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTTS()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (state.showStoryList) "Story Mode" 
                        else state.currentStory?.title ?: "Reading Story"
                    )
                },
                navigationIcon = {
                    if (!state.showStoryList) {
                        IconButton(onClick = { viewModel.backToStoryList() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    if (state.showStoryList) {
                        IconButton(onClick = { viewModel.showStreakScreen() }) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Reading Streaks & Achievements"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.showStreakScreen -> {
                    ReadingStreakScreen(
                        readingStats = state.readingStats,
                        currentGoal = state.currentGoal,
                        onBackClick = { viewModel.hideStreakScreen() },
                        onUpdateGoal = { goal -> viewModel.updateReadingGoal(goal) }
                    )
                }
                state.isGenerating -> {
                    StoryGenerationProgress(
                        progress = state.generationProgress,
                        phase = state.generationPhase
                    )
                }
                state.showStoryList -> {
                    StoryListScreen(
                        stories = state.allStories,
                        isLoading = state.isLoading,
                        readingStats = state.readingStats,
                        onCreateNewStory = { request ->
                            viewModel.generateStory(request)
                        },
                        onSelectStory = { storyId ->
                            viewModel.loadStory(storyId)
                        },
                        onDeleteStory = { storyId ->
                            viewModel.deleteStory(storyId)
                        }
                    )
                }
                state.currentStory != null -> {
                    StoryReadingScreen(
                        story = state.currentStory!!,
                        currentPage = state.currentPage,
                        isReadingAloud = state.isReadingAloud,
                        autoReadAloud = state.autoReadAloud,
                        onNextPage = { viewModel.goToNextPage() },
                        onPreviousPage = { viewModel.goToPreviousPage() },
                        onGoToPage = { pageNumber -> viewModel.goToPage(pageNumber) },
                        onCompleteStory = { viewModel.completeStory() },
                        onStartReadingAloud = { viewModel.startReadingAloud() },
                        onStopReadingAloud = { viewModel.stopReadingAloud() },
                        onToggleAutoReadAloud = { viewModel.toggleAutoReadAloud() }
                    )
                }
            }

            // Badge notification overlay
            state.showBadgeNotification?.let { badge ->
                BadgeUnlockedNotification(
                    badge = badge,
                    onDismiss = { viewModel.dismissBadgeNotification() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Error display
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
    }
}

@Composable
fun StoryListScreen(
    stories: List<Story>,
    isLoading: Boolean,
    readingStats: ReadingStats,
    onCreateNewStory: (StoryRequest) -> Unit,
    onSelectStory: (String) -> Unit,
    onDeleteStory: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Reading streak summary card
        if (readingStats.currentStreak > 0 || readingStats.unlockedBadges.isNotEmpty()) {
            ReadingStatsSummaryCard(
                readingStats = readingStats,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Create new story button
        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Story")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (stories.isEmpty()) {
            // Empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No stories yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Create your first story to get started!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Stories list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stories) { story ->
                    StoryCard(
                        story = story,
                        onSelect = { onSelectStory(story.id) },
                        onDelete = { onDeleteStory(story.id) }
                    )
                }
            }
        }
    }

    // Create story dialog
    if (showCreateDialog) {
        CreateStoryDialog(
            onDismiss = { showCreateDialog = false },
            onCreateStory = { request ->
                onCreateNewStory(request)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCard(
    story: Story,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${story.genre.name.lowercase().replace('_', ' ')} • ${story.targetAudience.name.lowercase().replace('_', ' ')}${if (story.hasImages) " • Illustrated" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete story",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (story.currentPage + 1).toFloat() / story.totalPages },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${story.currentPage + 1}/${story.totalPages}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (story.isCompleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Story") },
            text = { Text("Are you sure you want to delete \"${story.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CreateStoryDialog(
    onDismiss: () -> Unit,
    onCreateStory: (StoryRequest) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf(StoryGenre.ADVENTURE) }
    var selectedTarget by remember { mutableStateOf(StoryTarget.ELEMENTARY) }
    var pageCount by remember { mutableFloatStateOf(10f) }
    var characters by remember { mutableStateOf("") }
    var setting by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Story") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 500.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Story Idea") },
                        placeholder = { Text("Describe your story idea...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )
                }

                item {
                    Text("Genre", style = MaterialTheme.typography.labelMedium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(StoryGenre.entries) { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = { selectedGenre = genre },
                                label = { 
                                    Text(
                                        genre.name.lowercase().replace('_', ' '),
                                        fontSize = 12.sp
                                    ) 
                                }
                            )
                        }
                    }
                }

                item {
                    Text("Target Audience", style = MaterialTheme.typography.labelMedium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(StoryTarget.entries) { target ->
                            FilterChip(
                                selected = selectedTarget == target,
                                onClick = { selectedTarget = target },
                                label = { 
                                    Text(
                                        target.name.lowercase().replace('_', ' '),
                                        fontSize = 12.sp
                                    ) 
                                }
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Story Length: ${pageCount.toInt()} pages",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = pageCount,
                            onValueChange = { pageCount = it },
                            valueRange = 3f..20f,
                            steps = 16
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("3 pages", style = MaterialTheme.typography.labelSmall)
                            Text("20 pages", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = characters,
                        onValueChange = { characters = it },
                        label = { Text("Characters (optional)") },
                        placeholder = { Text("e.g., brave knight, wise dragon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = setting,
                        onValueChange = { setting = it },
                        label = { Text("Setting (optional)") },
                        placeholder = { Text("e.g., enchanted forest, space station") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = theme,
                        onValueChange = { theme = it },
                        label = { Text("Theme/Message (optional)") },
                        placeholder = { Text("e.g., friendship, courage, learning") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Convert page count to length enum for backwards compatibility
                    val length = when (pageCount.toInt()) {
                        in 3..6 -> StoryLength.SHORT
                        in 7..13 -> StoryLength.MEDIUM
                        else -> StoryLength.LONG
                    }
                    
                    val request = StoryRequest(
                        prompt = prompt,
                        genre = selectedGenre,
                        targetAudience = selectedTarget,
                        length = length,
                        characters = characters.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        setting = setting,
                        theme = theme,
                        exactPageCount = pageCount.toInt() // Add this field
                    )
                    onCreateStory(request)
                },
                enabled = prompt.isNotBlank()
            ) {
                Text("Create Story")
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
fun StoryGenerationProgress(
    progress: Pair<Int, Int>,
    phase: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "generation")
    val bookRotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "book_rotation"
    )

    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "sparkle_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated writing icon
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background circle with pulse
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
            )

            // Book icon
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .rotate(bookRotation),
                tint = MaterialTheme.colorScheme.primary
            )

            // Sparkles
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = 40.dp)
                    .rotate(sparkleRotation),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = phase,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        
        // Show additional info for image generation phase
        if (phase.contains("visual descriptions", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Creating illustrations for young readers ✨",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (progress.second > 0) {
            Text(
                text = "Page ${progress.first} of ${progress.second}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = { progress.first.toFloat() / progress.second },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun StoryReadingScreen(
    story: Story,
    currentPage: Int,
    isReadingAloud: Boolean = false,
    autoReadAloud: Boolean = false,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onCompleteStory: () -> Unit,
    onStartReadingAloud: () -> Unit = {},
    onStopReadingAloud: () -> Unit = {},
    onToggleAutoReadAloud: () -> Unit = {}
) {
    val currentStoryPage = story.pages.getOrNull(currentPage)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        // Story content with page turn animation
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    // Going forward - slide in from right, slide out to left
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutSlowInEasing
                        )
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutSlowInEasing
                        )
                    )
                } else {
                    // Going backward - slide in from left, slide out to right
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutSlowInEasing
                        )
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "pageTransition"
        ) { pageIndex ->
            StoryPageContent(
                story = story,
                pageIndex = pageIndex,
                isReadingAloud = isReadingAloud,
                onNextPage = onNextPage,
                onPreviousPage = onPreviousPage,
                onCompleteStory = onCompleteStory,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            )
        }

        // Navigation controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp), // Fixed height to prevent size changes
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page number at the start - fixed width container
            Box(
                modifier = Modifier.width(60.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "${currentPage + 1}/${story.totalPages}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // TTS Controls in center
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-read toggle
                FilterChip(
                    selected = autoReadAloud,
                    onClick = onToggleAutoReadAloud,
                    label = { 
                        Text(
                            "Auto-read",
                            fontSize = 12.sp
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            if (autoReadAloud) Icons.Default.Headphones else Icons.Default.HeadsetOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                
                // Play/Stop button
                FloatingActionButton(
                    onClick = {
                        if (isReadingAloud) {
                            onStopReadingAloud()
                        } else {
                            onStartReadingAloud()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (isReadingAloud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (isReadingAloud) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isReadingAloud) "Stop reading" else "Start reading",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Navigation buttons - fixed width container
            Box(
                modifier = Modifier.width(104.dp), // Reduced width for icon-only buttons
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    if (currentPage > 0) {
                        IconButton(
                            onClick = onPreviousPage,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondary,
                                    CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Previous page",
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }

                    // Next button or Completion button
                    if (currentPage < story.totalPages - 1) {
                        IconButton(
                            onClick = onNextPage,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward, 
                                contentDescription = "Next page",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else if (!story.isCompleted) {
                        // Show completion button on the last page if story is not completed
                        IconButton(
                            onClick = onCompleteStory,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondary,
                                    CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle, 
                                contentDescription = "Complete story",
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { /* Could show completion celebration */ },
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Done, 
                                contentDescription = "Story finished",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoryPageContent(
    story: Story,
    pageIndex: Int,
    isReadingAloud: Boolean,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onCompleteStory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStoryPage = story.pages.getOrNull(pageIndex)
    
    // Parallax scrolling state
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    
    // Character animation state
    val infiniteTransition = rememberInfiniteTransition(label = "characterAnimation")
    val characterFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "characterFloat"
    )
    
    Card(
        modifier = modifier
            .pointerInput(pageIndex, story.totalPages) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                    },
                    onDragEnd = {
                        // Swipe left (negative drag) = next page
                        // Swipe right (positive drag) = previous page
                        val threshold = 100f // Minimum swipe distance
                        if (totalDrag < -threshold && pageIndex < story.totalPages - 1) {
                            onNextPage()
                        } else if (totalDrag > threshold && pageIndex > 0) {
                            onPreviousPage()
                        }
                    }
                ) { _, dragAmount ->
                    totalDrag += dragAmount
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (currentStoryPage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                // Display image with zoom and character animation
                currentStoryPage.imageUrl?.let { imageUrl ->
                    ZoomableImage(
                        imageUrl = imageUrl,
                        contentDescription = currentStoryPage.imageDescription ?: "Story illustration for page ${pageIndex + 1}",
                        characterAnimation = characterFloat,
                        parallaxOffset = with(density) { scrollState.value.toDp() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Animated title
                currentStoryPage.title?.let { title ->
                    AnimatedTitle(
                        title = title,
                        isReadingAloud = isReadingAloud
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Reading indicator
                if (isReadingAloud) {
                    ReadingIndicator()
                }
                
                // Animated story content with parallax effect
                AnimatedStoryText(
                    content = currentStoryPage.content,
                    isReadingAloud = isReadingAloud,
                    scrollOffset = scrollState.value
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Page not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ZoomableImage(
    imageUrl: String,
    contentDescription: String,
    characterAnimation: Float,
    parallaxOffset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .graphicsLayer {
                // Parallax effect
                translationY = -parallaxOffset.toPx() * 0.5f
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + characterAnimation // Character floating animation
                }
                .pointerInput(Unit) {
                    detectTransformGestures(
                        onGesture = { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 3f)
                            if (scale > 1f) {
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(
                                        -(size.width * (scale - 1f) / 2f),
                                        size.width * (scale - 1f) / 2f
                                    ),
                                    y = (offset.y + pan.y).coerceIn(
                                        -(size.height * (scale - 1f) / 2f),
                                        size.height * (scale - 1f) / 2f
                                    )
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    )
                },
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun AnimatedTitle(
    title: String,
    isReadingAloud: Boolean,
    modifier: Modifier = Modifier
) {
    val titleScale by animateFloatAsState(
        targetValue = if (isReadingAloud) 1.05f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "titleScale"
    )
    
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = if (isReadingAloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .scale(titleScale)
    )
}

@Composable
fun ReadingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "readingIndicator")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "readingAlpha"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(bottom = 8.dp)
            .graphicsLayer {
                this.alpha = alpha
            }
    ) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Reading aloud...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AnimatedStoryText(
    content: String,
    isReadingAloud: Boolean,
    scrollOffset: Int,
    modifier: Modifier = Modifier
) {
    val textColor by animateColorAsState(
        targetValue = if (isReadingAloud) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "textColor"
    )
    
    Text(
        text = content,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = textColor
        ),
        lineHeight = 28.sp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Subtle parallax effect for text
                translationY = -scrollOffset * 0.1f
            }
    )
}

@Composable
fun ReadingStatsSummaryCard(
    readingStats: ReadingStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current streak
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Rocket,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "${readingStats.currentStreak}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "day streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Stories read
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "${readingStats.totalStoriesRead}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "stories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Latest badge
            if (readingStats.unlockedBadges.isNotEmpty()) {
                val latestBadge = readingStats.unlockedBadges.maxByOrNull { it.unlockedAt ?: 0 }
                if (latestBadge != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            latestBadge.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "latest badge",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingStreakScreen(
    readingStats: ReadingStats,
    currentGoal: ReadingGoal,
    onBackClick: () -> Unit,
    onUpdateGoal: (ReadingGoal) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Reading Streaks & Achievements",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current streak highlight
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Rocket,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${readingStats.currentStreak}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Day Reading Streak",
                    style = MaterialTheme.typography.titleMedium
                )
                if (readingStats.longestStreak > readingStats.currentStreak) {
                    Text(
                        "Best: ${readingStats.longestStreak} days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reading statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Stories",
                value = "${readingStats.totalStoriesRead}",
                icon = Icons.Default.MenuBook,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Pages",
                value = "${readingStats.totalPagesRead}",
                icon = Icons.Default.Description,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Hours",
                value = "${readingStats.totalTimeMinutes / 60}",
                icon = Icons.Default.Schedule,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Daily goal section
        Text(
            "Daily Reading Goal",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        DailyGoalCard(
            goal = currentGoal,
            onUpdateGoal = onUpdateGoal
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Achievement badges
        Text(
            "Achievement Badges",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (readingStats.unlockedBadges.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(readingStats.unlockedBadges) { badge ->
                    BadgeCard(badge = badge, isUnlocked = true)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    "No badges unlocked yet. Keep reading to earn your first badge!",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Next badge to unlock
        readingStats.nextBadge?.let { nextBadge ->
            Text(
                "Next Badge",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            BadgeCard(badge = nextBadge, isUnlocked = false)
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DailyGoalCard(
    goal: ReadingGoal,
    onUpdateGoal: (ReadingGoal) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Current Goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit goal")
                }
            }
            
            Text(
                "Read ${goal.dailyPagesGoal} pages OR ${goal.dailyTimeGoalMinutes} minutes daily",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (showEditDialog) {
        EditGoalDialog(
            currentGoal = goal,
            onDismiss = { showEditDialog = false },
            onSaveGoal = { newGoal ->
                onUpdateGoal(newGoal)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun BadgeCard(
    badge: AchievementBadge,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) 
                MaterialTheme.colorScheme.tertiaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isUnlocked) 
                    MaterialTheme.colorScheme.onTertiaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                badge.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isUnlocked) 
                    MaterialTheme.colorScheme.onTertiaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                badge.description,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isUnlocked) 
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun BadgeUnlockedNotification(
    badge: AchievementBadge,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated badge icon
            val infiniteTransition = rememberInfiniteTransition(label = "badge_celebration")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "badge_scale"
            )
            
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Badge Unlocked!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                badge.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                badge.description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Awesome!")
            }
        }
    }
}

@Composable
fun EditGoalDialog(
    currentGoal: ReadingGoal,
    onDismiss: () -> Unit,
    onSaveGoal: (ReadingGoal) -> Unit
) {
    var pagesGoal by remember { mutableFloatStateOf(currentGoal.dailyPagesGoal.toFloat()) }
    var timeGoal by remember { mutableFloatStateOf(currentGoal.dailyTimeGoalMinutes.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Daily Reading Goal") },
        text = {
            Column {
                Text(
                    "Pages Goal: ${pagesGoal.toInt()} pages",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = pagesGoal,
                    onValueChange = { pagesGoal = it },
                    valueRange = 1f..20f,
                    steps = 18
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Time Goal: ${timeGoal.toInt()} minutes",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = timeGoal,
                    onValueChange = { timeGoal = it },
                    valueRange = 5f..60f,
                    steps = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newGoal = currentGoal.copy(
                        dailyPagesGoal = pagesGoal.toInt(),
                        dailyTimeGoalMinutes = timeGoal.toInt(),
                        lastUpdated = System.currentTimeMillis()
                    )
                    onSaveGoal(newGoal)
                }
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