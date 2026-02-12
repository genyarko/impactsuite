import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_models.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_repository.dart';
import 'package:impactsuite_flutter/src/features/caption/live_caption_controller.dart';

class _FakeAiRepository implements AiRepository {
  int callCount = 0;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    callCount++;
    return AiGenerationResult(provider: AiProvider.gemma, text: 'Hola', model: 'fake');
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {}
}

void main() {
  test('submitTextInput adds typed transcript and translation', () async {
    final fakeRepository = _FakeAiRepository();
    final controller = LiveCaptionController(repository: fakeRepository);

    await controller.submitTextInput('Hello');

    expect(controller.state.transcriptHistory, hasLength(1));
    expect(controller.state.transcriptHistory.first.source, TranscriptSource.typed);
    expect(controller.state.transcriptHistory.first.translation, 'Hola');
  });

  test('translation cache avoids duplicate AI calls', () async {
    final fakeRepository = _FakeAiRepository();
    final controller = LiveCaptionController(repository: fakeRepository);

    await controller.submitTextInput('Hello');
    await controller.submitTextInput('Hello');

    expect(fakeRepository.callCount, 1);
  });
}
