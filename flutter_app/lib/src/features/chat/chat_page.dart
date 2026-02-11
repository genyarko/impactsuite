import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../ai/ai_conversation_controller.dart';
import '../ai/ai_providers.dart';

final _chatControllerProvider =
    StateNotifierProvider.autoDispose<AiConversationController, AiConversationState>((ref) {
      return AiConversationController(
        repository: ref.watch(unifiedAiRepositoryProvider),
        serviceType: 'chat',
      );
    });

class ChatPage extends ConsumerStatefulWidget {
  const ChatPage({super.key});

  @override
  ConsumerState<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends ConsumerState<ChatPage> {
  final TextEditingController _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(_chatControllerProvider);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Chat', style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 12),
            Expanded(
              child: state.messages.isEmpty
                  ? const Center(child: Text('Start a conversation.'))
                  : ListView.separated(
                      itemCount: state.messages.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 8),
                      itemBuilder: (context, index) {
                        final message = state.messages[index];
                        final alignment =
                            message.isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start;
                        final color = message.isUser
                            ? Theme.of(context).colorScheme.primaryContainer
                            : Theme.of(context).colorScheme.surfaceContainerHigh;

                        return Column(
                          crossAxisAlignment: alignment,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: color,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Text(message.text),
                            ),
                          ],
                        );
                      },
                    ),
            ),
            if (state.error != null) ...[
              const SizedBox(height: 8),
              Text(state.error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _controller,
                    enabled: !state.isLoading,
                    decoration: const InputDecoration(hintText: 'Ask anything...'),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: state.isLoading
                      ? null
                      : () async {
                          final text = _controller.text;
                          _controller.clear();
                          await ref.read(_chatControllerProvider.notifier).sendPrompt(text);
                        },
                  child: state.isLoading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.send),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
