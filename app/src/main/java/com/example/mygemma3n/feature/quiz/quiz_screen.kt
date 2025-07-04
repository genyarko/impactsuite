package com.example.mygemma3n.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mygemma3n.shared_utilities.OfflineRAG

/* top-level screen ------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizGeneratorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    /* simple debug log */
    LaunchedEffect(state) {
        println("QuizScreen State: subjects=${state.subjects.size}, " +
                "isGenerating=${state.isGenerating}, hasQuiz=${state.currentQuiz != null}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Adaptive Quiz Generator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { pv ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pv)
        ) {
            when {
                state.currentQuiz == null && !state.isGenerating -> {
                    if (state.subjects.isEmpty()) {
                        EmptyStateQuizSetup { s, t, c ->
                            viewModel.generateAdaptiveQuiz(s, t, c)
                        }
                    } else {
                        QuizSetupScreen(
                            subjects = state.subjects,                 // ← first param
                            onGenerateQuiz = { s, t, c ->              // ← second param
                                viewModel.generateAdaptiveQuiz(s, t, c)
                            }
                        )
                    }
                }

                state.isGenerating -> GenerationProgress(state.questionsGenerated)

                else -> state.currentQuiz?.let { quiz ->
                    QuizTakingScreen(
                        quiz           = quiz,
                        onAnswerSubmit = viewModel::submitAnswer,
                        onQuizComplete = viewModel::completeQuiz
                    )
                }
            }

        }
    }
}

/* simple circular progress indicator ----------------------------------- */

@Composable
private fun GenerationProgress(done: Int, modifier: Modifier = Modifier) { // Added modifier parameter
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())            // NEW
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text("Generating adaptive quiz…", style = MaterialTheme.typography.bodyLarge)
        Text("Questions created: $done", style = MaterialTheme.typography.bodyMedium)
    }
}
/* ────────────────────────────────────────────────────────────────────────
 * Empty-state & normal setup screens
 * ──────────────────────────────────────────────────────────────────────── */

@Composable
fun EmptyStateQuizSetup(onGenerateQuiz: (Subject, String, Int) -> Unit) {
    var subject      by remember { mutableStateOf<Subject?>(null) }
    var topicText    by remember { mutableStateOf("") }
    var questionCnt  by remember { mutableStateOf(10f) }
    val subjects = remember { OfflineRAG.Subject.entries }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())            // NEW
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create a new adaptive quiz", style = MaterialTheme.typography.headlineSmall)
        Text("No educational content loaded. You can still create a quiz!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        /* subject chooser (radio list) */
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Select Subject:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                subjects.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = subject == s,
                            onClick  = { subject = s }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.name)
                    }
                }
            }
        }

        OutlinedTextField(
            value = topicText,
            onValueChange = { topicText = it },
            label = { Text("Topic (optional)") },
            placeholder = { Text("e.g., Linear Equations") },
            modifier = Modifier.fillMaxWidth()
        )

        Column(Modifier.fillMaxWidth()) {
            Text("Number of questions: ${questionCnt.toInt()}")
            androidx.compose.material3.Slider(
                value       = questionCnt,
                onValueChange = { questionCnt = it },
                valueRange  = 5f..20f,
                steps       = 14
            )
        }

        Button(
            onClick  = {
                subject?.let { onGenerateQuiz(it, topicText.trim(), questionCnt.toInt()) }
            },
            enabled  = subject != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Quiz")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

@Composable
fun QuizSetupScreen(
    subjects: List<Subject>,
    onGenerateQuiz: (Subject, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var subject     by remember { mutableStateOf<Subject?>(null) }
    var topicText   by remember { mutableStateOf("") }
    var questionCnt by remember { mutableStateOf(10f) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())            // NEW
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create a new adaptive quiz", style = MaterialTheme.typography.headlineSmall)

        /* subject dropdown (simplified) */
        OutlinedButton(
            onClick = { /* todo menu */ },
            modifier = Modifier.fillMaxWidth()
        ) { Text(subject?.name ?: "Choose subject") }

        androidx.compose.material3.DropdownMenu(
            expanded = subject == null,
            onDismissRequest = {}
        ) {
            subjects.forEach { s ->
                androidx.compose.material3.DropdownMenuItem(
                    text    = { Text(s.name) },
                    onClick = { subject = s }
                )
            }
        }

        OutlinedTextField(
            value = topicText,
            onValueChange = { topicText = it },
            label = { Text("Topic (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Column(Modifier.fillMaxWidth()) {
            Text("Number of questions: ${questionCnt.toInt()}")
            androidx.compose.material3.Slider(
                value       = questionCnt,
                onValueChange = { questionCnt = it },
                valueRange  = 5f..20f,
                steps       = 15
            )
        }

        Button(
            onClick = {
                subject?.let { s -> onGenerateQuiz(s, topicText.trim(), questionCnt.toInt()) }
            },
            enabled = subject != null
        ) {
            Text("Generate Quiz")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

/*──────────────────────── Answer option helper ────────────────────────*/

@Composable
fun AnswerOption(
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        isCorrect  -> MaterialTheme.colorScheme.primary
        isWrong    -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else       -> MaterialTheme.colorScheme.surface
    }
    val content = if (isCorrect || isWrong)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurface

    ElevatedButton(
        onClick  = onClick,
        enabled  = enabled,
        colors   = ButtonDefaults.elevatedButtonColors(
            containerColor = container,
            contentColor   = content
        ),
        modifier = Modifier.fillMaxWidth()
    ) { Text(text) }
}

/*──────────────────────── Main quiz runner ───────────────────────────*/

@Composable
fun QuizTakingScreen(
    quiz: Quiz,
    onAnswerSubmit: (String, String) -> Unit,
    onQuizComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var index         by remember { mutableIntStateOf(0) }
    val q             = quiz.questions[index]
    var chosenAnswer  by remember(q.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LinearProgressIndicator(
        progress = { (index + 1) / quiz.questions.size.toFloat() },
        modifier = Modifier.fillMaxWidth(),
        color = ProgressIndicatorDefaults.linearColor,
        trackColor = ProgressIndicatorDefaults.linearTrackColor,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
        Spacer(Modifier.height(16.dp))
        Text("Question ${index + 1} of ${quiz.questions.size}",
            style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(q.questionText, style = MaterialTheme.typography.headlineSmall)

                if (q.hint != null && !q.isAnswered) {
                    Spacer(Modifier.height(8.dp))
                    var showHint by remember { mutableStateOf(false) }
                    TextButton(onClick = { showHint = !showHint }) {
                        Icon(Icons.Default.ThumbUp, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showHint) "Hide Hint" else "Show Hint")
                    }
                    if (showHint) {
                        Text(q.hint, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        when (q.questionType) {
            QuestionType.MULTIPLE_CHOICE -> q.options.forEach { opt ->
                AnswerOption(
                    text        = opt,
                    isSelected  = chosenAnswer == opt,
                    isCorrect   = q.isAnswered && opt == q.correctAnswer,
                    isWrong     = q.isAnswered && opt == q.userAnswer && opt != q.correctAnswer,
                    enabled     = !q.isAnswered,
                    onClick     = { chosenAnswer = opt }
                )
                Spacer(Modifier.height(8.dp))
            }

            QuestionType.TRUE_FALSE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("True", "False").forEach { opt ->
                        ElevatedButton(
                            onClick  = { chosenAnswer = opt },
                            enabled  = !q.isAnswered,
                            colors   = ButtonDefaults.elevatedButtonColors(
                                containerColor = when {
                                    q.isAnswered && opt == q.correctAnswer -> MaterialTheme.colorScheme.primary
                                    q.isAnswered && opt == q.userAnswer && opt != q.correctAnswer -> MaterialTheme.colorScheme.error
                                    chosenAnswer == opt -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) { Text(opt) }
                    }
                }
            }

            QuestionType.FILL_IN_BLANK -> {
                var txt by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = txt,
                    onValueChange = {
                        txt = it
                        chosenAnswer = it
                    },
                    label = { Text("Your answer") },
                    enabled = !q.isAnswered,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            QuestionType.SHORT_ANSWER -> {
                var txt by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = txt,
                    onValueChange = {
                        txt = it
                        chosenAnswer = it
                    },
                    label = { Text("Your answer") },
                    enabled = !q.isAnswered,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                )
            }

            QuestionType.MATCHING -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Matching questions are not supported yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { chosenAnswer = "skipped" }) {
                        Text("Skip Question")
                    }
                }
            }
        }

        /* feedback card */
        if (q.isAnswered && q.feedback != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (q.userAnswer == q.correctAnswer)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (q.userAnswer == q.correctAnswer)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Cancel,
                            null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (q.userAnswer == q.correctAnswer) "Correct!" else "Incorrect",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(q.feedback, style = MaterialTheme.typography.bodyMedium)
                    q.explanation?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Explanation: $it",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (index > 0) {
                OutlinedButton(onClick = { index-- }) {
                    Icon(Icons.Default.ArrowBack, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Previous")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            when {
                !q.isAnswered && chosenAnswer != null -> {
                    Button(onClick = { onAnswerSubmit(q.id, chosenAnswer!!) }) {
                        Text("Submit Answer")
                    }
                }

                q.isAnswered && index < quiz.questions.size - 1 -> {
                    Button(onClick = {
                        index++
                        chosenAnswer = null
                    }) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null)
                    }
                }

                q.isAnswered -> {
                    Button(onClick = onQuizComplete) {
                        Icon(Icons.Default.Done, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Complete Quiz")
                    }
                }
            }
        }
    }
}
