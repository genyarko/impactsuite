import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'chat_providers.dart';

class ChatListPage extends ConsumerWidget {
  const ChatListPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final storeAsync = ref.watch(chatStoreProvider);

    return storeAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(child: Text('Failed to load chats: $error')),
      data: (_) {
        final state = ref.watch(chatControllerProvider);
        final controller = ref.read(chatControllerProvider.notifier);

        return Scaffold(
          appBar: AppBar(title: const Text('CHAT HISTORY')),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: () async {
              final id = await controller.createNewSession();
              if (context.mounted) {
                context.go('/chat/$id');
              }
            },
            icon: const Icon(Icons.add),
            label: const Text('New Chat'),
          ),
          body: state.sessions.isEmpty
              ? const _EmptyState()
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 100),
                  itemBuilder: (context, index) {
                    final session = state.sessions[index];
                    return Card(
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor: Theme.of(context)
                              .colorScheme
                              .primaryContainer,
                          child: Icon(
                            Icons.chat_bubble_outline,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                        ),
                        title: Text(
                          session.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Text(
                          session.lastMessage ?? 'No messages yet',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        trailing: IconButton(
                          onPressed: () => controller.deleteSession(session.id),
                          icon: const Icon(Icons.delete_outline),
                        ),
                        onTap: () => context.go('/chat/${session.id}'),
                      ),
                    );
                  },
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemCount: state.sessions.length,
                ),
        );
      },
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.forum,
            size: 64,
            color: Theme.of(context).colorScheme.primary.withOpacity(0.5),
          ),
          const SizedBox(height: 12),
          Text(
            'No conversations yet',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 6),
          Text(
            'Start a new chat session to begin.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
