enum Subject {
  mathematics,
  science,
  history,
  languageArts,
  geography,
  english,
  general,
  economics,
  computerScience,
}

enum Difficulty { easy, medium, hard, adaptive }

enum QuestionType { multipleChoice, trueFalse, fillInBlank, shortAnswer, matching }

class Question {
  const Question({
    required this.questionText,
    required this.questionType,
    required this.correctAnswer,
    this.options = const [],
    this.explanation = '',
    this.conceptsCovered = const [],
    this.difficulty = Difficulty.medium,
  });

  final String questionText;
  final QuestionType questionType;
  final List<String> options;
  final String correctAnswer;
  final String explanation;
  final List<String> conceptsCovered;
  final Difficulty difficulty;
}

class LearnerProfile {
  const LearnerProfile({
    required this.strengthsBySubject,
    required this.weaknessesByConcept,
    required this.masteredConcepts,
    required this.totalQuestionsAnswered,
    required this.streakDays,
  });

  final Map<Subject, double> strengthsBySubject;
  final Map<String, List<String>> weaknessesByConcept;
  final Set<String> masteredConcepts;
  final int totalQuestionsAnswered;
  final int streakDays;
}
