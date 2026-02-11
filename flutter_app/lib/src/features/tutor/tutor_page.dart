import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../ai/ai_conversation_controller.dart';
import '../ai/ai_providers.dart';

final _tutorControllerProvider =
    StateNotifierProvider.autoDispose<AiConversationController, AiConversationState>((ref) {
      return AiConversationController(
        repository: ref.watch(unifiedAiRepositoryProvider),
        serviceType: 'tutor',
      );
    });

class TutorPage extends ConsumerStatefulWidget {
  const TutorPage({super.key});

  @override
  ConsumerState<TutorPage> createState() => _TutorPageState();
}

class _TutorPageState extends ConsumerState<TutorPage> {
  final TextEditingController _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(_tutorControllerProvider);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('AI Tutor', style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 8),
            Text(
              'Ask for explanations, examples, and guided steps.',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 12),
            Expanded(
              child: state.messages.isEmpty
                  ? const Center(child: Text('Try: Explain photosynthesis in simple terms.'))
                  : ListView.separated(
                      itemCount: state.messages.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 8),
                      itemBuilder: (context, index) {
                        final message = state.messages[index];
                        final alignment =
                            message.isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start;
                        final color = message.isUser
                            ? Theme.of(context).colorScheme.secondaryContainer
                            : Theme.of(context).colorScheme.surfaceContainerHighest;

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
                    decoration: const InputDecoration(hintText: 'Ask your tutor...'),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: state.isLoading
                      ? null
                      : () async {
                          final text = _controller.text;
                          _controller.clear();
                          await ref.read(_tutorControllerProvider.notifier).sendPrompt(text);
                        },
                  child: state.isLoading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.school),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
