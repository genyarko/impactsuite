import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/data/local/preferences/app_settings_store.dart';
import 'package:impactsuite_flutter/src/data/local/preferences/quiz_preferences_store.dart';
import 'package:impactsuite_flutter/src/features/settings/settings_page.dart';
import 'package:impactsuite_flutter/src/features/settings/settings_providers.dart';

void main() {
  testWidgets('renders settings sections with segmented button', (tester) async {
    final prefs = _InMemoryPreferencesStore();
    final quizStore = QuizPreferencesStore(prefs);
    final appStore = AppSettingsStore(prefs);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appSettingsStoreProvider.overrideWithValue(appStore),
        ],
        child: MaterialApp(
          home: SettingsPage(quizStore: quizStore),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('AI Configuration'), findsOneWidget);
    expect(find.text('Online AI Provider'), findsOneWidget);
    expect(find.text('Gemini'), findsOneWidget);
    expect(find.text('OpenAI'), findsOneWidget);

    // Scroll down to reveal remaining sections
    await tester.drag(find.byType(ListView), const Offset(0, -400));
    await tester.pumpAndSettle();
    expect(find.text('Display & Accessibility'), findsOneWidget);

    await tester.drag(find.byType(ListView), const Offset(0, -400));
    await tester.pumpAndSettle();
    expect(find.text('Quiz Behavior'), findsOneWidget);
  });

  testWidgets('switching provider shows correct API key field', (tester) async {
    final prefs = _InMemoryPreferencesStore();
    final quizStore = QuizPreferencesStore(prefs);
    final appStore = AppSettingsStore(prefs);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appSettingsStoreProvider.overrideWithValue(appStore),
        ],
        child: MaterialApp(
          home: SettingsPage(quizStore: quizStore),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // Default is Gemini — should show Gemini API Key field
    expect(find.text('Gemini API Key'), findsOneWidget);

    // Tap the OpenAI segment
    await tester.tap(find.text('OpenAI'));
    await tester.pumpAndSettle();

    // Now should show OpenAI API Key field instead
    expect(find.text('OpenAI API Key'), findsOneWidget);

    // Verify the selection is persisted
    final loaded = await appStore.read();
    expect(loaded.onlineModelProvider, OnlineModelProvider.openai);
  });

  testWidgets('API keys auto-save on change', (tester) async {
    final prefs = _InMemoryPreferencesStore();
    final quizStore = QuizPreferencesStore(prefs);
    final appStore = AppSettingsStore(prefs);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appSettingsStoreProvider.overrideWithValue(appStore),
        ],
        child: MaterialApp(
          home: SettingsPage(quizStore: quizStore),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // Enter a Gemini API key — auto-saves
    await tester.enterText(find.byType(TextField).first, 'abc123');
    await tester.pumpAndSettle();

    final loaded = await appStore.read();
    expect(loaded.geminiApiKey, 'abc123');
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
