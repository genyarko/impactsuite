import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

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

  Map<String, dynamic> toJson() => {
    'id': id,
    'mood_before': moodBefore,
    'mood_after': moodAfter,
    'created_at': createdAt.millisecondsSinceEpoch,
  };

  factory CbtSessionRecord.fromJson(Map<String, dynamic> json) {
    return CbtSessionRecord(
      id: json['id'] as String,
      moodBefore: json['mood_before'] as int,
      moodAfter: json['mood_after'] as int,
      createdAt: DateTime.fromMillisecondsSinceEpoch(json['created_at'] as int),
    );
  }
}

class CbtSessionStore {
  static const _sessionsKey = 'cbt.sessions';

  Future<List<CbtSessionRecord>> listAll() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getStringList(_sessionsKey) ?? const [];

    return raw
        .map((item) => CbtSessionRecord.fromJson(jsonDecode(item) as Map<String, dynamic>))
        .toList(growable: false)
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
  }

  Future<void> insert(CbtSessionRecord record) async {
    final prefs = await SharedPreferences.getInstance();
    final sessions = await listAll();
    final updated = [record, ...sessions.where((session) => session.id != record.id)];

    await prefs.setStringList(
      _sessionsKey,
      updated.map((session) => jsonEncode(session.toJson())).toList(growable: false),
    );
  }
}
