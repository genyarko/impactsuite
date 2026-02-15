import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/features/tutor/tutor_models.dart';
import 'tutor_controller.dart';
import 'tutor_providers.dart';

class TutorPage extends ConsumerStatefulWidget {
  const TutorPage({super.key});

  @override
  ConsumerState<TutorPage> createState() => _TutorPageState();
}

class _TutorPageState extends ConsumerState<TutorPage> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();

  @override
  void dispose() {
    _messageController.dispose();
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

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(tutorControllerProvider);

    // Auto-scroll when messages change
    ref.listen(tutorControllerProvider.select((s) => s.messages.length), (_, __) {
      _scrollToBottom();
    });

    return SafeArea(
      child: switch (state.phase) {
        TutorPhase.profile => const _ProfilePhase(key: ValueKey('profile')),
        TutorPhase.subjectSelect => const _SubjectSelectPhase(key: ValueKey('subjects')),
        TutorPhase.sessionSetup => const _SessionSetupPhase(key: ValueKey('setup')),
        TutorPhase.conversation => _ConversationPhase(
            messageController: _messageController,
            scrollController: _scrollController,
            key: const ValueKey('conversation'),
          ),
        TutorPhase.sessionSummary => const _SessionSummaryPhase(key: ValueKey('summary')),
      },
    );
  }
}

// =============================================================================
// Phase 1: Student Profile
// =============================================================================

class _ProfilePhase extends ConsumerStatefulWidget {
  const _ProfilePhase({super.key});

  @override
  ConsumerState<_ProfilePhase> createState() => _ProfilePhaseState();
}

class _ProfilePhaseState extends ConsumerState<_ProfilePhase> {
  final _nameController = TextEditingController();
  int _gradeLevel = 8;
  LearningStyle _learningStyle = LearningStyle.verbal;

  @override
  void initState() {
    super.initState();
    final profile = ref.read(tutorControllerProvider).studentProfile;
    if (profile != null) {
      _nameController.text = profile.name;
      _gradeLevel = profile.gradeLevel;
      _learningStyle = profile.learningStyle;
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                const Icon(Icons.school, size: 48),
                const SizedBox(height: 8),
                Text('Welcome to AI Tutor', style: theme.textTheme.headlineSmall),
                const SizedBox(height: 6),
                Text(
                  'Tell us a bit about yourself so we can personalize your learning experience.',
                  style: theme.textTheme.bodyMedium,
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Name
        Text('Your Name', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        TextField(
          controller: _nameController,
          decoration: const InputDecoration(
            hintText: 'Enter your name',
            border: OutlineInputBorder(),
            prefixIcon: Icon(Icons.person),
          ),
          textCapitalization: TextCapitalization.words,
        ),
        const SizedBox(height: 16),

        // Grade level
        Text('Grade Level: $_gradeLevel', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        Slider(
          value: _gradeLevel.toDouble(),
          min: 1,
          max: 12,
          divisions: 11,
          label: 'Grade $_gradeLevel',
          onChanged: (v) => setState(() => _gradeLevel = v.round()),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('1st', style: theme.textTheme.bodySmall),
              Text('6th', style: theme.textTheme.bodySmall),
              Text('12th', style: theme.textTheme.bodySmall),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // Learning style
        Text('Learning Style', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        ...LearningStyle.values.map((style) => RadioListTile<LearningStyle>(
              title: Text(style.label),
              subtitle: Text(style.description),
              value: style,
              groupValue: _learningStyle,
              onChanged: (v) => setState(() => _learningStyle = v!),
            )),
        const SizedBox(height: 24),

        FilledButton.icon(
          onPressed: _nameController.text.trim().isEmpty
              ? null
              : () {
                  ref.read(tutorControllerProvider.notifier).setStudentProfile(
                        _nameController.text,
                        _gradeLevel,
                        _learningStyle,
                      );
                },
          icon: const Icon(Icons.arrow_forward),
          label: const Text('Continue'),
        ),
      ],
    );
  }
}

// =============================================================================
// Phase 2: Subject Selection
// =============================================================================

class _SubjectSelectPhase extends ConsumerWidget {
  const _SubjectSelectPhase({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(tutorControllerProvider);
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                const Icon(Icons.school, size: 32),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Hello, ${state.studentProfile?.name ?? "Student"}!',
                        style: theme.textTheme.titleMedium,
                      ),
                      Text(
                        '${state.studentProfile?.gradeLevelLabel ?? ""} '
                        '${state.studentProfile?.learningStyle.label ?? ""} learner',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                TextButton(
                  onPressed: () => ref.read(tutorControllerProvider.notifier).editProfile(),
                  child: const Text('Edit'),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text('What would you like to study?', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        ..._subjects.map((subject) => _SubjectCard(
              subject: subject,
              onTap: () => ref.read(tutorControllerProvider.notifier).selectSubject(subject.name),
            )),
      ],
    );
  }
}

class _SubjectCard extends StatelessWidget {
  const _SubjectCard({required this.subject, required this.onTap});

  final _TutorSubject subject;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: subject.color.withValues(alpha: 0.15),
          child: Icon(subject.icon, color: subject.color),
        ),
        title: Text(subject.name),
        subtitle: Text(subject.description),
        trailing: const Icon(Icons.arrow_forward_ios, size: 16),
        onTap: onTap,
      ),
    );
  }
}

// =============================================================================
// Phase 3: Session Setup
// =============================================================================

class _SessionSetupPhase extends ConsumerWidget {
  const _SessionSetupPhase({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(tutorControllerProvider);
    final theme = Theme.of(context);
    final controller = ref.read(tutorControllerProvider.notifier);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          color: theme.colorScheme.primaryContainer,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Icon(Icons.school, color: theme.colorScheme.primary),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    '${state.selectedSubject} session for ${state.studentProfile?.name ?? ""}',
                    style: theme.textTheme.titleMedium,
                  ),
                ),
                TextButton(
                  onPressed: () => controller.changeSubject(),
                  child: const Text('Change'),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Text('Choose a session type:', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        ...TutorSessionType.values.map((type) => Card(
              child: ListTile(
                leading: CircleAvatar(
                  child: Icon(_sessionTypeIcon(type)),
                ),
                title: Text(type.label),
                subtitle: Text(type.description),
                trailing: const Icon(Icons.play_arrow),
                onTap: () => controller.startSession(type),
              ),
            )),
      ],
    );
  }

  IconData _sessionTypeIcon(TutorSessionType type) {
    switch (type) {
      case TutorSessionType.homeworkHelp:
        return Icons.assignment;
      case TutorSessionType.conceptExplanation:
        return Icons.lightbulb;
      case TutorSessionType.examPrep:
        return Icons.quiz;
      case TutorSessionType.practiceProblems:
        return Icons.calculate;
    }
  }
}

// =============================================================================
// Phase 4: Conversation
// =============================================================================

class _ConversationPhase extends ConsumerWidget {
  const _ConversationPhase({
    required this.messageController,
    required this.scrollController,
    super.key,
  });

  final TextEditingController messageController;
  final ScrollController scrollController;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(tutorControllerProvider);
    final controller = ref.read(tutorControllerProvider.notifier);
    final theme = Theme.of(context);
    final subject = _subjects.where((s) => s.name == state.selectedSubject).firstOrNull;

    return Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          // Header
          _ConversationHeader(
            subject: state.selectedSubject ?? '',
            sessionType: state.sessionType?.label ?? '',
            gradeLevel: state.studentProfile?.gradeLevel ?? 0,
            subjectColor: subject?.color ?? Colors.teal,
            subjectIcon: subject?.icon ?? Icons.school,
            onChangeSubject: () => controller.changeSubject(),
            onEndSession: () => controller.endSession(),
          ),

          // Topic suggestions (from curriculum data)
          if (state.curriculumTopics.isNotEmpty && state.messages.length <= 1) ...[
            const SizedBox(height: 8),
            SizedBox(
              height: 40,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: state.curriculumTopics.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final topic = state.curriculumTopics[index].title;
                  return ActionChip(
                    avatar: const Icon(Icons.lightbulb_outline, size: 16),
                    label: Text(topic),
                    onPressed: state.isLoading ? null : () => controller.sendMessage(topic),
                  );
                },
              ),
            ),
          ],

          const SizedBox(height: 8),

          // Messages
          Expanded(
            child: state.messages.isEmpty
                ? Center(
                    child: Text(
                      'Start by asking a question about ${state.selectedSubject}.',
                      style: theme.textTheme.bodyLarge,
                      textAlign: TextAlign.center,
                    ),
                  )
                : ListView.separated(
                    controller: scrollController,
                    itemCount: state.messages.length + (state.isLoading ? 1 : 0),
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (context, index) {
                      if (index == state.messages.length) {
                        return const _TypingIndicator();
                      }
                      final message = state.messages[index];
                      return _MessageBubble(
                        message: message,
                        onBookmark: () => controller.bookmarkMessage(message.id),
                      );
                    },
                  ),
          ),

          // Error banner
          if (state.error != null) ...[
            const SizedBox(height: 4),
            Row(
              children: [
                Expanded(
                  child: Text(
                    state.error!,
                    style: TextStyle(color: theme.colorScheme.error, fontSize: 12),
                  ),
                ),
                TextButton(
                  onPressed: () => controller.retryLastMessage(),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ],

          const SizedBox(height: 4),

          // Quick action buttons
          if (!state.isLoading && state.messages.length > 1)
            SizedBox(
              height: 36,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  _QuickActionChip(
                    label: 'Get Hint',
                    icon: Icons.tips_and_updates,
                    onPressed: state.currentTopic != null ? () => controller.getHint() : null,
                  ),
                  const SizedBox(width: 6),
                  _QuickActionChip(
                    label: 'Explain Simpler',
                    icon: Icons.auto_fix_high,
                    onPressed: () => controller.requestSimpler(),
                  ),
                  const SizedBox(width: 6),
                  _QuickActionChip(
                    label: 'Give Example',
                    icon: Icons.lightbulb,
                    onPressed: () => controller.requestExample(),
                  ),
                  const SizedBox(width: 6),
                  _QuickActionChip(
                    label: 'Quiz Me',
                    icon: Icons.quiz,
                    onPressed: () => controller.requestQuiz(),
                  ),
                ],
              ),
            ),

          const SizedBox(height: 8),

          // Input bar
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: messageController,
                  enabled: !state.isLoading,
                  textInputAction: TextInputAction.send,
                  onSubmitted: state.isLoading
                      ? null
                      : (text) {
                          controller.sendMessage(text);
                          messageController.clear();
                        },
                  decoration: const InputDecoration(
                    hintText: 'Ask your tutor...',
                    border: OutlineInputBorder(),
                    contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              FilledButton(
                onPressed: state.isLoading
                    ? null
                    : () {
                        controller.sendMessage(messageController.text);
                        messageController.clear();
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
    );
  }
}

class _ConversationHeader extends StatelessWidget {
  const _ConversationHeader({
    required this.subject,
    required this.sessionType,
    required this.gradeLevel,
    required this.subjectColor,
    required this.subjectIcon,
    required this.onChangeSubject,
    required this.onEndSession,
  });

  final String subject;
  final String sessionType;
  final int gradeLevel;
  final Color subjectColor;
  final IconData subjectIcon;
  final VoidCallback onChangeSubject;
  final VoidCallback onEndSession;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      color: colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          children: [
            CircleAvatar(
              radius: 16,
              backgroundColor: subjectColor.withValues(alpha: 0.2),
              child: Icon(subjectIcon, size: 18, color: subjectColor),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(subject, style: Theme.of(context).textTheme.titleSmall),
                  Text('$sessionType  Grade $gradeLevel',
                      style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
            TextButton(onPressed: onChangeSubject, child: const Text('Change')),
            IconButton(
              tooltip: 'End session',
              icon: const Icon(Icons.stop_circle_outlined, size: 20),
              onPressed: onEndSession,
            ),
          ],
        ),
      ),
    );
  }
}

class _QuickActionChip extends StatelessWidget {
  const _QuickActionChip({required this.label, required this.icon, this.onPressed});

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return ActionChip(
      avatar: Icon(icon, size: 16),
      label: Text(label, style: const TextStyle(fontSize: 12)),
      onPressed: onPressed,
      visualDensity: VisualDensity.compact,
    );
  }
}

// =============================================================================
// Phase 5: Session Summary
// =============================================================================

class _SessionSummaryPhase extends ConsumerWidget {
  const _SessionSummaryPhase({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(tutorControllerProvider);
    final insights = state.sessionInsights;
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          color: theme.colorScheme.primaryContainer,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                const Icon(Icons.check_circle, size: 48),
                const SizedBox(height: 8),
                Text('Session Complete!', style: theme.textTheme.headlineSmall),
                const SizedBox(height: 4),
                Text(state.selectedSubject ?? '', style: theme.textTheme.titleMedium),
              ],
            ),
          ),
        ),

        if (insights != null) ...[
          const SizedBox(height: 12),

          // Summary
          if (insights.summary.isNotEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Summary', style: theme.textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(insights.summary),
                  ],
                ),
              ),
            ),

          // Understanding gauge
          if (insights.overallUnderstanding > 0)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Understanding', style: theme.textTheme.titleMedium),
                    const SizedBox(height: 8),
                    LinearProgressIndicator(
                      value: insights.overallUnderstanding,
                      minHeight: 8,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    const SizedBox(height: 4),
                    Text('${(insights.overallUnderstanding * 100).round()}%'),
                  ],
                ),
              ),
            ),

          // Concepts covered
          if (insights.conceptsCovered.isNotEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Concepts Covered', style: theme.textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      children: insights.conceptsCovered
                          .map((c) => Chip(label: Text(c, style: const TextStyle(fontSize: 12))))
                          .toList(),
                    ),
                  ],
                ),
              ),
            ),

          // Strengths
          if (insights.strengths.isNotEmpty)
            _InsightsList(title: 'Strengths', items: insights.strengths, icon: Icons.star),

          // Areas for improvement
          if (insights.areasForImprovement.isNotEmpty)
            _InsightsList(
              title: 'Areas for Improvement',
              items: insights.areasForImprovement,
              icon: Icons.trending_up,
            ),

          // Next steps
          if (insights.nextSteps.isNotEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Next Steps', style: theme.textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(insights.nextSteps),
                  ],
                ),
              ),
            ),
        ],

        const SizedBox(height: 24),
        FilledButton.icon(
          onPressed: () => ref.read(tutorControllerProvider.notifier).startNewSession(),
          icon: const Icon(Icons.refresh),
          label: const Text('Start New Session'),
        ),
      ],
    );
  }
}

class _InsightsList extends StatelessWidget {
  const _InsightsList({required this.title, required this.items, required this.icon});

  final String title;
  final List<String> items;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            ...items.map((item) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(icon, size: 16, color: theme.colorScheme.primary),
                      const SizedBox(width: 8),
                      Expanded(child: Text(item)),
                    ],
                  ),
                )),
          ],
        ),
      ),
    );
  }
}

// =============================================================================
// Shared Widgets
// =============================================================================

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message, required this.onBookmark});

  final TutorMessage message;
  final VoidCallback onBookmark;

  @override
  Widget build(BuildContext context) {
    final isUser = message is TutorUserMessage;
    final colorScheme = Theme.of(context).colorScheme;
    final aiMessage = message is TutorAiMessage ? message as TutorAiMessage : null;

    return Row(
      mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (!isUser) ...[
          CircleAvatar(
            radius: 14,
            backgroundColor: colorScheme.primaryContainer,
            child: Icon(Icons.smart_toy, size: 16, color: colorScheme.primary),
          ),
          const SizedBox(width: 8),
        ],
        Flexible(
          child: Column(
            crossAxisAlignment: isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: isUser ? colorScheme.secondaryContainer : colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (aiMessage != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(_approachIcon(aiMessage.approach), size: 12, color: colorScheme.outline),
                            const SizedBox(width: 4),
                            Text(
                              aiMessage.approach.label,
                              style: TextStyle(fontSize: 10, color: colorScheme.outline),
                            ),
                          ],
                        ),
                      ),
                    SelectableText(message.content),
                  ],
                ),
              ),
              // Actions row
              if (!isUser)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton(
                      icon: Icon(
                        message.isBookmarked ? Icons.bookmark : Icons.bookmark_border,
                        size: 16,
                      ),
                      onPressed: onBookmark,
                      visualDensity: VisualDensity.compact,
                      tooltip: 'Bookmark',
                    ),
                    IconButton(
                      icon: const Icon(Icons.copy, size: 16),
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: message.content));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Copied to clipboard'), duration: Duration(seconds: 1)),
                        );
                      },
                      visualDensity: VisualDensity.compact,
                      tooltip: 'Copy',
                    ),
                  ],
                ),
            ],
          ),
        ),
      ],
    );
  }

  IconData _approachIcon(TeachingApproach approach) {
    switch (approach) {
      case TeachingApproach.socratic:
        return Icons.help_outline;
      case TeachingApproach.explanation:
        return Icons.menu_book;
      case TeachingApproach.problemSolving:
        return Icons.build;
      case TeachingApproach.encouragement:
        return Icons.favorite;
      case TeachingApproach.correction:
        return Icons.edit;
    }
  }
}

class _TypingIndicator extends StatelessWidget {
  const _TypingIndicator();

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Row(
      children: [
        CircleAvatar(
          radius: 14,
          backgroundColor: colorScheme.primaryContainer,
          child: Icon(Icons.smart_toy, size: 16, color: colorScheme.primary),
        ),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2, color: colorScheme.primary),
              ),
              const SizedBox(width: 8),
              Text('Thinking...', style: TextStyle(color: colorScheme.outline, fontSize: 13)),
            ],
          ),
        ),
      ],
    );
  }
}

// =============================================================================
// Subject Data (kept from original)
// =============================================================================

class _TutorSubject {
  const _TutorSubject({
    required this.name,
    required this.description,
    required this.icon,
    required this.color,
  });

  final String name;
  final String description;
  final IconData icon;
  final Color color;
}

const _subjects = <_TutorSubject>[
  _TutorSubject(
    name: 'Mathematics',
    description: 'Algebra, Geometry, Calculus, and more',
    icon: Icons.calculate,
    color: Color(0xFF2196F3),
  ),
  _TutorSubject(
    name: 'Science',
    description: 'Physics, Chemistry, Biology',
    icon: Icons.science,
    color: Color(0xFF4CAF50),
  ),
  _TutorSubject(
    name: 'English',
    description: 'Grammar, Writing, Literature',
    icon: Icons.menu_book,
    color: Color(0xFFFF9800),
  ),
  _TutorSubject(
    name: 'History',
    description: 'World History, Ancient civilizations, Modern history',
    icon: Icons.history_edu,
    color: Color(0xFF9C27B0),
  ),
  _TutorSubject(
    name: 'Geography',
    description: 'Physical geography, Human geography, Maps',
    icon: Icons.public,
    color: Color(0xFF00BCD4),
  ),
  _TutorSubject(
    name: 'Economics',
    description: 'Microeconomics, Macroeconomics, Markets',
    icon: Icons.trending_up,
    color: Color(0xFF795548),
  ),
  _TutorSubject(
    name: 'Computer Science',
    description: 'Programming, Algorithms, Data Structures',
    icon: Icons.computer,
    color: Color(0xFF607D8B),
  ),
];
