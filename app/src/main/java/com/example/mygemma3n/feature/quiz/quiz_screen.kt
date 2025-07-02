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
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mygemma3n.feature.quiz.Subject

// QuizScreen.kt - Compose UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizGeneratorViewModel = hiltViewModel()
) {
    val state by viewModel.quizState.collectAsState()

    // Add debug logging
    LaunchedEffect(state) {
        println("QuizScreen State: subjects=${state.subjects.size}, isGenerating=${state.isGenerating}, hasQuiz=${state.currentQuiz != null}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adaptive Quiz Generator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.currentQuiz == null && !state.isGenerating -> {
                    // Show setup screen OR empty state
                    if (state.subjects.isEmpty()) {
                        // Show empty state with manual subject selection
                        EmptyStateQuizSetup(
                            onGenerateQuiz = { s, t, c ->
                                viewModel.generateAdaptiveQuiz(s, t, c)
                            }
                        )
                    } else {
                        QuizSetupScreen(
                            subjects = state.subjects,
                            onGenerateQuiz = { s, t, c ->
                                viewModel.generateAdaptiveQuiz(s, t, c)
                            }
                        )
                    }
                }

                state.isGenerating -> {
                    // Generation Progress
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Generating adaptive quiz...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Questions created: ${state.questionsGenerated}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                state.currentQuiz != null -> {
                    // Quiz Taking Screen
                    QuizTakingScreen(
                        quiz = state.currentQuiz!!,
                        onAnswerSubmit = { questionId, answer ->
                            viewModel.submitAnswer(questionId, answer)
                        },
                        onQuizComplete = {
                            viewModel.completeQuiz()
                        }
                    )
                }
            }
        }
    }
}

// New composable for when subjects list is empty
@Composable
fun EmptyStateQuizSetup(
    onGenerateQuiz: (Subject, String, Int) -> Unit
) {
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var topicText by remember { mutableStateOf("") }
    var questionCount by remember { mutableStateOf(10f) }

    // All available subjects from the enum
    val allSubjects = remember { Subject.values().toList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Create a new adaptive quiz",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "No educational content loaded. You can still create a quiz!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Subject selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Select Subject:",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                allSubjects.forEach { subject ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedSubject == subject,
                            onClick = { selectedSubject = subject }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(subject.name)
                    }
                }
            }
        }

        // Topic text field
        OutlinedTextField(
            value = topicText,
            onValueChange = { topicText = it },
            label = { Text("Topic (optional)") },
            placeholder = { Text("e.g., Linear Equations, Cell Biology") },
            modifier = Modifier.fillMaxWidth()
        )

        // Question count slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Number of questions: ${questionCount.toInt()}")
            androidx.compose.material3.Slider(
                value = questionCount,
                onValueChange = { questionCount = it },
                valueRange = 5f..20f,
                steps = 14
            )
        }

        // Generate button
        Button(
            onClick = {
                selectedSubject?.let { subject ->
                    onGenerateQuiz(subject, topicText.trim(), questionCount.toInt())
                }
            },
            enabled = selectedSubject != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Quiz")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}
/* ────────────────────────────────────────────────────────────────
 * QuizSetupScreen – select subject / topic / #questions
 * ──────────────────────────────────────────────────────────────── */
@Composable
fun QuizSetupScreen(
    subjects: List<Subject>,
    onGenerateQuiz: (Subject, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var topicText by remember { mutableStateOf("") }
    var questionCount by remember { mutableStateOf(10f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Create a new adaptive quiz",
            style = MaterialTheme.typography.headlineSmall
        )

        // 1. Subject dropdown
        OutlinedButton(
            onClick = { /* show menu – simple selector for brevity */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedSubject?.name ?: "Choose subject")
        }
        // Simple drop-down menu
        androidx.compose.material3.DropdownMenu(
            expanded = selectedSubject == null,   // opens first time
            onDismissRequest = { /* no-op */ }
        ) {
            subjects.forEach { subj ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(subj.name) },
                    onClick = { selectedSubject = subj }
                )
            }
        }

        // 2. Topic text field
        OutlinedTextField(
            value = topicText,
            onValueChange = { topicText = it },
            label = { Text("Topic (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        // 3. Question count slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Number of questions: ${questionCount.toInt()}")
            androidx.compose.material3.Slider(
                value = questionCount,
                onValueChange = { questionCount = it },
                valueRange = 5f..20f,
                steps = 15
            )
        }

        // 4. Generate button
        Button(
            onClick = {
                val subj = selectedSubject ?: return@Button
                onGenerateQuiz(subj, topicText.trim(), questionCount.toInt())
            },
            enabled = selectedSubject != null
        ) {
            Text("Generate Quiz")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

/* ────────────────────────────────────────────────────────────────
 * AnswerOption – used by QuizTakingScreen for MCQ choices
 * ──────────────────────────────────────────────────────────────── */
@Composable
fun AnswerOption(
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = when {
        isCorrect  -> MaterialTheme.colorScheme.primary
        isWrong    -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else       -> MaterialTheme.colorScheme.surface
    }
    val contentColor =
        if (isCorrect || isWrong)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurface

    ElevatedButton(
        onClick  = onClick,
        enabled  = enabled,
        colors   = ButtonDefaults.elevatedButtonColors(
            containerColor = containerColor,
            contentColor   = contentColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
fun QuizTakingScreen(
    quiz: Quiz,
    onAnswerSubmit: (String, String) -> Unit,
    onQuizComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    val currentQuestion = quiz.questions[currentQuestionIndex]
    var selectedAnswer by remember(currentQuestion.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = (currentQuestionIndex + 1) / quiz.questions.size.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Question counter
        Text(
            text = "Question ${currentQuestionIndex + 1} of ${quiz.questions.size}",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Question
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = currentQuestion.questionText,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (currentQuestion.hint != null && !currentQuestion.isAnswered) {
                    Spacer(modifier = Modifier.height(8.dp))

                    var showHint by remember { mutableStateOf(false) }

                    TextButton(
                        onClick = { showHint = !showHint }
                    ) {
                        Icon(
                            Icons.Default.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showHint) "Hide Hint" else "Show Hint")
                    }

                    if (showHint) {
                        Text(
                            text = currentQuestion.hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Answer options
        when (currentQuestion.questionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                currentQuestion.options.forEach { option ->
                    AnswerOption(
                        text = option,
                        isSelected = selectedAnswer == option,
                        isCorrect = currentQuestion.isAnswered && option == currentQuestion.correctAnswer,
                        isWrong = currentQuestion.isAnswered && option == currentQuestion.userAnswer && option != currentQuestion.correctAnswer,
                        enabled = !currentQuestion.isAnswered,
                        onClick = { selectedAnswer = option }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            QuestionType.TRUE_FALSE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("True", "False").forEach { option ->
                        ElevatedButton(
                            onClick = { selectedAnswer = option },
                            enabled = !currentQuestion.isAnswered,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = when {
                                    currentQuestion.isAnswered && option == currentQuestion.correctAnswer ->
                                        MaterialTheme.colorScheme.primary
                                    currentQuestion.isAnswered && option == currentQuestion.userAnswer && option != currentQuestion.correctAnswer ->
                                        MaterialTheme.colorScheme.error
                                    selectedAnswer == option ->
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Text(option)
                        }
                    }
                }
            }

            QuestionType.FILL_IN_BLANK -> {
                var textAnswer by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = textAnswer,
                    onValueChange = {
                        textAnswer = it
                        selectedAnswer = it
                    },
                    label = { Text("Your answer") },
                    enabled = !currentQuestion.isAnswered,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            QuestionType.SHORT_ANSWER -> {
                var textAnswer by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = textAnswer,
                    onValueChange = {
                        textAnswer = it
                        selectedAnswer = it
                    },
                    label = { Text("Your answer") },
                    enabled = !currentQuestion.isAnswered,
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
                    Text(
                        text = "Matching questions are not supported yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { selectedAnswer = "skipped" }) {
                        Text("Skip Question")
                    }
                }
            }
        }

        // Feedback section
        if (currentQuestion.isAnswered && currentQuestion.feedback != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentQuestion.userAnswer == currentQuestion.correctAnswer)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (currentQuestion.userAnswer == currentQuestion.correctAnswer)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Cancel,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentQuestion.userAnswer == currentQuestion.correctAnswer)
                                "Correct!" else "Incorrect",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentQuestion.feedback,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (currentQuestion.explanation != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Explanation: ${currentQuestion.explanation}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentQuestionIndex > 0) {
                OutlinedButton(
                    onClick = { currentQuestionIndex-- }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Previous")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            if (!currentQuestion.isAnswered && selectedAnswer != null) {
                Button(
                    onClick = {
                        onAnswerSubmit(currentQuestion.id, selectedAnswer!!)
                    }
                ) {
                    Text("Submit Answer")
                }
            } else if (currentQuestion.isAnswered) {
                if (currentQuestionIndex < quiz.questions.size - 1) {
                    Button(
                        onClick = {
                            currentQuestionIndex++
                            selectedAnswer = null
                        }
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = onQuizComplete
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Complete Quiz")
                    }
                }
            }
        }
    }
}