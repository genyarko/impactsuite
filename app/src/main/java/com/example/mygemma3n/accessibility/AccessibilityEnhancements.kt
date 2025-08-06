package com.example.mygemma3n.accessibility

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mygemma3n.shared_utilities.OfflineRAG
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityManager @Inject constructor() {
    
    data class AccessibilitySettings(
        val highContrastMode: Boolean = false,
        val largeTextMode: Boolean = false,
        val reduceAnimations: Boolean = false,
        val voiceGuidanceEnabled: Boolean = false,
        val hapticFeedbackEnabled: Boolean = true,
        val slowAnimationsMode: Boolean = false,
        val colorBlindnessSupport: ColorBlindnessType = ColorBlindnessType.NONE
    )
    
    enum class ColorBlindnessType {
        NONE, PROTANOPIA, DEUTERANOPIA, TRITANOPIA
    }
    
    private val _settings = mutableStateOf(AccessibilitySettings())
    val settings: State<AccessibilitySettings> = _settings
    
    fun updateSettings(newSettings: AccessibilitySettings) {
        _settings.value = newSettings
        Timber.d("Accessibility settings updated: $newSettings")
    }
    
    fun getScaledTextSize(baseSize: Int): Int {
        return if (_settings.value.largeTextMode) {
            (baseSize * 1.3f).toInt()
        } else {
            baseSize
        }
    }
    
    fun getContrastColor(defaultColor: Color): Color {
        return if (_settings.value.highContrastMode) {
            when {
                defaultColor == Color.White -> Color.Black
                defaultColor == Color.Black -> Color.White
                else -> Color.Black // High contrast fallback
            }
        } else {
            defaultColor
        }
    }
    
    fun getAnimationDuration(defaultDuration: Int): Int {
        return when {
            _settings.value.reduceAnimations -> 0
            _settings.value.slowAnimationsMode -> (defaultDuration * 2)
            else -> defaultDuration
        }
    }
}

/**
 * Accessible button with enhanced semantics and keyboard navigation
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccessibleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .semantics {
                this.contentDescription = contentDescription
                this.role = role
                if (!enabled) {
                    disabled()
                }
            },
        enabled = enabled,
        content = content
    )
}

/**
 * Accessible text field with voice input support
 */
@Composable
fun AccessibleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    supportingText: String? = null,
    isError: Boolean = false,
    voiceInputEnabled: Boolean = false,
    onVoiceInput: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            isError = isError,
            trailingIcon = if (voiceInputEnabled && onVoiceInput != null) {
                {
                    IconButton(
                        onClick = onVoiceInput,
                        modifier = Modifier.semantics {
                            contentDescription = "Voice input for $label"
                            role = Role.Button
                        }
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice input"
                        )
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$label text field"
                    if (isError) {
                        error("Input error")
                    }
                    if (supportingText != null) {
                        stateDescription = supportingText
                    }
                }
        )
        
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp)
                    .semantics {
                        contentDescription = if (isError) "Error: $supportingText" else supportingText
                    }
            )
        }
    }
}

/**
 * Accessible subject card with enhanced navigation and descriptions
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccessibleSubjectCard(
    subject: OfflineRAG.Subject,
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // Provide additional context on long press
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description. ${if (isSelected) "Selected" else "Not selected"}. Double tap to open."
                role = Role.Button
                if (isSelected) {
                    selected = true
                }
            },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
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
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .semantics { 
                        contentDescription = "$title icon"
                    },
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Navigate to $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Accessible progress indicator with voice announcements
 */
@Composable
fun AccessibleProgressIndicator(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    val percentage = (progress * 100).toInt()
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            
            if (showPercentage) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                current = progress,
                                range = 0f..1f
                            )
                            contentDescription = "$label progress: $percentage percent"
                        },
        color = ProgressIndicatorDefaults.linearColor,
        trackColor = ProgressIndicatorDefaults.linearTrackColor,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}

/**
 * Accessible navigation with screen reader support
 */
@Composable
fun AccessibleNavigationHeader(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics {
                heading()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.semantics {
                        contentDescription = "Navigate back"
                        role = Role.Button
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    heading()
                }
            )
        }
        
        Row {
            actions()
        }
    }
}

/**
 * Accessible settings screen with voice control
 */
@Composable
fun AccessibilitySettingsScreen(
    accessibilityManager: AccessibilityManager,
    onBack: () -> Unit
) {
    val settings by accessibilityManager.settings
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        AccessibleNavigationHeader(
            title = "Accessibility Settings",
            onBackClick = onBack
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // High contrast mode
        AccessibleSettingSwitch(
            title = "High Contrast Mode",
            description = "Increase contrast for better visibility",
            checked = settings.highContrastMode,
            onCheckedChange = { 
                accessibilityManager.updateSettings(
                    settings.copy(highContrastMode = it)
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Large text mode
        AccessibleSettingSwitch(
            title = "Large Text",
            description = "Increase text size throughout the app",
            checked = settings.largeTextMode,
            onCheckedChange = { 
                accessibilityManager.updateSettings(
                    settings.copy(largeTextMode = it)
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Reduce animations
        AccessibleSettingSwitch(
            title = "Reduce Animations",
            description = "Minimize motion for sensitive users",
            checked = settings.reduceAnimations,
            onCheckedChange = { 
                accessibilityManager.updateSettings(
                    settings.copy(reduceAnimations = it)
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Voice guidance
        AccessibleSettingSwitch(
            title = "Voice Guidance",
            description = "Enable spoken descriptions and feedback",
            checked = settings.voiceGuidanceEnabled,
            onCheckedChange = { 
                accessibilityManager.updateSettings(
                    settings.copy(voiceGuidanceEnabled = it)
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Haptic feedback
        AccessibleSettingSwitch(
            title = "Haptic Feedback",
            description = "Vibration feedback for interactions",
            checked = settings.hapticFeedbackEnabled,
            onCheckedChange = { 
                accessibilityManager.updateSettings(
                    settings.copy(hapticFeedbackEnabled = it)
                )
            }
        )
    }
}

@Composable
private fun AccessibleSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description. ${if (checked) "Enabled" else "Disabled"}. Double tap to toggle."
                role = Role.Switch
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = if (checked) "$title enabled" else "$title disabled"
            }
        )
    }
}