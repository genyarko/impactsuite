import 'dart:async';

import '../../data/models/chat_models.dart';
import 'chat_store_contract.dart';

class InMemoryChatStore implements ChatStore {
  final _sessionsController = StreamController<List<ChatSession>>.broadcast();
  final _messageControllers = <String, StreamController<List<ChatMessage>>>{};
  final _sessions = <ChatSession>[];
  final _messagesBySession = <String, List<ChatMessage>>{};

  InMemoryChatStore() {
    _sessionsController.add(const <ChatSession>[]);
  }

  @override
  Future<void> migrate() async {}

  @override
  Stream<List<ChatSession>> watchAllSessions() => _sessionsController.stream;

  @override
  Stream<List<ChatMessage>> watchMessagesForSession(String sessionId) {
    final controller = _messageControllers.putIfAbsent(
      sessionId,
      () {
        final stream = StreamController<List<ChatMessage>>.broadcast();
        stream.add(_messagesBySession[sessionId] ?? const <ChatMessage>[]);
        return stream;
      },
    );
    return controller.stream;
  }

  @override
  Future<void> upsertSession(ChatSession session) async {
    final index = _sessions.indexWhere((s) => s.id == session.id);
    if (index >= 0) {
      _sessions[index] = session;
    } else {
      _sessions.add(session);
    }
    _sessions.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    _sessionsController.add(List<ChatSession>.unmodifiable(_sessions));
  }

  @override
  Future<void> insertMessage(ChatMessage message) async {
    final messages = _messagesBySession.putIfAbsent(message.sessionId, () => <ChatMessage>[]);
    messages.add(message);
    final controller = _messageControllers.putIfAbsent(
      message.sessionId,
      () => StreamController<List<ChatMessage>>.broadcast(),
    );
    controller.add(List<ChatMessage>.unmodifiable(messages));
  }

  @override
  Future<void> deleteSessionWithMessages(String sessionId) async {
    _sessions.removeWhere((s) => s.id == sessionId);
    _messagesBySession.remove(sessionId);
    _sessionsController.add(List<ChatSession>.unmodifiable(_sessions));
    _messageControllers.remove(sessionId)?.close();
  }

  @override
  Future<void> close() async {
    await _sessionsController.close();
    for (final controller in _messageControllers.values) {
      await controller.close();
    }
    _messageControllers.clear();
  }
}

Future<ChatStore> openChatStoreImpl() async => InMemoryChatStore();
