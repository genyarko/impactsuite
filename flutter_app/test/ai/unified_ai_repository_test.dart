import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/data/ai/unified_ai_repository.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_models.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_repository.dart';

void main() {
  test('uses on-device provider first and falls back to online on error', () async {
    final gemma = _FakeRepository(shouldThrow: true, provider: AiProvider.gemma);
    final gemini = _FakeRepository(responseText: 'online', provider: AiProvider.gemini);

    final openai = _FakeRepository(responseText: 'openai', provider: AiProvider.openai);

    final repository = UnifiedAiRepository(
      gemini: gemini,
      gemma: gemma,
      openai: openai,
      preferOnDevice: true,
    );

    final result = await repository.generate(const AiGenerationRequest(prompt: 'hello'));

    expect(result.provider, AiProvider.gemini);
    expect(result.text, 'online');
  });
}

class _FakeRepository implements AiRepository {
  _FakeRepository({
    this.shouldThrow = false,
    this.responseText = 'ok',
    required this.provider,
  });

  final bool shouldThrow;
  final String responseText;
  final AiProvider provider;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    if (shouldThrow) {
      throw Exception('failure');
    }

    return AiGenerationResult(provider: provider, text: responseText, model: 'test-model');
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {
    if (shouldThrow) {
      throw Exception('failure');
    }
    yield responseText;
  }
}
