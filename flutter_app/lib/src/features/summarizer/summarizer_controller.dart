import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

class SummarizerState {
  const SummarizerState({
    this.fileName,
    this.summary,
    this.error,
    this.isLoading = false,
    this.processingProgress = 0,
    this.extractedTextLength = 0,
    this.lastProcessedText,
  });

  final String? fileName;
  final String? summary;
  final String? error;
  final bool isLoading;
  final double processingProgress;
  final int extractedTextLength;
  final String? lastProcessedText;

  SummarizerState copyWith({
    String? fileName,
    Object? summary = _noChange,
    Object? error = _noChange,
    bool? isLoading,
    double? processingProgress,
    int? extractedTextLength,
    String? lastProcessedText,
  }) {
    return SummarizerState(
      fileName: fileName ?? this.fileName,
      summary: identical(summary, _noChange) ? this.summary : summary as String?,
      error: identical(error, _noChange) ? this.error : error as String?,
      isLoading: isLoading ?? this.isLoading,
      processingProgress: processingProgress ?? this.processingProgress,
      extractedTextLength: extractedTextLength ?? this.extractedTextLength,
      lastProcessedText: lastProcessedText ?? this.lastProcessedText,
    );
  }
}

const _noChange = Object();

class SummarizerController extends StateNotifier<SummarizerState> {
  SummarizerController({required AiRepository repository})
    : _repository = repository,
      super(const SummarizerState());

  final AiRepository _repository;

  Future<void> processText(String text, {String? fileName}) async {
    final cleanedText = text.trim();
    if (cleanedText.isEmpty || state.isLoading) {
      state = state.copyWith(error: 'Please enter some text to summarize.');
      return;
    }

    state = state.copyWith(
      fileName: fileName ?? state.fileName,
      isLoading: true,
      summary: null,
      error: null,
      processingProgress: 0.1,
      extractedTextLength: cleanedText.length,
      lastProcessedText: cleanedText,
    );

    try {
      state = state.copyWith(processingProgress: 0.35);
      final prompt = _buildSummaryPrompt(cleanedText);

      state = state.copyWith(processingProgress: 0.7);
      final result = await _repository.generate(
        AiGenerationRequest(
          prompt: '[summarizer] $prompt',
          maxOutputTokens: 800,
          temperature: 0.3,
        ),
      );

      state = state.copyWith(
        summary: result.text.trim(),
        error: null,
        isLoading: false,
        processingProgress: 1,
      );
    } catch (_) {
      state = state.copyWith(
        isLoading: false,
        processingProgress: 0,
        error: 'Unable to summarize right now. Please try again.',
      );
    }
  }

  Future<void> processFile({required String fileName, required Uint8List bytes}) async {
    try {
      final text = _extractText(fileName: fileName, bytes: bytes);
      await processText(text, fileName: fileName);
    } on UnsupportedError catch (error) {
      state = state.copyWith(
        fileName: fileName,
        summary: null,
        error: error.message,
        extractedTextLength: 0,
      );
    } catch (_) {
      state = state.copyWith(
        fileName: fileName,
        summary: null,
        error: 'Could not read this file. Try TXT or DOCX, or paste text.',
        extractedTextLength: 0,
      );
    }
  }

  Future<void> retry() async {
    final text = state.lastProcessedText;
    if (text == null || text.isEmpty) {
      return;
    }
    await processText(text, fileName: state.fileName);
  }

  void clear() {
    state = const SummarizerState();
  }

  String _extractText({required String fileName, required Uint8List bytes}) {
    final name = fileName.toLowerCase();
    if (name.endsWith('.txt')) {
      return utf8.decode(bytes, allowMalformed: true);
    }

    if (name.endsWith('.docx')) {
      final archive = ZipDecoder().decodeBytes(bytes);
      final docFile = archive.findFile('word/document.xml');
      if (docFile == null) {
        throw const UnsupportedError('Invalid DOCX file: document.xml was not found.');
      }
      final xmlContent = utf8.decode(docFile.content as List<int>, allowMalformed: true);
      final plainText = xmlContent
          .replaceAll(RegExp(r'<[^>]+>'), ' ')
          .replaceAll(RegExp(r'\s+'), ' ')
          .trim();
      if (plainText.isEmpty) {
        throw const UnsupportedError('This DOCX file does not contain extractable text.');
      }
      return plainText;
    }

    if (name.endsWith('.pdf')) {
      throw const UnsupportedError(
        'PDF extraction is limited in Flutter right now. Please paste text instead.',
      );
    }

    throw const UnsupportedError('Unsupported file type. Please use TXT or DOCX.');
  }

  String _buildSummaryPrompt(String text) {
    final textLength = text.length;
    if (textLength > 5000) {
      return '''Please provide a comprehensive summary of the following text. Focus on key themes, main arguments, and actionable takeaways.

Text to summarize:
$text''';
    }

    if (textLength > 1500) {
      return '''Please summarize the following text in 3-4 clear paragraphs. Cover the central idea, supporting details, and conclusions.

Text to summarize:
$text''';
    }

    return 'Please summarize the following text in 2-3 sentences and highlight the most important information:\n\n$text';
  }
}
