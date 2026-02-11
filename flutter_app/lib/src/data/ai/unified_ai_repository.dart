import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

class UnifiedAiRepository implements AiRepository {
  UnifiedAiRepository({
    required AiRepository gemini,
    required AiRepository gemma,
    this.preferOnDevice = true,
  })  : _gemini = gemini,
        _gemma = gemma;

  final AiRepository _gemini;
  final AiRepository _gemma;
  final bool preferOnDevice;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    final primary = preferOnDevice ? _gemma : _gemini;
    final fallback = preferOnDevice ? _gemini : _gemma;

    try {
      return await primary.generate(request);
    } catch (_) {
      return fallback.generate(request);
    }
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {
    final primary = preferOnDevice ? _gemma : _gemini;
    final fallback = preferOnDevice ? _gemini : _gemma;

    try {
      yield* primary.stream(request);
    } catch (_) {
      yield* fallback.stream(request);
    }
  }
}
