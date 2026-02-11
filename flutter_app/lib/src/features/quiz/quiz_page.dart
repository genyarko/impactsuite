import 'package:flutter/material.dart';

import '../../domain/quiz_models.dart';
import '../../domain/services/answer_checker.dart';
import '../../domain/services/quiz_prompt_generator.dart';

class QuizPage extends StatefulWidget {
  const QuizPage({super.key});

  @override
  State<QuizPage> createState() => _QuizPageState();
}

class _QuizPageState extends State<QuizPage> {
  final _checker = const AnswerChecker();
  final _generator = const QuizPromptGenerator();
  final _answerController = TextEditingController();

  final _sampleQuestion = const Question(
    questionText: 'What process do plants use to make food?',
    questionType: QuestionType.shortAnswer,
    correctAnswer: 'Photosynthesis',
    difficulty: Difficulty.easy,
  );

  String? _feedback;

  @override
  void dispose() {
    _answerController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final promptPreview = _generator.createStructuredPrompt(
      questionType: QuestionType.multipleChoice,
      subject: Subject.science,
      topic: 'Photosynthesis',
      difficulty: Difficulty.easy,
      previousQuestions: const ['What is chlorophyll?', 'Why do leaves look green?'],
    );

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text('Quiz Generator (Domain-first)', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 16),
          Text(_sampleQuestion.questionText),
          const SizedBox(height: 8),
          TextField(
            controller: _answerController,
            decoration: const InputDecoration(labelText: 'Your answer'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: () {
              final correct = _checker.checkAnswerVariations(
                userAnswer: _answerController.text,
                correctAnswer: _sampleQuestion.correctAnswer
              );
              setState(() {
                _feedback = correct ? '✅ Correct (flexible match)' : '❌ Try again';
              });
            },
            child: const Text('Check answer'),
          ),
          if (_feedback != null) ...[
            const SizedBox(height: 8),
            Text(_feedback!),
          ],
          const SizedBox(height: 24),
          Text('Prompt generator preview', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          SelectableText(promptPreview, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}
