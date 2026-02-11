# Flutter Rewrite Plan

This repository now includes a Flutter application shell in `flutter_app/` that maps the main
Kotlin/Compose feature areas into Flutter routes.

## What was migrated

- App shell with Material 3 theme and bottom navigation.
- Route placeholders for: Home, Tutor, Chat, Quiz, Summarizer, Story, Crisis, Settings.
- Domain-first quiz migration in Dart (`QuizPromptGenerator`, `AnswerChecker`, and quiz models).
- Basic widget + domain tests to validate app boot and migrated logic behavior.

## How to run

```bash
cd flutter_app
flutter pub get
flutter run
```

## Suggested migration sequence

1. **Domain first**: move pure business logic from Kotlin to Dart classes.
2. **Data layer**: replace Room/DataStore with Drift/Isar/SharedPreferences as needed.
3. **AI services**: encapsulate Gemma/Gemini calls in Dart repositories with platform channels for on-device inference.
4. **UI parity**: port one feature at a time from Compose screens into Flutter widgets.
5. **Validation**: keep Kotlin and Flutter outputs side-by-side until each feature reaches parity.
