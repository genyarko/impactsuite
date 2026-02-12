import '../../data/models/chat_models.dart';

abstract class ChatStore {
  Future<void> migrate();
  Stream<List<ChatSession>> watchAllSessions();
  Stream<List<ChatMessage>> watchMessagesForSession(String sessionId);
  Future<void> upsertSession(ChatSession session);
  Future<void> insertMessage(ChatMessage message);
  Future<void> deleteSessionWithMessages(String sessionId);
  Future<void> close();
}
