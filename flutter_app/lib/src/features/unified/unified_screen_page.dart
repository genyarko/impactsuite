import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../ai/ai_conversation_controller.dart';
import '../ai/ai_providers.dart';

class UnifiedFeatureShortcut {
  const UnifiedFeatureShortcut({required this.name, required this.command, required this.icon, required this.route, required this.description});

  final String name;
  final String command;
  final IconData icon;
  final String route;
  final String description;
}

final _unifiedControllerProvider = StateNotifierProvider.autoDispose<AiConversationController, AiConversationState>((ref) {
  return AiConversationController(repository: ref.watch(unifiedAiRepositoryProvider), serviceType: 'chat');
});

const _featureShortcuts = <UnifiedFeatureShortcut>[
  UnifiedFeatureShortcut(name: 'AI Tutor', command: 'tutor', icon: Icons.school, route: '/tutor', description: 'Get personalized lessons'),
  UnifiedFeatureShortcut(name: 'Quiz', command: 'quiz', icon: Icons.quiz, route: '/quiz', description: 'Generate practice quizzes'),
  UnifiedFeatureShortcut(name: 'Story Mode', command: 'story', icon: Icons.menu_book, route: '/story', description: 'Interactive stories'),
  UnifiedFeatureShortcut(name: 'CBT Coach', command: 'cbt', icon: Icons.psychology, route: '/cbt_coach', description: 'Mental wellness support'),
  UnifiedFeatureShortcut(name: 'Live Caption', command: 'caption', icon: Icons.closed_caption, route: '/live_caption', description: 'Real-time transcription'),
  UnifiedFeatureShortcut(name: 'Summarizer', command: 'summarizer', icon: Icons.summarize, route: '/summarizer', description: 'Document summarization'),
  UnifiedFeatureShortcut(name: 'Image Scan', command: 'scan', icon: Icons.photo_camera, route: '/plant_scanner', description: 'Identify images'),
  UnifiedFeatureShortcut(name: 'Crisis Help', command: 'crisis', icon: Icons.local_hospital, route: '/crisis', description: 'Emergency resources'),
  UnifiedFeatureShortcut(name: 'Analytics', command: 'analytics', icon: Icons.analytics, route: '/analytics', description: 'Learning insights'),
  UnifiedFeatureShortcut(name: 'Home Page', command: 'home', icon: Icons.home, route: '/home', description: 'Navigate to home page'),
  UnifiedFeatureShortcut(name: 'Settings', command: 'settings', icon: Icons.settings, route: '/settings', description: 'Manage app preferences'),
];

class UnifiedScreenPage extends ConsumerStatefulWidget {
  const UnifiedScreenPage({super.key});

  @override
  ConsumerState<UnifiedScreenPage> createState() => _UnifiedScreenPageState();
}

class _UnifiedScreenPageState extends ConsumerState<UnifiedScreenPage> {
  final TextEditingController _controller = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  bool _showFeatureMenu = false;

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _handleInputChanged(String value) {
    for (final feature in _featureShortcuts) {
      final input = value.toLowerCase().trim();
      if (input == feature.command || input.startsWith('/${feature.command}') || input.startsWith('open ${feature.command}') || input.startsWith('start ${feature.command}')) {
        _controller.clear();
        context.go(feature.route);
        return;
      }
    }
  }

  Future<void> _sendMessage() async {
    final text = _controller.text;
    if (text.trim().isEmpty) {
      return;
    }
    _controller.clear();
    await ref.read(_unifiedControllerProvider.notifier).sendPrompt(text);
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(_unifiedControllerProvider);
    final suggestion = _inputSuggestion(_controller.text);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients && state.messages.isNotEmpty) {
        _scrollController.animateTo(_scrollController.position.maxScrollExtent, duration: const Duration(milliseconds: 220), curve: Curves.easeOut);
      }
    });

    return SafeArea(
      child: Column(
        children: [
          _UnifiedTopBar(
            showFeatureMenu: _showFeatureMenu,
            onToggleMenu: () => setState(() => _showFeatureMenu = !_showFeatureMenu),
            onFeatureClick: (feature) {
              setState(() => _showFeatureMenu = false);
              context.go(feature.route);
            },
          ),
          AnimatedCrossFade(
            firstChild: const SizedBox.shrink(),
            secondChild: _FeatureMenuOverlay(
              features: _featureShortcuts,
              onFeatureClick: (feature) {
                setState(() => _showFeatureMenu = false);
                context.go(feature.route);
              },
            ),
            crossFadeState: _showFeatureMenu ? CrossFadeState.showSecond : CrossFadeState.showFirst,
            duration: const Duration(milliseconds: 180),
          ),
          if (state.error != null)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: Theme.of(context).colorScheme.errorContainer, borderRadius: BorderRadius.circular(12)),
              child: Text(state.error!, style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer)),
            ),
          Expanded(
            child: Stack(
              children: [
                state.messages.isEmpty
                    ? _UnifiedEmptyState(features: _featureShortcuts, onFeatureClick: (feature) => context.go(feature.route))
                    : ListView.separated(
                        controller: _scrollController,
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                        itemCount: state.messages.length + (state.isLoading ? 1 : 0),
                        separatorBuilder: (_, __) => const SizedBox(height: 12),
                        itemBuilder: (context, index) {
                          if (state.isLoading && index == state.messages.length) {
                            return const _TypingIndicator();
                          }
                          final message = state.messages[index];
                          return _UnifiedMessageBubble(message: message);
                        },
                      ),
                IgnorePointer(
                  child: Container(
                    height: 20,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [Theme.of(context).colorScheme.surface, Theme.of(context).colorScheme.surface.withOpacity(0)],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          _UnifiedInputArea(
            controller: _controller,
            isLoading: state.isLoading,
            suggestion: suggestion,
            onChanged: _handleInputChanged,
            onSend: _sendMessage,
            onCameraClick: () => context.go('/plant_scanner'),
            onMicClick: () {
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Voice capture is not yet wired in Flutter unified screen.')));
            },
          ),
        ],
      ),
    );
  }

  UnifiedFeatureShortcut? _inputSuggestion(String input) {
    if (!input.startsWith('/') || input.length < 2) {
      return null;
    }
    final command = input.substring(1).toLowerCase();
    for (final feature in _featureShortcuts) {
      if (feature.command.startsWith(command)) {
        return feature;
      }
    }
    return null;
  }
}

class _UnifiedTopBar extends StatelessWidget {
  const _UnifiedTopBar({required this.showFeatureMenu, required this.onToggleMenu, required this.onFeatureClick});

  final bool showFeatureMenu;
  final VoidCallback onToggleMenu;
  final ValueChanged<UnifiedFeatureShortcut> onFeatureClick;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Theme.of(context).colorScheme.surface,
      elevation: 4,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Row(
          children: [
            IconButton(onPressed: onToggleMenu, icon: Icon(showFeatureMenu ? Icons.close : Icons.menu)),
            const Expanded(child: Text('Unified AI', style: TextStyle(fontWeight: FontWeight.w700))),
            IconButton(
              onPressed: () => onFeatureClick(_featureShortcuts.firstWhere((feature) => feature.route == '/settings')),
              icon: const Icon(Icons.settings),
            ),
            IconButton(
              onPressed: () => onFeatureClick(_featureShortcuts.firstWhere((feature) => feature.route == '/summarizer')),
              icon: const Icon(Icons.upload_file),
            ),
          ],
        ),
      ),
    );
  }
}

class _FeatureMenuOverlay extends StatelessWidget {
  const _FeatureMenuOverlay({required this.features, required this.onFeatureClick});

  final List<UnifiedFeatureShortcut> features;
  final ValueChanged<UnifiedFeatureShortcut> onFeatureClick;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(maxHeight: 320),
      color: Theme.of(context).colorScheme.surface,
      child: ListView.separated(
        shrinkWrap: true,
        itemBuilder: (context, index) {
          final feature = features[index];
          return ListTile(
            leading: Icon(feature.icon),
            title: Text(feature.name),
            subtitle: Text(feature.description),
            trailing: Chip(label: Text('/${feature.command}')),
            onTap: () => onFeatureClick(feature),
          );
        },
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemCount: features.length,
      ),
    );
  }
}

class _UnifiedEmptyState extends StatelessWidget {
  const _UnifiedEmptyState({required this.features, required this.onFeatureClick});

  final List<UnifiedFeatureShortcut> features;
  final ValueChanged<UnifiedFeatureShortcut> onFeatureClick;

  @override
  Widget build(BuildContext context) {
    final quickShortcuts = features.take(5).toList();

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.auto_awesome, size: 56, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 12),
            Text('Unified AI Assistant', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 8),
            Text('Chat naturally or type slash commands to jump into any feature.', textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 12),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final feature in quickShortcuts)
                  ActionChip(
                    avatar: Icon(feature.icon, size: 16),
                    label: Text('/${feature.command}'),
                    onPressed: () => onFeatureClick(feature),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _UnifiedMessageBubble extends StatelessWidget {
  const _UnifiedMessageBubble({required this.message});

  final AiConversationMessage message;

  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 300),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: isUser ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            child: Text(message.text, style: TextStyle(color: isUser ? Theme.of(context).colorScheme.onPrimary : Theme.of(context).colorScheme.onSurface)),
          ),
        ),
      ),
    );
  }
}

class _TypingIndicator extends StatelessWidget {
  const _TypingIndicator();

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(18), color: Theme.of(context).colorScheme.surfaceContainerHighest),
        child: const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
      ),
    );
  }
}

class _UnifiedInputArea extends StatelessWidget {
  const _UnifiedInputArea({
    required this.controller,
    required this.isLoading,
    required this.suggestion,
    required this.onChanged,
    required this.onSend,
    required this.onCameraClick,
    required this.onMicClick,
  });

  final TextEditingController controller;
  final bool isLoading;
  final UnifiedFeatureShortcut? suggestion;
  final ValueChanged<String> onChanged;
  final Future<void> Function() onSend;
  final VoidCallback onCameraClick;
  final VoidCallback onMicClick;

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 8,
      color: Theme.of(context).colorScheme.surface,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (suggestion != null)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: Theme.of(context).colorScheme.primaryContainer, borderRadius: BorderRadius.circular(12)),
              child: Row(
                children: [
                  Icon(suggestion!.icon, size: 16),
                  const SizedBox(width: 8),
                  Expanded(child: Text('/${suggestion!.command} • ${suggestion!.description}')),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: TextField(
                    controller: controller,
                    enabled: !isLoading,
                    minLines: 1,
                    maxLines: 4,
                    onChanged: onChanged,
                    textInputAction: TextInputAction.send,
                    onSubmitted: isLoading ? null : (_) => onSend(),
                    decoration: const InputDecoration(
                      hintText: 'Chat with AI or type /quiz, /tutor, /scan…',
                      border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(24))),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton.filled(onPressed: isLoading ? null : onCameraClick, icon: const Icon(Icons.photo_camera)),
                const SizedBox(width: 8),
                IconButton.filled(onPressed: isLoading ? null : onMicClick, icon: const Icon(Icons.mic)),
                const SizedBox(width: 8),
                IconButton.filled(
                  onPressed: isLoading ? null : onSend,
                  icon: isLoading ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.send),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
