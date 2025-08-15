package com.mygemma3n.aiapp.feature.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.WindowManager
import com.mygemma3n.aiapp.feature.quiz.trivia.QuizTrivia
typealias QuizGeneratorViewModelState = QuizGeneratorViewModel.QuizState


/* top-level screen ------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizGeneratorViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Skip student dialog if we have custom content to process
    val hasCustomContent = com.mygemma3n.aiapp.shared_utilities.QuizContentManager.hasContent()
    var showStudentDialog by remember { mutableStateOf(!hasCustomContent) }
    var studentName by remember { mutableStateOf("") }
    var studentGrade by remember { mutableIntStateOf(5) }
    var studentCountry by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    
    // Keep screen awake during quiz generation
    DisposableEffect(state.isGenerating) {
        if (state.isGenerating) {
            val activity = context as? android.app.Activity
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            onDispose { }
        }
    }

    // Ensure subjects are loaded
    LaunchedEffect(Unit) {
        if (state.subjects.isEmpty()) {
            viewModel.loadSubjects()
        }
        
        // Check for custom content and generate quiz if available
        if (com.mygemma3n.aiapp.shared_utilities.QuizContentManager.hasContent()) {
            viewModel.generateQuizFromCustomContent()
        }
    }

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
                        // Show student info if available
                        state.studentName?.let { name ->
                            Text(
                                "Student: $name (Grade ${state.studentGrade})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                navigationIcon = {
                    onNavigateBack?.let { callback ->
                        IconButton(onClick = callback) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate back"
                            )
                        }
                    }
                },
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
                    // Use subjects from state, fallback to all subjects if empty
                    val availableSubjects = state.subjects.ifEmpty {
                        Subject.entries
                    }

                    EnhancedQuizSetupScreen(
                        subjects = availableSubjects,
                        userProgress = state.userProgress,
                        learnerProfile = state.learnerProfile,
                        mode = state.mode,
                        reviewAvailable = state.reviewQuestionsAvailable,
                        state = state,
                        viewModel = viewModel,
                        onModeChange = viewModel::setQuizMode,
                        onGenerateQuiz = { s, t, c ->
                            viewModel.generateAdaptiveQuiz(s, t, c)
                        }
                    )
                }

                state.isGenerating -> GenerationProgress(
                    done = state.questionsGenerated,
                    mode = state.mode,
                    studentGrade = state.studentGrade
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

            // Student information dialog
            if (showStudentDialog && state.studentName == null) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Welcome to Quiz!")
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "Please enter student information to get personalized quizzes:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = studentName,
                                onValueChange = { studentName = it },
                                label = { Text("Student Name") },
                                placeholder = { Text("Enter your name") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Country Selection
                            var expandedCountry by remember { mutableStateOf(false) }
                            val countries = listOf(
                                "United States", "Canada", "United Kingdom", "Australia", 
                                "New Zealand", "South Africa", "India", "Singapore", 
                                "Hong Kong", "United Arab Emirates", "Qatar", "Kuwait",
                                "Saudi Arabia", "Egypt", "Kenya", "Nigeria", "Ghana",
                                "Germany", "France", "Spain", "Italy", "Netherlands",
                                "Sweden", "Norway", "Denmark", "Finland", "Japan",
                                "South Korea", "China", "Thailand", "Malaysia", "Philippines",
                                "Indonesia", "Vietnam", "Brazil", "Mexico", "Argentina",
                                "Chile", "Colombia", "Peru", "Other"
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = expandedCountry,
                                onExpandedChange = { expandedCountry = !expandedCountry }
                            ) {
                                OutlinedTextField(
                                    value = studentCountry,
                                    onValueChange = { },
                                    readOnly = true,
                                    label = { Text("Country/Region") },
                                    placeholder = { Text("Select your country") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Public, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCountry)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    singleLine = true
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = expandedCountry,
                                    onDismissRequest = { expandedCountry = false }
                                ) {
                                    countries.forEach { country ->
                                        DropdownMenuItem(
                                            text = { Text(country) },
                                            onClick = {
                                                studentCountry = country
                                                expandedCountry = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "Grade Level: $studentGrade",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Slider(
                                value = studentGrade.toFloat(),
                                onValueChange = { studentGrade = it.toInt() },
                                valueRange = 1f..12f,
                                steps = 10,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Grade level indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Grade 1", style = MaterialTheme.typography.labelSmall)
                                Text("Grade 12", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (studentName.isNotBlank()) {
                                    viewModel.initializeQuizWithStudent(studentName, studentGrade, studentCountry)
                                    showStudentDialog = false
                                }
                            },
                            enabled = studentName.isNotBlank()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start Learning")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                // Allow to continue without student info
                                showStudentDialog = false
                            }
                        ) {
                            Text("Skip")
                        }
                    }
                )
            }


            // Error snackbar with proper dismissal
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(
                            onClick = {
                                viewModel.clearError()
                            }
                        ) {
                            Text("Dismiss")
                        }
                    },
                    dismissAction = {
                        IconButton(
                            onClick = {
                                viewModel.clearError()
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss"
                            )
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
    state: QuizGeneratorViewModelState,
    viewModel: QuizGeneratorViewModel,
    onModeChange: (QuizMode) -> Unit,
    onGenerateQuiz: (Subject, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var subject by remember { mutableStateOf<Subject?>(null) }
    var topicText by remember { mutableStateOf("") }
    var questionCnt by remember { mutableFloatStateOf(10f) }
    var showDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create a new adaptive quiz", style = MaterialTheme.typography.headlineSmall)

        // Student info card
        state.studentName?.let { name ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Welcome, $name!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Grade ${state.studentGrade} curriculum-aligned quizzes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

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
                    onClick = { showDropdown = true },
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
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
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
                                state.studentGrade?.let { grade ->
                                    viewModel.loadCurriculumTopics(grade)   // ✅ pass ONLY the grade
                                }
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

        // Topic input
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
    
    // Handle empty questions list
    if (quiz.questions.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No questions available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "There was an issue generating questions. Please try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onQuizComplete) {
                    Text("Go Back")
                }
            }
        }
        return
    }
    
    val q = quiz.questions[index]
    var chosenAnswer by remember(q.id) { mutableStateOf<String?>(null) }
    var showHint by remember(q.id) { mutableStateOf(false) }
    var showConceptDialog by remember { mutableStateOf(false) }

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
                    onClick = { showConceptDialog = true },
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

            // Use the isCorrect field that was already calculated by the ViewModel
            val isCorrect = q.isCorrect

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isCorrect)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Cancel,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isCorrect)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isCorrect) "Correct!" else "Incorrect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCorrect)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // Show the feedback text
                    Text(
                        q.feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCorrect)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )

                    // If incorrect, clearly show the correct answer
                    if (!isCorrect) {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Correct answer:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        q.correctAnswer,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

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
                                tint = if (isCorrect)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                q.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCorrect)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Concepts covered
                    if (q.conceptsCovered.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
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
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
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
    
    // Concept coverage dialog
    if (showConceptDialog) {
        AlertDialog(
            onDismissRequest = { showConceptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Quiz Concepts")
                }
            },
            text = {
                Column {
                    Text(
                        "This quiz covers the following concepts:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    conceptCoverage.forEach { (concept, count) ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$concept ($count question${if (count != 1) "s" else ""})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConceptDialog = false }) {
                    Text("Got it!")
                }
            }
        )
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
                        prefix = "${('A'..'Z').toList().getOrElse(idx) { 'A' + idx }}. "
                    )
                    if (idx < validOptions.size - 1) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            QuestionType.TRUE_FALSE -> {
                // For True/False, ensure we show only True/False buttons
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
                            Spacer(modifier = Modifier.width(8.dp))
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

/* ---------------------------------------------------------------------
 * Answer option helper
 * -------------------------------------------------------------------*/
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
    // Pick colours
    val (container, content) = when {
        isCorrect  -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        isWrong    -> MaterialTheme.colorScheme.error   to MaterialTheme.colorScheme.onError
        isSelected -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSurface
        else       -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }

    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = container,
            contentColor   = content
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
            // Result icon
            when {
                isCorrect -> Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                isWrong   -> Icon(Icons.Default.Cancel,      contentDescription = null, modifier = Modifier.size(20.dp))
                else -> {}
            }.also {
                if (isCorrect || isWrong) Spacer(Modifier.width(8.dp))
            }

            // Optional prefix (e.g. “A. ”)
            if (prefix.isNotBlank()) {
                Text(
                    text = prefix,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Option text
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* Enhanced progress indicator with mode info ----------------------------------- */

@Composable
private fun GenerationProgress(
    done: Int,
    total: Int = 10,
    mode: QuizMode,
    studentGrade: Int? = null,
    modifier: Modifier = Modifier
) {
    val state by (hiltViewModel<QuizGeneratorViewModel>()).state.collectAsStateWithLifecycle()

    // Animation states for creative loading
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val bookRotation by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "book_rotation"
    )

    val pencilOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pencil_offset"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Creative animated icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background circle with pulse
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
            )

            // Book icon rotating
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .rotate(bookRotation)
                    .offset(y = (-10).dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Pencil writing animation
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = pencilOffset.dp, y = pencilOffset.dp)
                    .rotate(-45f),
                tint = MaterialTheme.colorScheme.secondary
            )

            // Sparkles around
            repeat(3) { index ->
                val sparkleRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000 + index * 500, easing = LinearEasing)
                    ),
                    label = "sparkle_$index"
                )

                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .offset(x = 40.dp)
                        .rotate(sparkleRotation + index * 120f),
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Dynamic status text with animation
        AnimatedContent(
            targetState = state.generationPhase,
            transitionSpec = {
                fadeIn() + expandVertically() togetherWith fadeOut() + shrinkVertically()
            },
            label = "phase"
        ) { phase ->
            Text(
                text = phase,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Show question count progress
        if (done > 0) {
            Text(
                text = "$done of $total questions generated",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            when (mode) {
                QuizMode.NORMAL -> "Creating a fresh quiz just for you"
                QuizMode.REVIEW -> "Finding questions you haven't seen in a while"
                QuizMode.ADAPTIVE -> "Tailoring difficulty to your skill level"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Progress with animated questions counter
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
            )

            // Questions counter bubble
            if (done > 0) {
                Card(
                    modifier = Modifier.offset(y = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$done questions ready",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Trivia section while waiting
        TriviaWhileWaiting(studentGrade = studentGrade)
    }
}

/* Trivia component to entertain users during quiz generation */
@Composable
private fun TriviaWhileWaiting(
    studentGrade: Int?,
    modifier: Modifier = Modifier
) {
    var currentTriviaQuestion by remember { mutableStateOf<Question?>(null) }
    var showAnswer by remember { mutableStateOf(false) }
    var userAnswer by remember { mutableStateOf<String?>(null) }

    // Get a new trivia question periodically or on first load
    LaunchedEffect(Unit) {
        currentTriviaQuestion = QuizTrivia.getGradeAppropriateTriviaQuestions(studentGrade, 1).firstOrNull()
    }

    // Auto-refresh trivia question every 15 seconds
    LaunchedEffect(currentTriviaQuestion) {
        while (true) {
            delay(15000) // 15 seconds
            currentTriviaQuestion = QuizTrivia.getGradeAppropriateTriviaQuestions(studentGrade, 1).firstOrNull()
            showAnswer = false
            userAnswer = null
        }
    }

    currentTriviaQuestion?.let { question ->
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "While you wait... Trivia Time! 🎲",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Show question pool info
                    val availableCount = QuizTrivia.getAvailableQuestionCount(studentGrade)
                    val gradeText = when {
                        studentGrade == null -> "All questions available"
                        studentGrade == 0 -> "Kindergarten questions"
                        else -> "K-${studentGrade} questions available"
                    }
                    
                    Text(
                        "$gradeText • $availableCount total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Question
                Text(
                    text = question.questionText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Answer options based on question type
                when (question.questionType) {
                    QuestionType.MULTIPLE_CHOICE -> {
                        question.options.forEach { option ->
                            val isSelected = userAnswer == option
                            val isCorrect = showAnswer && option == question.correctAnswer
                            val isWrong = showAnswer && isSelected && option != question.correctAnswer

                            ElevatedButton(
                                onClick = {
                                    if (!showAnswer) {
                                        userAnswer = option
                                        showAnswer = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = when {
                                        isCorrect -> MaterialTheme.colorScheme.primary
                                        isWrong -> MaterialTheme.colorScheme.error
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                ),
                                enabled = !showAnswer
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCorrect) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else if (isWrong) {
                                        Icon(
                                            Icons.Default.Cancel,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(option)
                                }
                            }
                        }
                    }

                    QuestionType.TRUE_FALSE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("True", "False").forEach { option ->
                                val isSelected = userAnswer == option
                                val isCorrect = showAnswer && option == question.correctAnswer
                                val isWrong = showAnswer && isSelected && option != question.correctAnswer

                                ElevatedButton(
                                    onClick = {
                                        if (!showAnswer) {
                                            userAnswer = option
                                            showAnswer = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = when {
                                            isCorrect -> MaterialTheme.colorScheme.primary
                                            isWrong -> MaterialTheme.colorScheme.error
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    enabled = !showAnswer
                                ) {
                                    if (isCorrect) {
                                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                    } else if (isWrong) {
                                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                    } else {
                                        Icon(
                                            if (option == "True") Icons.Default.Check else Icons.Default.Close,
                                            null,
                                            Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(option)
                                }
                            }
                        }
                    }

                    QuestionType.FILL_IN_BLANK -> {
                        var textInput by remember { mutableStateOf("") }

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Your answer") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !showAnswer,
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))

                        if (!showAnswer) {
                            Button(
                                onClick = {
                                    userAnswer = textInput
                                    showAnswer = true
                                },
                                enabled = textInput.isNotBlank()
                            ) {
                                Text("Submit")
                            }
                        }
                    }

                    else -> {
                        // For other question types, just show the answer after a delay
                        LaunchedEffect(question.id) {
                            delay(3000)
                            showAnswer = true
                        }
                    }
                }

                // Show explanation when answer is revealed
                AnimatedVisibility(
                    visible = showAnswer,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Answer: ${question.correctAnswer}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                
                                if (question.explanation.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = question.explanation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Fun fact or encouraging message
                if (showAnswer) {
                    Spacer(Modifier.height(12.dp))
                    
                    Text(
                        text = QuizTrivia.getRandomFunFact(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Next question button
                if (showAnswer) {
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = {
                            currentTriviaQuestion = QuizTrivia.getGradeAppropriateTriviaQuestions(studentGrade, 1).firstOrNull()
                            showAnswer = false
                            userAnswer = null
                        }
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Question")
                    }
                }
            }
        }
    }
}

/* Empty state setup (unchanged but with mode selector) -------------------- */

@Composable
fun EmptyStateQuizSetup(
    mode: QuizMode,
    studentName: String?,            // ← new
    studentGrade: Int?,              // ← new
    curriculumTopics: List<String>,  // ← new

    onModeChange: (QuizMode) -> Unit,
    onGenerateQuiz: (Subject, String, Int) -> Unit,
) {
    var subject    by remember { mutableStateOf<Subject?>(null) }
    var topicText  by remember { mutableStateOf("") }
    var questionCnt by remember { mutableFloatStateOf(10f) }

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

        /* ─── Mode selector ──────────────────────────────────────── */
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
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
                            onClick  = { onModeChange(qMode) },
                            label    = { Text(qMode.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        /* ─── Subject chooser ────────────────────────────────────── */
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
                            onClick  = { subject = s }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.name)
                    }
                }
            }
        }

        /* ─── Topic input ────────────────────────────────────────── */
        OutlinedTextField(
            value = topicText,
            onValueChange = { topicText = it },
            label = { Text("Topic (optional)") },
            placeholder = { Text("e.g., Linear Equations") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        /* ─── Grade‑aligned topic suggestions ───────────────────── */
        if (curriculumTopics.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Suggested Topics for Grade ${studentGrade ?: "-"}:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(curriculumTopics) { topic ->
                            SuggestionChip(
                                onClick = { topicText = topic },
                                label   = { Text(topic) }
                            )
                        }
                    }
                }
            }
        }

        /* ─── Question count slider ──────────────────────────────── */
        Column(Modifier.fillMaxWidth()) {
            Text("Number of questions: ${questionCnt.toInt()}")
            Slider(
                value = questionCnt,
                onValueChange = { questionCnt = it },
                valueRange = 5f..20f,
                steps = 14
            )
        }

        /* ─── Generate button ────────────────────────────────────── */
        Button(
            onClick = {
                subject?.let { s ->
                    onGenerateQuiz(s, topicText.trim(), questionCnt.toInt())
                }
            },
            enabled  = subject != null && studentName != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                when (mode) {
                    QuizMode.NORMAL   -> Icons.Default.PlayArrow
                    QuizMode.REVIEW   -> Icons.Default.Refresh
                    QuizMode.ADAPTIVE -> Icons.Default.AutoAwesome
                },
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when (mode) {
                    QuizMode.NORMAL   -> "Generate Grade ${studentGrade ?: ""} Quiz"
                    QuizMode.REVIEW   -> "Start Review Session"
                    QuizMode.ADAPTIVE -> "Start Adaptive Quiz"
                }
            )
        }
    }
}
