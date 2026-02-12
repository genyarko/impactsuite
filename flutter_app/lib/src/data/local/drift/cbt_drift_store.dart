import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

class CbtSessionRecord {
  const CbtSessionRecord({
    required this.id,
    required this.moodBefore,
    required this.moodAfter,
    required this.createdAt,
  });

  final String id;
  final int moodBefore;
  final int moodAfter;
  final DateTime createdAt;
}

class CbtDriftStore extends DatabaseConnectionUser {
  CbtDriftStore._(super.connection);

  static Future<CbtDriftStore> open() async {
    final documentsDirectory = await getApplicationDocumentsDirectory();
    final dbFile = File(path.join(documentsDirectory.path, 'impactsuite.sqlite'));
    return CbtDriftStore._(DatabaseConnection(NativeDatabase(dbFile)));
  }

  Future<void> migrate() async {
    await customStatement('''
      CREATE TABLE IF NOT EXISTS cbt_sessions (
        id TEXT PRIMARY KEY,
        mood_before INTEGER NOT NULL,
        mood_after INTEGER NOT NULL,
        created_at INTEGER NOT NULL
      )
    ''');
  }

  Future<void> insert(CbtSessionRecord record) {
    return customStatement(
      'INSERT OR REPLACE INTO cbt_sessions (id, mood_before, mood_after, created_at) VALUES (?, ?, ?, ?)',
      [
        record.id,
        record.moodBefore,
        record.moodAfter,
        record.createdAt.millisecondsSinceEpoch,
      ],
    );
  }

  Future<List<CbtSessionRecord>> listAll() async {
    final rows = await customSelect(
      'SELECT * FROM cbt_sessions ORDER BY created_at DESC',
      readsFrom: {const TableUpdateQuery.onTableName('cbt_sessions')},
    ).get();

    return rows
        .map(
          (row) => CbtSessionRecord(
            id: row.read<String>('id'),
            moodBefore: row.read<int>('mood_before'),
            moodAfter: row.read<int>('mood_after'),
            createdAt: DateTime.fromMillisecondsSinceEpoch(row.read<int>('created_at')),
          ),
        )
        .toList(growable: false);
  }

  Future<void> close() => connection.executor.close();
}
