class PlantDiagnosis {
  const PlantDiagnosis({
    required this.label,
    required this.confidence,
  });

  final String label;
  final double confidence;
}

class PlantDiagnosisInterpreter {
  const PlantDiagnosisInterpreter();

  String explain(PlantDiagnosis diagnosis) {
    final percent = (diagnosis.confidence * 100).toStringAsFixed(1);
    if (diagnosis.confidence >= 0.85) {
      return 'High-confidence match for ${diagnosis.label} ($percent%). Apply targeted treatment.';
    }

    if (diagnosis.confidence >= 0.6) {
      return 'Likely ${diagnosis.label} ($percent%). Validate with one more image before action.';
    }

    return 'Low confidence ($percent%). Collect clearer leaf photos and environmental notes.';
  }
}
