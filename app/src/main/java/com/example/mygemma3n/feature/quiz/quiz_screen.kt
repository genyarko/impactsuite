package com.example.mygemma3n.feature.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel


/* top-level screen ------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizGeneratorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Adaptive Quiz Generator")
                        if (state.learnerProfile != null) {
                            Text(
                                "Streak: ${state.learnerProfile!!.streakDays} days | Total: ${state.learnerProfile!!.totalQuestionsAnswered} questions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    if (state.reviewQuestionsAvailable > 0) {
                        Badge(
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text("${state.reviewQuestionsAvailable} reviews")
                        }
                    }
                }
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
                        EmptyStateQuizSetup(
                            mode = state.mode,
                            onModeChange = viewModel::setQuizMode,
                            onGenerateQuiz = { s, t, c ->
                                viewModel.generateAdaptiveQuiz(s, t, c)
                            }
                        )
                    } else {
                        EnhancedQuizSetupScreen(
                            subjects = state.subjects,
                            userProgress = state.userProgress,
                            learnerProfile = state.learnerProfile,
                            mode = state.mode,
                            reviewAvailable = state.reviewQuestionsAvailable,
                            onModeChange = viewModel::setQuizMode,
                            onGenerateQuiz = { s, t, c ->
                                viewModel.generateAdaptiveQuiz(s, t, c)
                            }
                        )
                    }
                }

                state.isGenerating -> GenerationProgress(
                    done = state.questionsGenerated,
                    mode = state.mode
                )

                else -> state.currentQuiz?.let { quiz ->
                    EnhancedQuizTakingScreen(
                        quiz = quiz,
                        conceptCoverage = state.conceptCoverage,
                        onAnswerSubmit = viewModel::submitAnswer,
                        onQuizComplete = viewModel::completeQuiz
                    )
                }
            }

            // Error snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { /* Clear error */ }) {
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

/* Enhanced setup screen with mode selection ----------------------------------- */

@Composable
fun EnhancedQuizSetupScreen(
    subjects: List<Subject>,
    userProgress: Map<Subject, Float>,
    learnerProfile: LearnerProfile?,
    mode: QuizMode,
    reviewAvailable: Int,
    onModeChange: (QuizMode) -> Unit,
    onGenerateQuiz: (Subject, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var subject by remember { mutableStateOf<Subject?>(null) }
    var topicText by remember { mutableStateOf("") }
    var questionCnt by remember { mutableStateOf(10f) }
    var showDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create a new adaptive quiz", style = MaterialTheme.typography.headlineSmall)

        // Mode selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Quiz Mode",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuizMode.entries.forEach { qMode ->
                        FilterChip(
                            selected = mode == qMode,
                            onClick = { onModeChange(qMode) },
                            label = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when (qMode) {
                                        QuizMode.NORMAL -> Icon(Icons.Default.Quiz, null, Modifier.size(12.dp))
                                        QuizMode.REVIEW -> Icon(Icons.Default.Refresh, null, Modifier.size(12.dp))
                                        QuizMode.ADAPTIVE -> Icon(Icons.Default.AutoAwesome, null, Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = qMode.name,
                                        fontSize = 10.sp
                                    )

                                    if (qMode == QuizMode.REVIEW && reviewAvailable > 0) {
                                        Badge { Text("$reviewAvailable") }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Mode description
                Text(
                    when (mode) {
                        QuizMode.NORMAL -> "Standard quiz with mixed difficulty questions"
                        QuizMode.REVIEW -> "Review questions you haven't seen in a while"
                        QuizMode.ADAPTIVE -> "Difficulty adjusts based on your performance"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // Subject selector with progress
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Select Subject", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showDropdown = !showDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(subject?.name ?: "Choose subject")
                        subject?.let { subj ->
                            val accuracy = userProgress[subj] ?: 0f
                            LinearProgressIndicator(
                                progress = { accuracy },
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(4.dp),
                                color = when {
                                    accuracy > 0.8f -> Color.Green
                                    accuracy > 0.6f -> Color.Yellow
                                    else -> Color.Red
                                }
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    subjects.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(s.name)
                                    val accuracy = userProgress[s] ?: 0f
                                    Text(
                                        "${(accuracy * 100).toInt()}%",
                                        color = when {
                                            accuracy > 0.8f -> Color.Green
                                            accuracy > 0.6f -> Color.Yellow
                                            else -> Color.Red
                                        }
                                    )
                                }
                            },
                            onClick = {
                                subject = s
                                showDropdown = false
                            }
                        )
                    }
                }

                // Show mastered concepts for selected subject
                subject?.let { subj ->
                    learnerProfile?.masteredConcepts?.takeIf { it.isNotEmpty() }?.let { concepts ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Mastered: ${concepts.take(3).joinToString(", ")}${if (concepts.size > 3) " +${concepts.size - 3} more" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Number of questions: ${questionCnt.toInt()}")
                if (mode == QuizMode.REVIEW && reviewAvailable < questionCnt) {
                    Text(
                        "Only $reviewAvailable available for review",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Slider(
                value = questionCnt,
                onValueChange = { questionCnt = it },
                valueRange = 5f..20f,
                steps = 14,
                enabled = mode != QuizMode.REVIEW || reviewAvailable >= 5
            )
        }

        Button(
            onClick = {
                subject?.let { s -> onGenerateQuiz(s, topicText.trim(), questionCnt.toInt()) }
            },
            enabled = subject != null && (mode != QuizMode.REVIEW || reviewAvailable >= 5),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                when (mode) {
                    QuizMode.NORMAL -> Icons.Default.PlayArrow
                    QuizMode.REVIEW -> Icons.Default.Refresh
                    QuizMode.ADAPTIVE -> Icons.Default.AutoAwesome
                },
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when (mode) {
                    QuizMode.NORMAL -> "Generate Quiz"
                    QuizMode.REVIEW -> "Start Review Session"
                    QuizMode.ADAPTIVE -> "Start Adaptive Quiz"
                }
            )
        }
    }
}

/* Enhanced progress indicator with mode info ----------------------------------- */

@Composable
private fun GenerationProgress(
    done: Int,
    mode: QuizMode,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            when (mode) {
                QuizMode.NORMAL -> "Generating quiz questions..."
                QuizMode.REVIEW -> "Preparing review questions..."
                QuizMode.ADAPTIVE -> "Analyzing your progress and generating adaptive questions..."
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Questions created: $done",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/* Empty state setup (unchanged but with mode selector) -------------------- */

@Composable
fun EmptyStateQuizSetup(
    mode: QuizMode,
    onModeChange: (QuizMode) -> Unit,
    onGenerateQuiz: (Subject, String, Int) -> Unit
) {
    var subject by remember { mutableStateOf<Subject?>(null) }
    var topicText by remember { mutableStateOf("") }
    var questionCnt by remember { mutableStateOf(10f) }

    val subjects = remember { Subject.entries }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create a new adaptive quiz", style = MaterialTheme.typography.headlineSmall)
        Text(
            "No educational content loaded. You can still create a quiz!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Mode selector card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Quiz Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuizMode.entries.forEach { qMode ->
                        FilterChip(
                            selected = mode == qMode,
                            onClick = { onModeChange(qMode) },
                            label = { Text(qMode.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Subject chooser
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
                        RadioButton(
                            selected = subject == s,
                            onClick = { subject = s }
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
            Slider(
                value = questionCnt,
                onValueChange = { questionCnt = it },
                valueRange = 5f..20f,
                steps = 14
            )
        }

        Button(
            onClick = {
                subject?.let { onGenerateQuiz(it, topicText.trim(), questionCnt.toInt()) }
            },
            enabled = subject != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Quiz")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

/* Enhanced quiz taking screen with hints and concept display ------------------ */

@Composable
fun EnhancedQuizTakingScreen(
    quiz: Quiz,
    conceptCoverage: Map<String, Int>,
    onAnswerSubmit: (String, String) -> Unit,
    onQuizComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var index by remember { mutableIntStateOf(0) }
    val q = quiz.questions[index]
    var chosenAnswer by remember(q.id) { mutableStateOf<String?>(null) }
    var showHint by remember(q.id) { mutableStateOf(false) }

    var isSubmitting by remember(q.id) { mutableStateOf(false) }
    LaunchedEffect(q.isAnswered) { if (q.isAnswered) isSubmitting = false }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Progress and stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { (index + 1) / quiz.questions.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Question ${index + 1} of ${quiz.questions.size}",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Concept coverage chip
            @OptIn(ExperimentalMaterial3Api::class)
            if (conceptCoverage.isNotEmpty()) {
                AssistChip(
                    onClick = { /* TODO: show concept list */ },
                    modifier = Modifier.padding(start = 8.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            "${conceptCoverage.size} concepts",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Question card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                // Difficulty badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(q.questionText, style = MaterialTheme.typography.headlineSmall)
                    Badge(
                        containerColor = when (q.difficulty) {
                            Difficulty.EASY -> Color.Green
                            Difficulty.MEDIUM -> Color.Yellow
                            Difficulty.HARD -> Color.Red
                            Difficulty.ADAPTIVE -> MaterialTheme.colorScheme.primary
                        }
                    ) {
                        Text(q.difficulty.name)
                    }
                }

                // Review indicator
                q.lastSeenAt?.let {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Review question",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Hint section
                if (q.hint != null && !q.isAnswered) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showHint = !showHint },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            if (showHint) Icons.Default.VisibilityOff else Icons.Default.Lightbulb,
                            null,
                            Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showHint) "Hide Hint" else "Show Hint")
                    }

                    AnimatedVisibility(
                        visible = showHint,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    q.hint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Answer options - using the enhanced answer section
        EnhancedAnswerSection(
            question = q,
            chosenAnswer = chosenAnswer,
            onAnswerChange = { chosenAnswer = it }
        )

        // Enhanced feedback card
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
                            null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (q.userAnswer == q.correctAnswer) "Correct!" else "Incorrect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(q.feedback, style = MaterialTheme.typography.bodyMedium)

                    if (q.explanation.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Icon(
                                Icons.Default.Info,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                q.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Concepts covered
                    if (q.conceptsCovered.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Tag,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Concepts: ${q.conceptsCovered.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (index > 0) {
                OutlinedButton(onClick = {
                    index--
                    showHint = false
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Previous")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            when {
                /* ─── Submit Answer (now with loading support) ─── */
                !q.isAnswered && chosenAnswer != null -> {
                    Button(
                        onClick = {
                            isSubmitting = true
                            onAnswerSubmit(q.id, chosenAnswer!!)
                            showHint = false
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Submit Answer")
                        }
                    }
                }

                /* ─── Next question ─── */
                q.isAnswered && index < quiz.questions.size - 1 -> {
                    Button(onClick = {
                        index++
                        chosenAnswer = null
                        showHint = false
                    }) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }

                /* ─── Complete quiz ─── */
                q.isAnswered && index == quiz.questions.size - 1 -> {
                    Button(onClick = onQuizComplete) {
                        Icon(Icons.Default.Done, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Complete Quiz")
                    }
                }

                else -> {
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }
}

/* Enhanced Answer Section - handles all question types properly */

@Composable
fun EnhancedAnswerSection(
    question: Question,
    chosenAnswer: String?,
    onAnswerChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Validate question type matches the available options
    val effectiveQuestionType = when {
        question.questionType == QuestionType.MULTIPLE_CHOICE && question.options.size < 2 -> {
            // Fallback to short answer if not enough options
            QuestionType.SHORT_ANSWER
        }
        question.questionType == QuestionType.TRUE_FALSE && question.options.size != 2 -> {
            // Force true/false options
            QuestionType.TRUE_FALSE
        }
        else -> question.questionType
    }

    Column(modifier = modifier) {
        // Show a warning if question type was adjusted
        if (effectiveQuestionType != question.questionType) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Question format adjusted due to missing options",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        when (effectiveQuestionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                val validOptions = if (question.options.size >= 2) {
                    question.options
                } else {
                    // Create dummy options if needed
                    listOf("Option A", "Option B", "Option C", "Option D")
                }

                validOptions.forEachIndexed { idx, opt ->
                    AnswerOption(
                        text = opt,
                        isSelected = chosenAnswer == opt,
                        isCorrect = question.isAnswered && opt == question.correctAnswer,
                        isWrong = question.isAnswered && opt == question.userAnswer && opt != question.correctAnswer,
                        enabled = !question.isAnswered,
                        onClick = { onAnswerChange(opt) },
                        prefix = "${('A'..'D').toList()[idx]}. "
                    )
                    if (idx < validOptions.size - 1) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            QuestionType.TRUE_FALSE -> {
                // Always show True/False regardless of what's in options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("True" to Icons.Default.Check, "False" to Icons.Default.Close).forEach { (opt, icon) ->
                        ElevatedButton(
                            onClick = { onAnswerChange(opt) },
                            enabled = !question.isAnswered,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = when {
                                    question.isAnswered && opt == question.correctAnswer ->
                                        MaterialTheme.colorScheme.primary
                                    question.isAnswered && opt == question.userAnswer ->
                                        MaterialTheme.colorScheme.error
                                    chosenAnswer == opt ->
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Icon(icon, null)
                            Spacer(Modifier.width(8.dp))
                            Text(opt)
                        }
                    }
                }
            }

            QuestionType.FILL_IN_BLANK -> {
                var txt by remember(question.id) { mutableStateOf(chosenAnswer ?: "") }

                // Show the question with visual blank indicator
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = question.questionText.replace("_____", "[ ? ]"),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                OutlinedTextField(
                    value = txt,
                    onValueChange = {
                        txt = it
                        onAnswerChange(it)
                    },
                    label = { Text("Fill in the blank") },
                    placeholder = { Text("Type your answer") },
                    enabled = !question.isAnswered,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }

            QuestionType.SHORT_ANSWER -> {
                var txt by remember(question.id) { mutableStateOf(chosenAnswer ?: "") }
                OutlinedTextField(
                    value = txt,
                    onValueChange = {
                        txt = it
                        onAnswerChange(it)
                    },
                    label = { Text("Your answer") },
                    placeholder = { Text("Type your answer here...") },
                    enabled = !question.isAnswered,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 200.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                // Character counter for short answers
                if (!question.isAnswered) {
                    Text(
                        "${txt.length}/200 characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            else -> {
                // Fallback for any other type
                Text(
                    "Unsupported question type",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/* Answer option helper (enhanced with better visual feedback) ---------------- */

@Composable
fun AnswerOption(
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    prefix: String = ""
) {
    val container = when {
        isCorrect -> MaterialTheme.colorScheme.primary
        isWrong -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        isCorrect || isWrong -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = container,
            contentColor = content
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCorrect) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            } else if (isWrong) {
                Icon(Icons.Default.Cancel, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }

            if (prefix.isNotEmpty()) {
                Text(
                    prefix,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}