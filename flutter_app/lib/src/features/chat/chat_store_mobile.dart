import '../../data/local/drift/chat_drift_store.dart';
import '../../data/models/chat_models.dart';
import 'chat_store_contract.dart';

class DriftChatStore implements ChatStore {
  DriftChatStore(this._store);

  final ChatDriftStore _store;

  @override
  Future<void> close() => _store.close();

  @override
  Future<void> deleteSessionWithMessages(String sessionId) =>
      _store.deleteSessionWithMessages(sessionId);

  @override
  Future<void> insertMessage(ChatMessage message) => _store.insertMessage(message);

  @override
  Future<void> migrate() => _store.migrate();

  @override
  Future<void> upsertSession(ChatSession session) => _store.upsertSession(session);

  @override
  Stream<List<ChatSession>> watchAllSessions() => _store.watchAllSessions();

  @override
  Stream<List<ChatMessage>> watchMessagesForSession(String sessionId) =>
      _store.watchMessagesForSession(sessionId);
}

Future<ChatStore> openChatStoreImpl() async {
  final store = await ChatDriftStore.open();
  return DriftChatStore(store);
}
