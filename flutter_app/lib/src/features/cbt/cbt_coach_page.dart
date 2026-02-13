import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../domain/features/cbt/cbt_session_manager.dart';
import 'cbt_providers.dart';

class CbtCoachPage extends ConsumerStatefulWidget {
  const CbtCoachPage({super.key});

  @override
  ConsumerState<CbtCoachPage> createState() => _CbtCoachPageState();
}

class _CbtCoachPageState extends ConsumerState<CbtCoachPage> {
  final TextEditingController _inputController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _inputController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _sendMessage() {
    final text = _inputController.text.trim();
    if (text.isEmpty) return;
    _inputController.clear();
    ref.read(cbtControllerProvider.notifier).processTextInput(text);
    _scrollToBottom();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(cbtControllerProvider);
    final colorScheme = Theme.of(context).colorScheme;

    // Auto-scroll when conversation updates
    if (state.conversation.isNotEmpty) {
      _scrollToBottom();
    }

    return SafeArea(
      child: Column(
        children: [
          // Header
          _CbtHeader(
            isActive: state.isActive,
            emotion: state.currentEmotion,
            technique: state.suggestedTechnique,
            currentStep: state.currentStep,
          ),

          // Error banner
          if (state.error != null)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              color: colorScheme.errorContainer,
              child: Row(
                children: [
                  Icon(Icons.warning_amber, size: 16, color: colorScheme.onErrorContainer),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      state.error!,
                      style: TextStyle(color: colorScheme.onErrorContainer, fontSize: 12),
                    ),
                  ),
                ],
              ),
            ),

          // Session insights dialog (shown after session ends)
          if (!state.isActive && state.sessionInsights != null)
            _SessionInsightsCard(insights: state.sessionInsights!),

          // Conversation area
          Expanded(
            child: state.isActive
                ? _ConversationList(
                    messages: state.conversation,
                    isLoading: state.isLoading,
                    scrollController: _scrollController,
                  )
                : _InactiveView(
                    onStartSession: () {
                      ref.read(cbtControllerProvider.notifier).startSession();
                    },
                    onDeleteSessions: () => _showDeleteDialog(context),
                    onViewHistory: () => _showHistoryDialog(context),
                  ),
          ),

          // Action buttons (when session is active)
          if (state.isActive) ...[
            _ActionButtonsRow(
              isLoading: state.isLoading,
              onThoughtRecord: () => _showThoughtRecordDialog(context),
              onNextStep: () {
                ref.read(cbtControllerProvider.notifier).progressToNextStep();
              },
              onProgress: () {
                ref.read(cbtControllerProvider.notifier).requestProgress();
              },
              onTips: () {
                ref.read(cbtControllerProvider.notifier).requestTips();
              },
            ),

            // Input area
            _InputArea(
              controller: _inputController,
              isLoading: state.isLoading,
              onSend: _sendMessage,
              onEndSession: () {
                ref.read(cbtControllerProvider.notifier).endSession();
              },
            ),
          ],
        ],
      ),
    );
  }

  // -------------------------------------------------------------------------
  // Dialogs
  // -------------------------------------------------------------------------

  void _showThoughtRecordDialog(BuildContext context) {
    final situationController = TextEditingController();
    final thoughtController = TextEditingController();
    final evidenceForController = TextEditingController();
    final evidenceAgainstController = TextEditingController();
    final balancedController = TextEditingController();
    Emotion selectedEmotion = ref.read(cbtControllerProvider).currentEmotion ?? Emotion.neutral;
    double intensity = selectedEmotion.defaultIntensity;

    showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setDialogState) {
            return AlertDialog(
              title: const Text('Thought Record'),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    TextField(
                      controller: situationController,
                      decoration: const InputDecoration(
                        labelText: 'Situation',
                        hintText: 'What was happening?',
                        border: OutlineInputBorder(),
                      ),
                      maxLines: 2,
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: thoughtController,
                      decoration: const InputDecoration(
                        labelText: 'Automatic Thought',
                        hintText: 'What went through your mind?',
                        border: OutlineInputBorder(),
                      ),
                      maxLines: 2,
                    ),
                    const SizedBox(height: 12),
                    Text('Emotion', style: Theme.of(ctx).textTheme.titleSmall),
                    const SizedBox(height: 4),
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: Emotion.values.map((e) {
                        return ChoiceChip(
                          label: Text('${e.emoji} ${e.label}'),
                          selected: selectedEmotion == e,
                          onSelected: (_) {
                            setDialogState(() {
                              selectedEmotion = e;
                              intensity = e.defaultIntensity;
                            });
                          },
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 12),
                    Text('Intensity: ${(intensity * 100).round()}%'),
                    Slider(
                      value: intensity,
                      onChanged: (v) => setDialogState(() => intensity = v),
                      activeColor: Color.lerp(Colors.green, Colors.red, intensity),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: evidenceForController,
                      decoration: const InputDecoration(
                        labelText: 'Evidence For',
                        hintText: 'What supports this thought?',
                        border: OutlineInputBorder(),
                      ),
                      maxLines: 2,
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: evidenceAgainstController,
                      decoration: const InputDecoration(
                        labelText: 'Evidence Against',
                        hintText: 'What contradicts this thought?',
                        border: OutlineInputBorder(),
                      ),
                      maxLines: 2,
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: balancedController,
                      decoration: const InputDecoration(
                        labelText: 'Balanced Thought',
                        hintText: 'A more balanced perspective...',
                        border: OutlineInputBorder(),
                      ),
                      maxLines: 2,
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: () {
                    if (thoughtController.text.trim().isEmpty) return;
                    final now = DateTime.now();
                    final record = ThoughtRecord(
                      id: 'tr_${now.microsecondsSinceEpoch}',
                      timestamp: now,
                      situation: situationController.text.trim(),
                      automaticThought: thoughtController.text.trim(),
                      emotion: selectedEmotion,
                      emotionIntensity: intensity,
                      evidenceFor: evidenceForController.text.trim(),
                      evidenceAgainst: evidenceAgainstController.text.trim(),
                      balancedThought: balancedController.text.trim(),
                    );
                    Navigator.of(ctx).pop();
                    ref.read(cbtControllerProvider.notifier).createThoughtRecord(record);
                  },
                  child: const Text('Save'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  void _showDeleteDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: const Text('Manage Sessions'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.delete_sweep),
                title: const Text('Delete All Sessions'),
                subtitle: const Text('Remove all CBT session data'),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _confirmDelete(context, 'all sessions', () {
                    ref.read(cbtControllerProvider.notifier).deleteAllSessions();
                  });
                },
              ),
              ListTile(
                leading: const Icon(Icons.date_range),
                title: const Text('Delete Old Sessions'),
                subtitle: const Text('Remove sessions older than 30 days'),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _confirmDelete(context, 'sessions older than 30 days', () {
                    ref.read(cbtControllerProvider.notifier).deleteOldSessions(30);
                  });
                },
              ),
              ListTile(
                leading: const Icon(Icons.cleaning_services),
                title: const Text('Complete Privacy Reset'),
                subtitle: const Text('Delete everything permanently'),
                onTap: () {
                  Navigator.of(ctx).pop();
                  _confirmDelete(context, 'ALL data (this cannot be undone)', () {
                    ref.read(cbtControllerProvider.notifier).deleteAllSessions();
                  });
                },
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Close'),
            ),
          ],
        );
      },
    );
  }

  void _confirmDelete(BuildContext context, String description, VoidCallback onConfirm) {
    showDialog(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: const Text('Confirm Delete'),
          content: Text('Are you sure you want to delete $description?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: Theme.of(ctx).colorScheme.error,
              ),
              onPressed: () {
                Navigator.of(ctx).pop();
                onConfirm();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Sessions deleted.')),
                );
              },
              child: const Text('Delete'),
            ),
          ],
        );
      },
    );
  }

  void _showHistoryDialog(BuildContext parentContext) async {
    final sessions = await ref.read(cbtControllerProvider.notifier).getSessions();
    if (!mounted) return;

    showDialog(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: const Text('Session History'),
          content: SizedBox(
            width: double.maxFinite,
            height: 400,
            child: sessions.isEmpty
                ? const Center(child: Text('No sessions yet.'))
                : ListView.builder(
                    itemCount: sessions.length,
                    itemBuilder: (_, index) {
                      final session = sessions[index];
                      final emotion = session.emotion != null
                          ? Emotion.fromString(session.emotion!)
                          : null;
                      final dateStr = DateFormat('MMM d, yyyy h:mm a')
                          .format(session.createdAt);
                      final durationMin = (session.durationMs / 60000).round();

                      return Card(
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: emotion != null
                                ? _emotionColor(emotion).withValues(alpha: 0.2)
                                : null,
                            child: Text(emotion?.emoji ?? '?'),
                          ),
                          title: Text(session.techniqueLabel),
                          subtitle: Text(
                            '$dateStr\n${durationMin}min - ${session.emotionLabel}',
                          ),
                          isThreeLine: true,
                          trailing: session.completed
                              ? Icon(Icons.check_circle,
                                  color: Colors.green.shade400, size: 20)
                              : null,
                        ),
                      );
                    },
                  ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Close'),
            ),
          ],
        );
      },
    );
  }
}

// =============================================================================
// Header
// =============================================================================

class _CbtHeader extends StatelessWidget {
  const _CbtHeader({
    required this.isActive,
    this.emotion,
    this.technique,
    this.currentStep = 0,
  });

  final bool isActive;
  final Emotion? emotion;
  final CBTTechnique? technique;
  final int currentStep;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      margin: const EdgeInsets.all(12),
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: isActive
                      ? Colors.green.shade400
                      : colorScheme.primary,
                  child: const Icon(
                    Icons.psychology,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'CBT Coach',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      Text(
                        isActive ? 'Session Active' : 'Ready to start',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: isActive
                                  ? Colors.green.shade600
                                  : colorScheme.onSurfaceVariant,
                            ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (isActive && (emotion != null || technique != null)) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  if (emotion != null)
                    _EmotionChip(emotion: emotion!),
                  if (emotion != null && technique != null)
                    const SizedBox(width: 8),
                  if (technique != null)
                    Expanded(
                      child: _TechniqueChip(
                        technique: technique!,
                        currentStep: currentStep,
                      ),
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

// =============================================================================
// Emotion Chip
// =============================================================================

Color _emotionColor(Emotion emotion) {
  switch (emotion) {
    case Emotion.happy:
      return Colors.green;
    case Emotion.sad:
      return Colors.blue;
    case Emotion.angry:
      return Colors.red;
    case Emotion.anxious:
      return Colors.orange;
    case Emotion.fearful:
      return Colors.purple;
    case Emotion.surprised:
      return Colors.amber;
    case Emotion.disgusted:
      return Colors.brown;
    case Emotion.neutral:
      return Colors.grey;
  }
}

class _EmotionChip extends StatelessWidget {
  const _EmotionChip({required this.emotion});
  final Emotion emotion;

  @override
  Widget build(BuildContext context) {
    final color = _emotionColor(emotion);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(emotion.emoji, style: const TextStyle(fontSize: 16)),
          const SizedBox(width: 4),
          Text(
            emotion.label,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w600,
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }
}

// =============================================================================
// Technique Chip
// =============================================================================

class _TechniqueChip extends StatelessWidget {
  const _TechniqueChip({required this.technique, this.currentStep = 0});
  final CBTTechnique technique;
  final int currentStep;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.lightbulb_outline, size: 16, color: colorScheme.onSecondaryContainer),
          const SizedBox(width: 4),
          Flexible(
            child: Text(
              '${technique.name} (${currentStep + 1}/${technique.steps.length})',
              style: TextStyle(
                color: colorScheme.onSecondaryContainer,
                fontWeight: FontWeight.w500,
                fontSize: 12,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

// =============================================================================
// Conversation List
// =============================================================================

class _ConversationList extends StatelessWidget {
  const _ConversationList({
    required this.messages,
    required this.isLoading,
    required this.scrollController,
  });

  final List<CbtMessage> messages;
  final bool isLoading;
  final ScrollController scrollController;

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      controller: scrollController,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      itemCount: messages.length + (isLoading ? 1 : 0),
      itemBuilder: (context, index) {
        if (index == messages.length) {
          return const _TypingIndicator();
        }
        return _MessageBubble(message: messages[index]);
      },
    );
  }
}

// =============================================================================
// Message Bubble
// =============================================================================

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message});
  final CbtMessage message;

  @override
  Widget build(BuildContext context) {
    final isUser = message is UserMessage;
    final colorScheme = Theme.of(context).colorScheme;
    final timeStr = DateFormat('h:mm a').format(message.timestamp);

    final aiMsg = message is AiMessage ? message as AiMessage : null;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!isUser) ...[
            CircleAvatar(
              radius: 16,
              backgroundColor: colorScheme.secondaryContainer,
              child: Icon(Icons.psychology, size: 18, color: colorScheme.onSecondaryContainer),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: GestureDetector(
              onLongPress: () {
                Clipboard.setData(ClipboardData(text: message.content));
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Copied to clipboard'),
                    duration: Duration(seconds: 1),
                  ),
                );
              },
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: isUser
                      ? colorScheme.primary
                      : colorScheme.secondaryContainer,
                  borderRadius: BorderRadius.only(
                    topLeft: const Radius.circular(16),
                    topRight: const Radius.circular(16),
                    bottomLeft: Radius.circular(isUser ? 16 : 4),
                    bottomRight: Radius.circular(isUser ? 4 : 16),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (aiMsg?.techniqueName != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 6),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: (isUser ? colorScheme.onPrimary : colorScheme.primary)
                                .withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            aiMsg!.techniqueName!,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: isUser
                                  ? colorScheme.onPrimary
                                  : colorScheme.primary,
                            ),
                          ),
                        ),
                      ),
                    Text(
                      message.content,
                      style: TextStyle(
                        color: isUser
                            ? colorScheme.onPrimary
                            : colorScheme.onSecondaryContainer,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      timeStr,
                      style: TextStyle(
                        fontSize: 10,
                        color: (isUser
                                ? colorScheme.onPrimary
                                : colorScheme.onSecondaryContainer)
                            .withValues(alpha: 0.6),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          if (isUser) const SizedBox(width: 8),
        ],
      ),
    );
  }
}

// =============================================================================
// Typing Indicator
// =============================================================================

class _TypingIndicator extends StatefulWidget {
  const _TypingIndicator();

  @override
  State<_TypingIndicator> createState() => _TypingIndicatorState();
}

class _TypingIndicatorState extends State<_TypingIndicator>
    with TickerProviderStateMixin {
  late final List<AnimationController> _controllers;
  late final List<Animation<double>> _animations;

  @override
  void initState() {
    super.initState();
    _controllers = List.generate(3, (i) {
      return AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 600),
      )..repeat(reverse: true);
    });

    _animations = List.generate(3, (i) {
      return Tween<double>(begin: 0.3, end: 1.0).animate(
        CurvedAnimation(parent: _controllers[i], curve: Curves.easeInOut),
      );
    });

    // Stagger the animations
    for (int i = 0; i < 3; i++) {
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
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          CircleAvatar(
            radius: 16,
            backgroundColor: colorScheme.secondaryContainer,
            child: Icon(Icons.psychology, size: 18, color: colorScheme.onSecondaryContainer),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: colorScheme.secondaryContainer,
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(16),
                topRight: Radius.circular(16),
                bottomRight: Radius.circular(16),
                bottomLeft: Radius.circular(4),
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: List.generate(3, (i) {
                return AnimatedBuilder(
                  animation: _animations[i],
                  builder: (_, __) {
                    return Opacity(
                      opacity: _animations[i].value,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 2),
                        child: CircleAvatar(
                          radius: 4,
                          backgroundColor: colorScheme.onSecondaryContainer,
                        ),
                      ),
                    );
                  },
                );
              }),
            ),
          ),
        ],
      ),
    );
  }
}

// =============================================================================
// Action Buttons Row
// =============================================================================

class _ActionButtonsRow extends StatelessWidget {
  const _ActionButtonsRow({
    required this.isLoading,
    required this.onThoughtRecord,
    required this.onNextStep,
    required this.onProgress,
    required this.onTips,
  });

  final bool isLoading;
  final VoidCallback onThoughtRecord;
  final VoidCallback onNextStep;
  final VoidCallback onProgress;
  final VoidCallback onTips;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            ActionChip(
              avatar: const Icon(Icons.edit_note, size: 18),
              label: const Text('Thought Record'),
              onPressed: isLoading ? null : onThoughtRecord,
            ),
            const SizedBox(width: 8),
            ActionChip(
              avatar: const Icon(Icons.skip_next, size: 18),
              label: const Text('Next Step'),
              onPressed: isLoading ? null : onNextStep,
            ),
            const SizedBox(width: 8),
            ActionChip(
              avatar: const Icon(Icons.trending_up, size: 18),
              label: const Text('Progress'),
              onPressed: isLoading ? null : onProgress,
            ),
            const SizedBox(width: 8),
            ActionChip(
              avatar: const Icon(Icons.tips_and_updates, size: 18),
              label: const Text('Tips'),
              onPressed: isLoading ? null : onTips,
            ),
          ],
        ),
      ),
    );
  }
}

// =============================================================================
// Input Area
// =============================================================================

class _InputArea extends StatelessWidget {
  const _InputArea({
    required this.controller,
    required this.isLoading,
    required this.onSend,
    required this.onEndSession,
  });

  final TextEditingController controller;
  final bool isLoading;
  final VoidCallback onSend;
  final VoidCallback onEndSession;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(
          top: BorderSide(color: colorScheme.outlineVariant),
        ),
      ),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.stop_circle_outlined),
            tooltip: 'End Session',
            onPressed: isLoading ? null : onEndSession,
            color: colorScheme.error,
          ),
          const SizedBox(width: 4),
          Expanded(
            child: TextField(
              controller: controller,
              decoration: InputDecoration(
                hintText: 'Share your thoughts...',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(24),
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                isDense: true,
              ),
              maxLines: 3,
              minLines: 1,
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => onSend(),
            ),
          ),
          const SizedBox(width: 8),
          IconButton.filled(
            icon: isLoading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.send),
            onPressed: isLoading ? null : onSend,
          ),
        ],
      ),
    );
  }
}

// =============================================================================
// Inactive View (Start / History / Delete)
// =============================================================================

class _InactiveView extends StatelessWidget {
  const _InactiveView({
    required this.onStartSession,
    required this.onDeleteSessions,
    required this.onViewHistory,
  });

  final VoidCallback onStartSession;
  final VoidCallback onDeleteSessions;
  final VoidCallback onViewHistory;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.self_improvement,
              size: 64,
              color: colorScheme.primary.withValues(alpha: 0.5),
            ),
            const SizedBox(height: 16),
            Text(
              'Welcome to CBT Coach',
              style: Theme.of(context).textTheme.headlineSmall,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              'Start a session to work through thoughts and emotions '
              'using evidence-based CBT techniques.',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: onStartSession,
              icon: const Icon(Icons.play_arrow),
              label: const Text('Start Session'),
              style: FilledButton.styleFrom(
                minimumSize: const Size(200, 48),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                OutlinedButton.icon(
                  onPressed: onViewHistory,
                  icon: const Icon(Icons.history, size: 18),
                  label: const Text('History'),
                ),
                const SizedBox(width: 12),
                OutlinedButton.icon(
                  onPressed: onDeleteSessions,
                  icon: const Icon(Icons.delete_outline, size: 18),
                  label: const Text('Manage'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// =============================================================================
// Session Insights Card
// =============================================================================

class _SessionInsightsCard extends StatelessWidget {
  const _SessionInsightsCard({required this.insights});
  final SessionInsights insights;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      color: colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.insights, color: colorScheme.onTertiaryContainer),
                const SizedBox(width: 8),
                Text(
                  'Session Insights',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: colorScheme.onTertiaryContainer,
                      ),
                ),
              ],
            ),
            if (insights.summary.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                insights.summary,
                style: TextStyle(color: colorScheme.onTertiaryContainer),
              ),
            ],
            if (insights.keyInsights.isNotEmpty) ...[
              const SizedBox(height: 8),
              ...insights.keyInsights.map(
                (insight) => Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.star, size: 14, color: colorScheme.onTertiaryContainer),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          insight,
                          style: TextStyle(
                            color: colorScheme.onTertiaryContainer,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            if (insights.homework.isNotEmpty) ...[
              const SizedBox(height: 8),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.assignment, size: 16, color: colorScheme.onTertiaryContainer),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      'Homework: ${insights.homework}',
                      style: TextStyle(
                        color: colorScheme.onTertiaryContainer,
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
