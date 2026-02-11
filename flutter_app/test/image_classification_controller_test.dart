import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_models.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_repository.dart';
import 'package:impactsuite_flutter/src/features/plant/image_classification_controller.dart';

void main() {
  test('parses json classification response', () async {
    final controller = ImageClassificationController(
      repository: _FakeAiRepository(
        response: '{"label":"Tomato leaf blight","confidence":0.91,"description":"Fungal spots on leaf.","recommendedActions":["Remove affected leaves","Avoid overhead watering"]}',
      ),
    );

    await controller.analyzeImageBytes(Uint8List.fromList([1, 2, 3]), imageName: 'leaf.jpg');

    final state = controller.state;
    expect(state.error, isNull);
    expect(state.result, isNotNull);
    expect(state.result!.diagnosis.label, 'Tomato leaf blight');
    expect(state.result!.diagnosis.confidence, 0.91);
    expect(state.result!.recommendedActions, hasLength(2));
  });

  test('falls back when repository throws', () async {
    final controller = ImageClassificationController(
      repository: _ThrowingAiRepository(),
    );

    await controller.analyzeImageBytes(Uint8List.fromList([1, 2, 3]), imageName: 'leaf.jpg');

    expect(controller.state.result, isNull);
    expect(controller.state.error, contains('Classification failed'));
  });
}

class _FakeAiRepository implements AiRepository {
  _FakeAiRepository({required this.response});

  final String response;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    return AiGenerationResult(
      provider: AiProvider.gemma,
      text: response,
      model: 'fake-model',
    );
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {}
}

class _ThrowingAiRepository implements AiRepository {
  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) {
    throw Exception('failed');
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {}
}
