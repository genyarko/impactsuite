import '../quiz_models.dart';

class QuizPromptGenerator {
  const QuizPromptGenerator();

  String createStructuredPrompt({
    required QuestionType questionType,
    required Subject subject,
    required String topic,
    required Difficulty difficulty,
    String region = '',
    int? gradeLevel,
    LearnerProfile? learnerProfile,
    List<String> previousQuestions = const [],
  }) {
    final difficultyContext = _getComplexityDescriptor(difficulty);
    final details = _getQuestionTypeInstructions(questionType);
    final avoidancePattern = _getAvoidancePatterns(previousQuestions);

    final learnerContext = learnerProfile == null
        ? ''
        : '''
LEARNER PROFILE:
- Total Questions Answered: ${learnerProfile.totalQuestionsAnswered}
- Mastered Concepts: ${learnerProfile.masteredConcepts.take(5).join(', ')}
- Strengths by Subject: ${learnerProfile.strengthsBySubject.entries.take(3).map((entry) => '${entry.key.name}: ${(entry.value * 100).toInt()}%').join(', ')}
''';

    final regionContext = region.isNotEmpty
        ? '\nREGIONAL CONTEXT:\n'
            '- Country/Region: $region\n'
            '${gradeLevel != null ? '- Grade Level: ${gradeLevel == 0 ? 'Kindergarten' : 'Grade $gradeLevel'}\n' : ''}'
            '- Tailor questions to the curriculum, history, geography, culture, and examples relevant to $region\n'
            '- Use place names, historical figures, and events familiar to students in $region\n'
        : '';

    return '''
You are an expert educational content creator specializing in ${subject.name}.

TASK: Create ONE $difficultyContext ${questionType.name} question.

CONTEXT:
- Subject: ${subject.name}
- Topic: $topic
- Difficulty: ${difficulty.name}

$learnerContext$regionContext
REQUIREMENTS:
${details.instructions}
$avoidancePattern

CRITICAL RULES:
- Question must be directly related to $topic
- Ensure $difficultyContext complexity level
- Provide clear, educational explanations
- Use proper grammar and clear language
- Make content engaging and relevant

OUTPUT FORMAT:
Return ONLY valid JSON in this exact format (no markdown, no extra text):
${details.format}
''';
  }

  String _getAvoidancePatterns(List<String> previousQuestions) {
    if (previousQuestions.isEmpty) return '';
    final recent = previousQuestions.take(3).map((q) => '- ${q.substring(0, q.length > 60 ? 60 : q.length)}...').join('\n');
    return '''
CRITICAL AVOIDANCE:
Create a question completely different from these recent ones:
$recent

- Use different question formats and approaches
- Focus on different aspects of the topic
- Employ different cognitive skills and thinking processes
''';
  }

  String _getComplexityDescriptor(Difficulty difficulty) => switch (difficulty) {
        Difficulty.easy => 'beginner-level, foundational',
        Difficulty.medium => 'intermediate-level, application-focused',
        Difficulty.hard => 'advanced-level, synthesis and analysis',
        Difficulty.adaptive => 'dynamically-adjusted, personalized',
      };

  _QuestionInstruction _getQuestionTypeInstructions(QuestionType type) => switch (type) {
        QuestionType.multipleChoice => const _QuestionInstruction(
            instructions:
                '- Create exactly 4 answer options with the FULL TEXT of each answer\n- Do NOT use letter labels like "A", "B" — write the actual answer text\n- Make one option clearly correct\n- Create plausible but incorrect distractors\n- Ensure options are similar in length and structure\n- The correctAnswer must be the EXACT FULL TEXT of the correct option',
            format:
                '{"question":"What is the capital of France?","type":"MULTIPLE_CHOICE","options":["Paris","London","Berlin","Madrid"],"correctAnswer":"Paris","explanation":"Paris is the capital city of France."}',
          ),
        QuestionType.trueFalse => const _QuestionInstruction(
            instructions:
                '- Create a clear statement that can be definitively true or false\n- Avoid ambiguous statements\n- Test important concepts',
            format:
                '{"question":"...","type":"TRUE_FALSE","options":["True","False"],"correctAnswer":"True","explanation":"..."}',
          ),
        QuestionType.fillInBlank => const _QuestionInstruction(
            instructions:
                '- Use underscores (___) for the blank\n- Place blank on key concept\n- Provide enough context',
            format:
                '{"question":"... ___ ...","type":"FILL_IN_BLANK","correctAnswer":"...","explanation":"..."}',
          ),
        QuestionType.shortAnswer => const _QuestionInstruction(
            instructions:
                '- Ask open-ended questions requiring explanation\n- Expect 1-3 sentence response\n- Focus on understanding and application',
            format:
                '{"question":"...","type":"SHORT_ANSWER","correctAnswer":"Sample answer","explanation":"..."}',
          ),
        QuestionType.matching => const _QuestionInstruction(
            instructions:
                '- Create clear match pairs\n- Ensure one-to-one correspondence\n- Keep items unambiguous',
            format:
                '{"question":"Match items","type":"MATCHING","options":["..."],"correctAnswer":"1-A,2-B","explanation":"..."}',
          ),
      };
}

class _QuestionInstruction {
  const _QuestionInstruction({required this.instructions, required this.format});

  final String instructions;
  final String format;
}
