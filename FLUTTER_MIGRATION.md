# Flutter Rewrite Plan

*** check chatgpt for solution for web ocr when pdf extraction fails. 
*** improve story mode on pcs or large screen, more like a book with flipping pages.
*** web app wont open properly on huawei phones
*** camera initialization fails on mobile web app
This repository now includes a Flutter application shell in `flutter_app/` that maps the main
Kotlin/Compose feature areas into Flutter routes.

## What was migrated

- App shell with Material 3 theme and bottom navigation.
- Route placeholders for: Home, Tutor, Chat, Quiz, Summarizer, Story, Crisis, Settings.
- Basic widget test to validate app boot and route rendering.

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

## Completeness check (Kotlin ➜ Flutter)

Status for the three priority items requested in this migration phase:

1. **Domain first**: ✅ **Completed for current parity target**.
   - Flutter includes quiz + AI domain primitives (`quiz_models.dart`, `answer_checker.dart`,
     `quiz_prompt_generator.dart`, `ai_models.dart`, `ai_repository.dart`).
   - Added pure-Dart domain services for previously-missing Kotlin-heavy modules:
     analytics (`knowledge_gap_analyzer.dart`), CBT (`cbt_session_manager.dart`), story
     (`story_recommendation_service.dart`), crisis (`crisis_severity_classifier.dart`), and plant
     (`plant_diagnosis_interpreter.dart`).
2. **Data layer**: ✅ **Completed for current parity target**.
   - Existing migrations remain: Room chat/session schema ➜ Drift, quiz DataStore preferences ➜
     SharedPreferences.
   - Added Drift stores for analytics, CBT sessions, story memory, and plant scans plus
     SharedPreferences-backed app settings (`learning_analytics_drift_store.dart`,
     `cbt_drift_store.dart`, `story_drift_store.dart`, `plant_drift_store.dart`,
     `app_settings_store.dart`) to cover the outstanding Kotlin Room/DataStore categories in this
     phase.
3. **AI services**: ✅ **Mostly complete for current Flutter scope**.
   - Dart repositories + abstraction (`GeminiRepository`, `GemmaRepository`,
     `UnifiedAiRepository`) are in place.
   - Platform channels are wired for Gemini/Gemma text generation and streaming via Android
     `MainActivity`.
   - Note: channel methods currently only cover the migrated chat/tutor generation path; broader
     Kotlin AI feature parity remains pending as additional features are ported.

## Data layer migration status

- ✅ Room `chat_sessions` + `chat_messages` schema now has a Flutter-side Drift store in
  `flutter_app/lib/src/data/local/drift/chat_drift_store.dart`.
- ✅ DataStore-backed quiz settings now has a SharedPreferences-backed Flutter store in
  `flutter_app/lib/src/data/local/preferences/quiz_preferences_store.dart`.
- ⏭️ Isar is reserved for object-heavy / offline index use-cases (for example local vector payloads)
  and will be added as those features are ported.


## AI services migration status

- ✅ Added Dart AI repository abstractions (`AiRepository`) and request/result models.
- ✅ Added platform-channel bridge (`impactsuite/ai/methods` and `impactsuite/ai/stream`) for Gemini/Gemma text generation.
- ✅ Added `GeminiRepository`, `GemmaRepository`, and `UnifiedAiRepository` with fallback behavior.
- ✅ Implemented matching `MethodChannel` / `EventChannel` handlers in Android `MainActivity` and routed Tutor/Chat pages through `UnifiedAiRepository`.


## UI parity migration status

- ✅ Began Compose ➜ Flutter UI parity with the **Settings** feature.
- ✅ Completed Compose ➜ Flutter UI parity for the **Unified Screen** with command shortcuts, feature menu overlay, and unified chat experience routed as the app entry screen.
- ✅ Replaced Flutter `SettingsPage` placeholder with a functional settings screen wired to
  SharedPreferences-backed stores (`QuizPreferencesStore` and `AppSettingsStore`).
- ✅ Ported core settings groups from Kotlin into Flutter widgets:
  - AI configuration (Gemini API key + on-device preference),
  - Display & accessibility controls,
  - Quiz behavior controls (hints, auto-advance, explanations, time limit).
- ✅ Added widget test coverage for the new Flutter settings screen.

## Merge verification

- ✅ Verified prior data-layer and AI-layer migration files are present in `HEAD` (`git log --oneline -n 5` + file checks).
