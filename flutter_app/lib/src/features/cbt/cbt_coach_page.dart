import 'package:flutter/material.dart';

import '../../data/local/drift/cbt_drift_store.dart';
import '../../domain/features/cbt/cbt_session_manager.dart';

class CbtCoachPage extends StatefulWidget {
  const CbtCoachPage({super.key});

  @override
  State<CbtCoachPage> createState() => _CbtCoachPageState();
}

class _CbtCoachPageState extends State<CbtCoachPage> {
  final CbtSessionManager _sessionManager = const CbtSessionManager();
  final TextEditingController _thoughtController = TextEditingController();
  final TextEditingController _reframeController = TextEditingController();

  CbtMood _moodBefore = CbtMood.neutral;
  CbtMood _moodAfter = CbtMood.good;
  bool _showTechniqueDetails = true;
  bool _isSaving = false;
  CbtSessionSummary? _latestSummary;

  CbtDriftStore? _store;
  List<CbtSessionRecord> _sessionHistory = const [];

  @override
  void initState() {
    super.initState();
    _openStore();
  }

  @override
  void dispose() {
    _thoughtController.dispose();
    _reframeController.dispose();
    _store?.close();
    super.dispose();
  }

  Future<void> _openStore() async {
    final store = await CbtDriftStore.open();
    await store.migrate();
    final history = await store.listAll();

    if (!mounted) {
      await store.close();
      return;
    }

    setState(() {
      _store = store;
      _sessionHistory = history;
    });
  }

  Future<void> _saveSession() async {
    final thought = _thoughtController.text.trim();
    final reframe = _reframeController.text.trim();

    if (thought.isEmpty || reframe.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please complete both thought fields.')),
      );
      return;
    }

    final thoughtRecord = CbtThoughtRecord(
      automaticThought: thought,
      reframe: reframe,
      moodBefore: _moodBefore,
      moodAfter: _moodAfter,
    );

    final summary = _sessionManager.evaluate(thoughtRecord);

    setState(() => _isSaving = true);
    final now = DateTime.now();
    await _store?.insert(
      CbtSessionRecord(
        id: 'cbt_${now.microsecondsSinceEpoch}',
        moodBefore: _moodBefore.index,
        moodAfter: _moodAfter.index,
        createdAt: now,
      ),
    );

    final history = await _store?.listAll() ?? _sessionHistory;
    if (!mounted) {
      return;
    }

    setState(() {
      _latestSummary = summary;
      _sessionHistory = history;
      _isSaving = false;
      _thoughtController.clear();
      _reframeController.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            elevation: 3,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  CircleAvatar(
                    backgroundColor: colorScheme.primary,
                    child: Icon(Icons.psychology, color: colorScheme.onPrimary),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('CBT Coach', style: Theme.of(context).textTheme.headlineSmall),
                        Text(
                          'Track mood and challenge automatic thoughts.',
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          _MoodSection(
            label: 'How are you feeling now?',
            value: _moodBefore,
            onSelected: (mood) => setState(() => _moodBefore = mood),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Suggested technique', style: Theme.of(context).textTheme.titleMedium),
                      IconButton(
                        onPressed: () => setState(() => _showTechniqueDetails = !_showTechniqueDetails),
                        icon: Icon(_showTechniqueDetails ? Icons.expand_less : Icons.expand_more),
                      ),
                    ],
                  ),
                  Text(
                    'Thought Challenging',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
                  ),
                  if (_showTechniqueDetails) ...[
                    const SizedBox(height: 8),
                    ..._thoughtChallengingSteps
                        .map((step) => Padding(
                              padding: const EdgeInsets.only(bottom: 6),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Padding(
                                    padding: EdgeInsets.only(top: 3),
                                    child: Icon(Icons.check_circle_outline, size: 18),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(child: Text(step)),
                                ],
                              ),
                            ))
                        .toList(),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Thought record', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _thoughtController,
                    maxLines: 2,
                    decoration: const InputDecoration(
                      labelText: 'Automatic thought',
                      hintText: 'Example: I always fail at this.',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _reframeController,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      labelText: 'Balanced reframe',
                      hintText: 'Example: I have failed before, but I can improve with practice.',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 10),
                  _MoodSection(
                    label: 'How do you feel after reframing?',
                    value: _moodAfter,
                    onSelected: (mood) => setState(() => _moodAfter = mood),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: _isSaving ? null : _saveSession,
                    icon: _isSaving
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.save),
                    label: const Text('Save Session'),
                  ),
                ],
              ),
            ),
          ),
          if (_latestSummary != null) ...[
            const SizedBox(height: 12),
            Card(
              color: colorScheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Session insight', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 6),
                    Text('Mood shift: ${_latestSummary!.improvement >= 0 ? '+' : ''}${_latestSummary!.improvement}'),
                    Text(
                      _latestSummary!.hasMeaningfulReframe
                          ? 'Great work — your reframe looks specific and meaningful.'
                          : 'Try adding more detail to your reframe to make it stronger.',
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 12),
          Text('Previous sessions', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          if (_store == null)
            const Center(child: CircularProgressIndicator())
          else if (_sessionHistory.isEmpty)
            const Text('No saved sessions yet.')
          else
            ..._sessionHistory
                .take(10)
                .map(
                  (session) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.history),
                    title: Text(
                      '${_moodLabel(CbtMood.values[session.moodBefore])} → ${_moodLabel(CbtMood.values[session.moodAfter])}',
                    ),
                    subtitle: Text(session.createdAt.toLocal().toString()),
                  ),
                )
                .toList(),
        ],
      ),
    );
  }
}

class _MoodSection extends StatelessWidget {
  const _MoodSection({
    required this.label,
    required this.value,
    required this.onSelected,
  });

  final String label;
  final CbtMood value;
  final ValueChanged<CbtMood> onSelected;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: CbtMood.values
                  .map(
                    (mood) => ChoiceChip(
                      label: Text(_moodLabel(mood)),
                      selected: value == mood,
                      onSelected: (_) => onSelected(mood),
                    ),
                  )
                  .toList(),
            ),
          ],
        ),
      ),
    );
  }
}

String _moodLabel(CbtMood mood) {
  switch (mood) {
    case CbtMood.veryLow:
      return 'Very low';
    case CbtMood.low:
      return 'Low';
    case CbtMood.neutral:
      return 'Neutral';
    case CbtMood.good:
      return 'Good';
    case CbtMood.excellent:
      return 'Excellent';
  }
}

const List<String> _thoughtChallengingSteps = [
  'Identify the negative thought',
  'Examine evidence for and against it',
  'Consider an alternative perspective',
  'Create a balanced replacement thought',
];
