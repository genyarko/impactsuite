import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_models.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_repository.dart';
import 'package:impactsuite_flutter/src/features/ai/ai_conversation_controller.dart';

void main() {
  test('adds user message and generated response when stream is empty', () async {
    final controller = AiConversationController(
      repository: _FakeAiRepository(streamChunks: const [], generatedText: 'fallback'),
      serviceType: 'chat',
    );

    await controller.sendPrompt('Hello');

    expect(controller.state.messages.length, 2);
    expect(controller.state.messages.first.isUser, isTrue);
    expect(controller.state.messages.last.text, 'fallback');
    expect(controller.state.error, isNull);
  });
}

class _FakeAiRepository implements AiRepository {
  _FakeAiRepository({
    required this.streamChunks,
    required this.generatedText,
  });

  final List<String> streamChunks;
  final String generatedText;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    return AiGenerationResult(
      provider: AiProvider.gemini,
      text: generatedText,
      model: 'test-model',
    );
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {
    for (final chunk in streamChunks) {
      yield chunk;
    }
  }
}
