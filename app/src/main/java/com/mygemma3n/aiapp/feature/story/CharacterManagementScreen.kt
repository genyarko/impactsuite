package com.mygemma3n.aiapp.feature.story

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler

enum class CharacterManagementState {
    LIST,    // Showing character list
    CREATOR  // Showing character creator
}

@Composable
fun CharacterManagementWithNavigation(
    onBackClick: () -> Unit
) {
    var managementState by remember { mutableStateOf(CharacterManagementState.LIST) }
    
    // Handle back button navigation within character management
    BackHandler(enabled = managementState == CharacterManagementState.CREATOR) {
        managementState = CharacterManagementState.LIST
    }
    
    when (managementState) {
        CharacterManagementState.LIST -> {
            CharacterManagementScreen(
                onBackClick = onBackClick,
                onCreateCharacter = { managementState = CharacterManagementState.CREATOR }
            )
        }
        CharacterManagementState.CREATOR -> {
            CharacterCreatorScreen(
                onBackClick = { managementState = CharacterManagementState.LIST },
                onCharacterCreated = { character ->
                    // Character created successfully, go back to list
                    managementState = CharacterManagementState.LIST
                },
                forceCreationMode = true // Force creation mode in management flow
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterManagementScreen(
    onBackClick: () -> Unit,
    onCreateCharacter: () -> Unit,
    viewModel: CharacterCreatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Characters") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Create new character button
            Button(
                onClick = onCreateCharacter,
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
            
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.existingCharacters.isEmpty()) {
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
                    items(state.existingCharacters) { character ->
                        CharacterManagementCard(
                            character = character,
                            onEdit = { 
                                viewModel.editCharacter(character)
                                onCreateCharacter() // Navigate to creator in edit mode
                            },
                            onDelete = { viewModel.deleteCharacter(character) }
                        )
                    }
                }
            }
            
            // Error handling
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterManagementCard(
    character: Character,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
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
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = character.getPhysicalDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = character.getPersonalityDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
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
                            style = MaterialTheme.typography.labelSmall
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