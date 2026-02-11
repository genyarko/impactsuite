import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';
import 'platform_ai_channels.dart';

class GemmaRepository implements AiRepository {
  GemmaRepository({
    required PlatformAiChannels channels,
    this.defaultModel = 'gemma-3n-e4b-it',
  }) : _channels = channels;

  final PlatformAiChannels _channels;
  final String defaultModel;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    final text = await _channels.generateGemmaText(request);
    return AiGenerationResult(
      provider: AiProvider.gemma,
      text: text,
      model: request.model ?? defaultModel,
    );
  }

  @override
  Stream<String> stream(AiGenerationRequest request) {
    return _channels.streamText(provider: AiProvider.gemma, request: request);
  }
}
