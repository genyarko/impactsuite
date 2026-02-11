enum AiProvider { gemini, gemma }

class AiGenerationRequest {
  const AiGenerationRequest({
    required this.prompt,
    this.model,
    this.temperature = 0.7,
    this.topK = 40,
    this.topP = 0.95,
    this.maxOutputTokens = 1024,
    this.imageBase64,
  });

  final String prompt;
  final String? model;
  final double temperature;
  final int topK;
  final double topP;
  final int maxOutputTokens;
  final String? imageBase64;
}

class AiGenerationResult {
  const AiGenerationResult({
    required this.provider,
    required this.text,
    required this.model,
  });

  final AiProvider provider;
  final String text;
  final String model;
}
