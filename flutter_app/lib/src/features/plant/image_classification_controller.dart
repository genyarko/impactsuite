import 'dart:convert';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';

import '../../domain/features/plant/plant_diagnosis_interpreter.dart';
import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

class ImageClassificationState {
  const ImageClassificationState({
    this.isLoading = false,
    this.isOcrMode = false,
    this.selectedImageName,
    this.result,
    this.extractedText,
    this.error,
  });

  final bool isLoading;
  final bool isOcrMode;
  final String? selectedImageName;
  final ImageClassificationResult? result;
  final String? extractedText;
  final String? error;

  ImageClassificationState copyWith({
    bool? isLoading,
    bool? isOcrMode,
    String? selectedImageName,
    ImageClassificationResult? result,
    String? extractedText,
    String? error,
    bool clearResult = false,
    bool clearExtractedText = false,
    bool clearError = false,
  }) {
    return ImageClassificationState(
      isLoading: isLoading ?? this.isLoading,
      isOcrMode: isOcrMode ?? this.isOcrMode,
      selectedImageName: selectedImageName ?? this.selectedImageName,
      result: clearResult ? null : (result ?? this.result),
      extractedText: clearExtractedText ? null : (extractedText ?? this.extractedText),
      error: clearError ? null : (error ?? this.error),
    );
  }
}

class ImageClassificationResult {
  const ImageClassificationResult({
    required this.diagnosis,
    required this.explanation,
    required this.description,
    required this.recommendedActions,
  });

  final PlantDiagnosis diagnosis;
  final String explanation;
  final String description;
  final List<String> recommendedActions;
}

class ImageClassificationController extends ChangeNotifier {
  ImageClassificationController({
    required AiRepository repository,
    PlantDiagnosisInterpreter? interpreter,
  })  : _repository = repository,
        _interpreter = interpreter ?? const PlantDiagnosisInterpreter();

  final AiRepository _repository;
  final PlantDiagnosisInterpreter _interpreter;

  ImageClassificationState _state = const ImageClassificationState();
  ImageClassificationState get state => _state;

  void toggleOcrMode() {
    _state = _state.copyWith(
      isOcrMode: !_state.isOcrMode,
      clearError: true,
      clearResult: true,
      clearExtractedText: true,
    );
    notifyListeners();
  }

  Future<void> pickAndAnalyzeImage() async {
    _state = _state.copyWith(
      isLoading: true,
      clearError: true,
      clearResult: true,
      clearExtractedText: true,
    );
    notifyListeners();

    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.image,
        withData: true,
      );

      if (result == null || result.files.single.bytes == null) {
        _state = _state.copyWith(
          isLoading: false,
          error: 'Image selection canceled.',
          clearResult: true,
          clearExtractedText: true,
        );
        notifyListeners();
        return;
      }

      if (_state.isOcrMode) {
        await analyzeImageForOcr(
          result.files.single.bytes!,
          imageName: result.files.single.name,
        );
      } else {
        await analyzeImageBytes(
          result.files.single.bytes!,
          imageName: result.files.single.name,
        );
      }
    } catch (_) {
      _state = _state.copyWith(
        isLoading: false,
        error: 'Unable to access image picker on this device.',
        clearResult: true,
        clearExtractedText: true,
      );
      notifyListeners();
    }
  }

  Future<void> analyzeImageBytes(Uint8List bytes, {required String imageName}) async {
    _state = _state.copyWith(
      isLoading: true,
      selectedImageName: imageName,
      clearError: true,
      clearResult: true,
      clearExtractedText: true,
    );
    notifyListeners();

    try {
      final imageBase64 = base64Encode(bytes);
      final generation = await _repository.generate(
        AiGenerationRequest(
          prompt: _classificationPrompt,
          temperature: 0.2,
          maxOutputTokens: 800,
          imageBase64: imageBase64,
        ),
      );

      final parsed = _parseResponse(generation.text);
      final diagnosis = PlantDiagnosis(
        label: parsed.label,
        confidence: parsed.confidence,
      );

      _state = _state.copyWith(
        isLoading: false,
        result: ImageClassificationResult(
          diagnosis: diagnosis,
          explanation: _interpreter.explain(diagnosis),
          description: parsed.description,
          recommendedActions: parsed.recommendedActions,
        ),
      );
      notifyListeners();
    } catch (_) {
      _state = _state.copyWith(
        isLoading: false,
        error: 'Classification failed. Try a clearer image or switch AI provider in settings.',
      );
      notifyListeners();
    }
  }

  Future<void> analyzeImageForOcr(Uint8List bytes, {required String imageName}) async {
    _state = _state.copyWith(
      isLoading: true,
      selectedImageName: imageName,
      clearError: true,
      clearResult: true,
      clearExtractedText: true,
    );
    notifyListeners();

    try {
      final imageBase64 = base64Encode(bytes);
      final generation = await _repository.generate(
        const AiGenerationRequest(
          prompt: _ocrPrompt,
          temperature: 0.1,
          maxOutputTokens: 1200,
        ).copyWithImage(imageBase64),
      );

      final text = _parseOcrText(generation.text);
      _state = _state.copyWith(
        isLoading: false,
        extractedText: text,
      );
      notifyListeners();
    } catch (_) {
      _state = _state.copyWith(
        isLoading: false,
        error: 'OCR failed. Try a sharper image with better lighting.',
      );
      notifyListeners();
    }
  }

  _ParsedDiagnosis _parseResponse(String text) {
    final extractedJson = _extractJsonObject(text);

    if (extractedJson != null) {
      final dynamic decoded = jsonDecode(extractedJson);
      if (decoded is Map<String, dynamic>) {
        final confidenceRaw = decoded['confidence'];
        final confidence = confidenceRaw is num
            ? confidenceRaw.toDouble().clamp(0, 1)
            : _confidenceFromText(text);
        final actions = (decoded['recommendedActions'] as List<dynamic>?)
                ?.map((item) => item.toString().trim())
                .where((item) => item.isNotEmpty)
                .toList(growable: false) ??
            const <String>[];

        return _ParsedDiagnosis(
          label: (decoded['label']?.toString().trim().isNotEmpty ?? false)
              ? decoded['label'].toString().trim()
              : 'Unknown condition',
          confidence: confidence,
          description: (decoded['description']?.toString().trim().isNotEmpty ?? false)
              ? decoded['description'].toString().trim()
              : 'The model could not provide additional details.',
          recommendedActions: actions.isEmpty
              ? const <String>[
                  'Capture another image with better lighting.',
                  'Consult a local expert before treatment.',
                ]
              : actions,
        );
      }
    }

    return _ParsedDiagnosis(
      label: 'General image classification',
      confidence: _confidenceFromText(text),
      description: text.trim().isEmpty ? 'No additional details returned.' : text.trim(),
      recommendedActions: const <String>[
        'Retake the image to improve confidence.',
        'Use the diagnosis as guidance, then validate manually.',
      ],
    );
  }

  String _parseOcrText(String text) {
    final extractedJson = _extractJsonObject(text);
    if (extractedJson == null) {
      return text.trim().isEmpty ? 'No text detected.' : text.trim();
    }

    try {
      final dynamic decoded = jsonDecode(extractedJson);
      if (decoded is Map<String, dynamic>) {
        final ocrText = decoded['text']?.toString().trim() ?? '';
        if (ocrText.isNotEmpty) {
          return ocrText;
        }
      }
    } catch (_) {
      // Use raw fallback below.
    }

    return text.trim().isEmpty ? 'No text detected.' : text.trim();
  }

  String? _extractJsonObject(String text) {
    final start = text.indexOf('{');
    final end = text.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return text.substring(start, end + 1);
    }

    return null;
  }

  double _confidenceFromText(String text) {
    final match = RegExp(r'(\d{1,3})(?:\s?)%').firstMatch(text);
    if (match == null) {
      return 0.55;
    }

    final percent = double.tryParse(match.group(1) ?? '');
    if (percent == null) {
      return 0.55;
    }

    return (percent / 100).clamp(0.0, 1.0);
  }
}

class _ParsedDiagnosis {
  const _ParsedDiagnosis({
    required this.label,
    required this.confidence,
    required this.description,
    required this.recommendedActions,
  });

  final String label;
  final double confidence;
  final String description;
  final List<String> recommendedActions;
}

extension on AiGenerationRequest {
  AiGenerationRequest copyWithImage(String imageBase64) {
    return AiGenerationRequest(
      prompt: prompt,
      model: model,
      temperature: temperature,
      topK: topK,
      topP: topP,
      maxOutputTokens: maxOutputTokens,
      imageBase64: imageBase64,
    );
  }
}

const String _classificationPrompt = '''
Classify this image with focus on plant/food/household-object recognition.
Return strict JSON only using this schema:
{
  "label": "specific object or condition",
  "confidence": 0.0,
  "description": "short explanation",
  "recommendedActions": ["action1", "action2", "action3"]
}
- confidence must be between 0 and 1.
- keep recommendedActions practical and safe.
- if uncertain, set a lower confidence and explain uncertainty.
''';

const String _ocrPrompt = '''
Extract all readable text from this image.
Return strict JSON only using this schema:
{
  "text": "full extracted text preserving lines where possible"
}
- include handwriting if legible.
- if no text is readable, return {"text":""}.
''';
