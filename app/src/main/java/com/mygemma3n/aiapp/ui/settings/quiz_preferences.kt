package com.mygemma3n.aiapp.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Preferences data class
data class QuizPreferences(
    val textSize: TextSize = TextSize.MEDIUM,
    val animationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val highContrastMode: Boolean = false,
    val showHints: Boolean = true,
    val autoAdvanceQuestions: Boolean = false,
    val showExplanationsImmediately: Boolean = false,
    val questionTimeLimit: QuestionTimeLimit = QuestionTimeLimit.NONE,
    val theme: AppTheme = AppTheme.SYSTEM
)

enum class TextSize(val scale: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f)
}

enum class QuestionTimeLimit(val seconds: Int?) {
    NONE(null),
    THIRTY_SECONDS(30),
    ONE_MINUTE(60),
    TWO_MINUTES(120),
    FIVE_MINUTES(300)
}

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

// Preferences repository
@Singleton
class QuizPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val SHOW_HINTS = booleanPreferencesKey("show_hints")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        val SHOW_EXPLANATIONS = booleanPreferencesKey("show_explanations")
        val TIME_LIMIT = stringPreferencesKey("time_limit")
        val THEME = stringPreferencesKey("theme")
    }

    val preferences: Flow<QuizPreferences> = dataStore.data
        .catch { exception ->
            emit(emptyPreferences())
        }
        .map { preferences ->
            QuizPreferences(
                textSize = TextSize.valueOf(
                    preferences[PreferencesKeys.TEXT_SIZE] ?: TextSize.MEDIUM.name
                ),
                animationsEnabled = preferences[PreferencesKeys.ANIMATIONS_ENABLED] ?: true,
                soundEnabled = preferences[PreferencesKeys.SOUND_ENABLED] ?: true,
                hapticFeedbackEnabled = preferences[PreferencesKeys.HAPTIC_ENABLED] ?: true,
                highContrastMode = preferences[PreferencesKeys.HIGH_CONTRAST] ?: false,
                showHints = preferences[PreferencesKeys.SHOW_HINTS] ?: true,
                autoAdvanceQuestions = preferences[PreferencesKeys.AUTO_ADVANCE] ?: false,
                showExplanationsImmediately = preferences[PreferencesKeys.SHOW_EXPLANATIONS] ?: false,
                questionTimeLimit = QuestionTimeLimit.valueOf(
                    preferences[PreferencesKeys.TIME_LIMIT] ?: QuestionTimeLimit.NONE.name
                ),
                theme = AppTheme.valueOf(
                    preferences[PreferencesKeys.THEME] ?: AppTheme.SYSTEM.name
                )
            )
        }

    suspend fun updateTextSize(textSize: TextSize) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEXT_SIZE] = textSize.name
        }
    }

    suspend fun updateAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANIMATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun updateHighContrastMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_CONTRAST] = enabled
        }
    }

    suspend fun updateShowHints(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_HINTS] = enabled
        }
    }

    suspend fun updateAutoAdvance(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_ADVANCE] = enabled
        }
    }

    suspend fun updateShowExplanations(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_EXPLANATIONS] = enabled
        }
    }

    suspend fun updateTimeLimit(timeLimit: QuestionTimeLimit) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIME_LIMIT] = timeLimit.name
        }
    }

    suspend fun updateTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme.name
        }
    }
}

// ViewModel
@HiltViewModel
class QuizSettingsViewModel @Inject constructor(
    private val preferencesRepository: QuizPreferencesRepository
) : ViewModel() {
    val preferences = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = QuizPreferences()
        )

    fun updateTextSize(textSize: TextSize) {
        viewModelScope.launch {
            preferencesRepository.updateTextSize(textSize)
        }
    }

    fun updateAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAnimationsEnabled(enabled)
        }
    }

    fun updateSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSoundEnabled(enabled)
        }
    }

    fun updateHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHapticEnabled(enabled)
        }
    }

    fun updateHighContrastMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHighContrastMode(enabled)
        }
    }

    fun updateShowHints(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateShowHints(enabled)
        }
    }

    fun updateAutoAdvance(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAutoAdvance(enabled)
        }
    }

    fun updateShowExplanations(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateShowExplanations(enabled)
        }
    }

    fun updateTimeLimit(timeLimit: QuestionTimeLimit) {
        viewModelScope.launch {
            preferencesRepository.updateTimeLimit(timeLimit)
        }
    }

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferencesRepository.updateTheme(theme)
        }
    }
}

// Settings Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuizSettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {

            // Display Settings
            item {
                SettingsSection(title = "Display") {
                    TextSizeSetting(
                        currentSize = preferences.textSize,
                        onSizeChange = viewModel::updateTextSize
                    )

                    SwitchSetting(
                        title = "High Contrast Mode",
                        description = "Increase color contrast for better visibility",
                        icon = Icons.Default.Contrast,
                        checked = preferences.highContrastMode,
                        onCheckedChange = viewModel::updateHighContrastMode
                    )

                    SwitchSetting(
                        title = "Reduce Animations",
                        description = "Minimize motion for users sensitive to animations",
                        icon = Icons.Default.Animation,
                        checked = !preferences.animationsEnabled,
                        onCheckedChange = { viewModel.updateAnimationsEnabled(!it) }
                    )
                }
            }

            // Quiz Behavior Settings
            item {
                SettingsSection(title = "Quiz Behavior") {
                    SwitchSetting(
                        title = "Show Hints",
                        description = "Display hint button when available",
                        icon = Icons.Default.Lightbulb,
                        checked = preferences.showHints,
                        onCheckedChange = viewModel::updateShowHints
                    )

                    SwitchSetting(
                        title = "Auto-advance Questions",
                        description = "Automatically move to next question after answering",
                        icon = Icons.Default.SkipNext,
                        checked = preferences.autoAdvanceQuestions,
                        onCheckedChange = viewModel::updateAutoAdvance
                    )

                    SwitchSetting(
                        title = "Show Explanations Immediately",
                        description = "Display explanations right after answering",
                        icon = Icons.Default.Info,
                        checked = preferences.showExplanationsImmediately,
                        onCheckedChange = viewModel::updateShowExplanations
                    )

                    TimeLimitSetting(
                        currentLimit = preferences.questionTimeLimit,
                        onLimitChange = viewModel::updateTimeLimit
                    )
                }
            }

            // Feedback Settings
            item {
                SettingsSection(title = "Feedback") {
                    SwitchSetting(
                        title = "Sound Effects",
                        description = "Play sounds for correct/incorrect answers",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        checked = preferences.soundEnabled,
                        onCheckedChange = viewModel::updateSoundEnabled
                    )

                    SwitchSetting(
                        title = "Haptic Feedback",
                        description = "Vibrate on interactions",
                        icon = Icons.Default.Vibration,
                        checked = preferences.hapticFeedbackEnabled,
                        onCheckedChange = viewModel::updateHapticEnabled
                    )
                }
            }



        }
    }
}

// Settings Components
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsSection(title = title, onActionClick = null, content = content)
}

@Composable
private fun SettingsSection(
    title: String,
    onActionClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            onActionClick?.let { onClick ->
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Open $title settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title. $description. ${if (checked) "On" else "Off"}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun TextSizeSetting(
    currentSize: TextSize,
    onSizeChange: (TextSize) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Text Size",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextSize.entries.forEach { size ->
                    FilterChip(
                        selected = currentSize == size,
                        onClick = { onSizeChange(size) },
                        label = {
                            Text(
                                text = size.name.replace('_', ' '),
                                fontSize = (14 * size.scale).sp
                            )
                        }
                    )
                }
            }

            // Preview text
            Text(
                text = "Preview: The quick brown fox jumps over the lazy dog",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics {
                        contentDescription = "Text size preview"
                    }
            )
        }
    }
}

@Composable
private fun TimeLimitSetting(
    currentLimit: QuestionTimeLimit,
    onLimitChange: (QuestionTimeLimit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Question Time Limit",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = when (currentLimit) {
                        QuestionTimeLimit.NONE -> "No time limit"
                        QuestionTimeLimit.THIRTY_SECONDS -> "30 seconds"
                        QuestionTimeLimit.ONE_MINUTE -> "1 minute"
                        QuestionTimeLimit.TWO_MINUTES -> "2 minutes"
                        QuestionTimeLimit.FIVE_MINUTES -> "5 minutes"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )


                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                QuestionTimeLimit.entries.forEach { limit ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (limit) {
                                    QuestionTimeLimit.NONE -> "No time limit"
                                    QuestionTimeLimit.THIRTY_SECONDS -> "30 seconds"
                                    QuestionTimeLimit.ONE_MINUTE -> "1 minute"
                                    QuestionTimeLimit.TWO_MINUTES -> "2 minutes"
                                    QuestionTimeLimit.FIVE_MINUTES -> "5 minutes"
                                }
                            )
                        },
                        onClick = {
                            onLimitChange(limit)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun ThemeSetting(
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AppTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = currentTheme == theme,
                            onClick = { onThemeChange(theme) },
                            label = { Text(theme.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (theme) {
                                        AppTheme.LIGHT -> Icons.Default.LightMode
                                        AppTheme.DARK -> Icons.Default.DarkMode
                                        AppTheme.SYSTEM -> Icons.Default.SettingsBrightness
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

}