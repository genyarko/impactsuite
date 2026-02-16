import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

abstract class PreferencesStore {
  Future<bool?> getBool(String key);
  Future<String?> getString(String key);
  Future<void> setBool(String key, bool value);
  Future<void> setString(String key, String value);
}

class SecurePreferencesStore implements PreferencesStore {
  SecurePreferencesStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  @override
  Future<bool?> getBool(String key) async {
    final value = await _storage.read(key: key);
    if (value == null) return null;
    return value == 'true';
  }

  @override
  Future<String?> getString(String key) => _storage.read(key: key);

  @override
  Future<void> setBool(String key, bool value) =>
      _storage.write(key: key, value: value.toString());

  @override
  Future<void> setString(String key, String value) =>
      _storage.write(key: key, value: value);
}

class SharedPreferencesStore implements PreferencesStore {
  SharedPreferencesStore(this._prefs);

  final SharedPreferencesAsync _prefs;

  @override
  Future<bool?> getBool(String key) => _prefs.getBool(key);

  @override
  Future<String?> getString(String key) => _prefs.getString(key);

  @override
  Future<void> setBool(String key, bool value) => _prefs.setBool(key, value);

  @override
  Future<void> setString(String key, String value) => _prefs.setString(key, value);
}

class QuizPreferences {
  const QuizPreferences({
    this.textSize = TextSize.medium,
    this.animationsEnabled = true,
    this.soundEnabled = true,
    this.hapticFeedbackEnabled = true,
    this.highContrastMode = false,
    this.showHints = true,
    this.autoAdvanceQuestions = false,
    this.showExplanationsImmediately = false,
    this.questionTimeLimit = QuestionTimeLimit.none,
    this.theme = AppTheme.system,
  });

  final TextSize textSize;
  final bool animationsEnabled;
  final bool soundEnabled;
  final bool hapticFeedbackEnabled;
  final bool highContrastMode;
  final bool showHints;
  final bool autoAdvanceQuestions;
  final bool showExplanationsImmediately;
  final QuestionTimeLimit questionTimeLimit;
  final AppTheme theme;

  QuizPreferences copyWith({
    TextSize? textSize,
    bool? animationsEnabled,
    bool? soundEnabled,
    bool? hapticFeedbackEnabled,
    bool? highContrastMode,
    bool? showHints,
    bool? autoAdvanceQuestions,
    bool? showExplanationsImmediately,
    QuestionTimeLimit? questionTimeLimit,
    AppTheme? theme,
  }) {
    return QuizPreferences(
      textSize: textSize ?? this.textSize,
      animationsEnabled: animationsEnabled ?? this.animationsEnabled,
      soundEnabled: soundEnabled ?? this.soundEnabled,
      hapticFeedbackEnabled: hapticFeedbackEnabled ?? this.hapticFeedbackEnabled,
      highContrastMode: highContrastMode ?? this.highContrastMode,
      showHints: showHints ?? this.showHints,
      autoAdvanceQuestions: autoAdvanceQuestions ?? this.autoAdvanceQuestions,
      showExplanationsImmediately:
          showExplanationsImmediately ?? this.showExplanationsImmediately,
      questionTimeLimit: questionTimeLimit ?? this.questionTimeLimit,
      theme: theme ?? this.theme,
    );
  }
}

enum TextSize { small, medium, large, extraLarge }

enum QuestionTimeLimit {
  none,
  thirtySeconds,
  oneMinute,
  twoMinutes,
  fiveMinutes,
}

enum AppTheme { light, dark, system }

class QuizPreferencesStore {
  QuizPreferencesStore(this._prefs);

  static const _textSizeKey = 'quiz.text_size';
  static const _animationsEnabledKey = 'quiz.animations_enabled';
  static const _soundEnabledKey = 'quiz.sound_enabled';
  static const _hapticEnabledKey = 'quiz.haptic_enabled';
  static const _highContrastKey = 'quiz.high_contrast';
  static const _showHintsKey = 'quiz.show_hints';
  static const _autoAdvanceKey = 'quiz.auto_advance';
  static const _showExplanationsKey = 'quiz.show_explanations';
  static const _timeLimitKey = 'quiz.time_limit';
  static const _themeKey = 'quiz.theme';

  final PreferencesStore _prefs;

  Future<QuizPreferences> read() async {
    return QuizPreferences(
      textSize: await _readEnum(_textSizeKey, TextSize.values, TextSize.medium),
      animationsEnabled: await _prefs.getBool(_animationsEnabledKey) ?? true,
      soundEnabled: await _prefs.getBool(_soundEnabledKey) ?? true,
      hapticFeedbackEnabled: await _prefs.getBool(_hapticEnabledKey) ?? true,
      highContrastMode: await _prefs.getBool(_highContrastKey) ?? false,
      showHints: await _prefs.getBool(_showHintsKey) ?? true,
      autoAdvanceQuestions: await _prefs.getBool(_autoAdvanceKey) ?? false,
      showExplanationsImmediately: await _prefs.getBool(_showExplanationsKey) ?? false,
      questionTimeLimit:
          await _readEnum(_timeLimitKey, QuestionTimeLimit.values, QuestionTimeLimit.none),
      theme: await _readEnum(_themeKey, AppTheme.values, AppTheme.system),
    );
  }

  Future<void> write(QuizPreferences preferences) async {
    await _prefs.setString(_textSizeKey, preferences.textSize.name);
    await _prefs.setBool(_animationsEnabledKey, preferences.animationsEnabled);
    await _prefs.setBool(_soundEnabledKey, preferences.soundEnabled);
    await _prefs.setBool(_hapticEnabledKey, preferences.hapticFeedbackEnabled);
    await _prefs.setBool(_highContrastKey, preferences.highContrastMode);
    await _prefs.setBool(_showHintsKey, preferences.showHints);
    await _prefs.setBool(_autoAdvanceKey, preferences.autoAdvanceQuestions);
    await _prefs.setBool(_showExplanationsKey, preferences.showExplanationsImmediately);
    await _prefs.setString(_timeLimitKey, preferences.questionTimeLimit.name);
    await _prefs.setString(_themeKey, preferences.theme.name);
  }

  Future<void> update(QuizPreferences Function(QuizPreferences current) updater) async {
    final current = await read();
    await write(updater(current));
  }

  Future<T> _readEnum<T extends Enum>(String key, List<T> values, T fallback) async {
    final rawValue = await _prefs.getString(key);
    if (rawValue == null) {
      return fallback;
    }

    for (final value in values) {
      if (value.name == rawValue) {
        return value;
      }
    }

    return fallback;
  }
}
