import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../../domain/features/cbt/cbt_session_manager.dart';

/// Stored CBT session record with full conversation data.
class CbtSessionRecord {
  const CbtSessionRecord({
    required this.id,
    required this.createdAt,
    this.emotion,
    this.techniqueId,
    this.transcript = const [],
    this.durationMs = 0,
    this.completed = false,
    this.effectiveness = 0,
  });

  final String id;
  final DateTime createdAt;
  final String? emotion;
  final String? techniqueId;
  final List<Map<String, dynamic>> transcript;
  final int durationMs;
  final bool completed;
  final int effectiveness;

  String get emotionLabel {
    if (emotion == null) return 'Unknown';
    return Emotion.fromString(emotion!).label;
  }

  String get techniqueLabel {
    return CBTTechnique.fromId(techniqueId)?.name ?? 'General';
  }

  String get summary {
    for (final msg in transcript) {
      if (msg['role'] == 'user') {
        final content = msg['content'] as String? ?? '';
        return content.length > 60 ? '${content.substring(0, 60)}...' : content;
      }
    }
    return 'CBT Session';
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'created_at': createdAt.millisecondsSinceEpoch,
    'emotion': emotion,
    'technique_id': techniqueId,
    'transcript': transcript,
    'duration_ms': durationMs,
    'completed': completed,
    'effectiveness': effectiveness,
  };

  factory CbtSessionRecord.fromJson(Map<String, dynamic> json) {
    return CbtSessionRecord(
      id: json['id'] as String,
      createdAt: DateTime.fromMillisecondsSinceEpoch(json['created_at'] as int),
      emotion: json['emotion'] as String?,
      techniqueId: json['technique_id'] as String?,
      transcript: (json['transcript'] as List<dynamic>?)
              ?.map((e) => Map<String, dynamic>.from(e as Map))
              .toList() ??
          const [],
      durationMs: json['duration_ms'] as int? ?? 0,
      completed: json['completed'] as bool? ?? false,
      effectiveness: json['effectiveness'] as int? ?? 0,
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
    final updated = [record, ...sessions.where((s) => s.id != record.id)];

    await prefs.setStringList(
      _sessionsKey,
      updated.map((s) => jsonEncode(s.toJson())).toList(growable: false),
    );
  }

  Future<void> deleteSession(String id) async {
    final prefs = await SharedPreferences.getInstance();
    final sessions = await listAll();
    final updated = sessions.where((s) => s.id != id).toList();

    await prefs.setStringList(
      _sessionsKey,
      updated.map((s) => jsonEncode(s.toJson())).toList(growable: false),
    );
  }

  Future<void> deleteAllSessions() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_sessionsKey);
  }

  Future<void> deleteOldSessions(int days) async {
    final prefs = await SharedPreferences.getInstance();
    final sessions = await listAll();
    final cutoff = DateTime.now().subtract(Duration(days: days));
    final updated = sessions.where((s) => s.createdAt.isAfter(cutoff)).toList();

    await prefs.setStringList(
      _sessionsKey,
      updated.map((s) => jsonEncode(s.toJson())).toList(growable: false),
    );
  }
}
