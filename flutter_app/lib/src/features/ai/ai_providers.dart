import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/ai/dart_gemini_repository.dart';
import '../../data/ai/dart_openai_repository.dart';
import '../../data/ai/gemma_repository.dart';
import '../../data/ai/platform_ai_channels.dart';
import '../../data/ai/unified_ai_repository.dart';
import '../../domain/services/ai/ai_repository.dart';
import '../settings/settings_providers.dart';

final platformAiChannelsProvider = Provider<PlatformAiChannels>((ref) {
  return PlatformAiChannels();
});

/// On-device Gemma model via platform channel.
/// Falls back gracefully when native handler is unavailable.
final gemmaRepositoryProvider = Provider<AiRepository>((ref) {
  return GemmaRepository(channels: ref.watch(platformAiChannelsProvider));
});

/// Gemini online via direct Dart HTTP calls (no platform channel needed).
final geminiRepositoryProvider = Provider<AiRepository>((ref) {
  final settings = ref.watch(appSettingsProvider);
  return DartGeminiRepository(apiKey: settings.geminiApiKey);
});

/// OpenAI online via direct Dart HTTP calls (no platform channel needed).
final openAiRepositoryProvider = Provider<AiRepository>((ref) {
  final settings = ref.watch(appSettingsProvider);
  return DartOpenAiRepository(apiKey: settings.openAiApiKey);
});

final unifiedAiRepositoryProvider = Provider<AiRepository>((ref) {
  final settings = ref.watch(appSettingsProvider);
  return UnifiedAiRepository(
    gemini: ref.watch(geminiRepositoryProvider),
    gemma: ref.watch(gemmaRepositoryProvider),
    openai: ref.watch(openAiRepositoryProvider),
    onlineModelProvider: settings.onlineModelProvider,
    preferOnDevice: settings.enableOfflineModel,
  );
});
