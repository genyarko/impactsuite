import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../data/local/preferences/app_settings_store.dart';
import '../../data/local/preferences/quiz_preferences_store.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({
    this.quizStore,
    this.appSettingsStore,
    super.key,
  });

  final QuizPreferencesStore? quizStore;
  final AppSettingsStore? appSettingsStore;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late final QuizPreferencesStore _quizStore;
  late final AppSettingsStore _appSettingsStore;

  final _geminiApiKeyController = TextEditingController();

  QuizPreferences _quizPreferences = const QuizPreferences();
  AppSettings _appSettings = const AppSettings();

  bool _loading = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final prefs = SharedPreferencesStore(SharedPreferencesAsync());
    _quizStore = widget.quizStore ?? QuizPreferencesStore(prefs);
    _appSettingsStore = widget.appSettingsStore ?? AppSettingsStore(prefs);
    _load();
  }

  @override
  void dispose() {
    _geminiApiKeyController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final loadedQuiz = await _quizStore.read();
    final loadedApp = await _appSettingsStore.read();

    if (!mounted) {
      return;
    }

    setState(() {
      _quizPreferences = loadedQuiz;
      _appSettings = loadedApp;
      _geminiApiKeyController.text = loadedApp.geminiApiKey;
      _loading = false;
    });
  }

  Future<void> _saveAll() async {
    setState(() => _saving = true);

    final appSettings = _appSettings.copyWith(
      geminiApiKey: _geminiApiKeyController.text.trim(),
      lastModelSyncEpochMs: DateTime.now().millisecondsSinceEpoch,
    );

    await _quizStore.write(_quizPreferences);
    await _appSettingsStore.write(appSettings);

    if (!mounted) {
      return;
    }

    setState(() {
      _appSettings = appSettings;
      _saving = false;
    });

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Settings saved')),
    );
  }

  @override
  Widget build(BuildContext context) {
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
            'Ported from the Kotlin settings flow: quiz behavior, accessibility, and AI configuration.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 20),
          _SettingsCard(
            title: 'AI Configuration',
            child: Column(
              children: [
                TextField(
                  controller: _geminiApiKeyController,
                  decoration: const InputDecoration(
                    labelText: 'Gemini API Key',
                    border: OutlineInputBorder(),
                    hintText: 'Enter key used by Gemini online provider',
                  ),
                ),
                const SizedBox(height: 12),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Prefer on-device model'),
                  subtitle: const Text('Use Gemma first, fallback to Gemini when needed'),
                  value: _appSettings.enableOfflineModel,
                  onChanged: (value) {
                    setState(() {
                      _appSettings = _appSettings.copyWith(enableOfflineModel: value);
                    });
                  },
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
                      .map((value) => DropdownMenuItem(value: value, child: Text(_textSizeLabel(value))))
                      .toList(growable: false),
                  onChanged: (value) {
                    if (value == null) {
                      return;
                    }
                    setState(() {
                      _quizPreferences = _quizPreferences.copyWith(textSize: value);
                    });
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('High contrast mode'),
                  subtitle: const Text('Increase contrast for readability'),
                  value: _quizPreferences.highContrastMode,
                  onChanged: (value) {
                    setState(() {
                      _quizPreferences = _quizPreferences.copyWith(highContrastMode: value);
                    });
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Animations enabled'),
                  subtitle: const Text('Turn off to reduce motion'),
                  value: _quizPreferences.animationsEnabled,
                  onChanged: (value) {
                    setState(() {
                      _quizPreferences = _quizPreferences.copyWith(animationsEnabled: value);
                    });
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
                    setState(() {
                      _quizPreferences = _quizPreferences.copyWith(showHints: value);
                    });
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Auto-advance questions'),
                  value: _quizPreferences.autoAdvanceQuestions,
                  onChanged: (value) {
                    setState(() {
                      _quizPreferences = _quizPreferences.copyWith(autoAdvanceQuestions: value);
                    });
                  },
                ),
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Show explanations immediately'),
                  value: _quizPreferences.showExplanationsImmediately,
                  onChanged: (value) {
                    setState(() {
                      _quizPreferences =
                          _quizPreferences.copyWith(showExplanationsImmediately: value);
                    });
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
                    if (value == null) {
                      return;
                    }
                    setState(() {
                      _quizPreferences = _quizPreferences.copyWith(questionTimeLimit: value);
                    });
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _saving ? null : _saveAll,
            icon: _saving
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save),
            label: Text(_saving ? 'Saving...' : 'Save settings'),
          ),
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
