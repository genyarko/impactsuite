enum CbtMood { veryLow, low, neutral, good, excellent }

class CbtThoughtRecord {
  const CbtThoughtRecord({
    required this.automaticThought,
    required this.reframe,
    required this.moodBefore,
    required this.moodAfter,
  });

  final String automaticThought;
  final String reframe;
  final CbtMood moodBefore;
  final CbtMood moodAfter;
}

class CbtSessionSummary {
  const CbtSessionSummary({
    required this.improvement,
    required this.hasMeaningfulReframe,
  });

  final int improvement;
  final bool hasMeaningfulReframe;
}

class CbtSessionManager {
  const CbtSessionManager();

  CbtSessionSummary evaluate(CbtThoughtRecord record) {
    final improvement = record.moodAfter.index - record.moodBefore.index;
    final hasMeaningfulReframe =
        record.reframe.trim().split(RegExp(r'\s+')).length >= 5 &&
        record.reframe.trim().toLowerCase() != record.automaticThought.trim().toLowerCase();

    return CbtSessionSummary(
      improvement: improvement,
      hasMeaningfulReframe: hasMeaningfulReframe,
    );
  }
}
