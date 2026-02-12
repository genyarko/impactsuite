import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'chat_store.dart';
import 'chat_store_contract.dart';
import '../ai/ai_providers.dart';
import 'chat_controller.dart';

final chatStoreProvider = FutureProvider<ChatStore>((ref) async {
  final store = await openChatStore();
  await store.migrate();
  ref.onDispose(store.close);
  return store;
});

final chatControllerProvider = StateNotifierProvider.autoDispose<ChatController, ChatState>((ref) {
  final asyncStore = ref.watch(chatStoreProvider);
  final store = asyncStore.valueOrNull;
  if (store == null) {
    throw StateError('Chat store is not initialized');
  }

  return ChatController(
    store: store,
    repository: ref.watch(unifiedAiRepositoryProvider),
  );
});
