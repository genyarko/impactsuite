import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/models/chat_models.dart';
import 'chat_providers.dart';

class ChatPage extends ConsumerStatefulWidget {
  const ChatPage({required this.sessionId, super.key});

  final String sessionId;

  @override
  ConsumerState<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends ConsumerState<ChatPage> {
  final TextEditingController _controller = TextEditingController();

  @override
  void initState() {
    super.initState();
    Future.microtask(() async {
      await ref.read(chatStoreProvider.future);
      await ref.read(chatControllerProvider.notifier).ensureSessionSelected(
            preferredSessionId: widget.sessionId,
          );
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final storeAsync = ref.watch(chatStoreProvider);

    return storeAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(child: Text('Failed to load chat: $error')),
      data: (_) {
        final state = ref.watch(chatControllerProvider);

        return SafeArea(
          child: Column(
            children: [
              _ChatTopBar(
                onBack: () => context.go('/chat'),
              ),
              if (state.error != null)
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.errorContainer,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    state.error!,
                    style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer),
                  ),
                ),
              Expanded(
                child: Stack(
                  children: [
                    state.messages.isEmpty
                        ? const _EmptyChatState()
                        : ListView.separated(
                            reverse: true,
                            padding: const EdgeInsets.all(16),
                            itemCount: state.messages.length + (state.isLoading ? 1 : 0),
                            separatorBuilder: (_, __) => const SizedBox(height: 12),
                            itemBuilder: (context, index) {
                              if (state.isLoading && index == 0) {
                                return const _TypingIndicator();
                              }

                              final message = state
                                  .messages[state.messages.length - 1 - (state.isLoading ? index - 1 : index)];
                              return _MessageBubble(message: message);
                            },
                          ),
                    IgnorePointer(
                      child: Container(
                        height: 18,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Theme.of(context).colorScheme.surface,
                              Theme.of(context).colorScheme.surface.withOpacity(0),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              _InputArea(
                controller: _controller,
                isLoading: state.isLoading,
                onSend: () async {
                  final value = _controller.text;
                  _controller.clear();
                  await ref.read(chatControllerProvider.notifier).sendPrompt(value);
                },
              ),
            ],
          ),
        );
      },
    );
  }
}

class _ChatTopBar extends StatelessWidget {
  const _ChatTopBar({required this.onBack});

  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 3,
      color: Theme.of(context).colorScheme.surface,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          children: [
            IconButton(onPressed: onBack, icon: const Icon(Icons.arrow_back)),
            Container(
              width: 40,
              height: 40,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(colors: [Color(0xFF7C4DFF), Color(0xFF536DFE)]),
              ),
              child: const Icon(Icons.psychology, color: Colors.white),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('AI Assistant', style: Theme.of(context).textTheme.titleMedium),
                Text('Always here to help', style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyChatState extends StatelessWidget {
  const _EmptyChatState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.chat_bubble_outline,
              size: 64,
              color: Theme.of(context).colorScheme.primary.withOpacity(0.35),
            ),
            const SizedBox(height: 12),
            Text('Start a conversation', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 8),
            Text(
              'Ask me anything or use voice input to begin',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
        ),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message});

  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    if (message.isUser) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 280),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primary,
                    borderRadius: const BorderRadius.only(
                      topLeft: Radius.circular(20),
                      topRight: Radius.circular(4),
                      bottomLeft: Radius.circular(20),
                      bottomRight: Radius.circular(20),
                    ),
                  ),
                  child: Text(
                    message.content,
                    style: TextStyle(color: Theme.of(context).colorScheme.onPrimary),
                  ),
                ),
                const SizedBox(height: 4),
                Text(_formatMessageTime(message.timestamp), style: Theme.of(context).textTheme.labelSmall),
              ],
            ),
          ),
        ],
      );
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Container(
          width: 32,
          height: 32,
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(colors: [Color(0xFF7C4DFF), Color(0xFF536DFE)]),
          ),
          child: const Icon(Icons.auto_awesome, size: 18, color: Colors.white),
        ),
        const SizedBox(width: 8),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 280),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(4),
                    topRight: Radius.circular(20),
                    bottomLeft: Radius.circular(20),
                    bottomRight: Radius.circular(20),
                  ),
                ),
                child: Text(message.content),
              ),
              if (message.content.length > 100)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: ActionChip(
                    onPressed: null,
                    avatar: const Icon(Icons.quiz, size: 16),
                    label: const Text('Generate Quiz'),
                  ),
                ),
              const SizedBox(height: 4),
              Text(_formatMessageTime(message.timestamp), style: Theme.of(context).textTheme.labelSmall),
            ],
          ),
        ),
      ],
    );
  }
}

class _TypingIndicator extends StatelessWidget {
  const _TypingIndicator();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          margin: const EdgeInsets.only(left: 40),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(20),
          ),
          child: const SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ],
    );
  }
}

class _InputArea extends StatelessWidget {
  const _InputArea({
    required this.controller,
    required this.isLoading,
    required this.onSend,
  });

  final TextEditingController controller;
  final bool isLoading;
  final Future<void> Function() onSend;

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 8,
      color: Theme.of(context).colorScheme.surface,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: TextField(
                controller: controller,
                enabled: !isLoading,
                minLines: 1,
                maxLines: 4,
                decoration: const InputDecoration(
                  hintText: 'Type a message or use voice',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.all(Radius.circular(24)),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filled(
              onPressed: isLoading ? null : () {},
              icon: const Icon(Icons.mic),
            ),
            const SizedBox(width: 8),
            IconButton.filled(
              onPressed: isLoading
                  ? null
                  : () async {
                      await onSend();
                    },
              icon: isLoading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.send),
            ),
          ],
        ),
      ),
    );
  }
}

String _formatMessageTime(DateTime timestamp) {
  final hour = timestamp.hour % 12 == 0 ? 12 : timestamp.hour % 12;
  final minute = timestamp.minute.toString().padLeft(2, '0');
  final period = timestamp.hour >= 12 ? 'PM' : 'AM';
  return '$hour:$minute $period';
}
