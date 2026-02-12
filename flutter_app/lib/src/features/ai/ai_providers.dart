import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/ai/gemma_repository.dart';
import '../../data/ai/gemini_repository.dart';
import '../../data/ai/openai_repository.dart';
import '../../data/ai/platform_ai_channels.dart';
import '../../data/ai/unified_ai_repository.dart';
import '../../domain/services/ai/ai_repository.dart';
import '../settings/settings_providers.dart';

final platformAiChannelsProvider = Provider<PlatformAiChannels>((ref) {
  return PlatformAiChannels();
});

final geminiRepositoryProvider = Provider<AiRepository>((ref) {
  return GeminiRepository(channels: ref.watch(platformAiChannelsProvider));
});

final gemmaRepositoryProvider = Provider<AiRepository>((ref) {
  return GemmaRepository(channels: ref.watch(platformAiChannelsProvider));
});

final openAiRepositoryProvider = Provider<AiRepository>((ref) {
  return OpenAiRepository(channels: ref.watch(platformAiChannelsProvider));
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
