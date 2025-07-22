package com.example.mygemma3n.feature.summarizer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SummarizerScreen(viewModel: SummarizerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.processFile(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Document Summarizer", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            launcher.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"))
        }) { Text("Pick Document") }

        Spacer(Modifier.height(16.dp))

        state.fileName?.let { Text("File: $it", fontWeight = FontWeight.SemiBold) }
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        state.summary?.let {
            Text("Summary:", fontWeight = FontWeight.Bold)
            Text(it)
        }
        if (state.questions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Questions:", fontWeight = FontWeight.Bold)
            LazyColumn {
                items(state.questions) { q ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(q.questionText, fontWeight = FontWeight.SemiBold)
                        q.options.forEach { opt -> Text("• $opt") }
                        Text("Answer: ${q.correctAnswer}")
                    }
                }
            }
        }
        if (state.summary != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.retry() }) { Text("Retry") }
                OutlinedButton(onClick = { viewModel.clear() }) { Text("Clear") }
            }
        }
    }
}
