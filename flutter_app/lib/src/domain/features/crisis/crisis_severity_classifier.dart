enum CrisisSeverity { low, medium, high, critical }

class CrisisSeverityClassifier {
  const CrisisSeverityClassifier();

  CrisisSeverity classify(String reportText) {
    final normalized = reportText.toLowerCase();

    if (_containsAny(normalized, const ['not breathing', 'unconscious', 'severe bleeding'])) {
      return CrisisSeverity.critical;
    }

    if (_containsAny(normalized, const ['chest pain', 'fainted', 'panic attack'])) {
      return CrisisSeverity.high;
    }

    if (_containsAny(normalized, const ['sprain', 'burn', 'dizziness', 'fever'])) {
      return CrisisSeverity.medium;
    }

    return CrisisSeverity.low;
  }

  bool _containsAny(String text, List<String> phrases) {
    return phrases.any(text.contains);
  }
}
