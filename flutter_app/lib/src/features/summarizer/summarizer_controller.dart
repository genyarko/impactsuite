import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pdfx/pdfx.dart' as pdfx;
import 'package:syncfusion_flutter_pdf/pdf.dart';

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
    this.statusLabel,
  });

  final String? fileName;
  final String? summary;
  final String? error;
  final bool isLoading;
  final double processingProgress;
  final int extractedTextLength;
  final String? lastProcessedText;
  final String? statusLabel;

  SummarizerState copyWith({
    String? fileName,
    Object? summary = _noChange,
    Object? error = _noChange,
    bool? isLoading,
    double? processingProgress,
    int? extractedTextLength,
    String? lastProcessedText,
    Object? statusLabel = _noChange,
  }) {
    return SummarizerState(
      fileName: fileName ?? this.fileName,
      summary: identical(summary, _noChange) ? this.summary : summary as String?,
      error: identical(error, _noChange) ? this.error : error as String?,
      isLoading: isLoading ?? this.isLoading,
      processingProgress: processingProgress ?? this.processingProgress,
      extractedTextLength: extractedTextLength ?? this.extractedTextLength,
      lastProcessedText: lastProcessedText ?? this.lastProcessedText,
      statusLabel: identical(statusLabel, _noChange) ? this.statusLabel : statusLabel as String?,
    );
  }
}

const _noChange = Object();

class SummarizerController extends StateNotifier<SummarizerState> {
  SummarizerController({required AiRepository repository})
      : _repository = repository,
        super(const SummarizerState());

  final AiRepository _repository;

  static const _ocrPrompt =
      'Extract all readable text from this PDF page image. '
      'Return only the extracted text, preserving paragraph structure. '
      'If no text is readable, return an empty string.';

  static const _maxOcrPages = 10;

  Future<void> processFile({
    required String fileName,
    required Uint8List bytes,
    String? pdfPassword,
  }) async {
    // Prevent double-submit
    if (state.isLoading) return;

    state = state.copyWith(
      fileName: fileName,
      isLoading: true,
      summary: null,
      error: null,
      processingProgress: 0.1,
      extractedTextLength: 0,
      lastProcessedText: null,
      statusLabel: null,
    );

    try {
      state = state.copyWith(processingProgress: 0.2, statusLabel: 'Extracting text...');

      String extractedText = '';

      // Try standard text extraction first
      try {
        extractedText = await _extractTextOffMainIfPossible(
          fileName: fileName,
          bytes: bytes,
          pdfPassword: pdfPassword,
        );
      } on UnsupportedError {
        // For PDFs, we may still try OCR; for other file types, rethrow
        if (!fileName.toLowerCase().endsWith('.pdf')) rethrow;
      }

      // If text extraction yielded nothing for a PDF, try OCR via AI vision
      if (extractedText.trim().isEmpty && fileName.toLowerCase().endsWith('.pdf')) {
        state = state.copyWith(
          processingProgress: 0.15,
          statusLabel: 'Extracting text via OCR...',
        );
        extractedText = await _extractTextViaOcr(bytes);
      }

      if (extractedText.trim().isEmpty) {
        throw UnsupportedError(
          'No readable text found. The document may be empty or contain only non-text content.',
        );
      }

      state = state.copyWith(
        extractedTextLength: extractedText.length,
        lastProcessedText: extractedText,
        processingProgress: 0.35,
        statusLabel: 'Generating summary...',
      );

      await processText(extractedText, fileName: fileName);
    } on UnsupportedError catch (e) {
      state = state.copyWith(
        isLoading: false,
        processingProgress: 0,
        summary: null,
        error: e.message,
        extractedTextLength: 0,
        statusLabel: null,
      );
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        processingProgress: 0,
        summary: null,
        error: 'Could not read this file. Try TXT or DOCX, or paste text.',
        extractedTextLength: 0,
        statusLabel: null,
      );
    }
  }

  /// Renders PDF pages to images and uses AI vision to extract text (OCR).
  Future<String> _extractTextViaOcr(Uint8List bytes) async {
    final doc = await pdfx.PdfDocument.openData(bytes);
    final pageCount = doc.pagesCount;
    final pagesToProcess = pageCount > _maxOcrPages ? _maxOcrPages : pageCount;
    final buffer = StringBuffer();

    for (var i = 1; i <= pagesToProcess; i++) {
      state = state.copyWith(
        statusLabel: 'Extracting text via OCR (page $i/$pagesToProcess)...',
        processingProgress: 0.15 + (0.20 * (i / pagesToProcess)),
      );

      final page = await doc.getPage(i);
      final pageImage = await page.render(
        width: page.width * 2,
        height: page.height * 2,
        format: pdfx.PdfPageImageFormat.png,
      );
      await page.close();

      if (pageImage == null) continue;

      final base64Image = base64Encode(pageImage.bytes);

      try {
        final result = await _repository.generate(
          AiGenerationRequest(
            prompt: '[ocr] $_ocrPrompt',
            imageBase64: base64Image,
            maxOutputTokens: 2048,
            temperature: 0.1,
          ),
        );

        final pageText = result.text.trim();
        if (pageText.isNotEmpty) {
          buffer.writeln(pageText);
          buffer.writeln();
        }
      } catch (_) {
        // Skip pages that fail OCR; continue with remaining pages
      }
    }

    doc.close();
    return buffer.toString().trim();
  }

  Future<void> processText(String text, {String? fileName}) async {
    final cleanedText = text.trim();
    if (cleanedText.isEmpty || state.isLoading == false && state.summary == null && state.error != null) {
      // If empty text, just show error
      state = state.copyWith(error: 'Please enter some text to summarize.');
      return;
    }

    state = state.copyWith(
      fileName: fileName ?? state.fileName,
      isLoading: true,
      summary: null,
      error: null,
      processingProgress: 0.4,
      extractedTextLength: cleanedText.length,
      lastProcessedText: cleanedText,
      statusLabel: null,
    );

    try {
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
        processingProgress: 1.0,
        statusLabel: null,
      );
    } catch (_) {
      state = state.copyWith(
        isLoading: false,
        processingProgress: 0,
        error: 'Unable to summarize right now. Please try again.',
        statusLabel: null,
      );
    }
  }

  /// ✅ Your UI expects this.
  Future<void> retry() async {
    final text = state.lastProcessedText;
    if (text == null || text.trim().isEmpty) return;
    await processText(text, fileName: state.fileName);
  }

  void clear() => state = const SummarizerState();

  // ---------- Extraction (web-safe) ----------

  Future<String> _extractTextOffMainIfPossible({
    required String fileName,
    required Uint8List bytes,
    String? pdfPassword,
  }) async {
    // On web, compute/isolate behavior can be flaky. Run synchronously.
    if (kIsWeb) {
      return _extractText(fileName: fileName, bytes: bytes, pdfPassword: pdfPassword);
    }

    // compute can only pass ONE argument; wrap args in a simple map.
    return compute(_extractTextComputeEntry, <String, Object?>{
      'fileName': fileName,
      'bytes': bytes,
      'pdfPassword': pdfPassword,
    });
  }

  /// Top-level / static entry required for compute.
  static String _extractTextComputeEntry(Map<String, Object?> args) {
    final fileName = args['fileName'] as String;
    final bytes = args['bytes'] as Uint8List;
    final pdfPassword = args['pdfPassword'] as String?;
    return _extractTextStatic(fileName: fileName, bytes: bytes, pdfPassword: pdfPassword);
  }

  String _extractText({
    required String fileName,
    required Uint8List bytes,
    String? pdfPassword,
  }) {
    return _extractTextStatic(fileName: fileName, bytes: bytes, pdfPassword: pdfPassword);
  }

  static String _extractTextStatic({
    required String fileName,
    required Uint8List bytes,
    String? pdfPassword,
  }) {
    final name = fileName.toLowerCase();

    if (name.endsWith('.txt')) {
      return utf8.decode(bytes, allowMalformed: true);
    }

    if (name.endsWith('.docx')) {
      final archive = ZipDecoder().decodeBytes(bytes);
      final docFile = archive.findFile('word/document.xml');
      if (docFile == null) {
        throw UnsupportedError('Invalid DOCX file: document.xml was not found.');
      }

      final xml = utf8.decode(docFile.content as List<int>, allowMalformed: true);

      // Simple extraction: grab <w:t>...</w:t> text nodes
      final matches = RegExp(r'<w:t[^>]*>(.*?)</w:t>', dotAll: true).allMatches(xml);
      final buffer = StringBuffer();
      for (final m in matches) {
        final t = m.group(1);
        if (t != null && t.trim().isNotEmpty) buffer.write('$t ');
      }

      final plainText = buffer.toString().replaceAll(RegExp(r'\s+'), ' ').trim();
      if (plainText.isEmpty) {
        throw UnsupportedError('This DOCX file does not contain extractable text.');
      }
      return plainText;
    }

    if (name.endsWith('.pdf')) {
      PdfDocument? document;
      try {
        // NOTE: no isEncrypted check; not available in your version.
        // If password is needed and not provided, this may throw.
        document = PdfDocument(inputBytes: bytes, password: pdfPassword);

        final extractor = PdfTextExtractor(document);
        final pageCount = document.pages.count;

        final buffer = StringBuffer();
        for (var i = 0; i < pageCount; i++) {
          final pageText = extractor
              .extractText(startPageIndex: i, endPageIndex: i)
              .trim();
          if (pageText.isNotEmpty) {
            buffer.writeln(pageText);
            buffer.writeln();
          }
        }

        final plainText = buffer.toString().replaceAll(RegExp(r'\s+'), ' ').trim();
        if (plainText.isEmpty) {
          throw UnsupportedError(
            'No extractable text found in this PDF. It may be scanned/image-based or protected.',
          );
        }
        return plainText;
      } catch (e) {
        if (e is UnsupportedError) rethrow;

        // Common cases:
        // - password required
        // - corrupted/unsupported PDF
        throw UnsupportedError(
          'Could not read this PDF (${e.runtimeType}). If it is password-protected, provide a password. '
              'If it is scanned, it needs OCR.',
        );
      } finally {
        document?.dispose();
      }
    }

    throw UnsupportedError('Unsupported file type. Use PDF, DOCX, or TXT.');
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

