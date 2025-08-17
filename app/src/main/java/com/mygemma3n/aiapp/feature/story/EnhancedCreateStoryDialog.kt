package com.mygemma3n.aiapp.feature.story

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedCreateStoryDialog(
    onDismiss: () -> Unit,
    onCreateStory: (StoryRequest) -> Unit,
    viewModel: CreateStoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // Reset to initial state when dialog is opened
    LaunchedEffect(Unit) {
        viewModel.resetToInitialState()
    }
    
    var currentTab by remember { mutableIntStateOf(0) }
    var showCharacterCreator by remember { mutableStateOf(false) }
    val tabs = listOf("Characters", "Template", "Mood", "Details")
    
    // If character creator is shown, don't show the main dialog
    if (showCharacterCreator) {
        CharacterCreatorScreen(
            onBackClick = { showCharacterCreator = false },
            onCharacterCreated = { character ->
                // Character created successfully, close creator and refresh list
                showCharacterCreator = false
                viewModel.refreshCharacters()
            },
            forceCreationMode = false // Allow full navigation in dialog mode
        )
        return
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.8f),
        title = { 
            Text("Create New Story") 
        },
        text = {
            Column {
                // Quick Status Summary
                QuickStartSummary(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Tab Row
                TabRow(
                    selectedTabIndex = currentTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            text = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tab Content
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> CharactersTab(
                            state = state,
                            onUpdateState = viewModel::updateState,
                            onOpenCharacterCreator = { showCharacterCreator = true }
                        )
                        1 -> TemplateTab(
                            state = state,
                            onUpdateState = viewModel::updateState
                        )
                        2 -> MoodTab(
                            state = state,
                            onUpdateState = viewModel::updateState,
                            onDetectMood = viewModel::detectCurrentMood
                        )
                        3 -> BasicStoryTab(
                            state = state,
                            onUpdateState = viewModel::updateState
                        )
                    }
                }
            }
        },
        confirmButton = {
            val canCreateStory = state.prompt.isNotBlank() || 
                                (state.selectedCharacters.isNotEmpty() && state.selectedTemplate != null)
            
            Button(
                onClick = {
                    val request = viewModel.buildStoryRequest()
                    onCreateStory(request)
                },
                enabled = canCreateStory
            ) {
                val buttonText = when {
                    state.selectedCharacters.isNotEmpty() && state.selectedTemplate != null -> "Create Story"
                    state.prompt.isNotBlank() -> "Create Story"
                    else -> "Choose Character & Template"
                }
                Text(buttonText)
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
fun BasicStoryTab(
    state: CreateStoryState,
    onUpdateState: (CreateStoryState) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { onUpdateState(state.copy(prompt = it)) },
                label = { Text("Story Idea (optional)") },
                placeholder = { Text("Add custom story details, or use Character + Template for quick start...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            
            // Show helpful hint when characters and template are selected
            if (state.selectedCharacters.isNotEmpty() && state.selectedTemplate != null) {
                Text(
                    "✨ Ready to create! Your story will use the selected characters and template. Add custom details above for extra personalization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        item {
            Text("Genre", style = MaterialTheme.typography.labelMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(StoryGenre.entries) { genre ->
                    FilterChip(
                        selected = state.selectedGenre == genre,
                        onClick = { onUpdateState(state.copy(selectedGenre = genre)) },
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
                        selected = state.selectedTarget == target,
                        onClick = { onUpdateState(state.copy(selectedTarget = target)) },
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
            Column {
                Text(
                    "Story Length: ${state.pageCount.toInt()} pages",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = state.pageCount,
                    onValueChange = { onUpdateState(state.copy(pageCount = it)) },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.setting,
                    onValueChange = { onUpdateState(state.copy(setting = it)) },
                    label = { Text("Setting (optional)") },
                    placeholder = { Text("Where does it take place?") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = state.theme,
                    onValueChange = { onUpdateState(state.copy(theme = it)) },
                    label = { Text("Theme (optional)") },
                    placeholder = { Text("Main message") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun CharactersTab(
    state: CreateStoryState,
    onUpdateState: (CreateStoryState) -> Unit,
    onOpenCharacterCreator: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Custom Characters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedButton(
                    onClick = onOpenCharacterCreator,
                    modifier = Modifier.size(width = 120.dp, height = 36.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create", fontSize = 12.sp)
                }
            }
        }
        
        if (state.availableCharacters.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No custom characters yet",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Create characters to use in your stories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(state.availableCharacters) { character ->
                CharacterSelectionCard(
                    character = character,
                    isSelected = state.selectedCharacters.contains(character),
                    onToggleSelection = { isSelected ->
                        val newSelection = if (isSelected) {
                            state.selectedCharacters + character
                        } else {
                            state.selectedCharacters - character
                        }
                        onUpdateState(state.copy(selectedCharacters = newSelection))
                    }
                )
            }
        }
        
        item {
            OutlinedTextField(
                value = state.basicCharacters,
                onValueChange = { onUpdateState(state.copy(basicCharacters = it)) },
                label = { Text("Additional Characters (optional)") },
                placeholder = { Text("brave knight, wise owl, funny dragon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSelectionCard(
    character: Character,
    isSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit
) {
    Card(
        onClick = { onToggleSelection(!isSelected) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggleSelection
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = character.getPhysicalDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (character.personalityTraits.isNotEmpty()) {
                    Text(
                        text = character.personalityTraits.take(2).joinToString(", ") { 
                            it.name.lowercase().replace('_', ' ') 
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun TemplateTab(
    state: CreateStoryState,
    onUpdateState: (CreateStoryState) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Story Templates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                FilterChip(
                    selected = state.selectedTemplate == null,
                    onClick = { onUpdateState(state.copy(selectedTemplate = null)) },
                    label = { Text("Free Form", fontSize = 12.sp) }
                )
            }
        }
        
        if (state.availableTemplates.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "Loading templates...",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(state.availableTemplates) { template ->
                TemplateSelectionCard(
                    template = template,
                    isSelected = state.selectedTemplate?.id == template.id,
                    onSelect = { 
                        onUpdateState(state.copy(selectedTemplate = template))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateSelectionCard(
    template: Template,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    getTemplateIcon(template.type),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            "${template.estimatedPages} pages",
                            fontSize = 10.sp
                        ) 
                    }
                )
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            template.genre.name.lowercase().replace('_', ' '),
                            fontSize = 10.sp
                        ) 
                    }
                )
            }
        }
    }
}

@Composable
fun MoodTab(
    state: CreateStoryState,
    onUpdateState: (CreateStoryState) -> Unit,
    onDetectMood: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reading Mood",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedButton(
                    onClick = onDetectMood,
                    modifier = Modifier.size(width = 120.dp, height = 36.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Detect", fontSize = 12.sp)
                }
            }
        }
        
        if (state.detectedMood != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Detected Mood: ${state.detectedMood.primaryMood.name.lowercase().replace('_', ' ')}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            state.detectedMood.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        item {
            Text("Choose Your Mood", style = MaterialTheme.typography.labelMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(ReadingMood.entries) { mood ->
                    FilterChip(
                        selected = state.selectedMood == mood,
                        onClick = { onUpdateState(state.copy(selectedMood = mood)) },
                        label = { 
                            Text(
                                mood.name.lowercase().replace('_', ' '),
                                fontSize = 11.sp
                            ) 
                        },
                        leadingIcon = {
                            Icon(
                                getMoodIcon(mood),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
        
        if (state.selectedMood != null || state.detectedMood != null) {
            item {
                val activeMood = state.selectedMood ?: state.detectedMood?.primaryMood
                activeMood?.let { mood ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                "Recommendations for ${mood.name.lowercase().replace('_', ' ')} mood:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                getMoodRecommendationText(mood),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getTemplateIcon(type: TemplateType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        TemplateType.HEROS_JOURNEY -> Icons.Default.Person
        TemplateType.MYSTERY -> Icons.Default.Search
        TemplateType.FRIENDSHIP -> Icons.Default.Favorite
        TemplateType.ADVENTURE -> Icons.Default.Explore
        TemplateType.SLICE_OF_LIFE -> Icons.Default.Home
        TemplateType.FAIRY_TALE -> Icons.Default.AutoAwesome
        TemplateType.SCIENCE_FICTION -> Icons.Default.Rocket
        TemplateType.COMING_OF_AGE -> Icons.Default.School
        TemplateType.ANIMAL_ADVENTURE -> Icons.Default.Pets
        TemplateType.FAMILY_DRAMA -> Icons.Default.FamilyRestroom
        TemplateType.FOLKLORE -> Icons.Default.MenuBook
        TemplateType.SPACE_ADVENTURE -> Icons.Default.Rocket
    }
}

fun getMoodIcon(mood: ReadingMood): androidx.compose.ui.graphics.vector.ImageVector {
    return when (mood) {
        ReadingMood.ENERGETIC -> Icons.Default.Bolt
        ReadingMood.CALM -> Icons.Default.Spa
        ReadingMood.CURIOUS -> Icons.Default.Search
        ReadingMood.CREATIVE -> Icons.Default.Brush
        ReadingMood.SOCIAL -> Icons.Default.Group
        ReadingMood.CONTEMPLATIVE -> Icons.Default.Psychology
        ReadingMood.PLAYFUL -> Icons.Default.SentimentSatisfied
        ReadingMood.COMFORT -> Icons.Default.Home
        ReadingMood.CHALLENGE -> Icons.AutoMirrored.Default.TrendingUp
    }
}

fun getMoodRecommendationText(mood: ReadingMood): String {
    return when (mood) {
        ReadingMood.ENERGETIC -> "Perfect for action-packed adventures and exciting journeys"
        ReadingMood.CALM -> "Great for gentle, peaceful stories that soothe and relax"
        ReadingMood.CURIOUS -> "Ideal for mysteries and educational content that sparks learning"
        ReadingMood.CREATIVE -> "Wonderful for imaginative fantasy and creative storytelling"
        ReadingMood.SOCIAL -> "Perfect for friendship and family stories that connect hearts"
        ReadingMood.CONTEMPLATIVE -> "Great for thoughtful stories that inspire reflection"
        ReadingMood.PLAYFUL -> "Ideal for funny, lighthearted tales that bring joy"
        ReadingMood.COMFORT -> "Perfect for familiar, comforting stories that feel like home"
        ReadingMood.CHALLENGE -> "Great for complex, thought-provoking narratives"
    }
}

@Composable
fun QuickStartSummary(
    state: CreateStoryState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when {
                state.selectedCharacters.isNotEmpty() && state.selectedTemplate != null -> 
                    MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                "Quick Start Progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Characters status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.selectedCharacters.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (state.selectedCharacters.isNotEmpty()) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (state.selectedCharacters.isNotEmpty()) 
                            "${state.selectedCharacters.size} character${if (state.selectedCharacters.size > 1) "s" else ""}"
                        else 
                            "Choose characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.selectedCharacters.isNotEmpty()) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Template status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.selectedTemplate != null) Icons.Default.CheckCircle else Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (state.selectedTemplate != null) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        state.selectedTemplate?.name ?: "Choose template",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.selectedTemplate != null) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            
            // Show ready status
            if (state.selectedCharacters.isNotEmpty() && state.selectedTemplate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Ready to create! Add mood or details for extra personalization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}