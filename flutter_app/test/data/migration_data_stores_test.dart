import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/data/local/drift/cbt_drift_store.dart';
import 'package:impactsuite_flutter/src/data/local/drift/learning_analytics_drift_store.dart';
import 'package:impactsuite_flutter/src/data/local/drift/plant_drift_store.dart';
import 'package:impactsuite_flutter/src/data/local/drift/story_drift_store.dart';
import 'package:impactsuite_flutter/src/data/local/preferences/app_settings_store.dart';
import 'package:impactsuite_flutter/src/data/local/preferences/quiz_preferences_store.dart';

void main() {
  test('analytics/cbt/story/plant drift stores can migrate and persist', () async {
    final executor = NativeDatabase.memory();
    final connection = DatabaseConnection(executor);

    final analytics = LearningAnalyticsDriftStore(connection);
    final cbt = CbtDriftStore(connection);
    final story = StoryDriftStore(connection);
    final plant = PlantDriftStore(connection);

    await analytics.migrate();
    await cbt.migrate();
    await story.migrate();
    await plant.migrate();

    final now = DateTime(2025);

    await analytics.insert(
      LearningAnalyticsRecord(id: 'a1', subject: 'science', accuracy: 0.8, timestamp: now),
    );
    await cbt.insert(
      CbtSessionRecord(id: 'c1', moodBefore: 1, moodAfter: 3, createdAt: now),
    );
    await story.insert(StoryMemoryRecord(id: 's1', theme: 'space', readAt: now));
    await plant.insert(
      PlantScanRecord(id: 'p1', diagnosis: 'rust', confidence: 0.7, timestamp: now),
    );

    expect((await analytics.listAll()).single.subject, 'science');
    expect((await cbt.listAll()).single.moodAfter, 3);
    expect((await story.recent()).single.theme, 'space');
    expect((await plant.listAll()).single.diagnosis, 'rust');

    await executor.close();
  });

  test('app settings store persists key migration settings', () async {
    final backing = _InMemoryPreferencesStore();
    final store = AppSettingsStore(backing);

    await store.write(
      const AppSettings(geminiApiKey: 'k-test', enableOfflineModel: false, lastModelSyncEpochMs: 123),
    );

    final settings = await store.read();
    expect(settings.geminiApiKey, 'k-test');
    expect(settings.enableOfflineModel, isFalse);
    expect(settings.lastModelSyncEpochMs, 123);
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
