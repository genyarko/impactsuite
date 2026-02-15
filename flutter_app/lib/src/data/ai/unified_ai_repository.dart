import '../../data/local/preferences/app_settings_store.dart';
import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

class UnifiedAiRepository implements AiRepository {
  UnifiedAiRepository({
    required AiRepository gemini,
    required AiRepository gemma,
    required AiRepository openai,
    this.onlineModelProvider = OnlineModelProvider.gemini,
    this.preferOnDevice = true,
  })  : _gemini = gemini,
        _gemma = gemma,
        _openai = openai;

  final AiRepository _gemini;
  final AiRepository _gemma;
  final AiRepository _openai;
  final OnlineModelProvider onlineModelProvider;
  final bool preferOnDevice;

  AiRepository get _onlineRepo => switch (onlineModelProvider) {
        OnlineModelProvider.gemini => _gemini,
        OnlineModelProvider.openai => _openai,
      };

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    final primary = preferOnDevice ? _gemma : _onlineRepo;
    final fallback = preferOnDevice ? _onlineRepo : _gemma;

    try {
      return await primary.generate(request);
    } catch (_) {
      return fallback.generate(request);
    }
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {
    final primary = preferOnDevice ? _gemma : _onlineRepo;
    final fallback = preferOnDevice ? _onlineRepo : _gemma;

    var useFallback = false;

    try {
      await for (final chunk in primary.stream(request)) {
        yield chunk;
      }
    } catch (_) {
      useFallback = true;
    }

    if (useFallback) {
      yield* fallback.stream(request);
    }
  }
}
