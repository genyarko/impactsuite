import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/services/speech_recognition_service.dart';
import '../ai/ai_providers.dart';
import 'live_caption_controller.dart';

final _liveCaptionControllerProvider =
    StateNotifierProvider.autoDispose<LiveCaptionController, LiveCaptionState>((ref) {
      return LiveCaptionController(repository: ref.watch(unifiedAiRepositoryProvider));
    });

class LiveCaptionPage extends ConsumerStatefulWidget {
  const LiveCaptionPage({super.key});

  @override
  ConsumerState<LiveCaptionPage> createState() => _LiveCaptionPageState();
}

class _LiveCaptionPageState extends ConsumerState<LiveCaptionPage> {
  final _textController = TextEditingController();
  final _scrollController = ScrollController();
  final SpeechRecognitionService _speechService = SpeechRecognitionService();
  int _lastHistoryCount = 0;

  @override
  void dispose() {
    _speechService.dispose();
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _startVoiceCapture() async {
    final controller = ref.read(_liveCaptionControllerProvider.notifier);
    final state = ref.read(_liveCaptionControllerProvider);

    final available = await _speechService.init();
    if (!available) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Speech recognition not available on this device.')),
        );
      }
      return;
    }

    controller.startLiveCaption();

    // Map source language code to speech locale
    final localeId = state.sourceLanguage == CaptionLanguage.auto
        ? 'en_US'
        : state.sourceLanguage.code;

    _beginListening(localeId);
  }

  void _beginListening(String localeId) {
    _speechService.startListening(
      localeId: localeId,
      onResult: (text, isFinal) {
        if (isFinal && text.trim().isNotEmpty) {
          ref.read(_liveCaptionControllerProvider.notifier).submitVoiceTranscript(text);
          // Restart listening for continuous mode
          if (ref.read(_liveCaptionControllerProvider).isListening) {
            Future.delayed(const Duration(milliseconds: 200), () {
              if (mounted && ref.read(_liveCaptionControllerProvider).isListening) {
                _beginListening(localeId);
              }
            });
          }
        }
      },
      onError: (error) {
        if (mounted) {
          // Auto-restart on transient errors while still in listening mode
          final state = ref.read(_liveCaptionControllerProvider);
          if (state.isListening && error != 'error_permission') {
            Future.delayed(const Duration(milliseconds: 500), () {
              if (mounted && ref.read(_liveCaptionControllerProvider).isListening) {
                _beginListening(localeId);
              }
            });
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('Speech error: $error')),
            );
          }
        }
      },
      onStatus: (status) {
        // When speech engine finishes (e.g. silence timeout), restart if still active
        if (status == 'done' || status == 'notListening') {
          if (mounted && ref.read(_liveCaptionControllerProvider).isListening) {
            Future.delayed(const Duration(milliseconds: 200), () {
              if (mounted && ref.read(_liveCaptionControllerProvider).isListening) {
                final state = ref.read(_liveCaptionControllerProvider);
                final locale = state.sourceLanguage == CaptionLanguage.auto
                    ? 'en_US'
                    : state.sourceLanguage.code;
                _beginListening(locale);
              }
            });
          }
        }
      },
    );
  }

  Future<void> _stopVoiceCapture() async {
    await _speechService.stopListening();
    ref.read(_liveCaptionControllerProvider.notifier).stopLiveCaption();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(_liveCaptionControllerProvider);
    if (state.transcriptHistory.length != _lastHistoryCount) {
      _lastHistoryCount = state.transcriptHistory.length;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            _scrollController.position.maxScrollExtent + 120,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOut,
          );
        }
      });
    }

    return Container(
      color: Colors.black.withValues(alpha: 0.82),
      child: Stack(
        children: [
          Column(
            children: [
              const SizedBox(height: 10),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(
                  children: [
                    Expanded(
                      child: _LanguageSelector(
                        label: 'From',
                        selected: state.sourceLanguage,
                        onSelected: ref.read(_liveCaptionControllerProvider.notifier).setSourceLanguage,
                      ),
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 8),
                      child: Icon(Icons.arrow_forward, color: Colors.white),
                    ),
                    Expanded(
                      child: _LanguageSelector(
                        label: 'To',
                        selected: state.targetLanguage,
                        onSelected:
                            (value) =>
                                ref.read(_liveCaptionControllerProvider.notifier).setTargetLanguage(value),
                      ),
                    ),
                  ],
                ),
              ),
              if (state.isListening)
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      state.isUsingOnlineService ? Icons.cloud : Icons.computer,
                      size: 16,
                      color: state.isUsingOnlineService ? Colors.greenAccent : Colors.cyanAccent,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      state.isUsingOnlineService ? 'Online Mode' : 'Offline Mode',
                      style: const TextStyle(color: Colors.white70, fontSize: 12),
                    ),
                  ],
                ),
              Expanded(
                child: ListView.separated(
                  controller: _scrollController,
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 160),
                  itemCount:
                      state.transcriptHistory.isEmpty && !state.isListening
                          ? 1
                          : state.transcriptHistory.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    if (state.transcriptHistory.isEmpty && !state.isListening) {
                      return Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Text(
                            'Tap mic to start live caption',
                            style: Theme.of(context).textTheme.bodyLarge,
                          ),
                        ),
                      );
                    }
                    return _TranscriptItem(entry: state.transcriptHistory[index], state: state);
                  },
                ),
              ),
              if (state.error != null)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Card(
                    color: Theme.of(context).colorScheme.errorContainer,
                    child: Padding(
                      padding: const EdgeInsets.all(8),
                      child: Text(state.error!),
                    ),
                  ),
                ),
            ],
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 86,
            child: Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _textController,
                        textInputAction: TextInputAction.send,
                        onChanged: (_) => setState(() {}),
                        onSubmitted: (_) => _submitText(),
                        decoration: const InputDecoration(
                          hintText: 'Type to translate...',
                          border: InputBorder.none,
                        ),
                      ),
                    ),
                    IconButton(
                      onPressed: _textController.text.trim().isEmpty ? null : _submitText,
                      icon: const Icon(Icons.send),
                    ),
                  ],
                ),
              ),
            ),
          ),
          Positioned(
            bottom: 16,
            right: 16,
            child: FloatingActionButton(
              backgroundColor:
                  state.isListening
                      ? Theme.of(context).colorScheme.error
                      : Theme.of(context).colorScheme.primary,
              onPressed: () {
                if (state.isListening) {
                  _stopVoiceCapture();
                } else {
                  _startVoiceCapture();
                }
              },
              child: Icon(state.isListening ? Icons.mic_off : Icons.mic),
            ),
          ),
          if (state.isListening)
            Positioned(
              top: 72,
              right: 16,
              child: Card(
                color: Theme.of(context).colorScheme.error,
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  child: Text('● LIVE', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                ),
              ),
            ),
        ],
      ),
    );
  }

  void _submitText() {
    final text = _textController.text;
    _textController.clear();
    ref.read(_liveCaptionControllerProvider.notifier).submitTextInput(text);
    setState(() {});
  }
}

class _TranscriptItem extends StatelessWidget {
  const _TranscriptItem({required this.entry, required this.state});

  final TranscriptEntry entry;
  final LiveCaptionState state;

  @override
  Widget build(BuildContext context) {
    final time = TimeOfDay.fromDateTime(entry.timestamp).format(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Icon(entry.source == TranscriptSource.voice ? Icons.mic : Icons.keyboard, size: 16),
                        const SizedBox(width: 8),
                        Text(state.sourceLanguage.displayName),
                      ],
                    ),
                    Text(time, style: Theme.of(context).textTheme.labelSmall),
                  ],
                ),
                const SizedBox(height: 4),
                Text(entry.transcript),
              ],
            ),
          ),
        ),
        if (entry.translation != null && state.sourceLanguage != state.targetLanguage)
          Padding(
            padding: const EdgeInsets.only(left: 24),
            child: Card(
              color: Theme.of(context).colorScheme.primary,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      state.targetLanguage.displayName,
                      style: TextStyle(color: Theme.of(context).colorScheme.onPrimary.withValues(alpha: 0.8)),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      entry.translation!,
                      style: TextStyle(color: Theme.of(context).colorScheme.onPrimary),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _LanguageSelector extends StatelessWidget {
  const _LanguageSelector({required this.label, required this.selected, required this.onSelected});

  final String label;
  final CaptionLanguage selected;
  final ValueChanged<CaptionLanguage> onSelected;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.secondaryContainer.withValues(alpha: 0.92),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () async {
          final selectedLanguage = await showDialog<CaptionLanguage>(
            context: context,
            builder: (_) => _LanguagePickerDialog(currentLanguage: selected),
          );
          if (selectedLanguage != null) {
            onSelected(selectedLanguage);
          }
        },
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: Theme.of(context).textTheme.labelSmall),
                  Text(selected.displayName, overflow: TextOverflow.ellipsis),
                ],
              ),
              const Icon(Icons.arrow_drop_down),
            ],
          ),
        ),
      ),
    );
  }
}

class _LanguagePickerDialog extends StatefulWidget {
  const _LanguagePickerDialog({required this.currentLanguage});

  final CaptionLanguage currentLanguage;

  @override
  State<_LanguagePickerDialog> createState() => _LanguagePickerDialogState();
}

class _LanguagePickerDialogState extends State<_LanguagePickerDialog> {
  String _searchQuery = '';

  @override
  Widget build(BuildContext context) {
    final filtered =
        _searchQuery.isEmpty
            ? CaptionLanguage.all
            : CaptionLanguage.all.where((lang) {
              final query = _searchQuery.toLowerCase();
              return lang.displayName.toLowerCase().contains(query) || lang.code.toLowerCase().contains(query);
            }).toList();

    return Dialog(
      child: SizedBox(
        width: 520,
        height: MediaQuery.of(context).size.height * 0.8,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('Select Language (${CaptionLanguage.all.length} languages)'),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: TextField(
                decoration: InputDecoration(
                  hintText: 'Search languages...',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon:
                      _searchQuery.isEmpty
                          ? null
                          : IconButton(
                            onPressed: () => setState(() => _searchQuery = ''),
                            icon: const Icon(Icons.clear),
                          ),
                ),
                onChanged: (value) => setState(() => _searchQuery = value),
              ),
            ),
            const Divider(),
            Expanded(
              child: ListView(
                children: [
                  if (_searchQuery.isEmpty) ...[
                    const Padding(
                      padding: EdgeInsets.fromLTRB(16, 8, 16, 4),
                      child: Text('Popular Languages'),
                    ),
                    ...CaptionLanguage.popular.map((language) {
                      return _LanguageItem(
                        language: language,
                        selected: widget.currentLanguage == language,
                        onTap: () => Navigator.of(context).pop(language),
                      );
                    }),
                    const Divider(),
                    const Padding(
                      padding: EdgeInsets.fromLTRB(16, 8, 16, 4),
                      child: Text('All Languages'),
                    ),
                  ],
                  ...filtered.map(
                    (language) => _LanguageItem(
                      language: language,
                      selected: widget.currentLanguage == language,
                      onTap: () => Navigator.of(context).pop(language),
                    ),
                  ),
                  if (filtered.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(16),
                      child: Text('No languages found'),
                    ),
                ],
              ),
            ),
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.all(8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('${filtered.length} languages'),
                  TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LanguageItem extends StatelessWidget {
  const _LanguageItem({required this.language, required this.selected, required this.onTap});

  final CaptionLanguage language;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        color:
            selected
                ? Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.3)
                : Colors.transparent,
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(language.displayName),
                  Text(language.code, style: Theme.of(context).textTheme.labelSmall),
                ],
              ),
            ),
            if (selected)
              Text(
                '✓',
                style: TextStyle(color: Theme.of(context).colorScheme.primary),
              ),
          ],
        ),
      ),
    );
  }
}
