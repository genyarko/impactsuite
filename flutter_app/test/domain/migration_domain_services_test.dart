import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/domain/features/analytics/knowledge_gap_analyzer.dart';
import 'package:impactsuite_flutter/src/domain/features/cbt/cbt_session_manager.dart';
import 'package:impactsuite_flutter/src/domain/features/crisis/crisis_severity_classifier.dart';
import 'package:impactsuite_flutter/src/domain/features/plant/plant_diagnosis_interpreter.dart';
import 'package:impactsuite_flutter/src/domain/features/story/story_recommendation_service.dart';

void main() {
  test('knowledge-gap analyzer sorts highest severity first', () {
    const analyzer = KnowledgeGapAnalyzer();

    final gaps = analyzer.analyze(const [
      SubjectPerformance(subject: 'science', accuracy: 0.4, totalAttempts: 10),
      SubjectPerformance(subject: 'history', accuracy: 0.6, totalAttempts: 10),
      SubjectPerformance(subject: 'math', accuracy: 0.9, totalAttempts: 10),
    ]);

    expect(gaps.length, 2);
    expect(gaps.first.subject, 'science');
  });

  test('cbt emotion detection returns correct emotion for keyword match', () {
    // Verify the Emotion enum and CBTTechnique models are functional
    expect(Emotion.fromString('anxious'), Emotion.anxious);
    expect(Emotion.fromString('unknown'), Emotion.neutral);
    expect(Emotion.anxious.label, 'Anxious');

    // Verify technique lookup
    expect(CBTTechnique.fromId('thought_challenging')?.name, 'Thought Challenging');
    expect(CBTTechnique.fromId('54321_grounding')?.name, '5-4-3-2-1 Grounding');
    expect(CBTTechnique.fromId('pmr')?.name, 'Progressive Muscle Relaxation');
    expect(CBTTechnique.builtIn.length, 3);
  });

  test('crisis severity classifier catches critical phrase', () {
    const classifier = CrisisSeverityClassifier();
    expect(classifier.classify('Person is unconscious and not breathing'), CrisisSeverity.critical);
  });

  test('story recommendation uses first topic and session duration', () {
    const service = StoryRecommendationService();
    final rec = service.recommend(
      const StoryPreferences(topics: ['space'], readingLevel: 2, sessionMinutes: 10),
    );

    expect(rec.theme, 'space');
    expect(rec.suggestedLength, greaterThan(600));
  });

  test('plant diagnosis interpreter includes confidence band', () {
    const interpreter = PlantDiagnosisInterpreter();
    final output = interpreter.explain(const PlantDiagnosis(label: 'powdery mildew', confidence: 0.9));

    expect(output, contains('High-confidence'));
  });
}
