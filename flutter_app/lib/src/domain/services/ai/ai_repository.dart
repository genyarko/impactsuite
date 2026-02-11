import 'ai_models.dart';

abstract class AiRepository {
  Future<AiGenerationResult> generate(AiGenerationRequest request);
  Stream<String> stream(AiGenerationRequest request);
}
