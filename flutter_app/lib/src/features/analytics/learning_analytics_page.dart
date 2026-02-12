import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../domain/quiz_models.dart';

enum MasteryTrend { improving, stable, declining }

class QuizAnalytics {
  const QuizAnalytics({
    required this.overallAccuracy,
    required this.totalQuestionsAnswered,
    required this.questionsToday,
    required this.currentStreak,
    required this.longestStreak,
    required this.subjectPerformance,
    required this.frequentlyMissedQuestions,
    required this.topicCoverage,
    required this.difficultyBreakdown,
    required this.weeklyProgress,
    required this.conceptMastery,
  });

  final double overallAccuracy;
  final int totalQuestionsAnswered;
  final int questionsToday;
  final int currentStreak;
  final int longestStreak;
  final Map<Subject, SubjectAnalytics> subjectPerformance;
  final List<MissedQuestionInfo> frequentlyMissedQuestions;
  final Map<String, TopicCoverageInfo> topicCoverage;
  final Map<Difficulty, DifficultyStats> difficultyBreakdown;
  final List<DailyProgress> weeklyProgress;
  final Map<String, ConceptMasteryInfo> conceptMastery;
}

class SubjectAnalytics {
  const SubjectAnalytics({
    required this.accuracy,
    required this.questionsAnswered,
    required this.averageTimePerQuestion,
    required this.lastAttempted,
    required this.topicBreakdown,
  });

  final double accuracy;
  final int questionsAnswered;
  final double averageTimePerQuestion;
  final DateTime lastAttempted;
  final Map<String, double> topicBreakdown;
}

class MissedQuestionInfo {
  const MissedQuestionInfo({
    required this.questionText,
    required this.timesAttempted,
    required this.timesCorrect,
    required this.lastAttempted,
    required this.concepts,
    required this.difficulty,
    required this.subject,
  });

  final String questionText;
  final int timesAttempted;
  final int timesCorrect;
  final DateTime lastAttempted;
  final List<String> concepts;
  final Difficulty difficulty;
  final Subject subject;
}

class TopicCoverageInfo {
  const TopicCoverageInfo({
    required this.topicName,
    required this.totalQuestions,
    required this.questionsAttempted,
    required this.coveragePercentage,
    required this.lastCovered,
    required this.accuracy,
  });

  final String topicName;
  final int totalQuestions;
  final int questionsAttempted;
  final double coveragePercentage;
  final DateTime? lastCovered;
  final double accuracy;
}

class DifficultyStats {
  const DifficultyStats({
    required this.questionsAttempted,
    required this.accuracy,
    required this.averageTime,
  });

  final int questionsAttempted;
  final double accuracy;
  final double averageTime;
}

class DailyProgress {
  const DailyProgress({
    required this.date,
    required this.questionsAnswered,
    required this.accuracy,
    required this.subjects,
  });

  final DateTime date;
  final int questionsAnswered;
  final double accuracy;
  final Set<Subject> subjects;
}

class ConceptMasteryInfo {
  const ConceptMasteryInfo({
    required this.concept,
    required this.masteryLevel,
    required this.questionsAnswered,
    required this.lastSeen,
    required this.trend,
  });

  final String concept;
  final double masteryLevel;
  final int questionsAnswered;
  final DateTime lastSeen;
  final MasteryTrend trend;
}

class LearningAnalyticsPage extends StatelessWidget {
  const LearningAnalyticsPage({super.key, this.analytics});

  final QuizAnalytics? analytics;

  @override
  Widget build(BuildContext context) {
    final model = analytics ?? _demoAnalytics();

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Quiz Analytics Dashboard', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 16),
          _OverviewCard(analytics: model),
          const SizedBox(height: 16),
          _WeeklyProgressCard(weeklyProgress: model.weeklyProgress),
          const SizedBox(height: 16),
          _SubjectPerformanceCard(subjectPerformance: model.subjectPerformance),
          const SizedBox(height: 16),
          _FrequentlyMissedCard(missedQuestions: model.frequentlyMissedQuestions),
          const SizedBox(height: 16),
          _TopicCoverageCard(topicCoverage: model.topicCoverage),
          const SizedBox(height: 16),
          _ConceptMasteryCard(conceptMastery: model.conceptMastery),
          const SizedBox(height: 16),
          _DifficultyBreakdownCard(difficultyBreakdown: model.difficultyBreakdown),
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}

class _OverviewCard extends StatelessWidget {
  const _OverviewCard({required this.analytics});

  final QuizAnalytics analytics;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Learning Overview', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _StatItem(icon: Icons.school, value: '${analytics.totalQuestionsAnswered}', label: 'Total Questions'),
                _StatItem(icon: Icons.trending_up, value: '${(analytics.overallAccuracy * 100).round()}%', label: 'Accuracy'),
                _StatItem(icon: Icons.local_fire_department, value: '${analytics.currentStreak}', label: 'Day Streak'),
              ],
            ),
            if (analytics.questionsToday > 0) ...[
              const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(value: (analytics.questionsToday / 20).clamp(0, 1).toDouble(), minHeight: 8),
              ),
              const SizedBox(height: 6),
              Text('${analytics.questionsToday}/20 questions today', style: Theme.of(context).textTheme.labelMedium),
            ],
          ],
        ),
      ),
    );
  }
}

class _StatItem extends StatelessWidget {
  const _StatItem({required this.icon, required this.value, required this.label});

  final IconData icon;
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon),
        const SizedBox(height: 4),
        Text(value, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
        Text(label, style: Theme.of(context).textTheme.labelSmall),
      ],
    );
  }
}

class _WeeklyProgressCard extends StatelessWidget {
  const _WeeklyProgressCard({required this.weeklyProgress});

  final List<DailyProgress> weeklyProgress;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Weekly Progress', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            SizedBox(
              height: 200,
              child: CustomPaint(painter: _WeeklyProgressPainter(weeklyProgress)),
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: weeklyProgress
                  .take(7)
                  .map((day) => Text(_weekdayLabel(day.date), style: Theme.of(context).textTheme.labelSmall))
                  .toList(growable: false),
            ),
            const SizedBox(height: 8),
            const Wrap(
              spacing: 12,
              children: [
                _LegendDot(color: Color(0xFF4CAF50), label: 'Questions'),
                _LegendDot(color: Color(0xFF2196F3), label: 'Accuracy'),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static String _weekdayLabel(DateTime date) {
    const labels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    return labels[date.weekday - 1];
  }
}

class _LegendDot extends StatelessWidget {
  const _LegendDot({required this.color, required this.label});

  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 10, height: 10, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 4),
        Text(label),
      ],
    );
  }
}

class _WeeklyProgressPainter extends CustomPainter {
  _WeeklyProgressPainter(this.weeklyProgress);

  final List<DailyProgress> weeklyProgress;

  @override
  void paint(Canvas canvas, Size size) {
    if (weeklyProgress.isEmpty) return;

    const padding = 30.0;
    final graphWidth = size.width - (padding * 2);
    final graphHeight = size.height - (padding * 2);
    final maxQuestions = weeklyProgress.map((day) => day.questionsAnswered).reduce(math.max).clamp(1, 10000);
    final points = weeklyProgress.take(7).toList(growable: false);
    if (points.length <= 1) return;

    final gridPaint = Paint()..color = Colors.grey.withValues(alpha: 0.25)..strokeWidth = 1;
    for (var i = 0; i <= 4; i++) {
      final y = padding + (i * graphHeight / 4);
      canvas.drawLine(Offset(padding, y), Offset(size.width - padding, y), gridPaint);
    }

    final questionPath = Path();
    final accuracyPath = Path();

    for (var i = 0; i < points.length; i++) {
      final day = points[i];
      final x = padding + (i * graphWidth / (points.length - 1));
      final questionY = padding + graphHeight - ((day.questionsAnswered / maxQuestions) * graphHeight);
      final accuracyY = padding + graphHeight - (day.accuracy * graphHeight);

      if (i == 0) {
        questionPath.moveTo(x, questionY);
        accuracyPath.moveTo(x, accuracyY);
      } else {
        questionPath.lineTo(x, questionY);
        accuracyPath.lineTo(x, accuracyY);
      }

      canvas.drawCircle(Offset(x, questionY), 4, Paint()..color = const Color(0xFF4CAF50));
      canvas.drawCircle(Offset(x, accuracyY), 4, Paint()..color = const Color(0xFF2196F3));
    }

    canvas.drawPath(questionPath, Paint()..color = const Color(0xFF4CAF50)..style = PaintingStyle.stroke..strokeWidth = 3);
    canvas.drawPath(accuracyPath, Paint()..color = const Color(0xFF2196F3)..style = PaintingStyle.stroke..strokeWidth = 3);
  }

  @override
  bool shouldRepaint(covariant _WeeklyProgressPainter oldDelegate) => oldDelegate.weeklyProgress != weeklyProgress;
}

class _SubjectPerformanceCard extends StatelessWidget {
  const _SubjectPerformanceCard({required this.subjectPerformance});

  final Map<Subject, SubjectAnalytics> subjectPerformance;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Subject Performance', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...subjectPerformance.entries.map((entry) {
              final color = _performanceColor(entry.value.accuracy);
              return Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(_titleCase(entry.key.name)),
                        Text('${(entry.value.accuracy * 100).round()}%', style: TextStyle(color: color)),
                      ],
                    ),
                    const SizedBox(height: 4),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(3),
                      child: LinearProgressIndicator(value: entry.value.accuracy, minHeight: 6, color: color),
                    ),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Text('${entry.value.questionsAnswered} questions', style: Theme.of(context).textTheme.labelSmall),
                    ),
                  ],
                ),
              );
            }),
          ],
        ),
      ),
    );
  }
}

class _FrequentlyMissedCard extends StatelessWidget {
  const _FrequentlyMissedCard({required this.missedQuestions});

  final List<MissedQuestionInfo> missedQuestions;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Frequently Missed Questions', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                Icon(Icons.error_outline, color: Theme.of(context).colorScheme.error),
              ],
            ),
            const SizedBox(height: 12),
            if (missedQuestions.isEmpty)
              Text('Great job! No frequently missed questions.', style: Theme.of(context).textTheme.bodyMedium)
            else
              ...missedQuestions.take(5).map(
                (question) => Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(question.questionText, maxLines: 2, overflow: TextOverflow.ellipsis),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            Chip(label: Text('${question.timesCorrect}/${question.timesAttempted} correct')),
                            Chip(label: Text(question.difficulty.name.toUpperCase())),
                            Chip(label: Text(_titleCase(question.subject.name))),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _TopicCoverageCard extends StatelessWidget {
  const _TopicCoverageCard({required this.topicCoverage});

  final Map<String, TopicCoverageInfo> topicCoverage;

  @override
  Widget build(BuildContext context) {
    final sortedTopics = topicCoverage.values.toList(growable: false)..sort((a, b) => a.coveragePercentage.compareTo(b.coveragePercentage));
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Topic Coverage', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...sortedTopics.take(10).map(
              (topic) => ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(topic.topicName),
                subtitle: Text('${topic.questionsAttempted}/${topic.totalQuestions} questions'),
                trailing: SizedBox(
                  width: 52,
                  height: 52,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      CircularProgressIndicator(value: topic.coveragePercentage, strokeWidth: 4, color: _performanceColor(topic.coveragePercentage)),
                      Text('${(topic.coveragePercentage * 100).round()}%', style: Theme.of(context).textTheme.labelSmall),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ConceptMasteryCard extends StatelessWidget {
  const _ConceptMasteryCard({required this.conceptMastery});

  final Map<String, ConceptMasteryInfo> conceptMastery;

  @override
  Widget build(BuildContext context) {
    final concepts = conceptMastery.values.toList(growable: false)..sort((a, b) => b.masteryLevel.compareTo(a.masteryLevel));

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Concept Mastery', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...concepts.take(5).map(
              (concept) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Row(
                  children: [
                    Icon(_trendIcon(concept.trend), color: _trendColor(concept.trend)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(concept.concept),
                          Text('${concept.questionsAnswered} questions', style: Theme.of(context).textTheme.labelSmall),
                        ],
                      ),
                    ),
                    CircleAvatar(
                      radius: 20,
                      backgroundColor: _performanceColor(concept.masteryLevel).withValues(alpha: 0.2),
                      child: Text('${(concept.masteryLevel * 100).round()}%', style: TextStyle(color: _performanceColor(concept.masteryLevel), fontSize: 11)),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DifficultyBreakdownCard extends StatelessWidget {
  const _DifficultyBreakdownCard({required this.difficultyBreakdown});

  final Map<Difficulty, DifficultyStats> difficultyBreakdown;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Performance by Difficulty', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...difficultyBreakdown.entries.map(
              (entry) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Row(
                  children: [
                    Container(width: 12, height: 12, decoration: BoxDecoration(color: _difficultyColor(entry.key), shape: BoxShape.circle)),
                    const SizedBox(width: 8),
                    Expanded(child: Text(entry.key.name.toUpperCase())),
                    Text('${(entry.value.accuracy * 100).round()}%'),
                    const SizedBox(width: 8),
                    Text('${entry.value.questionsAttempted} q • ${entry.value.averageTime.round()}s', style: Theme.of(context).textTheme.labelSmall),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

Color _difficultyColor(Difficulty difficulty) {
  switch (difficulty) {
    case Difficulty.easy:
      return const Color(0xFF4CAF50);
    case Difficulty.medium:
      return const Color(0xFFFFC107);
    case Difficulty.hard:
      return const Color(0xFFF44336);
    case Difficulty.adaptive:
      return const Color(0xFF2196F3);
  }
}

Color _performanceColor(double score) {
  if (score > 0.8) return const Color(0xFF4CAF50);
  if (score > 0.6) return const Color(0xFFFFC107);
  return const Color(0xFFF44336);
}

IconData _trendIcon(MasteryTrend trend) {
  switch (trend) {
    case MasteryTrend.improving:
      return Icons.trending_up;
    case MasteryTrend.stable:
      return Icons.remove;
    case MasteryTrend.declining:
      return Icons.trending_down;
  }
}

Color _trendColor(MasteryTrend trend) {
  switch (trend) {
    case MasteryTrend.improving:
      return const Color(0xFF4CAF50);
    case MasteryTrend.stable:
      return Colors.grey;
    case MasteryTrend.declining:
      return const Color(0xFFF44336);
  }
}

String _titleCase(String input) {
  if (input.isEmpty) return input;
  final withSpaces = input.replaceAllMapped(RegExp(r'([A-Z])'), (match) => ' ${match.group(1)}');
  return withSpaces[0].toUpperCase() + withSpaces.substring(1);
}

QuizAnalytics _demoAnalytics() {
  final now = DateTime.now();
  return QuizAnalytics(
    overallAccuracy: 0.78,
    totalQuestionsAnswered: 428,
    questionsToday: 14,
    currentStreak: 9,
    longestStreak: 21,
    subjectPerformance: const {
      Subject.mathematics: SubjectAnalytics(
        accuracy: 0.84,
        questionsAnswered: 124,
        averageTimePerQuestion: 42,
        lastAttempted: DateTime(2025, 2, 10),
        topicBreakdown: {'algebra': 0.81, 'geometry': 0.87},
      ),
      Subject.science: SubjectAnalytics(
        accuracy: 0.72,
        questionsAnswered: 118,
        averageTimePerQuestion: 46,
        lastAttempted: DateTime(2025, 2, 11),
        topicBreakdown: {'physics': 0.68, 'biology': 0.76},
      ),
      Subject.history: SubjectAnalytics(
        accuracy: 0.65,
        questionsAnswered: 96,
        averageTimePerQuestion: 51,
        lastAttempted: DateTime(2025, 2, 9),
        topicBreakdown: {'ancient': 0.62, 'modern': 0.67},
      ),
    },
    frequentlyMissedQuestions: const [
      MissedQuestionInfo(
        questionText: 'Which force law best describes how pressure and volume relate in a closed gas system?',
        timesAttempted: 5,
        timesCorrect: 1,
        lastAttempted: DateTime(2025, 2, 8),
        concepts: ['Gas Laws'],
        difficulty: Difficulty.hard,
        subject: Subject.science,
      ),
      MissedQuestionInfo(
        questionText: 'Solve for x: 3(2x - 5) = 21. What is x?',
        timesAttempted: 4,
        timesCorrect: 1,
        lastAttempted: DateTime(2025, 2, 10),
        concepts: ['Linear Equations'],
        difficulty: Difficulty.medium,
        subject: Subject.mathematics,
      ),
    ],
    topicCoverage: const {
      'Thermodynamics': TopicCoverageInfo(
        topicName: 'Thermodynamics',
        totalQuestions: 20,
        questionsAttempted: 5,
        coveragePercentage: 0.25,
        lastCovered: DateTime(2025, 2, 6),
        accuracy: 0.58,
      ),
      'World War II': TopicCoverageInfo(
        topicName: 'World War II',
        totalQuestions: 18,
        questionsAttempted: 7,
        coveragePercentage: 0.39,
        lastCovered: DateTime(2025, 2, 9),
        accuracy: 0.61,
      ),
      'Geometry Proofs': TopicCoverageInfo(
        topicName: 'Geometry Proofs',
        totalQuestions: 12,
        questionsAttempted: 9,
        coveragePercentage: 0.75,
        lastCovered: DateTime(2025, 2, 11),
        accuracy: 0.79,
      ),
    },
    difficultyBreakdown: const {
      Difficulty.easy: DifficultyStats(questionsAttempted: 160, accuracy: 0.9, averageTime: 24),
      Difficulty.medium: DifficultyStats(questionsAttempted: 190, accuracy: 0.76, averageTime: 41),
      Difficulty.hard: DifficultyStats(questionsAttempted: 78, accuracy: 0.52, averageTime: 69),
    },
    weeklyProgress: List<DailyProgress>.generate(7, (index) {
      final date = now.subtract(Duration(days: 6 - index));
      final questions = [10, 16, 12, 18, 21, 14, 15][index];
      final accuracy = [0.62, 0.71, 0.69, 0.75, 0.8, 0.77, 0.79][index];
      return DailyProgress(
        date: date,
        questionsAnswered: questions,
        accuracy: accuracy,
        subjects: const {Subject.mathematics, Subject.science},
      );
    }),
    conceptMastery: const {
      'Linear Equations': ConceptMasteryInfo(
        concept: 'Linear Equations',
        masteryLevel: 0.87,
        questionsAnswered: 22,
        lastSeen: DateTime(2025, 2, 11),
        trend: MasteryTrend.improving,
      ),
      'Cell Biology': ConceptMasteryInfo(
        concept: 'Cell Biology',
        masteryLevel: 0.73,
        questionsAnswered: 16,
        lastSeen: DateTime(2025, 2, 10),
        trend: MasteryTrend.stable,
      ),
      'Historical Causation': ConceptMasteryInfo(
        concept: 'Historical Causation',
        masteryLevel: 0.58,
        questionsAnswered: 19,
        lastSeen: DateTime(2025, 2, 7),
        trend: MasteryTrend.declining,
      ),
    },
  );
}
