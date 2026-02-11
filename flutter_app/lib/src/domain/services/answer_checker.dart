class AnswerChecker {
  const AnswerChecker();

  String normalizeAnswer(String answer) {
    var normalized = answer.trim().toLowerCase();
    normalized = normalized.replaceFirst(RegExp(r'^(the|a|an)\s+'), '');
    normalized = normalized.replaceAll(RegExp(r'''[.,!?;:'"-]'''), '');
    normalized = normalized.replaceAll(RegExp(r'\s+'), ' ').trim();
    return normalized;
  }

  bool isFlexibleAnswer(String expectedAnswer, String userAnswer) {
    const flexiblePatterns = [
      'answers will vary',
      'answers may vary',
      'various answers',
      'multiple answers',
      'depends on',
      'student answers',
      'open response',
      'personal opinion',
      'individual response',
      'varies',
      'different answers',
      'any reasonable',
      'sample answer',
      'example answer',
      'possible answer',
      'could include',
      'might include',
    ];

    final isFlexibleExpected = flexiblePatterns.any(
      (pattern) => expectedAnswer.toLowerCase().contains(pattern),
    );
    if (!isFlexibleExpected) return false;

    final trimmed = userAnswer.trim();
    const rejected = {
      '',
      "i don't know",
      'dont know',
      'idk',
      'no idea',
      'nothing',
      'not sure',
      'dunno',
      '?',
      '??',
      '???',
    };

    if (rejected.contains(trimmed.toLowerCase())) return false;
    return trimmed.length >= 2;
  }

  bool checkAnswerVariations({
    required String userAnswer,
    required String correctAnswer
  }) {
    final normalizedUser = normalizeAnswer(userAnswer);
    final correctParts = _extractCorrectAnswerParts(correctAnswer);

    final directMatch = correctParts.any(
      (part) => part == normalizedUser || part.contains(normalizedUser) || normalizedUser.contains(part),
    );
    if (directMatch) return true;

    final knownVariations = <String, List<String>>{
      'industrial revolution': ['industrialization', 'industrial age', 'industrial era'],
      'photosynthesis': ['photo synthesis', 'photosynthetic process'],
      'evaporation': ['evaporating', 'water evaporation'],
      'mitochondria': ['mitochondrion', 'mitochondrial'],
      'karma': ['actions and consequences', 'law of karma'],
      'dharma': ['righteous duty', 'moral law', 'religious duty'],
    };

    for (final part in [...correctParts, normalizeAnswer(correctAnswer)]) {
      final variations = knownVariations[part] ?? const <String>[];
      if (variations.any((value) => normalizeAnswer(value) == normalizedUser)) {
        return true;
      }
    }

    final userWords = normalizedUser.split(' ').where((w) => w.length > 2).toList();
    final correctWords = correctParts.expand((p) => p.split(' ').where((w) => w.length > 2)).toList();
    if (userWords.isEmpty || correctWords.isEmpty) return false;

    final semantic = <String, List<String>>{
      'water': ['drinking', 'irrigation', 'hydration'],
      'food': ['farming', 'agriculture', 'crops', 'harvest'],
      'transport': ['transportation', 'trade', 'travel', 'movement'],
      'climate': ['weather', 'environment', 'conditions'],
      'egyptian': ['egypt', 'ancient'],
      'nile': ['river'],
    };

    int matchingWords = 0;
    for (final userWord in userWords) {
      final matched = correctWords.any((correctWord) {
        return userWord == correctWord ||
            (userWord.length > 3 && correctWord.contains(userWord)) ||
            (correctWord.length > 3 && userWord.contains(correctWord)) ||
            semantic[userWord]?.contains(correctWord) == true ||
            semantic[correctWord]?.contains(userWord) == true;
      });
      if (matched) matchingWords++;
    }

    const keyConcepts = {
      'water', 'food', 'transport', 'trade', 'farming', 'agriculture', 'leader', 'ruler',
      'egypt', 'egyptian', 'nile', 'river', 'civilization', 'culture', 'religion', 'roads',
      'travel', 'commerce', 'economy'
    };
    final keyUser = userWords.where(keyConcepts.contains).toSet();
    final keyCorrect = correctWords.where(keyConcepts.contains).toSet();
    final conceptCoverage = keyCorrect.isEmpty ? 0.0 : keyUser.length / keyCorrect.length;
    final similarity = matchingWords / (userWords.length > correctWords.length ? userWords.length : correctWords.length);

    return keyUser.isNotEmpty || similarity >= 0.3 || conceptCoverage >= 0.5;
  }

  List<String> _extractCorrectAnswerParts(String correctAnswer) {
    final normalized = normalizeAnswer(correctAnswer);
    if (normalized.contains(' or ')) {
      return normalized.split(' or ').map(normalizeAnswer).toList();
    }
    if (normalized.contains(' and ')) {
      return normalized.split(' and ').map(normalizeAnswer).toList();
    }
    return [normalized];
  }
}
