import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/data/local/preferences/app_settings_store.dart';
import 'package:impactsuite_flutter/src/data/local/preferences/quiz_preferences_store.dart';
import 'package:impactsuite_flutter/src/features/settings/settings_page.dart';

void main() {
  testWidgets('renders settings sections and saves', (tester) async {
    final prefs = _InMemoryPreferencesStore();
    final quizStore = QuizPreferencesStore(prefs);
    final appStore = AppSettingsStore(prefs);

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          quizStore: quizStore,
          appSettingsStore: appStore,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('AI Configuration'), findsOneWidget);
    expect(find.text('Display & Accessibility'), findsOneWidget);
    expect(find.text('Quiz Behavior'), findsOneWidget);

    await tester.enterText(find.byType(TextField).at(0), 'abc123');
    await tester.enterText(find.byType(TextField).at(2), 'speech123');
    await tester.enterText(find.byType(TextField).at(3), 'maps123');
    await tester.tap(find.text('Save settings'));
    await tester.pumpAndSettle();

    expect(find.text('Settings saved'), findsOneWidget);

    final loaded = await appStore.read();
    expect(loaded.geminiApiKey, 'abc123');
    expect(loaded.googleCloudSpeechApiKey, 'speech123');
    expect(loaded.googleMapsApiKey, 'maps123');
    expect(loaded.onlineModelProvider, OnlineModelProvider.gemini);
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
