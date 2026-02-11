import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';
import 'platform_ai_channels.dart';

class GeminiRepository implements AiRepository {
  GeminiRepository({
    required PlatformAiChannels channels,
    this.defaultModel = 'gemini-1.5-flash',
  }) : _channels = channels;

  final PlatformAiChannels _channels;
  final String defaultModel;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    final text = await _channels.generateGeminiText(request);
    return AiGenerationResult(
      provider: AiProvider.gemini,
      text: text,
      model: request.model ?? defaultModel,
    );
  }

  @override
  Stream<String> stream(AiGenerationRequest request) {
    return _channels.streamText(provider: AiProvider.gemini, request: request);
  }
}
