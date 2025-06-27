package com.example.mygemma3n.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// LiveCaptionScreen.kt - Compose UI
@Composable
fun LiveCaptionScreen(
    viewModel: LiveCaptionViewModel = hiltViewModel()
) {
    val state by viewModel.captionState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            // Performance indicator
            if (state.latencyMs > 0) {
                Text(
                    text = "Latency: ${state.latencyMs}ms",
                    color = when {
                        state.latencyMs < 300 -> Color.Green
                        state.latencyMs < 500 -> Color.Yellow
                        else -> Color.Red
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Live transcript
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = state.currentTranscript.ifEmpty { "Listening..." },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Translation if different language
            if (state.translatedText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = state.translatedText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Language selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LanguageSelector(
                    label = "From",
                    selected = state.sourceLanguage,
                    onLanguageSelected = { viewModel.setSourceLanguage(it) }
                )

                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White
                )

                LanguageSelector(
                    label = "To",
                    selected = state.targetLanguage,
                    onLanguageSelected = { viewModel.setTargetLanguage(it) }
                )
            }
        }

        // Floating action button for control
        FloatingActionButton(
            onClick = {
                if (state.isListening) {
                    viewModel.stopLiveCaption()
                } else {
                    viewModel.startLiveCaption()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                if (state.isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (state.isListening) "Stop" else "Start"
            )
        }
    }
}