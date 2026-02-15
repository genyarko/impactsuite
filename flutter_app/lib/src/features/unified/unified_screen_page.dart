import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../ai/ai_conversation_controller.dart';
import '../ai/ai_providers.dart';

class FeatureShortcut {
  const FeatureShortcut({required this.name, required this.command, required this.icon, required this.route, required this.description});

  final String name;
  final String command;
  final IconData icon;
  final String route;
  final String description;
}

final _unifiedControllerProvider = StateNotifierProvider.autoDispose<AiConversationController, AiConversationState>((ref) {
  return AiConversationController(repository: ref.watch(unifiedAiRepositoryProvider), serviceType: 'chat');
});

const _features = <FeatureShortcut>[
  FeatureShortcut(name: 'AI Tutor', command: 'tutor', icon: Icons.school, route: '/tutor', description: 'Get personalized lessons'),
  FeatureShortcut(name: 'Quiz', command: 'quiz', icon: Icons.quiz, route: '/quiz', description: 'Generate practice quizzes'),
  FeatureShortcut(name: 'Story Mode', command: 'story', icon: Icons.menu_book, route: '/story', description: 'Interactive stories'),
  FeatureShortcut(name: 'CBT Coach', command: 'cbt', icon: Icons.psychology, route: '/cbt_coach', description: 'Mental wellness support'),
  FeatureShortcut(name: 'Live Caption', command: 'caption', icon: Icons.closed_caption, route: '/live_caption', description: 'Real-time transcription'),
  FeatureShortcut(name: 'Summarizer', command: 'summarizer', icon: Icons.summarize, route: '/summarizer', description: 'Document summarization'),
  FeatureShortcut(name: 'Image Scan', command: 'scan', icon: Icons.photo_camera, route: '/plant_scanner', description: 'Identify images'),
  FeatureShortcut(name: 'Crisis Help', command: 'crisis', icon: Icons.local_hospital, route: '/crisis', description: 'Emergency resources'),
  FeatureShortcut(name: 'Analytics', command: 'analytics', icon: Icons.analytics, route: '/analytics', description: 'Learning insights'),
  FeatureShortcut(name: 'Home Page', command: 'home', icon: Icons.home, route: '/home', description: 'Navigate to home page'),
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
    for (final feature in _features) {
      final input = value.toLowerCase().trim();
      if (input == feature.command ||
          input.startsWith('/${feature.command}') ||
          input.startsWith('open ${feature.command}') ||
          input.startsWith('start ${feature.command}')) {
        _controller.clear();
        context.go(feature.route);
        return;
      }
    }
  }

  Future<void> _sendMessage() async {
    final text = _controller.text;
    if (text.trim().isEmpty) return;
    _controller.clear();
    await ref.read(_unifiedControllerProvider.notifier).sendPrompt(text);
  }

  FeatureShortcut? _inputSuggestion(String input) {
    if (!input.startsWith('/') || input.length < 2) return null;
    final command = input.substring(1).toLowerCase();
    for (final feature in _features) {
      if (feature.command.startsWith(command)) return feature;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(_unifiedControllerProvider);
    final suggestion = _inputSuggestion(_controller.text);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients && state.messages.isNotEmpty) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOut,
        );
      }
    });

    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: _buildTopBar(context),
      body: Column(
        children: [
          // Feature menu overlay
          AnimatedCrossFade(
            firstChild: const SizedBox.shrink(),
            secondChild: _FeatureMenuOverlay(
              features: _features,
              onFeatureClick: (feature) {
                setState(() => _showFeatureMenu = false);
                context.go(feature.route);
              },
            ),
            crossFadeState: _showFeatureMenu ? CrossFadeState.showSecond : CrossFadeState.showFirst,
            duration: const Duration(milliseconds: 180),
          ),

          // Error message
          if (state.error != null)
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: SizedBox(
                  width: double.infinity,
                  child: Text(
                    state.error!,
                    style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer),
                  ),
                ),
              ),
            ),

          // Messages area
          Expanded(
            child: Stack(
              children: [
                state.messages.isEmpty
                    ? _UnifiedEmptyState(
                        features: _features,
                        onFeatureClick: (feature) => context.go(feature.route),
                      )
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
                          return message.isUser
                              ? _UserMessageBubble(message: message)
                              : _AIMessageBubble(message: message);
                        },
                      ),
                // Gradient fade at top
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  child: IgnorePointer(
                    child: Container(
                      height: 20,
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            Theme.of(context).colorScheme.surface,
                            Theme.of(context).colorScheme.surface.withValues(alpha: 0),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),

          // Input area
          _EnhancedChatInputArea(
            controller: _controller,
            isLoading: state.isLoading,
            suggestion: suggestion,
            onChanged: _handleInputChanged,
            onSend: _sendMessage,
            onCameraClick: () => context.go('/plant_scanner'),
            onMicClick: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Voice capture is not yet wired in Flutter.')),
              );
            },
          ),
        ],
      ),
    );
  }

  PreferredSizeWidget _buildTopBar(BuildContext context) {
    return AppBar(
      leading: IconButton(
        onPressed: () => setState(() => _showFeatureMenu = !_showFeatureMenu),
        icon: Icon(_showFeatureMenu ? Icons.close : Icons.menu),
      ),
      title: const SizedBox.shrink(),
      actions: [
        IconButton(
          onPressed: () => context.go('/settings'),
          icon: const Icon(Icons.settings),
          tooltip: 'Settings',
        ),
        IconButton(
          onPressed: () => context.go('/summarizer'),
          icon: const Icon(Icons.upload),
          tooltip: 'Upload Document',
        ),
      ],
      elevation: 4,
      surfaceTintColor: Colors.transparent,
      shadowColor: Theme.of(context).shadowColor,
    );
  }
}

// ─── Feature Menu Overlay (vertical list of cards, matching Kotlin) ───

class _FeatureMenuOverlay extends StatelessWidget {
  const _FeatureMenuOverlay({required this.features, required this.onFeatureClick});

  final List<FeatureShortcut> features;
  final ValueChanged<FeatureShortcut> onFeatureClick;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Material(
      color: colorScheme.surface,
      elevation: 8,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxHeight: 300),
        child: ListView.separated(
          shrinkWrap: true,
          padding: const EdgeInsets.all(8),
          itemCount: features.length,
          separatorBuilder: (_, __) => const SizedBox(height: 4),
          itemBuilder: (context, index) {
            final feature = features[index];
            return Card(
              color: colorScheme.surfaceContainerHighest,
              elevation: 0,
              margin: EdgeInsets.zero,
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => onFeatureClick(feature),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Icon(feature.icon, size: 24, color: colorScheme.primary),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              feature.name,
                              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                    color: colorScheme.onSurfaceVariant,
                                  ),
                            ),
                            Text(
                              feature.description,
                              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: colorScheme.onSurfaceVariant.withValues(alpha: 0.7),
                                  ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: colorScheme.primaryContainer,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          '/${feature.command}',
                          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                                color: colorScheme.primary,
                              ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

// ─── Empty State (matches Kotlin: Chat icon, title, subtitle, horizontal chip row) ───

class _UnifiedEmptyState extends StatelessWidget {
  const _UnifiedEmptyState({required this.features, required this.onFeatureClick});

  final List<FeatureShortcut> features;
  final ValueChanged<FeatureShortcut> onFeatureClick;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final quickFeatures = features.take(5).toList();

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.chat,
              size: 64,
              color: colorScheme.primary.withValues(alpha: 0.3),
            ),
            const SizedBox(height: 16),
            Text(
              'Welcome to G3N AI',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.w500,
                    color: colorScheme.onSurfaceVariant,
                  ),
            ),
            const SizedBox(height: 8),
            Text(
              'Start chatting or activate features:',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant.withValues(alpha: 0.7),
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            // Horizontal scrollable chip row (first 5 features)
            SizedBox(
              height: 36,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: quickFeatures.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final feature = quickFeatures[index];
                  return ActionChip(
                    avatar: Icon(feature.icon, size: 16),
                    label: Text('/${feature.command}'),
                    onPressed: () => onFeatureClick(feature),
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Or use the menu button above for all features',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── User Message Bubble (asymmetric corners, timestamp) ───

class _UserMessageBubble extends StatelessWidget {
  const _UserMessageBubble({required this.message});

  final AiConversationMessage message;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 280),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Card(
                color: colorScheme.primary,
                elevation: 2,
                shape: const RoundedRectangleBorder(
                  borderRadius: BorderRadius.only(
                    topLeft: Radius.circular(20),
                    topRight: Radius.circular(4),
                    bottomLeft: Radius.circular(20),
                    bottomRight: Radius.circular(20),
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  child: Text(
                    message.text,
                    style: TextStyle(color: colorScheme.onPrimary),
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _formatTime(message.timestamp),
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: colorScheme.onSurfaceVariant.withValues(alpha: 0.6),
                    ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ─── AI Message Bubble (avatar, asymmetric corners, timestamp) ───

class _AIMessageBubble extends StatelessWidget {
  const _AIMessageBubble({required this.message});

  final AiConversationMessage message;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Row(
      mainAxisAlignment: MainAxisAlignment.start,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        // AI avatar
        Container(
          width: 32,
          height: 32,
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(
              colors: [Color(0xFF7C4DFF), Color(0xFF536DFE)],
            ),
          ),
          child: const Icon(Icons.auto_awesome, color: Colors.white, size: 18),
        ),
        const SizedBox(width: 8),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 280),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Card(
                color: colorScheme.surfaceContainerHighest,
                elevation: 1,
                shape: const RoundedRectangleBorder(
                  borderRadius: BorderRadius.only(
                    topLeft: Radius.circular(4),
                    topRight: Radius.circular(20),
                    bottomLeft: Radius.circular(20),
                    bottomRight: Radius.circular(20),
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  child: Text(
                    message.text,
                    style: TextStyle(color: colorScheme.onSurfaceVariant),
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _formatTime(message.timestamp),
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: colorScheme.onSurfaceVariant.withValues(alpha: 0.6),
                    ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ─── Typing Indicator (animated bouncing dots, matching Kotlin) ───

class _TypingIndicator extends StatefulWidget {
  const _TypingIndicator();

  @override
  State<_TypingIndicator> createState() => _TypingIndicatorState();
}

class _TypingIndicatorState extends State<_TypingIndicator> with TickerProviderStateMixin {
  late final List<AnimationController> _controllers;
  late final List<Animation<double>> _animations;

  @override
  void initState() {
    super.initState();
    _controllers = List.generate(3, (i) {
      return AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 600),
      );
    });
    _animations = _controllers.map((c) {
      return Tween<double>(begin: 0.3, end: 1.0).animate(
        CurvedAnimation(parent: c, curve: Curves.easeInOut),
      );
    }).toList();

    for (var i = 0; i < 3; i++) {
      Future.delayed(Duration(milliseconds: i * 200), () {
        if (mounted) _controllers[i].repeat(reverse: true);
      });
    }
  }

  @override
  void dispose() {
    for (final c in _controllers) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Row(
      mainAxisAlignment: MainAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 40),
          child: Card(
            color: colorScheme.surfaceContainerHighest,
            elevation: 1,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: List.generate(3, (i) {
                  return AnimatedBuilder(
                    animation: _animations[i],
                    builder: (context, _) {
                      return Container(
                        width: 8,
                        height: 8,
                        margin: EdgeInsets.only(right: i < 2 ? 4 : 0),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: colorScheme.onSurfaceVariant.withValues(alpha: _animations[i].value),
                        ),
                      );
                    },
                  );
                }),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

// ─── Enhanced Input Area (matches Kotlin: card-wrapped input with inline buttons) ───

class _EnhancedChatInputArea extends StatelessWidget {
  const _EnhancedChatInputArea({
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
  final FeatureShortcut? suggestion;
  final ValueChanged<String> onChanged;
  final Future<void> Function() onSend;
  final VoidCallback onCameraClick;
  final VoidCallback onMicClick;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Material(
      elevation: 8,
      color: colorScheme.surface,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Command suggestion card
          if (suggestion != null)
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              color: colorScheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    Icon(suggestion!.icon, size: 16, color: colorScheme.primary),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        '/${suggestion!.command} - ${suggestion!.description}',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: colorScheme.onPrimaryContainer,
                            ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

          // Input card with inline buttons
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
              color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
              elevation: 0,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    // Input field
                    Expanded(
                      child: TextField(
                        controller: controller,
                        enabled: !isLoading,
                        minLines: 1,
                        maxLines: 4,
                        onChanged: onChanged,
                        textInputAction: TextInputAction.send,
                        onSubmitted: isLoading ? null : (_) => onSend(),
                        style: Theme.of(context).textTheme.bodyLarge,
                        decoration: InputDecoration(
                          hintText: 'Chat with AI or type commands below...',
                          hintStyle: TextStyle(
                            color: colorScheme.onSurfaceVariant.withValues(alpha: 0.6),
                          ),
                          border: InputBorder.none,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        ),
                      ),
                    ),
                    // Inline buttons
                    IconButton(
                      onPressed: isLoading ? null : onCameraClick,
                      icon: Icon(
                        Icons.photo_camera,
                        size: 20,
                        color: !isLoading ? colorScheme.onSurfaceVariant : colorScheme.onSurface.withValues(alpha: 0.38),
                      ),
                      constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                      padding: EdgeInsets.zero,
                    ),
                    IconButton(
                      onPressed: isLoading ? null : onMicClick,
                      icon: Icon(
                        Icons.mic,
                        size: 20,
                        color: !isLoading ? colorScheme.onSurfaceVariant : colorScheme.onSurface.withValues(alpha: 0.38),
                      ),
                      constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                      padding: EdgeInsets.zero,
                    ),
                    IconButton(
                      onPressed: (controller.text.isNotEmpty && !isLoading) ? onSend : null,
                      icon: isLoading
                          ? SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: colorScheme.primary,
                              ),
                            )
                          : Icon(
                              Icons.send,
                              size: 20,
                              color: (controller.text.isNotEmpty && !isLoading)
                                  ? colorScheme.primary
                                  : colorScheme.onSurface.withValues(alpha: 0.38),
                            ),
                      constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                      padding: EdgeInsets.zero,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _formatTime(DateTime? timestamp) {
  if (timestamp == null) return '';
  return DateFormat.jm().format(timestamp);
}
