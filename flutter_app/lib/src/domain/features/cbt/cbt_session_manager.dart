// CBT domain models matching the Kotlin implementation.

// ---------------------------------------------------------------------------
// Emotion
// ---------------------------------------------------------------------------

enum Emotion {
  happy,
  sad,
  angry,
  anxious,
  neutral,
  fearful,
  surprised,
  disgusted;

  String get label {
    switch (this) {
      case Emotion.happy:
        return 'Happy';
      case Emotion.sad:
        return 'Sad';
      case Emotion.angry:
        return 'Angry';
      case Emotion.anxious:
        return 'Anxious';
      case Emotion.neutral:
        return 'Neutral';
      case Emotion.fearful:
        return 'Fearful';
      case Emotion.surprised:
        return 'Surprised';
      case Emotion.disgusted:
        return 'Disgusted';
    }
  }

  String get emoji {
    switch (this) {
      case Emotion.happy:
        return '\u{1F60A}';
      case Emotion.sad:
        return '\u{1F622}';
      case Emotion.angry:
        return '\u{1F620}';
      case Emotion.anxious:
        return '\u{1F630}';
      case Emotion.neutral:
        return '\u{1F610}';
      case Emotion.fearful:
        return '\u{1F628}';
      case Emotion.surprised:
        return '\u{1F632}';
      case Emotion.disgusted:
        return '\u{1F922}';
    }
  }

  double get defaultIntensity {
    switch (this) {
      case Emotion.angry:
        return 0.9;
      case Emotion.fearful:
        return 0.85;
      case Emotion.anxious:
        return 0.8;
      case Emotion.disgusted:
        return 0.75;
      case Emotion.sad:
        return 0.7;
      case Emotion.surprised:
        return 0.6;
      case Emotion.happy:
        return 0.3;
      case Emotion.neutral:
        return 0.1;
    }
  }

  static Emotion fromString(String value) {
    return Emotion.values.firstWhere(
      (e) => e.name == value,
      orElse: () => Emotion.neutral,
    );
  }
}

// ---------------------------------------------------------------------------
// CBT Technique
// ---------------------------------------------------------------------------

enum TechniqueCategory {
  cognitiveRestructuring,
  behavioralActivation,
  mindfulness,
  relaxation,
  problemSolving,
  exposureTherapy,
}

class CBTTechnique {
  const CBTTechnique({
    required this.id,
    required this.name,
    required this.description,
    required this.category,
    required this.steps,
    this.durationMinutes = 15,
    this.effectiveness = 0.8,
  });

  final String id;
  final String name;
  final String description;
  final TechniqueCategory category;
  final List<String> steps;
  final int durationMinutes;
  final double effectiveness;

  static const thoughtChallenging = CBTTechnique(
    id: 'thought_challenging',
    name: 'Thought Challenging',
    description: 'Identify and challenge negative automatic thoughts by examining '
        'evidence for and against them, then create balanced alternative thoughts.',
    category: TechniqueCategory.cognitiveRestructuring,
    steps: [
      'Identify the negative automatic thought',
      'Examine evidence for and against it',
      'Consider an alternative perspective',
      'Create a balanced replacement thought',
    ],
    durationMinutes: 15,
    effectiveness: 0.85,
  );

  static const grounding54321 = CBTTechnique(
    id: '54321_grounding',
    name: '5-4-3-2-1 Grounding',
    description: 'A mindfulness technique that uses your five senses to ground you '
        'in the present moment, reducing anxiety and panic.',
    category: TechniqueCategory.mindfulness,
    steps: [
      'Name 5 things you can SEE',
      'Name 4 things you can TOUCH',
      'Name 3 things you can HEAR',
      'Name 2 things you can SMELL',
      'Name 1 thing you can TASTE',
    ],
    durationMinutes: 5,
    effectiveness: 0.80,
  );

  static const progressiveMuscleRelaxation = CBTTechnique(
    id: 'pmr',
    name: 'Progressive Muscle Relaxation',
    description: 'Systematically tense and release different muscle groups to '
        'reduce physical tension and promote relaxation.',
    category: TechniqueCategory.relaxation,
    steps: [
      'Find a comfortable position and close your eyes',
      'Start with your feet — tense for 5 seconds, then release',
      'Move to calves, thighs, abdomen — tense and release each',
      'Continue with hands, arms, shoulders, and face',
      'Take a few deep breaths and notice the relaxation',
    ],
    durationMinutes: 20,
    effectiveness: 0.75,
  );

  static const List<CBTTechnique> builtIn = [
    thoughtChallenging,
    grounding54321,
    progressiveMuscleRelaxation,
  ];

  static CBTTechnique? fromId(String? id) {
    if (id == null) return null;
    for (final t in builtIn) {
      if (t.id == id) return t;
    }
    return null;
  }
}

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

sealed class CbtMessage {
  const CbtMessage({required this.content, required this.timestamp});
  final String content;
  final DateTime timestamp;
}

class UserMessage extends CbtMessage {
  const UserMessage({
    required super.content,
    required super.timestamp,
  });
}

class AiMessage extends CbtMessage {
  const AiMessage({
    required super.content,
    required super.timestamp,
    this.techniqueId,
  });

  final String? techniqueId;

  String? get techniqueName => CBTTechnique.fromId(techniqueId)?.name;
}

// ---------------------------------------------------------------------------
// Thought Record
// ---------------------------------------------------------------------------

class ThoughtRecord {
  const ThoughtRecord({
    required this.id,
    required this.timestamp,
    required this.situation,
    required this.automaticThought,
    required this.emotion,
    required this.emotionIntensity,
    this.evidenceFor = '',
    this.evidenceAgainst = '',
    this.balancedThought = '',
    this.newEmotionIntensity = 0.5,
  });

  final String id;
  final DateTime timestamp;
  final String situation;
  final String automaticThought;
  final Emotion emotion;
  final double emotionIntensity;
  final String evidenceFor;
  final String evidenceAgainst;
  final String balancedThought;
  final double newEmotionIntensity;
}

// ---------------------------------------------------------------------------
// Session Insights
// ---------------------------------------------------------------------------

class SessionInsights {
  const SessionInsights({
    this.summary = '',
    this.keyInsights = const [],
    this.progress = '',
    this.homework = '',
    this.nextSteps = '',
  });

  final String summary;
  final List<String> keyInsights;
  final String progress;
  final String homework;
  final String nextSteps;
}

// ---------------------------------------------------------------------------
// Personalized Recommendations
// ---------------------------------------------------------------------------

class PersonalizedRecommendations {
  const PersonalizedRecommendations({
    this.insight = '',
    this.recommendedTechnique,
    this.actionPlan = '',
    this.basedOnSessions = 0,
  });

  final String insight;
  final CBTTechnique? recommendedTechnique;
  final String actionPlan;
  final int basedOnSessions;
}

// ---------------------------------------------------------------------------
// CBT Session State
// ---------------------------------------------------------------------------

class CBTSessionState {
  const CBTSessionState({
    this.isActive = false,
    this.currentEmotion,
    this.suggestedTechnique,
    this.conversation = const [],
    this.thoughtRecord,
    this.currentStep = 0,
    this.userTypedInput = '',
    this.sessionDurationMs = 0,
    this.sessionInsights,
    this.error,
    this.isLoading = false,
  });

  final bool isActive;
  final Emotion? currentEmotion;
  final CBTTechnique? suggestedTechnique;
  final List<CbtMessage> conversation;
  final ThoughtRecord? thoughtRecord;
  final int currentStep;
  final String userTypedInput;
  final int sessionDurationMs;
  final SessionInsights? sessionInsights;
  final String? error;
  final bool isLoading;

  CBTSessionState copyWith({
    bool? isActive,
    Emotion? currentEmotion,
    bool clearEmotion = false,
    CBTTechnique? suggestedTechnique,
    bool clearTechnique = false,
    List<CbtMessage>? conversation,
    ThoughtRecord? thoughtRecord,
    bool clearThoughtRecord = false,
    int? currentStep,
    String? userTypedInput,
    int? sessionDurationMs,
    SessionInsights? sessionInsights,
    bool clearInsights = false,
    String? error,
    bool clearError = false,
    bool? isLoading,
  }) {
    return CBTSessionState(
      isActive: isActive ?? this.isActive,
      currentEmotion: clearEmotion ? null : (currentEmotion ?? this.currentEmotion),
      suggestedTechnique:
          clearTechnique ? null : (suggestedTechnique ?? this.suggestedTechnique),
      conversation: conversation ?? this.conversation,
      thoughtRecord:
          clearThoughtRecord ? null : (thoughtRecord ?? this.thoughtRecord),
      currentStep: currentStep ?? this.currentStep,
      userTypedInput: userTypedInput ?? this.userTypedInput,
      sessionDurationMs: sessionDurationMs ?? this.sessionDurationMs,
      sessionInsights:
          clearInsights ? null : (sessionInsights ?? this.sessionInsights),
      error: clearError ? null : (error ?? this.error),
      isLoading: isLoading ?? this.isLoading,
    );
  }
}
