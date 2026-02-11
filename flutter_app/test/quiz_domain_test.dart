import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/domain/quiz_models.dart';
import 'package:impactsuite_flutter/src/domain/services/answer_checker.dart';
import 'package:impactsuite_flutter/src/domain/services/quiz_prompt_generator.dart';

void main() {
  group('AnswerChecker', () {
    const checker = AnswerChecker();

    test('normalizes answers', () {
      expect(checker.normalizeAnswer(' The Photosynthesis! '), 'photosynthesis');
    });

    test('accepts known variation', () {
      final result = checker.checkAnswerVariations(
        userAnswer: 'photo synthesis',
        correctAnswer: 'Photosynthesis',
      );
      expect(result, isTrue);
    });

    test('rejects weak flexible answer', () {
      expect(checker.isFlexibleAnswer('answers may vary', 'idk'), isFalse);
    });
  });

  group('QuizPromptGenerator', () {
    const generator = QuizPromptGenerator();

    test('builds structured prompt with required sections', () {
      final prompt = generator.createStructuredPrompt(
        questionType: QuestionType.multipleChoice,
        subject: Subject.science,
        topic: 'Plant cells',
        difficulty: Difficulty.medium,
        previousQuestions: const ['What is a cell wall?'],
      );

      expect(prompt, contains('TASK: Create ONE'));
      expect(prompt, contains('CRITICAL AVOIDANCE'));
      expect(prompt, contains('OUTPUT FORMAT'));
    });
  });
}
