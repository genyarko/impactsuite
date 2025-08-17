package com.mygemma3n.aiapp.feature.story

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCreatorScreen(
    onBackClick: () -> Unit,
    onCharacterCreated: (Character) -> Unit,
    forceCreationMode: Boolean = true, // Force creation mode when called from management screen
    viewModel: CharacterCreatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // Handle mode based on forceCreationMode parameter
    LaunchedEffect(forceCreationMode) {
        if (forceCreationMode && state.showCharacterList) {
            // Force creation mode (from management screen)
            viewModel.startCreatingNewCharacter()
        } else if (!forceCreationMode && !state.showCharacterList) {
            // Ensure we show character list first (from dialog)
            viewModel.showCharacterList()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Character" else "Create Character") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.currentCharacter.name.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.saveCharacter { character ->
                                    onCharacterCreated(character)
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.showCharacterList && !forceCreationMode) {
                CharacterListTab(
                    characters = state.existingCharacters,
                    onCreateNew = { viewModel.startCreatingNewCharacter() },
                    onEditCharacter = { character -> viewModel.editCharacter(character) },
                    onSelectCharacter = { character -> onCharacterCreated(character) },
                    onDeleteCharacter = { character -> viewModel.deleteCharacter(character) }
                )
            } else {
                CharacterCreatorTab(
                    character = state.currentCharacter,
                    onUpdateCharacter = { updatedCharacter -> viewModel.updateCurrentCharacter(updatedCharacter) },
                    onShowCharacterList = { if (forceCreationMode) null else { viewModel.showCharacterList() } }
                )
            }
        }
    }
}

@Composable
fun CharacterListTab(
    characters: List<Character>,
    onCreateNew: () -> Unit,
    onEditCharacter: (Character) -> Unit,
    onSelectCharacter: (Character) -> Unit,
    onDeleteCharacter: (Character) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Create new character button
        Button(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Character")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (characters.isEmpty()) {
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
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No characters yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Create your first character to use in stories!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Characters list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(characters) { character ->
                    CharacterCard(
                        character = character,
                        onEdit = { onEditCharacter(character) },
                        onSelect = { onSelectCharacter(character) },
                        onDelete = { onDeleteCharacter(character) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCard(
    character: Character,
    onEdit: () -> Unit,
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
                        text = character.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = character.getPhysicalDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = character.getPersonalityDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit character",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete character",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Character role badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            character.characterRole.name.lowercase().replace('_', ' '),
                            fontSize = 12.sp
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            getCharacterRoleIcon(character.characterRole),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                if (character.useCount > 0) {
                    Text(
                        "Used ${character.useCount} times",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Character") },
            text = { Text("Are you sure you want to delete \"${character.name}\"? This action cannot be undone.") },
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
fun CharacterCreatorTab(
    character: Character,
    onUpdateCharacter: (Character) -> Unit,
    onShowCharacterList: (() -> Unit)? = null // Null means don't show the button
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with character preview
        CharacterPreviewCard(character = character)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Basic Information Section
        CharacterBasicInfoSection(
            character = character,
            onUpdateCharacter = onUpdateCharacter
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Appearance Section
        CharacterAppearanceSection(
            character = character,
            onUpdateCharacter = onUpdateCharacter
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Personality Section
        CharacterPersonalitySection(
            character = character,
            onUpdateCharacter = onUpdateCharacter
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Background Section
        CharacterBackgroundSection(
            character = character,
            onUpdateCharacter = onUpdateCharacter
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Action buttons
        if (onShowCharacterList != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShowCharacterList,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View All Characters")
                }
                
                Button(
                    onClick = { /* Preview in story */ },
                    modifier = Modifier.weight(1f),
                    enabled = character.name.isNotBlank()
                ) {
                    Text("Preview in Story")
                }
            }
        }
    }
}

@Composable
fun CharacterPreviewCard(character: Character) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Character avatar placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = character.name.ifBlank { "New Character" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (character.name.isNotBlank()) {
                Text(
                    text = character.getPhysicalDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun CharacterBasicInfoSection(
    character: Character,
    onUpdateCharacter: (Character) -> Unit
) {
    SectionCard(title = "Basic Information") {
        Column {
            OutlinedTextField(
                value = character.name,
                onValueChange = { onUpdateCharacter(character.copy(name = it)) },
                label = { Text("Character Name") },
                placeholder = { Text("Enter character name...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gender selection
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gender", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(Gender.entries) { gender ->
                            FilterChip(
                                selected = character.gender == gender,
                                onClick = { onUpdateCharacter(character.copy(gender = gender)) },
                                label = { 
                                    Text(
                                        gender.name.lowercase(),
                                        fontSize = 11.sp
                                    ) 
                                }
                            )
                        }
                    }
                }
                
                // Age group selection
                Column(modifier = Modifier.weight(1f)) {
                    Text("Age Group", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(AgeGroup.entries) { age ->
                            FilterChip(
                                selected = character.ageGroup == age,
                                onClick = { onUpdateCharacter(character.copy(ageGroup = age)) },
                                label = { 
                                    Text(
                                        age.name.lowercase().replace('_', ' '),
                                        fontSize = 11.sp
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Character role
            Text("Character Role", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(CharacterRole.entries) { role ->
                    FilterChip(
                        selected = character.characterRole == role,
                        onClick = { onUpdateCharacter(character.copy(characterRole = role)) },
                        label = { 
                            Text(
                                role.name.lowercase().replace('_', ' '),
                                fontSize = 11.sp
                            ) 
                        },
                        leadingIcon = {
                            Icon(
                                getCharacterRoleIcon(role),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CharacterAppearanceSection(
    character: Character,
    onUpdateCharacter: (Character) -> Unit
) {
    SectionCard(title = "Appearance") {
        Column {
            // Hair and Eyes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hair Color", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(HairColor.entries) { hair ->
                            FilterChip(
                                selected = character.hairColor == hair,
                                onClick = { onUpdateCharacter(character.copy(hairColor = hair)) },
                                label = { 
                                    Text(
                                        hair.name.lowercase(),
                                        fontSize = 11.sp
                                    ) 
                                }
                            )
                        }
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Eye Color", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(EyeColor.entries) { eye ->
                            FilterChip(
                                selected = character.eyeColor == eye,
                                onClick = { onUpdateCharacter(character.copy(eyeColor = eye)) },
                                label = { 
                                    Text(
                                        eye.name.lowercase(),
                                        fontSize = 11.sp
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Skin tone and body type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Skin Tone", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(SkinTone.entries) { skin ->
                            FilterChip(
                                selected = character.skinTone == skin,
                                onClick = { onUpdateCharacter(character.copy(skinTone = skin)) },
                                label = { 
                                    Text(
                                        skin.name.lowercase().replace('_', ' '),
                                        fontSize = 11.sp
                                    ) 
                                }
                            )
                        }
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Body Type", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(BodyType.entries) { body ->
                            FilterChip(
                                selected = character.bodyType == body,
                                onClick = { onUpdateCharacter(character.copy(bodyType = body)) },
                                label = { 
                                    Text(
                                        body.name.lowercase(),
                                        fontSize = 11.sp
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterPersonalitySection(
    character: Character,
    onUpdateCharacter: (Character) -> Unit
) {
    SectionCard(title = "Personality & Abilities") {
        Column {
            // Personality traits
            Text("Personality Traits", style = MaterialTheme.typography.labelMedium)
            Text(
                "Choose up to 4 traits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(120.dp)
            ) {
                items(PersonalityTrait.entries) { trait ->
                    val isSelected = character.personalityTraits.contains(trait)
                    val canSelect = character.personalityTraits.size < 4 || isSelected
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newTraits = if (isSelected) {
                                character.personalityTraits - trait
                            } else if (canSelect) {
                                character.personalityTraits + trait
                            } else {
                                character.personalityTraits
                            }
                            onUpdateCharacter(character.copy(personalityTraits = newTraits))
                        },
                        label = { 
                            Text(
                                trait.name.lowercase().replace('_', ' '),
                                fontSize = 10.sp
                            ) 
                        },
                        enabled = canSelect
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Special abilities
            Text("Special Abilities", style = MaterialTheme.typography.labelMedium)
            Text(
                "Choose up to 2 abilities",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(100.dp)
            ) {
                items(SpecialAbility.entries) { ability ->
                    val isSelected = character.specialAbilities.contains(ability)
                    val canSelect = character.specialAbilities.size < 2 || isSelected
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newAbilities = if (isSelected) {
                                character.specialAbilities - ability
                            } else if (canSelect) {
                                character.specialAbilities + ability
                            } else {
                                character.specialAbilities
                            }
                            onUpdateCharacter(character.copy(specialAbilities = newAbilities))
                        },
                        label = { 
                            Text(
                                ability.name.lowercase().replace('_', ' '),
                                fontSize = 10.sp
                            ) 
                        },
                        enabled = canSelect
                    )
                }
            }
        }
    }
}

@Composable
fun CharacterBackgroundSection(
    character: Character,
    onUpdateCharacter: (Character) -> Unit
) {
    SectionCard(title = "Background & Story") {
        Column {
            OutlinedTextField(
                value = character.backstory,
                onValueChange = { onUpdateCharacter(character.copy(backstory = it)) },
                label = { Text("Backstory") },
                placeholder = { Text("What's their background and history?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = character.occupation,
                    onValueChange = { onUpdateCharacter(character.copy(occupation = it)) },
                    label = { Text("Occupation") },
                    placeholder = { Text("What do they do?") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = character.homeland,
                    onValueChange = { onUpdateCharacter(character.copy(homeland = it)) },
                    label = { Text("Homeland") },
                    placeholder = { Text("Where are they from?") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = character.goals,
                onValueChange = { onUpdateCharacter(character.copy(goals = it)) },
                label = { Text("Goals & Motivations") },
                placeholder = { Text("What drives them?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = character.favoriteThings.joinToString(", "),
                    onValueChange = { 
                        val favorites = it.split(",").map { item -> item.trim() }.filter { item -> item.isNotEmpty() }
                        onUpdateCharacter(character.copy(favoriteThings = favorites))
                    },
                    label = { Text("Favorite Things") },
                    placeholder = { Text("books, music, cats") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = character.fears.joinToString(", "),
                    onValueChange = { 
                        val fears = it.split(",").map { item -> item.trim() }.filter { item -> item.isNotEmpty() }
                        onUpdateCharacter(character.copy(fears = fears))
                    },
                    label = { Text("Fears") },
                    placeholder = { Text("spiders, heights") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = character.catchphrase,
                onValueChange = { onUpdateCharacter(character.copy(catchphrase = it)) },
                label = { Text("Catchphrase (Optional)") },
                placeholder = { Text("Something they always say...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

fun getCharacterRoleIcon(role: CharacterRole): ImageVector {
    return when (role) {
        CharacterRole.PROTAGONIST -> Icons.Default.Star
        CharacterRole.SIDEKICK -> Icons.Default.Person
        CharacterRole.MENTOR -> Icons.Default.School
        CharacterRole.ANTAGONIST -> Icons.Default.Block
        CharacterRole.COMIC_RELIEF -> Icons.Default.SentimentSatisfied
        CharacterRole.WISE_GUIDE -> Icons.Default.Psychology
        CharacterRole.HELPER -> Icons.Default.Handshake
        CharacterRole.CHALLENGER -> Icons.Default.FlashOn
        CharacterRole.LOVE_INTEREST -> Icons.Default.Favorite
        CharacterRole.MYSTERIOUS_STRANGER -> Icons.Default.QuestionMark
        CharacterRole.TRICKSTER -> Icons.Default.Psychology
        CharacterRole.HERO -> Icons.Default.Shield
        CharacterRole.GUARDIAN -> Icons.Default.Security
        CharacterRole.PROTECTOR -> Icons.Default.Shield
    }
}