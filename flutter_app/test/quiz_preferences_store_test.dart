import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/data/local/preferences/quiz_preferences_store.dart';

void main() {
  group('QuizPreferencesStore', () {
    test('uses Kotlin-equivalent defaults when no values exist', () async {
      final store = QuizPreferencesStore(_InMemoryPreferencesStore());

      final prefs = await store.read();

      expect(prefs.textSize, TextSize.medium);
      expect(prefs.animationsEnabled, isTrue);
      expect(prefs.soundEnabled, isTrue);
      expect(prefs.theme, AppTheme.system);
      expect(prefs.questionTimeLimit, QuestionTimeLimit.none);
    });

    test('persists and reloads updates', () async {
      final backingStore = _InMemoryPreferencesStore();
      final store = QuizPreferencesStore(backingStore);

      await store.update(
        (current) => current.copyWith(
          textSize: TextSize.extraLarge,
          autoAdvanceQuestions: true,
          questionTimeLimit: QuestionTimeLimit.oneMinute,
          theme: AppTheme.dark,
        ),
      );

      final reloaded = await store.read();

      expect(reloaded.textSize, TextSize.extraLarge);
      expect(reloaded.autoAdvanceQuestions, isTrue);
      expect(reloaded.questionTimeLimit, QuestionTimeLimit.oneMinute);
      expect(reloaded.theme, AppTheme.dark);
    });
  });
}

class _InMemoryPreferencesStore implements PreferencesStore {
  final Map<String, Object> _values = <String, Object>{};

  @override
  Future<bool?> getBool(String key) async => _values[key] as bool?;

  @override
  Future<String?> getString(String key) async => _values[key] as String?;

  @override
  Future<void> setBool(String key, bool value) async {
    _values[key] = value;
  }

  @override
  Future<void> setString(String key, String value) async {
    _values[key] = value;
  }
}
