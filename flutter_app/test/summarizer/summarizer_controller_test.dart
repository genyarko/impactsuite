import 'dart:convert';

import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_models.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_repository.dart';
import 'package:impactsuite_flutter/src/features/summarizer/summarizer_controller.dart';

void main() {
  test('processText generates summary and stores extracted text length', () async {
    final controller = SummarizerController(repository: _FakeAiRepository());

    await controller.processText('This is sample text to summarize.');

    expect(controller.state.summary, 'A short summary');
    expect(controller.state.error, isNull);
    expect(controller.state.extractedTextLength, 33);
    expect(controller.state.isLoading, isFalse);
  });

  test('processText with empty text returns validation error', () async {
    final controller = SummarizerController(repository: _FakeAiRepository());

    await controller.processText('   ');

    expect(controller.state.error, 'Please enter some text to summarize.');
    expect(controller.state.summary, isNull);
  });

  test('processFile extracts docx text and summarizes', () async {
    final encoder = ZipEncoder();
    final archive = Archive()
      ..addFile(
        ArchiveFile(
          'word/document.xml',
          43,
          utf8.encode('<w:document><w:t>Hello from docx</w:t></w:document>'),
        ),
      );
    final bytes = encoder.encode(archive)!;

    final controller = SummarizerController(repository: _FakeAiRepository());
    await controller.processFile(fileName: 'example.docx', bytes: bytes);

    expect(controller.state.error, isNull);
    expect(controller.state.fileName, 'example.docx');
    expect(controller.state.summary, 'A short summary');
    expect(controller.state.extractedTextLength, greaterThan(0));
  });
}

class _FakeAiRepository implements AiRepository {
  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    return const AiGenerationResult(
      provider: AiProvider.gemini,
      text: 'A short summary',
      model: 'test-model',
    );
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {}
}
