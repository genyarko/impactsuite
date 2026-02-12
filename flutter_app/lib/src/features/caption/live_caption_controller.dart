import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

enum TranscriptSource { voice, typed }

class CaptionLanguage {
  const CaptionLanguage({required this.code, required this.displayName});

  final String code;
  final String displayName;

  static const auto = CaptionLanguage(code: 'auto', displayName: 'Auto-detect');
  static const english = CaptionLanguage(code: 'en', displayName: 'English');

  static const all = <CaptionLanguage>[
    auto,
    english,
    CaptionLanguage(code: 'zh', displayName: 'Chinese (Mandarin)'),
    CaptionLanguage(code: 'hi', displayName: 'Hindi'),
    CaptionLanguage(code: 'es', displayName: 'Spanish'),
    CaptionLanguage(code: 'fr', displayName: 'French'),
    CaptionLanguage(code: 'ar', displayName: 'Arabic'),
    CaptionLanguage(code: 'bn', displayName: 'Bengali'),
    CaptionLanguage(code: 'pt', displayName: 'Portuguese'),
    CaptionLanguage(code: 'ru', displayName: 'Russian'),
    CaptionLanguage(code: 'ja', displayName: 'Japanese'),
    CaptionLanguage(code: 'de', displayName: 'German'),
    CaptionLanguage(code: 'it', displayName: 'Italian'),
    CaptionLanguage(code: 'nl', displayName: 'Dutch'),
    CaptionLanguage(code: 'sw', displayName: 'Swahili'),
    CaptionLanguage(code: 'ko', displayName: 'Korean'),
    CaptionLanguage(code: 'tr', displayName: 'Turkish'),
    CaptionLanguage(code: 'pl', displayName: 'Polish'),
    CaptionLanguage(code: 'uk', displayName: 'Ukrainian'),
    CaptionLanguage(code: 'vi', displayName: 'Vietnamese'),
    CaptionLanguage(code: 'th', displayName: 'Thai'),
    CaptionLanguage(code: 'id', displayName: 'Indonesian'),
    CaptionLanguage(code: 'ms', displayName: 'Malay'),
    CaptionLanguage(code: 'ta', displayName: 'Tamil'),
    CaptionLanguage(code: 'te', displayName: 'Telugu'),
    CaptionLanguage(code: 'ur', displayName: 'Urdu'),
    CaptionLanguage(code: 'fa', displayName: 'Persian'),
    CaptionLanguage(code: 'he', displayName: 'Hebrew'),
    CaptionLanguage(code: 'am', displayName: 'Amharic'),
    CaptionLanguage(code: 'af', displayName: 'Afrikaans'),
    CaptionLanguage(code: 'fi', displayName: 'Finnish'),
    CaptionLanguage(code: 'da', displayName: 'Danish'),
    CaptionLanguage(code: 'no', displayName: 'Norwegian'),
    CaptionLanguage(code: 'cs', displayName: 'Czech'),
    CaptionLanguage(code: 'el', displayName: 'Greek'),
    CaptionLanguage(code: 'ro', displayName: 'Romanian'),
    CaptionLanguage(code: 'hu', displayName: 'Hungarian'),
    CaptionLanguage(code: 'ca', displayName: 'Catalan'),
  ];

  static const popular = <CaptionLanguage>[
    auto,
    english,
    CaptionLanguage(code: 'es', displayName: 'Spanish'),
    CaptionLanguage(code: 'fr', displayName: 'French'),
    CaptionLanguage(code: 'de', displayName: 'German'),
    CaptionLanguage(code: 'zh', displayName: 'Chinese (Mandarin)'),
    CaptionLanguage(code: 'ja', displayName: 'Japanese'),
    CaptionLanguage(code: 'ko', displayName: 'Korean'),
    CaptionLanguage(code: 'hi', displayName: 'Hindi'),
    CaptionLanguage(code: 'ar', displayName: 'Arabic'),
    CaptionLanguage(code: 'pt', displayName: 'Portuguese'),
    CaptionLanguage(code: 'ru', displayName: 'Russian'),
    CaptionLanguage(code: 'it', displayName: 'Italian'),
  ];

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is CaptionLanguage && other.code == code;
  }

  @override
  int get hashCode => code.hashCode;
}

class TranscriptEntry {
  const TranscriptEntry({
    required this.transcript,
    required this.timestamp,
    required this.source,
    this.translation,
  });

  final String transcript;
  final String? translation;
  final DateTime timestamp;
  final TranscriptSource source;

  TranscriptEntry copyWith({
    String? transcript,
    String? translation,
    DateTime? timestamp,
    TranscriptSource? source,
  }) {
    return TranscriptEntry(
      transcript: transcript ?? this.transcript,
      translation: translation ?? this.translation,
      timestamp: timestamp ?? this.timestamp,
      source: source ?? this.source,
    );
  }
}

class LiveCaptionState {
  const LiveCaptionState({
    this.isListening = false,
    this.currentTranscript = '',
    this.translatedText = '',
    this.sourceLanguage = CaptionLanguage.auto,
    this.targetLanguage = CaptionLanguage.english,
    this.error,
    this.isUsingOnlineService = false,
    this.transcriptHistory = const [],
  });

  final bool isListening;
  final String currentTranscript;
  final String translatedText;
  final CaptionLanguage sourceLanguage;
  final CaptionLanguage targetLanguage;
  final String? error;
  final bool isUsingOnlineService;
  final List<TranscriptEntry> transcriptHistory;

  LiveCaptionState copyWith({
    bool? isListening,
    String? currentTranscript,
    String? translatedText,
    CaptionLanguage? sourceLanguage,
    CaptionLanguage? targetLanguage,
    String? error,
    bool clearError = false,
    bool? isUsingOnlineService,
    List<TranscriptEntry>? transcriptHistory,
  }) {
    return LiveCaptionState(
      isListening: isListening ?? this.isListening,
      currentTranscript: currentTranscript ?? this.currentTranscript,
      translatedText: translatedText ?? this.translatedText,
      sourceLanguage: sourceLanguage ?? this.sourceLanguage,
      targetLanguage: targetLanguage ?? this.targetLanguage,
      error: clearError ? null : (error ?? this.error),
      isUsingOnlineService: isUsingOnlineService ?? this.isUsingOnlineService,
      transcriptHistory: transcriptHistory ?? this.transcriptHistory,
    );
  }
}

class LiveCaptionController extends StateNotifier<LiveCaptionState> {
  LiveCaptionController({
    required AiRepository repository,
    this.preferOnline = false,
  })  : _repository = repository,
        super(LiveCaptionState(isUsingOnlineService: preferOnline));

  final AiRepository _repository;
  final bool preferOnline;
  final Map<String, String> _translationCache = {};

  void startLiveCaption() {
    state = state.copyWith(isListening: true, clearError: true);
  }

  void stopLiveCaption() {
    state = state.copyWith(
      isListening: false,
      currentTranscript: '',
      translatedText: '',
      transcriptHistory: const [],
      clearError: true,
    );
  }

  void setSourceLanguage(CaptionLanguage language) {
    state = state.copyWith(sourceLanguage: language, translatedText: '');
  }

  Future<void> setTargetLanguage(CaptionLanguage language) async {
    state = state.copyWith(targetLanguage: language);
    for (var i = 0; i < state.transcriptHistory.length; i++) {
      final transcript = state.transcriptHistory[i].transcript;
      final translation = await _translate(transcript);
      if (translation == null) {
        continue;
      }
      final updated = [...state.transcriptHistory];
      updated[i] = updated[i].copyWith(translation: translation);
      state = state.copyWith(transcriptHistory: updated, translatedText: translation);
    }
  }

  Future<void> submitTextInput(String text) => _ingestTranscript(text, TranscriptSource.typed);

  Future<void> submitVoiceTranscript(String text) => _ingestTranscript(text, TranscriptSource.voice);

  Future<void> _ingestTranscript(String text, TranscriptSource source) async {
    final sanitized = text.trim();
    if (sanitized.isEmpty) {
      return;
    }

    final entry = TranscriptEntry(
      transcript: sanitized,
      timestamp: DateTime.now(),
      source: source,
    );

    state = state.copyWith(
      currentTranscript: sanitized,
      transcriptHistory: [...state.transcriptHistory, entry],
      clearError: true,
    );

    final translation = await _translate(sanitized);
    if (translation == null) {
      return;
    }

    final updated = [...state.transcriptHistory];
    updated[updated.length - 1] = updated.last.copyWith(translation: translation);
    state = state.copyWith(translatedText: translation, transcriptHistory: updated);
  }

  Future<String?> _translate(String sourceText) async {
    var source = state.sourceLanguage;
    final target = state.targetLanguage;

    if (source == target) {
      return null;
    }
    if (source == CaptionLanguage.auto) {
      source = CaptionLanguage.english;
    }

    final prefixLength = sourceText.length > 100 ? 100 : sourceText.length;
    final cacheKey = '${sourceText.substring(0, prefixLength)}_${source.code}_${target.code}';
    final cached = _translationCache[cacheKey];
    if (cached != null) {
      return cached;
    }

    try {
      final result = await _repository.generate(
        AiGenerationRequest(
          prompt:
              'Translate this ${source.displayName} text to ${target.displayName}. '
              'Give ONLY the direct translation, no explanations or alternatives:\n"$sourceText"',
          temperature: 0.2,
          maxOutputTokens: 256,
        ),
      );
      final translation = result.text.trim();
      if (translation.isEmpty) {
        return null;
      }
      _translationCache[cacheKey] = translation;
      return translation;
    } catch (error) {
      state = state.copyWith(error: 'Translation failed: $error');
      return null;
    }
  }
}
