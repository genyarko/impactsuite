import 'quiz_preferences_store.dart';

class AppSettings {
  const AppSettings({
    this.geminiApiKey = '',
    this.openAiApiKey = '',
    this.googleCloudSpeechApiKey = '',
    this.googleMapsApiKey = '',
    this.onlineModelProvider = OnlineModelProvider.gemini,
    this.enableOfflineModel = true,
    this.lastModelSyncEpochMs,
  });

  final String geminiApiKey;
  final String openAiApiKey;
  final String googleCloudSpeechApiKey;
  final String googleMapsApiKey;
  final OnlineModelProvider onlineModelProvider;
  final bool enableOfflineModel;
  final int? lastModelSyncEpochMs;

  AppSettings copyWith({
    String? geminiApiKey,
    String? openAiApiKey,
    String? googleCloudSpeechApiKey,
    String? googleMapsApiKey,
    OnlineModelProvider? onlineModelProvider,
    bool? enableOfflineModel,
    int? lastModelSyncEpochMs,
  }) {
    return AppSettings(
      geminiApiKey: geminiApiKey ?? this.geminiApiKey,
      openAiApiKey: openAiApiKey ?? this.openAiApiKey,
      googleCloudSpeechApiKey: googleCloudSpeechApiKey ?? this.googleCloudSpeechApiKey,
      googleMapsApiKey: googleMapsApiKey ?? this.googleMapsApiKey,
      onlineModelProvider: onlineModelProvider ?? this.onlineModelProvider,
      enableOfflineModel: enableOfflineModel ?? this.enableOfflineModel,
      lastModelSyncEpochMs: lastModelSyncEpochMs ?? this.lastModelSyncEpochMs,
    );
  }
}

enum OnlineModelProvider {
  gemini,
  openai;

  static OnlineModelProvider fromString(String? value) {
    return OnlineModelProvider.values.firstWhere(
      (provider) => provider.name == value,
      orElse: () => OnlineModelProvider.gemini,
    );
  }
}

class AppSettingsStore {
  AppSettingsStore(this._prefs);

  static const _geminiApiKey = 'settings.gemini_api_key';
  static const _openAiApiKey = 'settings.openai_api_key';
  static const _googleCloudSpeechApiKey = 'settings.google_cloud_speech_api_key';
  static const _googleMapsApiKey = 'settings.google_maps_api_key';
  static const _onlineModelProvider = 'settings.online_model_provider';
  static const _enableOfflineModel = 'settings.enable_offline_model';
  static const _lastModelSync = 'settings.last_model_sync';

  final PreferencesStore _prefs;

  Future<AppSettings> read() async {
    final apiKey = await _prefs.getString(_geminiApiKey) ?? '';
    final openAiApiKey = await _prefs.getString(_openAiApiKey) ?? '';
    final speechApiKey = await _prefs.getString(_googleCloudSpeechApiKey) ?? '';
    final mapsApiKey = await _prefs.getString(_googleMapsApiKey) ?? '';
    final onlineProviderRaw = await _prefs.getString(_onlineModelProvider);
    final offline = await _prefs.getBool(_enableOfflineModel) ?? true;
    final lastSyncRaw = await _prefs.getString(_lastModelSync);

    return AppSettings(
      geminiApiKey: apiKey,
      openAiApiKey: openAiApiKey,
      googleCloudSpeechApiKey: speechApiKey,
      googleMapsApiKey: mapsApiKey,
      onlineModelProvider: OnlineModelProvider.fromString(onlineProviderRaw),
      enableOfflineModel: offline,
      lastModelSyncEpochMs: int.tryParse(lastSyncRaw ?? ''),
    );
  }

  Future<void> write(AppSettings settings) async {
    await _prefs.setString(_geminiApiKey, settings.geminiApiKey);
    await _prefs.setString(_openAiApiKey, settings.openAiApiKey);
    await _prefs.setString(_googleCloudSpeechApiKey, settings.googleCloudSpeechApiKey);
    await _prefs.setString(_googleMapsApiKey, settings.googleMapsApiKey);
    await _prefs.setString(_onlineModelProvider, settings.onlineModelProvider.name);
    await _prefs.setBool(_enableOfflineModel, settings.enableOfflineModel);
    if (settings.lastModelSyncEpochMs != null) {
      await _prefs.setString(_lastModelSync, settings.lastModelSyncEpochMs.toString());
    }
  }
}
