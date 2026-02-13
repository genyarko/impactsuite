import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../data/models/chat_models.dart';
import 'chat_providers.dart';

// Session type detection matching Kotlin's getSessionType
enum _SessionType { chat, liveCaption, aiTutor }

_SessionType _getSessionType(String title) {
  if (title.startsWith('Live caption')) return _SessionType.liveCaption;

  final lower = title.toLowerCase();
  const tutorKeywords = [
    'science-general', 'math', 'mathematics', 'history', 'english',
    'physics', 'chemistry', 'biology', 'geography', 'literature',
    'ai tutor', 'tutor', 'homework', 'practice', 'exam prep',
    'science',
  ];
  for (final keyword in tutorKeywords) {
    if (lower.contains(keyword)) return _SessionType.aiTutor;
  }

  return _SessionType.chat;
}

// Color schemes per session type matching Kotlin
class _SessionColors {
  const _SessionColors({
    required this.primary,
    required this.icon,
  });

  final Color primary;
  final IconData icon;

  Color get background => primary.withValues(alpha: 0.08);
  Color get iconBackground => primary.withValues(alpha: 0.12);
}

const _chatColors = _SessionColors(
  primary: Color(0xFF1976D2),
  icon: Icons.chat,
);

const _liveCaptionColors = _SessionColors(
  primary: Color(0xFF00796B),
  icon: Icons.mic,
);

const _aiTutorColors = _SessionColors(
  primary: Color(0xFF8E24AA),
  icon: Icons.school,
);

_SessionColors _colorsForType(_SessionType type) => switch (type) {
      _SessionType.chat => _chatColors,
      _SessionType.liveCaption => _liveCaptionColors,
      _SessionType.aiTutor => _aiTutorColors,
    };

String _badgeLabel(_SessionType type) => switch (type) {
      _SessionType.chat => 'CHAT',
      _SessionType.liveCaption => 'LIVE',
      _SessionType.aiTutor => 'TUTOR',
    };

// Title formatting matching Kotlin's formatTitle
String _formatTitle(String title, _SessionType type) {
  switch (type) {
    case _SessionType.liveCaption:
      final match = RegExp(r'Live caption (\d{4}-\d{2}-\d{2} \d{2}:\d{2})').firstMatch(title);
      if (match != null) {
        try {
          final date = DateFormat('yyyy-MM-dd HH:mm').parse(match.group(1)!);
          return 'Live Caption - ${DateFormat('MMM d, h:mm a').format(date)}';
        } catch (_) {
          return 'Live Caption Session';
        }
      }
      return 'Live Caption Session';

    case _SessionType.aiTutor:
      final lower = title.toLowerCase();
      if (lower.contains('science-general') || lower.contains('science')) {
        return 'AI Tutor - Science';
      }
      if (lower.contains('mathematics')) return 'AI Tutor - Mathematics';
      if (lower.contains('math')) return 'AI Tutor - Math';
      if (lower.contains('history')) return 'AI Tutor - History';
      if (lower.contains('english')) return 'AI Tutor - English';
      if (lower.contains('physics')) return 'AI Tutor - Physics';
      if (lower.contains('chemistry')) return 'AI Tutor - Chemistry';
      if (lower.contains('biology')) return 'AI Tutor - Biology';
      if (lower.contains('geography')) return 'AI Tutor - Geography';
      if (lower.contains('literature')) return 'AI Tutor - Literature';
      if (lower.contains('ai tutor')) return title;
      if (lower.contains('homework')) return 'AI Tutor - Homework Help';
      if (lower.contains('practice')) return 'AI Tutor - Practice';
      if (lower.contains('exam prep')) return 'AI Tutor - Exam Prep';
      if (lower.contains('tutor')) return title;
      return 'AI Tutor Session';

    case _SessionType.chat:
      return title;
  }
}

// Relative timestamp formatting matching Kotlin's formatTimestamp
String _formatTimestamp(DateTime timestamp) {
  final now = DateTime.now();
  final diff = now.difference(timestamp);

  if (diff.inMinutes < 1) return 'Just now';
  if (diff.inHours < 1) return '${diff.inMinutes}m ago';
  if (diff.inDays < 1) return '${diff.inHours}h ago';
  if (diff.inDays < 7) return '${diff.inDays}d ago';
  return DateFormat('MMM d').format(timestamp);
}

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
          appBar: AppBar(
            title: const Text(
              'CHAT HISTORY',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
          ),
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
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
                  itemCount: state.sessions.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final session = state.sessions[index];
                    return _ChatSessionItem(
                      session: session,
                      onTap: () => context.go('/chat/${session.id}'),
                      onDelete: () => controller.deleteSession(session.id),
                    );
                  },
                ),
        );
      },
    );
  }
}

class _ChatSessionItem extends StatelessWidget {
  const _ChatSessionItem({
    required this.session,
    required this.onTap,
    required this.onDelete,
  });

  final ChatSession session;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final type = _getSessionType(session.title);
    final colors = _colorsForType(type);

    return Card(
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: InkWell(
        onTap: onTap,
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                colors.background,
                colors.background.withValues(alpha: 0.04),
                Colors.transparent,
              ],
              stops: const [0.0, 0.5, 1.0],
            ),
          ),
          padding: const EdgeInsets.all(16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Icon
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: colors.iconBackground,
                ),
                child: Icon(colors.icon, color: colors.primary, size: 24),
              ),
              const SizedBox(width: 12),

              // Content
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Title row with badge
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            _formatTitle(session.title, type),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.w600,
                                ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: colors.primary.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            _badgeLabel(type),
                            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: colors.primary,
                                ),
                          ),
                        ),
                      ],
                    ),

                    // Last message preview
                    if (session.lastMessage != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        session.lastMessage!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                      ),
                    ],

                    // Timestamp
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Icon(
                          Icons.schedule,
                          size: 14,
                          color: Theme.of(context)
                              .colorScheme
                              .onSurfaceVariant
                              .withValues(alpha: 0.6),
                        ),
                        const SizedBox(width: 4),
                        Text(
                          _formatTimestamp(session.updatedAt),
                          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                                color: Theme.of(context)
                                    .colorScheme
                                    .onSurfaceVariant
                                    .withValues(alpha: 0.6),
                              ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),

              // Delete button with confirmation
              IconButton(
                onPressed: () => _confirmDelete(context),
                icon: Icon(
                  Icons.delete_outline,
                  color: Theme.of(context)
                      .colorScheme
                      .onSurfaceVariant
                      .withValues(alpha: 0.6),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _confirmDelete(BuildContext context) {
    final type = _getSessionType(session.title);
    final typeName = switch (type) {
      _SessionType.chat => 'Chat',
      _SessionType.liveCaption => 'Live Caption',
      _SessionType.aiTutor => 'AI Tutor Session',
    };

    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Delete $typeName?'),
        content: const Text('This action cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              onDelete();
            },
            child: Text(
              'Delete',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        ],
      ),
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
            color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.5),
          ),
          const SizedBox(height: 16),
          Text(
            'No conversations yet',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w500,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
          const SizedBox(height: 8),
          Text(
            'Start a new chat, live caption, or AI tutor session',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context)
                      .colorScheme
                      .onSurfaceVariant
                      .withValues(alpha: 0.7),
                ),
          ),
        ],
      ),
    );
  }
}
