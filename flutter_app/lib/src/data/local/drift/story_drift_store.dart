import 'package:drift/drift.dart';

class StoryMemoryRecord {
  const StoryMemoryRecord({
    required this.id,
    required this.theme,
    required this.readAt,
  });

  final String id;
  final String theme;
  final DateTime readAt;
}

class StoryDriftStore extends DatabaseConnectionUser {
  StoryDriftStore(super.connection);

  Future<void> migrate() async {
    await customStatement('''
      CREATE TABLE IF NOT EXISTS story_memory (
        id TEXT PRIMARY KEY,
        theme TEXT NOT NULL,
        read_at INTEGER NOT NULL
      )
    ''');
  }

  Future<void> insert(StoryMemoryRecord record) {
    return customStatement(
      'INSERT OR REPLACE INTO story_memory (id, theme, read_at) VALUES (?, ?, ?)',
      [record.id, record.theme, record.readAt.millisecondsSinceEpoch],
    );
  }

  Future<List<StoryMemoryRecord>> recent({int limit = 20}) async {
    final rows = await customSelect(
      'SELECT * FROM story_memory ORDER BY read_at DESC LIMIT ?',
      variables: [Variable.withInt(limit)],
      readsFrom: {const TableUpdateQuery.onTableName('story_memory')},
    ).get();

    return rows
        .map(
          (row) => StoryMemoryRecord(
            id: row.read<String>('id'),
            theme: row.read<String>('theme'),
            readAt: DateTime.fromMillisecondsSinceEpoch(row.read<int>('read_at')),
          ),
        )
        .toList(growable: false);
  }
}
