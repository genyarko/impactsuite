import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';

import '../../domain/quiz_models.dart';

enum QuizMode { normal, review, adaptive }

class QuizPage extends StatefulWidget {
  const QuizPage({super.key});

  @override
  State<QuizPage> createState() => _QuizPageState();
}

class _QuizPageState extends State<QuizPage> {
  final TextEditingController _topicController = TextEditingController();
  final List<String> _curriculumTopics = const [
    'Linear Equations',
    'Cell Structure',
    'World War II',
    'Poetry Analysis',
    'Map Skills',
  ];

  Subject? _selectedSubject;
  Difficulty _selectedDifficulty = Difficulty.medium;
  QuizMode _selectedMode = QuizMode.normal;
  int _questionCount = 10;
  bool _isGenerating = false;
  int _generatedCount = 0;
  Timer? _generationTimer;

  String? _triviaQuestion;
  List<String> _triviaOptions = const [];
  String? _triviaAnswer;
  String? _selectedTriviaAnswer;
  bool _showTriviaAnswer = false;
  Timer? _triviaRotationTimer;

  static const String _studentName = 'Student';
  static const int _studentGrade = 8;

  @override
  void initState() {
    super.initState();
    _loadTrivia();
    _triviaRotationTimer = Timer.periodic(const Duration(seconds: 15), (_) => _loadTrivia());
  }

  @override
  void dispose() {
    _generationTimer?.cancel();
    _triviaRotationTimer?.cancel();
    _topicController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 250),
        child: _isGenerating ? _buildGenerationState(context) : _buildSetupState(context),
      ),
    );
  }

  Widget _buildSetupState(BuildContext context) {
    final theme = Theme.of(context);

    return ListView(
      key: const ValueKey('setup'),
      padding: const EdgeInsets.all(16),
      children: [
        Text('Create a new adaptive quiz', style: theme.textTheme.headlineSmall),
        const SizedBox(height: 8),
        Text(
          'No educational content loaded. You can still create a quiz!',
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 16),
        _ModeCard(
          mode: _selectedMode,
          onModeChanged: (mode) => setState(() => _selectedMode = mode),
        ),
        const SizedBox(height: 16),
        _SubjectCard(
          selectedSubject: _selectedSubject,
          onSubjectChanged: (subject) => setState(() => _selectedSubject = subject),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<Difficulty>(
          value: _selectedDifficulty,
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
            if (value == null) return;
            setState(() => _selectedDifficulty = value);
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
                  'Suggested Topics for Grade $_studentGrade:',
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
                          onPressed: () => setState(() => _topicController.text = topic),
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
                Text('Number of questions: $_questionCount'),
                Slider(
                  value: _questionCount.toDouble(),
                  min: 5,
                  max: 20,
                  divisions: 15,
                  onChanged: (value) => setState(() => _questionCount = value.round()),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: (_selectedSubject == null || _studentName.isEmpty) ? null : _startGeneration,
          icon: Icon(
            switch (_selectedMode) {
              QuizMode.normal => Icons.play_arrow,
              QuizMode.review => Icons.refresh,
              QuizMode.adaptive => Icons.auto_awesome,
            },
          ),
          label: Text(
            switch (_selectedMode) {
              QuizMode.normal => 'Generate Grade $_studentGrade Quiz',
              QuizMode.review => 'Start Review Session',
              QuizMode.adaptive => 'Start Adaptive Quiz',
            },
          ),
        ),
      ],
    );
  }

  Widget _buildGenerationState(BuildContext context) {
    final progress = _questionCount == 0 ? 0.0 : _generatedCount / _questionCount;

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
                Text('$_generatedCount of $_questionCount questions generated'),
                const SizedBox(height: 12),
                LinearProgressIndicator(value: progress.clamp(0, 1)),
                const SizedBox(height: 12),
                Text(
                  switch (_selectedMode) {
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
          onPressed: _cancelGeneration,
          icon: const Icon(Icons.close),
          label: const Text('Cancel generation'),
        ),
      ],
    );
  }

  void _startGeneration() {
    setState(() {
      _isGenerating = true;
      _generatedCount = 0;
    });

    _generationTimer?.cancel();
    _generationTimer = Timer.periodic(const Duration(milliseconds: 400), (timer) {
      if (!mounted) return;
      if (_generatedCount >= _questionCount) {
        timer.cancel();
        setState(() => _isGenerating = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Quiz ready: ${_titleCase(_selectedSubject!.name)} • ${_titleCase(_selectedDifficulty.name)}',
            ),
          ),
        );
        return;
      }
      setState(() => _generatedCount++);
    });
  }

  void _cancelGeneration() {
    _generationTimer?.cancel();
    setState(() {
      _isGenerating = false;
      _generatedCount = 0;
    });
  }

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

  String _titleCase(String value) => value
      .split('_')
      .map((part) => part.isEmpty ? part : '${part[0].toUpperCase()}${part.substring(1)}')
      .join(' ');
}

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
                  if (value != null) {
                    onSubjectChanged(value);
                  }
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
                    'While you wait... Trivia Time! 🎲',
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
