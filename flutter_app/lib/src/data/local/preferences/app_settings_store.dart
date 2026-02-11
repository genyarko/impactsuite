import 'quiz_preferences_store.dart';

class AppSettings {
  const AppSettings({
    this.geminiApiKey = '',
    this.enableOfflineModel = true,
    this.lastModelSyncEpochMs,
  });

  final String geminiApiKey;
  final bool enableOfflineModel;
  final int? lastModelSyncEpochMs;

  AppSettings copyWith({
    String? geminiApiKey,
    bool? enableOfflineModel,
    int? lastModelSyncEpochMs,
  }) {
    return AppSettings(
      geminiApiKey: geminiApiKey ?? this.geminiApiKey,
      enableOfflineModel: enableOfflineModel ?? this.enableOfflineModel,
      lastModelSyncEpochMs: lastModelSyncEpochMs ?? this.lastModelSyncEpochMs,
    );
  }
}

class AppSettingsStore {
  AppSettingsStore(this._prefs);

  static const _geminiApiKey = 'settings.gemini_api_key';
  static const _enableOfflineModel = 'settings.enable_offline_model';
  static const _lastModelSync = 'settings.last_model_sync';

  final PreferencesStore _prefs;

  Future<AppSettings> read() async {
    final apiKey = await _prefs.getString(_geminiApiKey) ?? '';
    final offline = await _prefs.getBool(_enableOfflineModel) ?? true;
    final lastSyncRaw = await _prefs.getString(_lastModelSync);

    return AppSettings(
      geminiApiKey: apiKey,
      enableOfflineModel: offline,
      lastModelSyncEpochMs: int.tryParse(lastSyncRaw ?? ''),
    );
  }

  Future<void> write(AppSettings settings) async {
    await _prefs.setString(_geminiApiKey, settings.geminiApiKey);
    await _prefs.setBool(_enableOfflineModel, settings.enableOfflineModel);
    if (settings.lastModelSyncEpochMs != null) {
      await _prefs.setString(_lastModelSync, settings.lastModelSyncEpochMs.toString());
    }
  }
}
