import 'package:drift/drift.dart';

class LearningAnalyticsRecord {
  const LearningAnalyticsRecord({
    required this.id,
    required this.subject,
    required this.accuracy,
    required this.timestamp,
  });

  final String id;
  final String subject;
  final double accuracy;
  final DateTime timestamp;
}

class LearningAnalyticsDriftStore extends DatabaseConnectionUser {
  LearningAnalyticsDriftStore(super.connection);

  Future<void> migrate() async {
    await customStatement('''
      CREATE TABLE IF NOT EXISTS learning_analytics (
        id TEXT PRIMARY KEY,
        subject TEXT NOT NULL,
        accuracy REAL NOT NULL,
        timestamp INTEGER NOT NULL
      )
    ''');
  }

  Future<void> insert(LearningAnalyticsRecord record) {
    return customStatement(
      'INSERT OR REPLACE INTO learning_analytics (id, subject, accuracy, timestamp) VALUES (?, ?, ?, ?)',
      [
        record.id,
        record.subject,
        record.accuracy,
        record.timestamp.millisecondsSinceEpoch,
      ],
    );
  }

  Future<List<LearningAnalyticsRecord>> listAll() async {
    final rows = await customSelect(
      'SELECT * FROM learning_analytics ORDER BY timestamp DESC',
      readsFrom: {const TableUpdateQuery.onTableName('learning_analytics')},
    ).get();

    return rows
        .map(
          (row) => LearningAnalyticsRecord(
            id: row.read<String>('id'),
            subject: row.read<String>('subject'),
            accuracy: row.read<double>('accuracy'),
            timestamp: DateTime.fromMillisecondsSinceEpoch(row.read<int>('timestamp')),
          ),
        )
        .toList(growable: false);
  }
}
