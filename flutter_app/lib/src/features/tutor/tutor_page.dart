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

  _TutorSubject? _selectedSubject;
  bool _showTopics = true;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _sendPrompt(String text) async {
    final trimmed = text.trim();
    if (trimmed.isEmpty) {
      return;
    }

    _controller.clear();
    await ref.read(_tutorControllerProvider.notifier).sendPrompt(trimmed);
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(_tutorControllerProvider);

    return SafeArea(
      child: _selectedSubject == null
          ? _TutorWelcomeScreen(
              onStartSubject: (subject) {
                setState(() {
                  _selectedSubject = subject;
                });
                _sendPrompt(subject.starterPrompt);
              },
            )
          : Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _TutorHeader(
                    subject: _selectedSubject!,
                    showTopics: _showTopics,
                    onToggleTopics: () => setState(() => _showTopics = !_showTopics),
                    onChangeSubject: () => setState(() => _selectedSubject = null),
                  ),
                  if (_showTopics) ...[
                    const SizedBox(height: 8),
                    SizedBox(
                      height: 40,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemBuilder: (context, index) {
                          final topic = _selectedSubject!.sampleTopics[index];
                          return ActionChip(
                            avatar: const Icon(Icons.lightbulb_outline, size: 16),
                            label: Text(topic),
                            onPressed: state.isLoading ? null : () => _sendPrompt('Explain $topic'),
                          );
                        },
                        separatorBuilder: (_, __) => const SizedBox(width: 8),
                        itemCount: _selectedSubject!.sampleTopics.length,
                      ),
                    ),
                  ],
                  const SizedBox(height: 12),
                  Expanded(
                    child: state.messages.isEmpty
                        ? Center(
                            child: Text(
                              'Ask about ${_selectedSubject!.name} to get guided help.',
                              style: Theme.of(context).textTheme.bodyLarge,
                              textAlign: TextAlign.center,
                            ),
                          )
                        : ListView.separated(
                            itemCount: state.messages.length,
                            separatorBuilder: (_, __) => const SizedBox(height: 10),
                            itemBuilder: (context, index) {
                              final message = state.messages[index];
                              return _TutorMessageBubble(message: message);
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
                          textInputAction: TextInputAction.send,
                          onSubmitted: state.isLoading ? null : _sendPrompt,
                          decoration: InputDecoration(
                            hintText: 'Ask your tutor about ${_selectedSubject!.name}...',
                            border: const OutlineInputBorder(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      FilledButton(
                        onPressed: state.isLoading ? null : () => _sendPrompt(_controller.text),
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

class _TutorHeader extends StatelessWidget {
  const _TutorHeader({
    required this.subject,
    required this.showTopics,
    required this.onToggleTopics,
    required this.onChangeSubject,
  });

  final _TutorSubject subject;
  final bool showTopics;
  final VoidCallback onToggleTopics;
  final VoidCallback onChangeSubject;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      color: colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: subject.color.withValues(alpha: 0.2),
              child: Icon(subject.icon, color: subject.color),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('AI Tutor', style: Theme.of(context).textTheme.titleMedium),
                  Text('${subject.name} • ${subject.sessionLabel}'),
                ],
              ),
            ),
            IconButton(
              tooltip: showTopics ? 'Hide suggested topics' : 'Show suggested topics',
              onPressed: onToggleTopics,
              icon: Icon(showTopics ? Icons.topic : Icons.topic_outlined),
            ),
            TextButton(onPressed: onChangeSubject, child: const Text('Change')),
          ],
        ),
      ),
    );
  }
}

class _TutorWelcomeScreen extends StatelessWidget {
  const _TutorWelcomeScreen({required this.onStartSubject});

  final ValueChanged<_TutorSubject> onStartSubject;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                const Icon(Icons.school, size: 42),
                const SizedBox(height: 8),
                Text('Welcome to AI Tutor', style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 6),
                Text(
                  'Pick a subject to start a focused tutoring session.',
                  style: Theme.of(context).textTheme.bodyMedium,
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text('What would you like to work on today?', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        ..._subjects.map((subject) => _SubjectSelectionCard(subject: subject, onStart: onStartSubject)),
      ],
    );
  }
}

class _SubjectSelectionCard extends StatefulWidget {
  const _SubjectSelectionCard({required this.subject, required this.onStart});

  final _TutorSubject subject;
  final ValueChanged<_TutorSubject> onStart;

  @override
  State<_SubjectSelectionCard> createState() => _SubjectSelectionCardState();
}

class _SubjectSelectionCardState extends State<_SubjectSelectionCard> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final subject = widget.subject;

    return Card(
      child: InkWell(
        onTap: () => setState(() => _expanded = !_expanded),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    backgroundColor: subject.color.withValues(alpha: 0.15),
                    child: Icon(subject.icon, color: subject.color),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(subject.name, style: Theme.of(context).textTheme.titleMedium),
                        Text(subject.description),
                      ],
                    ),
                  ),
                  Icon(_expanded ? Icons.keyboard_arrow_up : Icons.arrow_forward_ios, size: 18),
                ],
              ),
              if (_expanded) ...[
                const SizedBox(height: 12),
                Text('Sample Topics', style: Theme.of(context).textTheme.labelLarge),
                const SizedBox(height: 6),
                ...subject.sampleTopics.take(3).map(
                  (topic) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Row(
                      children: [
                        Icon(Icons.circle, size: 7, color: subject.color),
                        const SizedBox(width: 8),
                        Expanded(child: Text(topic)),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                FilledButton.icon(
                  onPressed: () => widget.onStart(subject),
                  icon: const Icon(Icons.play_arrow),
                  label: Text('Start Learning ${subject.name}'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _TutorMessageBubble extends StatelessWidget {
  const _TutorMessageBubble({required this.message});

  final AiConversationMessage message;

  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;
    final colorScheme = Theme.of(context).colorScheme;

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
          child: Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: isUser ? colorScheme.secondaryContainer : colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(message.text),
          ),
        ),
      ],
    );
  }
}

class _TutorSubject {
  const _TutorSubject({
    required this.name,
    required this.description,
    required this.sessionLabel,
    required this.icon,
    required this.color,
    required this.sampleTopics,
    required this.starterPrompt,
  });

  final String name;
  final String description;
  final String sessionLabel;
  final IconData icon;
  final Color color;
  final List<String> sampleTopics;
  final String starterPrompt;
}

const _subjects = <_TutorSubject>[
  _TutorSubject(
    name: 'Mathematics',
    description: 'Algebra, Geometry, Calculus, and more',
    sessionLabel: 'Practice Problems',
    icon: Icons.calculate,
    color: Color(0xFF2196F3),
    sampleTopics: ['Linear equations and functions', 'Geometric shapes and angles', 'Probability and statistics'],
    starterPrompt: 'Let\'s start a mathematics practice session. Give me one warm-up question.',
  ),
  _TutorSubject(
    name: 'Science',
    description: 'Physics, Chemistry, Biology',
    sessionLabel: 'Concept Explanation',
    icon: Icons.science,
    color: Color(0xFF4CAF50),
    sampleTopics: ['Forces and motion', 'Chemical reactions', 'Cell biology and genetics'],
    starterPrompt: 'Let\'s start science concept explanation with a short intro and a question for me.',
  ),
  _TutorSubject(
    name: 'English',
    description: 'Grammar, Writing, Literature',
    sessionLabel: 'Homework Help',
    icon: Icons.menu_book,
    color: Color(0xFFFF9800),
    sampleTopics: ['Grammar and sentence structure', 'Poetry analysis and writing', 'Essay writing techniques'],
    starterPrompt: 'Let\'s do English homework help. Ask me what assignment I am working on.',
  ),
  _TutorSubject(
    name: 'History',
    description: 'World History, Ancient civilizations, Modern history',
    sessionLabel: 'Concept Explanation',
    icon: Icons.history_edu,
    color: Color(0xFF9C27B0),
    sampleTopics: ['Ancient civilizations', 'World wars and conflicts', 'Modern democracy'],
    starterPrompt: 'Let\'s start history tutoring. Give me a brief timeline and quiz me.',
  ),
  _TutorSubject(
    name: 'Geography',
    description: 'Physical geography, Human geography, Maps',
    sessionLabel: 'Concept Explanation',
    icon: Icons.public,
    color: Color(0xFF00BCD4),
    sampleTopics: ['Climate and weather patterns', 'Physical landscapes', 'Population and urbanization'],
    starterPrompt: 'Let\'s start geography tutoring with one key concept and one check question.',
  ),
  _TutorSubject(
    name: 'Economics',
    description: 'Microeconomics, Macroeconomics, Markets',
    sessionLabel: 'Concept Explanation',
    icon: Icons.trending_up,
    color: Color(0xFF795548),
    sampleTopics: ['Supply and demand', 'Market structures', 'International trade'],
    starterPrompt: 'Let\'s start economics tutoring. Explain supply and demand with a simple example.',
  ),
  _TutorSubject(
    name: 'Computer Science',
    description: 'Programming, Algorithms, Data Structures, Web Development',
    sessionLabel: 'Practice Problems',
    icon: Icons.computer,
    color: Color(0xFF607D8B),
    sampleTopics: ['Programming fundamentals', 'Data structures and algorithms', 'Web development basics'],
    starterPrompt: 'Let\'s start computer science practice. Give me one beginner coding challenge.',
  ),
];
