import 'package:drift/drift.dart';

import '../../models/chat_models.dart';
import 'encrypted_database.dart';

/// Drift-based local storage replacing the Room chat/session schema.
class ChatDriftStore extends DatabaseConnectionUser {
  ChatDriftStore._(super.connection);

  static Future<ChatDriftStore> open() async {
    final connection = await openEncryptedDatabase();
    return ChatDriftStore._(connection);
  }

  Future<void> migrate() async {
    await customStatement('''
      CREATE TABLE IF NOT EXISTS chat_sessions (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        last_message TEXT
      )
    ''');

    await customStatement('''
      CREATE TABLE IF NOT EXISTS chat_messages (
        id TEXT PRIMARY KEY,
        session_id TEXT NOT NULL,
        content TEXT NOT NULL,
        is_user INTEGER NOT NULL,
        timestamp INTEGER NOT NULL,
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
      )
    ''');

    await customStatement(
      'CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id ON chat_messages(session_id)',
    );
  }

  Future<void> upsertSession(ChatSession session) {
    return customStatement(
      '''
      INSERT INTO chat_sessions (id, title, created_at, updated_at, last_message)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        title = excluded.title,
        created_at = excluded.created_at,
        updated_at = excluded.updated_at,
        last_message = excluded.last_message
      ''',
      [
        session.id,
        session.title,
        session.createdAt.millisecondsSinceEpoch,
        session.updatedAt.millisecondsSinceEpoch,
        session.lastMessage,
      ],
    );
  }

  Stream<List<ChatSession>> watchAllSessions() {
    return customSelectStream(
      'SELECT * FROM chat_sessions ORDER BY updated_at DESC',
      readsFrom: {const TableUpdateQuery.onTableName('chat_sessions')},
    ).map((rows) => rows.map(_sessionFromRow).toList(growable: false));
  }

  Future<ChatSession?> getSessionById(String id) async {
    final rows = await customSelect(
      'SELECT * FROM chat_sessions WHERE id = ? LIMIT 1',
      variables: [Variable.withString(id)],
      readsFrom: {},
    ).getSingleOrNull();

    return rows == null ? null : _sessionFromRow(rows);
  }

  Future<void> insertMessage(ChatMessage message) {
    return customStatement(
      '''
      INSERT INTO chat_messages (id, session_id, content, is_user, timestamp)
      VALUES (?, ?, ?, ?, ?)
      ''',
      [
        message.id,
        message.sessionId,
        message.content,
        message.isUser ? 1 : 0,
        message.timestamp.millisecondsSinceEpoch,
      ],
    );
  }

  Stream<List<ChatMessage>> watchMessagesForSession(String sessionId) {
    return customSelectStream(
      'SELECT * FROM chat_messages WHERE session_id = ? ORDER BY timestamp ASC',
      variables: [Variable.withString(sessionId)],
      readsFrom: {const TableUpdateQuery.onTableName('chat_messages')},
    ).map((rows) => rows.map(_messageFromRow).toList(growable: false));
  }

  Future<void> deleteSessionWithMessages(String sessionId) async {
    await customStatement('DELETE FROM chat_messages WHERE session_id = ?', [sessionId]);
    await customStatement('DELETE FROM chat_sessions WHERE id = ?', [sessionId]);
  }

  Future<void> close() => connection.executor.close();

  ChatSession _sessionFromRow(QueryRow row) {
    return ChatSession(
      id: row.read<String>('id'),
      title: row.read<String>('title'),
      createdAt: DateTime.fromMillisecondsSinceEpoch(row.read<int>('created_at')),
      updatedAt: DateTime.fromMillisecondsSinceEpoch(row.read<int>('updated_at')),
      lastMessage: row.readNullable<String>('last_message'),
    );
  }

  ChatMessage _messageFromRow(QueryRow row) {
    return ChatMessage(
      id: row.read<String>('id'),
      sessionId: row.read<String>('session_id'),
      content: row.read<String>('content'),
      isUser: row.read<int>('is_user') == 1,
      timestamp: DateTime.fromMillisecondsSinceEpoch(row.read<int>('timestamp')),
    );
  }
}
