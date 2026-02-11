import 'package:drift/drift.dart';

class PlantScanRecord {
  const PlantScanRecord({
    required this.id,
    required this.diagnosis,
    required this.confidence,
    required this.timestamp,
  });

  final String id;
  final String diagnosis;
  final double confidence;
  final DateTime timestamp;
}

class PlantDriftStore extends DatabaseConnectionUser {
  PlantDriftStore(super.connection);

  Future<void> migrate() async {
    await customStatement('''
      CREATE TABLE IF NOT EXISTS plant_scans (
        id TEXT PRIMARY KEY,
        diagnosis TEXT NOT NULL,
        confidence REAL NOT NULL,
        timestamp INTEGER NOT NULL
      )
    ''');
  }

  Future<void> insert(PlantScanRecord record) {
    return customStatement(
      'INSERT OR REPLACE INTO plant_scans (id, diagnosis, confidence, timestamp) VALUES (?, ?, ?, ?)',
      [
        record.id,
        record.diagnosis,
        record.confidence,
        record.timestamp.millisecondsSinceEpoch,
      ],
    );
  }

  Future<List<PlantScanRecord>> listAll() async {
    final rows = await customSelect(
      'SELECT * FROM plant_scans ORDER BY timestamp DESC',
      readsFrom: {const TableUpdateQuery.onTableName('plant_scans')},
    ).get();

    return rows
        .map(
          (row) => PlantScanRecord(
            id: row.read<String>('id'),
            diagnosis: row.read<String>('diagnosis'),
            confidence: row.read<double>('confidence'),
            timestamp: DateTime.fromMillisecondsSinceEpoch(row.read<int>('timestamp')),
          ),
        )
        .toList(growable: false);
  }
}
