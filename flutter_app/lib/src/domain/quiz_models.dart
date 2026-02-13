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

enum QuizMode { normal, review, adaptive }

class Question {
  const Question({
    required this.questionText,
    required this.questionType,
    required this.correctAnswer,
    this.id = '',
    this.options = const [],
    this.explanation = '',
    this.conceptsCovered = const [],
    this.difficulty = Difficulty.medium,
    this.hint = '',
    this.points = 1,
    this.userAnswer,
    this.isAnswered = false,
    this.isCorrect = false,
    this.feedback = '',
    this.timeSpentSeconds = 0,
  });

  final String id;
  final String questionText;
  final QuestionType questionType;
  final List<String> options;
  final String correctAnswer;
  final String explanation;
  final List<String> conceptsCovered;
  final Difficulty difficulty;
  final String hint;
  final int points;
  final String? userAnswer;
  final bool isAnswered;
  final bool isCorrect;
  final String feedback;
  final int timeSpentSeconds;

  Question copyWith({
    String? id,
    String? questionText,
    QuestionType? questionType,
    List<String>? options,
    String? correctAnswer,
    String? explanation,
    List<String>? conceptsCovered,
    Difficulty? difficulty,
    String? hint,
    int? points,
    String? userAnswer,
    bool? isAnswered,
    bool? isCorrect,
    String? feedback,
    int? timeSpentSeconds,
  }) {
    return Question(
      id: id ?? this.id,
      questionText: questionText ?? this.questionText,
      questionType: questionType ?? this.questionType,
      options: options ?? this.options,
      correctAnswer: correctAnswer ?? this.correctAnswer,
      explanation: explanation ?? this.explanation,
      conceptsCovered: conceptsCovered ?? this.conceptsCovered,
      difficulty: difficulty ?? this.difficulty,
      hint: hint ?? this.hint,
      points: points ?? this.points,
      userAnswer: userAnswer ?? this.userAnswer,
      isAnswered: isAnswered ?? this.isAnswered,
      isCorrect: isCorrect ?? this.isCorrect,
      feedback: feedback ?? this.feedback,
      timeSpentSeconds: timeSpentSeconds ?? this.timeSpentSeconds,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'questionText': questionText,
        'questionType': questionType.name,
        'options': options,
        'correctAnswer': correctAnswer,
        'explanation': explanation,
        'conceptsCovered': conceptsCovered,
        'difficulty': difficulty.name,
        'hint': hint,
        'points': points,
        'userAnswer': userAnswer,
        'isAnswered': isAnswered,
        'isCorrect': isCorrect,
        'feedback': feedback,
        'timeSpentSeconds': timeSpentSeconds,
      };

  factory Question.fromJson(Map<String, dynamic> json) {
    return Question(
      id: json['id'] as String? ?? '',
      questionText: json['questionText'] as String? ?? '',
      questionType: QuestionType.values.firstWhere(
        (e) => e.name == json['questionType'],
        orElse: () => QuestionType.multipleChoice,
      ),
      options: (json['options'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      correctAnswer: json['correctAnswer'] as String? ?? '',
      explanation: json['explanation'] as String? ?? '',
      conceptsCovered: (json['conceptsCovered'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      difficulty: Difficulty.values.firstWhere(
        (e) => e.name == json['difficulty'],
        orElse: () => Difficulty.medium,
      ),
      hint: json['hint'] as String? ?? '',
      points: json['points'] as int? ?? 1,
      userAnswer: json['userAnswer'] as String?,
      isAnswered: json['isAnswered'] as bool? ?? false,
      isCorrect: json['isCorrect'] as bool? ?? false,
      feedback: json['feedback'] as String? ?? '',
      timeSpentSeconds: json['timeSpentSeconds'] as int? ?? 0,
    );
  }
}

class Quiz {
  const Quiz({
    required this.id,
    required this.subject,
    required this.questions,
    this.topic = '',
    this.difficulty = Difficulty.medium,
    this.mode = QuizMode.normal,
    this.createdAt,
    this.completedAt,
    this.score,
  });

  final String id;
  final Subject subject;
  final String topic;
  final List<Question> questions;
  final Difficulty difficulty;
  final QuizMode mode;
  final DateTime? createdAt;
  final DateTime? completedAt;
  final double? score;

  int get totalQuestions => questions.length;
  int get answeredCount => questions.where((q) => q.isAnswered).length;
  int get correctCount => questions.where((q) => q.isCorrect).length;

  Quiz copyWith({
    String? id,
    Subject? subject,
    String? topic,
    List<Question>? questions,
    Difficulty? difficulty,
    QuizMode? mode,
    DateTime? createdAt,
    DateTime? completedAt,
    double? score,
  }) {
    return Quiz(
      id: id ?? this.id,
      subject: subject ?? this.subject,
      topic: topic ?? this.topic,
      questions: questions ?? this.questions,
      difficulty: difficulty ?? this.difficulty,
      mode: mode ?? this.mode,
      createdAt: createdAt ?? this.createdAt,
      completedAt: completedAt ?? this.completedAt,
      score: score ?? this.score,
    );
  }
}

class QuizResult {
  const QuizResult({
    required this.quizId,
    required this.questionId,
    required this.wasCorrect,
    required this.attemptedAt,
    this.difficulty = Difficulty.medium,
    this.conceptsTested = const [],
  });

  final String quizId;
  final String questionId;
  final bool wasCorrect;
  final DateTime attemptedAt;
  final Difficulty difficulty;
  final List<String> conceptsTested;
}

class WrongAnswerRecord {
  const WrongAnswerRecord({
    required this.questionId,
    required this.questionText,
    required this.userAnswer,
    required this.correctAnswer,
    this.conceptsTested = const [],
    this.attemptedAt,
  });

  final String questionId;
  final String questionText;
  final String userAnswer;
  final String correctAnswer;
  final List<String> conceptsTested;
  final DateTime? attemptedAt;
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
