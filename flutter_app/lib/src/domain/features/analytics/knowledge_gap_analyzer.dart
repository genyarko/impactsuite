class SubjectPerformance {
  const SubjectPerformance({
    required this.subject,
    required this.accuracy,
    required this.totalAttempts,
  });

  final String subject;
  final double accuracy;
  final int totalAttempts;
}

class KnowledgeGap {
  const KnowledgeGap({
    required this.subject,
    required this.severity,
    required this.recommendation,
  });

  final String subject;
  final double severity;
  final String recommendation;
}

class KnowledgeGapAnalyzer {
  const KnowledgeGapAnalyzer();

  List<KnowledgeGap> analyze(List<SubjectPerformance> performance) {
    return performance
        .where((entry) => entry.totalAttempts >= 3 && entry.accuracy < 0.7)
        .map(
          (entry) => KnowledgeGap(
            subject: entry.subject,
            severity: (1 - entry.accuracy).clamp(0, 1),
            recommendation: _recommendationFor(entry.subject),
          ),
        )
        .toList(growable: false)
      ..sort((a, b) => b.severity.compareTo(a.severity));
  }

  String _recommendationFor(String subject) {
    return 'Prioritize scaffolded practice for $subject with short retrieval quizzes.';
  }
}
