import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/preferences/app_settings_store.dart';
import '../../data/local/preferences/quiz_preferences_store.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'settings_providers.dart';

class SettingsPage extends ConsumerStatefulWidget {
  const SettingsPage({
    this.quizStore,
    super.key,
  });

  final QuizPreferencesStore? quizStore;

  @override
  ConsumerState<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends ConsumerState<SettingsPage> {
  late final QuizPreferencesStore _quizStore;

  final _geminiApiKeyController = TextEditingController();
  final _openAiApiKeyController = TextEditingController();
  final _speechApiKeyController = TextEditingController();
  final _mapsApiKeyController = TextEditingController();

  QuizPreferences _quizPreferences = const QuizPreferences();

  bool _loading = true;
  bool _apiKeysInitialized = false;

  @override
  void initState() {
    super.initState();
    _quizStore = widget.quizStore ??
        QuizPreferencesStore(SharedPreferencesStore(SharedPreferencesAsync()));
    _loadQuizPreferences();
  }

  @override
  void dispose() {
    _geminiApiKeyController.dispose();
    _openAiApiKeyController.dispose();
    _speechApiKeyController.dispose();
    _mapsApiKeyController.dispose();
    super.dispose();
  }

  Future<void> _loadQuizPreferences() async {
    final loadedQuiz = await _quizStore.read();

    if (!mounted) return;

    setState(() {
      _quizPreferences = loadedQuiz;
      _loading = false;
    });
  }

  void _syncApiKeyControllers(AppSettings settings) {
    if (!_apiKeysInitialized) {
      _geminiApiKeyController.text = settings.geminiApiKey;
      _openAiApiKeyController.text = settings.openAiApiKey;
      _speechApiKeyController.text = settings.googleCloudSpeechApiKey;
      _mapsApiKeyController.text = settings.googleMapsApiKey;
      _apiKeysInitialized = true;
    }
  }

  void _updateAppSettings(AppSettings Function(AppSettings) updater) {
    ref.read(appSettingsProvider.notifier).update(updater);
  }

  Future<void> _saveQuizPreferences(QuizPreferences prefs) async {
    setState(() => _quizPreferences = prefs);
    await _quizStore.write(prefs);
  }

  void _saveApiKey({String? gemini, String? openAi, String? speech, String? maps}) {
    _updateAppSettings((s) => s.copyWith(
          geminiApiKey: gemini,
          openAiApiKey: openAi,
          googleCloudSpeechApiKey: speech,
          googleMapsApiKey: maps,
        ));
  }

  @override
  Widget build(BuildContext context) {
    final appSettings = ref.watch(appSettingsProvider);
    _syncApiKeyControllers(appSettings);

    if (_loading) {
      return const SafeArea(child: Center(child: CircularProgressIndicator()));
    }

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Settings', style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 8),
          Text(
            'Configure AI providers, quiz behavior, and accessibility.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 20),
          _SettingsCard(
            title: 'AI Configuration',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Online AI Provider',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: SegmentedButton<OnlineModelProvider>(
                    segments: const [
                      ButtonSegment(
                        value: OnlineModelProvider.gemini,
                        label: Text('Gemini'),
                        icon: Icon(Icons.auto_awesome),
                      ),
                      ButtonSegment(
                        value: OnlineModelProvider.openai,
                        label: Text('OpenAI'),
                        icon: Icon(Icons.psychology),
                      ),
                    ],
                    selected: {appSettings.onlineModelProvider},
                    onSelectionChanged: (selected) {
                      _updateAppSettings(
                        (s) => s.copyWith(onlineModelProvider: selected.first),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 16),
                if (appSettings.onlineModelProvider == OnlineModelProvider.gemini)
                  TextField(
                    controller: _geminiApiKeyController,
                    decoration: const InputDecoration(
                      labelText: 'Gemini API Key',
                      border: OutlineInputBorder(),
                      hintText: 'Enter key used by Gemini online provider',
                    ),
                    onChanged: (value) => _saveApiKey(gemini: value.trim()),
                  )
                else
                  TextField(
                    controller: _openAiApiKeyController,
                    decoration: const InputDecoration(
                      labelText: 'OpenAI API Key',
                      border: OutlineInputBorder(),
                      hintText: 'sk-... key for OpenAI online provider',
                    ),
                    onChanged: (value) => _saveApiKey(openAi: value.trim()),
                  ),
                const SizedBox(height: 16),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Prefer on-device model'),
                  subtitle: const Text(
                    'Use Gemma first, fallback to selected online provider when needed',
                  ),
                  value: appSettings.enableOfflineModel,
                  onChanged: (value) {
                    _updateAppSettings(
                      (s) => s.copyWith(enableOfflineModel: value),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _SettingsCard(
            title: 'Google Cloud Integrations',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: _speechApiKeyController,
                  decoration: const InputDecoration(
                    labelText: 'Google Cloud Speech API Key',
                    border: OutlineInputBorder(),
                    hintText: 'Used by Live Caption, AI Tutor voice, and CBT Coach',
                  ),
                  onChanged: (value) => _saveApiKey(speech: value.trim()),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _mapsApiKeyController,
                  decoration: const InputDecoration(
                    labelText: 'Google Maps API Key (Optional)',
                    border: OutlineInputBorder(),
                    hintText: 'Required for crisis map / nearby services lookup',
                  ),
                  onChanged: (value) => _saveApiKey(maps: value.trim()),
                ),
                const SizedBox(height: 12),
                Text(
                  'Speech integration powers voice-first features and Maps integration powers location-based crisis support.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _SettingsCard(
            title: 'Display & Accessibility',
            child: Column(
              children: [
                DropdownButtonFormField<TextSize>(
                  value: _quizPreferences.textSize,
                  decoration: const InputDecoration(
                    labelText: 'Text size',
                    border: OutlineInputBorder(),
                  ),
                  items: TextSize.values
                      .map((value) => DropdownMenuItem(
                            value: value,
                            child: Text(_textSizeLabel(value)),
                          ))
                      .toList(growable: false),
                  onChanged: (value) {
                    if (value == null) return;
                    _saveQuizPreferences(
                      _quizPreferences.copyWith(textSize: value),
                    );
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('High contrast mode'),
                  subtitle: const Text('Increase contrast for readability'),
                  value: _quizPreferences.highContrastMode,
                  onChanged: (value) {
                    _saveQuizPreferences(
                      _quizPreferences.copyWith(highContrastMode: value),
                    );
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Animations enabled'),
                  subtitle: const Text('Turn off to reduce motion'),
                  value: _quizPreferences.animationsEnabled,
                  onChanged: (value) {
                    _saveQuizPreferences(
                      _quizPreferences.copyWith(animationsEnabled: value),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _SettingsCard(
            title: 'Quiz Behavior',
            child: Column(
              children: [
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Show hints'),
                  value: _quizPreferences.showHints,
                  onChanged: (value) {
                    _saveQuizPreferences(
                      _quizPreferences.copyWith(showHints: value),
                    );
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Auto-advance questions'),
                  value: _quizPreferences.autoAdvanceQuestions,
                  onChanged: (value) {
                    _saveQuizPreferences(
                      _quizPreferences.copyWith(autoAdvanceQuestions: value),
                    );
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Show explanations immediately'),
                  value: _quizPreferences.showExplanationsImmediately,
                  onChanged: (value) {
                    _saveQuizPreferences(
                      _quizPreferences.copyWith(
                        showExplanationsImmediately: value,
                      ),
                    );
                  },
                ),
                DropdownButtonFormField<QuestionTimeLimit>(
                  value: _quizPreferences.questionTimeLimit,
                  decoration: const InputDecoration(
                    labelText: 'Time limit per question',
                    border: OutlineInputBorder(),
                  ),
                  items: QuestionTimeLimit.values
                      .map(
                        (value) => DropdownMenuItem(
                          value: value,
                          child: Text(_timeLimitLabel(value)),
                        ),
                      )
                      .toList(growable: false),
                  onChanged: (value) {
                    if (value == null) return;
                    _saveQuizPreferences(
                      _quizPreferences.copyWith(questionTimeLimit: value),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }

  String _textSizeLabel(TextSize size) => switch (size) {
        TextSize.small => 'Small',
        TextSize.medium => 'Medium',
        TextSize.large => 'Large',
        TextSize.extraLarge => 'Extra Large',
      };

  String _timeLimitLabel(QuestionTimeLimit limit) => switch (limit) {
        QuestionTimeLimit.none => 'No limit',
        QuestionTimeLimit.thirtySeconds => '30 seconds',
        QuestionTimeLimit.oneMinute => '1 minute',
        QuestionTimeLimit.twoMinutes => '2 minutes',
        QuestionTimeLimit.fiveMinutes => '5 minutes',
      };
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}
