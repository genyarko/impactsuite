import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../domain/quiz_models.dart';
import 'quiz_controller.dart';
import 'quiz_providers.dart';

class QuizPage extends ConsumerStatefulWidget {
  const QuizPage({super.key});

  @override
  ConsumerState<QuizPage> createState() => _QuizPageState();
}

class _QuizPageState extends ConsumerState<QuizPage> {
  final TextEditingController _studentNameController = TextEditingController();
  final TextEditingController _regionController = TextEditingController();
  final TextEditingController _topicController = TextEditingController();
  final TextEditingController _answerController = TextEditingController();

  int? _studentGrade;

  // Trivia state (during generation)
  String? _triviaQuestion;
  List<String> _triviaOptions = const [];
  String? _triviaAnswer;
  String? _selectedTriviaAnswer;
  bool _showTriviaAnswer = false;
  Timer? _triviaRotationTimer;

  static const List<int> _gradeLevels = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  static const List<String> _curriculumTopics = [
    'Linear Equations',
    'Cell Structure',
    'World War II',
    'Poetry Analysis',
    'Map Skills',
  ];

  @override
  void initState() {
    super.initState();
    _loadTrivia();
  }

  @override
  void dispose() {
    _triviaRotationTimer?.cancel();
    _studentNameController.dispose();
    _regionController.dispose();
    _topicController.dispose();
    _answerController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final quizState = ref.watch(quizControllerProvider);

    // Start/stop trivia timer based on phase
    if (quizState.phase == QuizPhase.generating && _triviaRotationTimer == null) {
      _triviaRotationTimer = Timer.periodic(const Duration(seconds: 15), (_) => _loadTrivia());
    } else if (quizState.phase != QuizPhase.generating && _triviaRotationTimer != null) {
      _triviaRotationTimer?.cancel();
      _triviaRotationTimer = null;
    }

    return SafeArea(
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 250),
        child: switch (quizState.phase) {
          QuizPhase.profile => _buildProfileStep(context, quizState),
          QuizPhase.setup => _buildSetupState(context, quizState),
          QuizPhase.generating => _buildGenerationState(context, quizState),
          QuizPhase.taking => _buildTakingState(context, quizState),
          QuizPhase.results => _buildResultsState(context, quizState),
        },
      ),
    );
  }

  // ===========================================================================
  // Profile Phase
  // ===========================================================================

  Widget _buildProfileStep(BuildContext context, QuizState quizState) {
    return ListView(
      key: const ValueKey('profile-step'),
      padding: const EdgeInsets.all(16),
      children: [
        Text('Learner profile', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 8),
        Text(
          "Tell us the student's name, country/region, and grade level for better quiz tuning.",
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                TextField(
                  controller: _studentNameController,
                  onChanged: (_) => setState(() {}),
                  decoration: const InputDecoration(
                    labelText: 'Student name',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _regionController,
                  onChanged: (_) => setState(() {}),
                  decoration: const InputDecoration(
                    labelText: 'Country / Region',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  value: _studentGrade,
                  decoration: const InputDecoration(
                    labelText: 'Grade level',
                    border: OutlineInputBorder(),
                  ),
                  items: _gradeLevels
                      .map(
                        (grade) => DropdownMenuItem<int>(
                          value: grade,
                          child: Text(grade == 0 ? 'Kindergarten' : 'Grade $grade'),
                        ),
                      )
                      .toList(),
                  onChanged: (value) => setState(() => _studentGrade = value),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: _canContinueProfile
              ? () {
                  ref.read(quizControllerProvider.notifier).setProfile(
                        _studentNameController.text.trim(),
                        _regionController.text.trim(),
                        _studentGrade,
                      );
                }
              : null,
          icon: const Icon(Icons.arrow_forward),
          label: const Text('Continue'),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () {
            ref.read(quizControllerProvider.notifier).skipProfile();
          },
          icon: const Icon(Icons.skip_next),
          label: const Text('Skip for now'),
        ),
      ],
    );
  }

  bool get _canContinueProfile {
    return _studentNameController.text.trim().isNotEmpty &&
        _regionController.text.trim().isNotEmpty &&
        _studentGrade != null;
  }

  // ===========================================================================
  // Setup Phase
  // ===========================================================================

  Widget _buildSetupState(BuildContext context, QuizState quizState) {
    final theme = Theme.of(context);
    final controller = ref.read(quizControllerProvider.notifier);

    return ListView(
      key: const ValueKey('setup'),
      padding: const EdgeInsets.all(16),
      children: [
        Text('Create a new adaptive quiz', style: theme.textTheme.headlineSmall),
        const SizedBox(height: 8),
        if (quizState.generationError != null) ...[
          Card(
            color: theme.colorScheme.errorContainer,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Icon(Icons.error_outline, color: theme.colorScheme.onErrorContainer),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      quizState.generationError!,
                      style: TextStyle(color: theme.colorScheme.onErrorContainer),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
        ],
        _ModeCard(
          mode: quizState.mode,
          onModeChanged: (mode) => controller.updateSetup(mode: mode),
        ),
        const SizedBox(height: 16),
        _SubjectCard(
          selectedSubject: quizState.subject,
          onSubjectChanged: (subject) => controller.updateSetup(subject: subject),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<Difficulty>(
          value: quizState.difficulty,
          decoration: const InputDecoration(
            labelText: 'Difficulty',
            border: OutlineInputBorder(),
          ),
          items: Difficulty.values
              .map(
                (d) => DropdownMenuItem(
                  value: d,
                  child: Text(_titleCase(d.name)),
                ),
              )
              .toList(),
          onChanged: (value) {
            if (value != null) controller.updateSetup(difficulty: value);
          },
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _topicController,
          decoration: const InputDecoration(
            labelText: 'Topic (optional)',
            hintText: 'e.g., Linear Equations',
            border: OutlineInputBorder(),
          ),
          onChanged: (value) => controller.updateSetup(topic: value),
        ),
        const SizedBox(height: 16),
        Card(
          color: theme.colorScheme.secondaryContainer,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Suggested Topics for ${_studentGradeLabel(quizState)}:',
                  style: theme.textTheme.labelLarge,
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: _curriculumTopics
                      .map(
                        (topic) => ActionChip(
                          label: Text(topic),
                          onPressed: () {
                            _topicController.text = topic;
                            controller.updateSetup(topic: topic);
                          },
                        ),
                      )
                      .toList(),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Number of questions: ${quizState.questionCount}'),
                Slider(
                  value: quizState.questionCount.toDouble(),
                  min: 5,
                  max: 20,
                  divisions: 15,
                  onChanged: (value) => controller.updateSetup(questionCount: value.round()),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: quizState.subject == null
              ? null
              : () => ref.read(quizControllerProvider.notifier).generateQuiz(),
          icon: Icon(
            switch (quizState.mode) {
              QuizMode.normal => Icons.play_arrow,
              QuizMode.review => Icons.refresh,
              QuizMode.adaptive => Icons.auto_awesome,
            },
          ),
          label: Text(
            switch (quizState.mode) {
              QuizMode.normal => 'Generate ${_studentGradeLabel(quizState)} Quiz',
              QuizMode.review => 'Start Review Session',
              QuizMode.adaptive => 'Start Adaptive Quiz',
            },
          ),
        ),
      ],
    );
  }

  String _studentGradeLabel(QuizState quizState) {
    if (quizState.studentGrade == null) {
      return quizState.profileSkipped ? 'Any Grade' : 'Not Set';
    }
    return quizState.studentGrade == 0 ? 'Kindergarten' : 'Grade ${quizState.studentGrade}';
  }

  // ===========================================================================
  // Generation Phase
  // ===========================================================================

  Widget _buildGenerationState(BuildContext context, QuizState quizState) {
    final progress = quizState.questionCount == 0
        ? 0.0
        : quizState.generatedCount / quizState.questionCount;

    return ListView(
      key: const ValueKey('progress'),
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                const SizedBox(
                  width: 64,
                  height: 64,
                  child: CircularProgressIndicator(strokeWidth: 6),
                ),
                const SizedBox(height: 16),
                const Text(
                  'Building your quiz...',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 8),
                Text('${quizState.generatedCount} of ${quizState.questionCount} questions generated'),
                const SizedBox(height: 12),
                LinearProgressIndicator(value: progress.clamp(0, 1).toDouble()),
                const SizedBox(height: 12),
                Text(
                  switch (quizState.mode) {
                    QuizMode.normal => 'Creating a fresh quiz just for you',
                    QuizMode.review => 'Finding questions you have not seen in a while',
                    QuizMode.adaptive => 'Tailoring difficulty to your skill level',
                  },
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        _TriviaCard(
          question: _triviaQuestion,
          options: _triviaOptions,
          answer: _triviaAnswer,
          selectedAnswer: _selectedTriviaAnswer,
          showAnswer: _showTriviaAnswer,
          onSelect: (value) {
            if (_showTriviaAnswer) return;
            setState(() {
              _selectedTriviaAnswer = value;
              _showTriviaAnswer = true;
            });
          },
          onNext: _loadTrivia,
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          onPressed: () => ref.read(quizControllerProvider.notifier).cancelGeneration(),
          icon: const Icon(Icons.close),
          label: const Text('Cancel generation'),
        ),
      ],
    );
  }

  // ===========================================================================
  // Taking Phase (NEW)
  // ===========================================================================

  Widget _buildTakingState(BuildContext context, QuizState quizState) {
    final quiz = quizState.quiz;
    if (quiz == null) return const SizedBox.shrink();

    final question = quizState.currentQuestion;
    if (question == null) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final idx = quizState.currentQuestionIndex;
    final total = quiz.questions.length;
    final progress = total == 0 ? 0.0 : (idx + 1) / total;
    final answeredCount = quiz.answeredCount;

    return Column(
      key: const ValueKey('taking'),
      children: [
        // Progress header
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Question ${idx + 1} of $total',
                    style: theme.textTheme.titleMedium,
                  ),
                  Text(
                    '$answeredCount answered',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              LinearProgressIndicator(value: progress),
            ],
          ),
        ),

        // Question + answers
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // Question card
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _QuestionTypeChip(type: question.questionType),
                      const SizedBox(height: 12),
                      Text(
                        question.questionText,
                        style: theme.textTheme.bodyLarge?.copyWith(
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (question.hint.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Icon(Icons.lightbulb_outline,
                                size: 16, color: theme.colorScheme.primary),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                'Hint: ${question.hint}',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  fontStyle: FontStyle.italic,
                                  color: theme.colorScheme.primary,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Answer options
              _AnswerOptions(
                question: question,
                answerController: _answerController,
                onSubmit: (answer) {
                  ref.read(quizControllerProvider.notifier).submitAnswer(answer);
                  _answerController.clear();
                },
              ),

              // Feedback
              if (question.isAnswered) ...[
                const SizedBox(height: 16),
                _FeedbackCard(question: question),
              ],
            ],
          ),
        ),

        // Navigation buttons
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: Row(
            children: [
              if (idx > 0)
                OutlinedButton.icon(
                  onPressed: () => ref.read(quizControllerProvider.notifier).previousQuestion(),
                  icon: const Icon(Icons.arrow_back, size: 18),
                  label: const Text('Previous'),
                ),
              const Spacer(),
              if (idx < total - 1)
                FilledButton.icon(
                  onPressed: () => ref.read(quizControllerProvider.notifier).nextQuestion(),
                  icon: const Icon(Icons.arrow_forward, size: 18),
                  label: const Text('Next'),
                )
              else
                FilledButton.icon(
                  onPressed: () => ref.read(quizControllerProvider.notifier).completeQuiz(),
                  icon: const Icon(Icons.check, size: 18),
                  label: const Text('Finish Quiz'),
                ),
            ],
          ),
        ),
      ],
    );
  }

  // ===========================================================================
  // Results Phase (NEW)
  // ===========================================================================

  Widget _buildResultsState(BuildContext context, QuizState quizState) {
    final quiz = quizState.quiz;
    if (quiz == null) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final correct = quiz.correctCount;
    final total = quiz.totalQuestions;
    final scorePercent = (quizState.score ?? 0) * 100;

    return ListView(
      key: const ValueKey('results'),
      padding: const EdgeInsets.all(16),
      children: [
        // Score circle
        Center(
          child: Column(
            children: [
              SizedBox(
                width: 120,
                height: 120,
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    SizedBox(
                      width: 120,
                      height: 120,
                      child: CircularProgressIndicator(
                        value: (quizState.score ?? 0).clamp(0, 1).toDouble(),
                        strokeWidth: 10,
                        backgroundColor: theme.colorScheme.surfaceContainerHighest,
                        color: scorePercent >= 70
                            ? Colors.green
                            : scorePercent >= 40
                                ? Colors.orange
                                : Colors.red,
                      ),
                    ),
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '${scorePercent.round()}%',
                          style: theme.textTheme.headlineMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          '$correct / $total',
                          style: theme.textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Text(
                _scoreMessage(scorePercent),
                style: theme.textTheme.titleMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 4),
              Text(
                '${_titleCase(quiz.subject.name)} - ${quiz.topic.isNotEmpty ? quiz.topic : "General"}',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Action buttons
        Row(
          children: [
            Expanded(
              child: FilledButton.icon(
                onPressed: () => ref.read(quizControllerProvider.notifier).startNewQuiz(),
                icon: const Icon(Icons.refresh),
                label: const Text('New Quiz'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: () => context.push('/quiz/analytics'),
                icon: const Icon(Icons.bar_chart),
                label: const Text('Analytics'),
              ),
            ),
          ],
        ),
        // Retry failed questions button
        if (quiz.questions.any((q) => q.isAnswered && !q.isCorrect)) ...[
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () =>
                  ref.read(quizControllerProvider.notifier).retryFailedQuestions(),
              icon: const Icon(Icons.replay),
              label: Text(
                'Retry Failed Questions (${quiz.questions.where((q) => q.isAnswered && !q.isCorrect).length})',
              ),
            ),
          ),
        ],
        const SizedBox(height: 24),

        // Per-question review
        Text('Question Review', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        ...List.generate(quiz.questions.length, (i) {
          final q = quiz.questions[i];
          return Card(
            margin: const EdgeInsets.only(bottom: 8),
            child: ListTile(
              leading: CircleAvatar(
                backgroundColor: q.isCorrect
                    ? Colors.green.shade100
                    : q.isAnswered
                        ? Colors.red.shade100
                        : Colors.grey.shade200,
                child: Icon(
                  q.isCorrect
                      ? Icons.check
                      : q.isAnswered
                          ? Icons.close
                          : Icons.remove,
                  color: q.isCorrect
                      ? Colors.green.shade700
                      : q.isAnswered
                          ? Colors.red.shade700
                          : Colors.grey,
                  size: 20,
                ),
              ),
              title: Text(
                q.questionText,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: q.isAnswered
                  ? Text(
                      q.isCorrect
                          ? 'Your answer: ${q.userAnswer}'
                          : 'Your answer: ${q.userAnswer}\nCorrect: ${q.correctAnswer}',
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                    )
                  : const Text('Not answered'),
              isThreeLine: q.isAnswered && !q.isCorrect,
            ),
          );
        }),
      ],
    );
  }

  String _scoreMessage(double percent) {
    if (percent >= 90) return 'Excellent work!';
    if (percent >= 70) return 'Great job!';
    if (percent >= 50) return 'Good effort!';
    if (percent >= 30) return 'Keep practicing!';
    return 'Room for improvement';
  }

  // ===========================================================================
  // Trivia helpers
  // ===========================================================================

  void _loadTrivia() {
    const bank = [
      (
        'Which planet has the shortest day?',
        ['Mercury', 'Earth', 'Jupiter', 'Mars'],
        'Jupiter',
      ),
      (
        'What is the value of pi rounded to 2 decimal places?',
        ['3.12', '3.14', '3.16', '3.18'],
        '3.14',
      ),
      (
        'Who wrote "Romeo and Juliet"?',
        ['Dickens', 'Shakespeare', 'Hemingway', 'Austen'],
        'Shakespeare',
      ),
    ];

    final random = Random();
    final tuple = bank[random.nextInt(bank.length)];
    setState(() {
      _triviaQuestion = tuple.$1;
      _triviaOptions = tuple.$2;
      _triviaAnswer = tuple.$3;
      _selectedTriviaAnswer = null;
      _showTriviaAnswer = false;
    });
  }

  // ===========================================================================
  // Utilities
  // ===========================================================================

  String _titleCase(String value) => value
      .replaceAllMapped(RegExp(r'([a-z])([A-Z])'), (m) => '${m.group(1)} ${m.group(2)}')
      .split('_')
      .map((part) => part.isEmpty ? part : '${part[0].toUpperCase()}${part.substring(1)}')
      .join(' ');
}

// =============================================================================
// Question Type Chip
// =============================================================================

class _QuestionTypeChip extends StatelessWidget {
  const _QuestionTypeChip({required this.type});
  final QuestionType type;

  @override
  Widget build(BuildContext context) {
    final (label, icon) = switch (type) {
      QuestionType.multipleChoice => ('Multiple Choice', Icons.radio_button_checked),
      QuestionType.trueFalse => ('True / False', Icons.toggle_on_outlined),
      QuestionType.fillInBlank => ('Fill in the Blank', Icons.edit_outlined),
      QuestionType.shortAnswer => ('Short Answer', Icons.short_text),
      QuestionType.matching => ('Matching', Icons.compare_arrows),
    };

    return Chip(
      avatar: Icon(icon, size: 16),
      label: Text(label, style: const TextStyle(fontSize: 12)),
      visualDensity: VisualDensity.compact,
    );
  }
}

// =============================================================================
// Answer Options
// =============================================================================

class _AnswerOptions extends StatefulWidget {
  const _AnswerOptions({
    required this.question,
    required this.answerController,
    required this.onSubmit,
  });

  final Question question;
  final TextEditingController answerController;
  final ValueChanged<String> onSubmit;

  @override
  State<_AnswerOptions> createState() => _AnswerOptionsState();
}

class _AnswerOptionsState extends State<_AnswerOptions> {
  String? _selectedOption;

  @override
  void initState() {
    super.initState();
    widget.answerController.addListener(_onTextChanged);
  }

  @override
  void dispose() {
    widget.answerController.removeListener(_onTextChanged);
    super.dispose();
  }

  void _onTextChanged() {
    // Rebuild so the submit button enables/disables properly
    setState(() {});
  }

  @override
  void didUpdateWidget(covariant _AnswerOptions oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.answerController != widget.answerController) {
      oldWidget.answerController.removeListener(_onTextChanged);
      widget.answerController.addListener(_onTextChanged);
    }
    if (oldWidget.question.id != widget.question.id) {
      _selectedOption = null;
      widget.answerController.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    final q = widget.question;

    if (q.isAnswered) {
      // Show answered state
      switch (q.questionType) {
        case QuestionType.multipleChoice:
        case QuestionType.trueFalse:
          return _buildOptionsAnswered(q);
        default:
          return _buildTextAnswered(q);
      }
    }

    switch (q.questionType) {
      case QuestionType.multipleChoice:
        return _buildMultipleChoice(q);
      case QuestionType.trueFalse:
        return _buildTrueFalse(q);
      case QuestionType.fillInBlank:
      case QuestionType.shortAnswer:
        return _buildTextInput(q);
      case QuestionType.matching:
        return _buildTextInput(q);
    }
  }

  Widget _buildMultipleChoice(Question q) {
    return Column(
      children: [
        ...q.options.map((option) {
          final isSelected = _selectedOption == option;
          return Card(
            margin: const EdgeInsets.only(bottom: 8),
            color: isSelected
                ? Theme.of(context).colorScheme.primaryContainer
                : null,
            child: ListTile(
              leading: Radio<String>(
                value: option,
                groupValue: _selectedOption,
                onChanged: (value) => setState(() => _selectedOption = value),
              ),
              title: Text(option),
              onTap: () => setState(() => _selectedOption = option),
            ),
          );
        }),
        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: _selectedOption == null
                ? null
                : () => widget.onSubmit(_selectedOption!),
            child: const Text('Submit Answer'),
          ),
        ),
      ],
    );
  }

  Widget _buildTrueFalse(Question q) {
    return Row(
      children: [
        Expanded(
          child: FilledButton.tonal(
            onPressed: () => widget.onSubmit('True'),
            style: FilledButton.styleFrom(
              minimumSize: const Size(0, 56),
            ),
            child: const Text('True', style: TextStyle(fontSize: 18)),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: FilledButton.tonal(
            onPressed: () => widget.onSubmit('False'),
            style: FilledButton.styleFrom(
              minimumSize: const Size(0, 56),
            ),
            child: const Text('False', style: TextStyle(fontSize: 18)),
          ),
        ),
      ],
    );
  }

  Widget _buildTextInput(Question q) {
    return Column(
      children: [
        TextField(
          controller: widget.answerController,
          decoration: InputDecoration(
            labelText: q.questionType == QuestionType.fillInBlank
                ? 'Fill in the blank'
                : 'Type your answer',
            border: const OutlineInputBorder(),
          ),
          maxLines: q.questionType == QuestionType.shortAnswer ? 3 : 1,
          textInputAction: TextInputAction.done,
          onSubmitted: (value) {
            if (value.trim().isNotEmpty) widget.onSubmit(value.trim());
          },
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: widget.answerController.text.trim().isEmpty
                ? null
                : () => widget.onSubmit(widget.answerController.text.trim()),
            child: const Text('Submit Answer'),
          ),
        ),
      ],
    );
  }

  Widget _buildOptionsAnswered(Question q) {
    final colorScheme = Theme.of(context).colorScheme;
    return Column(
      children: q.options.map((option) {
        final isUser = option == q.userAnswer;
        final isCorrect = option.toLowerCase() == q.correctAnswer.toLowerCase();
        final background = isCorrect
            ? Colors.green.shade50
            : isUser
                ? Colors.red.shade50
                : null;

        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          color: background,
          child: ListTile(
            leading: Icon(
              isCorrect
                  ? Icons.check_circle
                  : isUser
                      ? Icons.cancel
                      : Icons.circle_outlined,
              color: isCorrect
                  ? Colors.green
                  : isUser
                      ? Colors.red
                      : colorScheme.outline,
            ),
            title: Text(option),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildTextAnswered(Question q) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Your answer:', style: Theme.of(context).textTheme.labelMedium),
            const SizedBox(height: 4),
            Text(q.userAnswer ?? 'No answer', style: Theme.of(context).textTheme.bodyLarge),
          ],
        ),
      ),
    );
  }
}

// =============================================================================
// Feedback Card
// =============================================================================

class _FeedbackCard extends StatelessWidget {
  const _FeedbackCard({required this.question});
  final Question question;

  @override
  Widget build(BuildContext context) {
    final isCorrect = question.isCorrect;
    final color = isCorrect ? Colors.green : Colors.red;

    return Card(
      color: color.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              isCorrect ? Icons.check_circle : Icons.cancel,
              color: color.shade700,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                question.feedback,
                style: TextStyle(color: color.shade900),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// =============================================================================
// Mode Card
// =============================================================================

class _ModeCard extends StatelessWidget {
  const _ModeCard({required this.mode, required this.onModeChanged});

  final QuizMode mode;
  final ValueChanged<QuizMode> onModeChanged;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Quiz Mode', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: QuizMode.values
                  .map(
                    (value) => ChoiceChip(
                      label: Text(_title(value.name)),
                      selected: value == mode,
                      onSelected: (_) => onModeChanged(value),
                    ),
                  )
                  .toList(),
            ),
          ],
        ),
      ),
    );
  }

  String _title(String value) => value[0].toUpperCase() + value.substring(1);
}

// =============================================================================
// Subject Card
// =============================================================================

class _SubjectCard extends StatelessWidget {
  const _SubjectCard({required this.selectedSubject, required this.onSubjectChanged});

  final Subject? selectedSubject;
  final ValueChanged<Subject> onSubjectChanged;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Select Subject:', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ...Subject.values.map(
              (subject) => RadioListTile<Subject>(
                dense: true,
                title: Text(_label(subject.name)),
                value: subject,
                groupValue: selectedSubject,
                onChanged: (value) {
                  if (value != null) onSubjectChanged(value);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _label(String value) {
    return value
        .replaceAllMapped(RegExp(r'([a-z])([A-Z])'), (m) => '${m.group(1)} ${m.group(2)}')
        .split('_')
        .map((part) => part[0].toUpperCase() + part.substring(1))
        .join(' ');
  }
}

// =============================================================================
// Trivia Card
// =============================================================================

class _TriviaCard extends StatelessWidget {
  const _TriviaCard({
    required this.question,
    required this.options,
    required this.answer,
    required this.selectedAnswer,
    required this.showAnswer,
    required this.onSelect,
    required this.onNext,
  });

  final String? question;
  final List<String> options;
  final String? answer;
  final String? selectedAnswer;
  final bool showAnswer;
  final ValueChanged<String> onSelect;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    if (question == null || answer == null) {
      return const SizedBox.shrink();
    }

    final scheme = Theme.of(context).colorScheme;

    return Card(
      color: scheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.quiz, color: scheme.primary),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    'While you wait... Trivia Time!',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(question!),
            const SizedBox(height: 12),
            ...options.map((option) {
              final isSelected = selectedAnswer == option;
              final isCorrect = showAnswer && option == answer;
              final isWrong = showAnswer && isSelected && option != answer;
              final background = isCorrect
                  ? scheme.primaryContainer
                  : isWrong
                      ? scheme.errorContainer
                      : Colors.transparent;

              return Container(
                margin: const EdgeInsets.only(bottom: 8),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  color: background,
                ),
                child: ListTile(
                  dense: true,
                  title: Text(option),
                  trailing: showAnswer
                      ? Icon(
                          isCorrect
                              ? Icons.check_circle
                              : isWrong
                                  ? Icons.cancel
                                  : Icons.circle_outlined,
                        )
                      : null,
                  onTap: showAnswer ? null : () => onSelect(option),
                ),
              );
            }),
            if (showAnswer) ...[
              const SizedBox(height: 4),
              Text('Answer: $answer', style: const TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: onNext,
                icon: const Icon(Icons.refresh),
                label: const Text('New Question'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
