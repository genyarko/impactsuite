import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../ai/ai_providers.dart';
import 'summarizer_controller.dart';

final _summarizerControllerProvider =
    StateNotifierProvider.autoDispose<SummarizerController, SummarizerState>((ref) {
      return SummarizerController(repository: ref.watch(unifiedAiRepositoryProvider));
    });

class SummarizerPage extends ConsumerStatefulWidget {
  const SummarizerPage({super.key});

  @override
  ConsumerState<SummarizerPage> createState() => _SummarizerPageState();
}

class _SummarizerPageState extends ConsumerState<SummarizerPage> {
  @override
  Widget build(BuildContext context) {
    final state = ref.watch(_summarizerControllerProvider);

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _UploadCard(
            fileName: state.fileName,
            onPickFile: state.isLoading ? null : _pickFile,
            onPasteText: state.isLoading ? null : () => _openTextInputDialog(context),
          ),
          if (state.isLoading) ...[
            const SizedBox(height: 16),
            _LoadingCard(progress: state.processingProgress, statusLabel: state.statusLabel),
          ],
          if (state.error != null) ...[
            const SizedBox(height: 16),
            _ErrorCard(error: state.error!),
          ],
          if (state.summary != null) ...[
            const SizedBox(height: 16),
            _SummaryCard(
              summary: state.summary!,
              textLength: state.extractedTextLength,
              onGenerateQuiz: () => _generateQuizFromSummary(state.lastProcessedText ?? state.summary!),
            ),
          ],
          if (state.summary != null || state.error != null) ...[
            const SizedBox(height: 16),
            _ActionButtons(
              hasSummary: state.summary != null,
              isLoading: state.isLoading,
              onRetry: () => ref.read(_summarizerControllerProvider.notifier).retry(),
              onClear: () => ref.read(_summarizerControllerProvider.notifier).clear(),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _pickFile() async {
    final result = await FilePicker.platform.pickFiles(
      allowMultiple: false,
      withData: true,
      type: FileType.custom,
      allowedExtensions: const ['txt', 'docx', 'pdf'],
    );

    final picked = result?.files.single;
    if (picked == null || picked.bytes == null) {
      return;
    }

    await ref
        .read(_summarizerControllerProvider.notifier)
        .processFile(fileName: picked.name, bytes: picked.bytes!);
  }

  void _generateQuizFromSummary(String sourceText) {
    context.push('/quiz', extra: sourceText);
  }

  Future<void> _openTextInputDialog(BuildContext context) async {
    final textController = TextEditingController();

    await showDialog<void>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Paste or Type Text'),
          content: SizedBox(
            width: 500,
            child: TextField(
              controller: textController,
              maxLines: 12,
              decoration: const InputDecoration(
                hintText: 'Paste your document text here...',
                border: OutlineInputBorder(),
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
            FilledButton(
              onPressed: () async {
                Navigator.of(context).pop();
                await ref.read(_summarizerControllerProvider.notifier).processText(textController.text);
              },
              child: const Text('Process'),
            ),
          ],
        );
      },
    );

    textController.dispose();
  }
}

class _UploadCard extends StatelessWidget {
  const _UploadCard({
    required this.fileName,
    required this.onPickFile,
    required this.onPasteText,
  });

  final String? fileName;
  final VoidCallback? onPickFile;
  final VoidCallback? onPasteText;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Card(
      color: scheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            CircleAvatar(
              radius: 32,
              backgroundColor: scheme.primary.withOpacity(0.15),
              child: Icon(
                fileName == null ? Icons.upload : Icons.check_circle,
                color: scheme.primary,
                size: 30,
              ),
            ),
            const SizedBox(height: 12),
            if (fileName != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.insert_drive_file, color: scheme.primary),
                    const SizedBox(width: 8),
                    Flexible(child: Text(fileName!, overflow: TextOverflow.ellipsis)),
                  ],
                ),
              ),
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: onPickFile,
                    icon: const Icon(Icons.folder_open),
                    label: Text(fileName == null ? 'Pick File' : 'Change'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: onPasteText,
                    icon: const Icon(Icons.text_fields),
                    label: const Text('Paste Text'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Supports TXT, DOCX, and PDF files',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}

class _LoadingCard extends StatelessWidget {
  const _LoadingCard({required this.progress, this.statusLabel});

  final double progress;
  final String? statusLabel;

  @override
  Widget build(BuildContext context) {
    String label;
    if (statusLabel != null) {
      label = statusLabel!;
    } else if (progress < 0.3) {
      label = 'Extracting text...';
    } else if (progress < 0.7) {
      label = 'Generating summary...';
    } else if (progress < 0.95) {
      label = 'Getting there...';
    } else {
      label = 'Finalizing...';
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            CircularProgressIndicator(value: progress),
            const SizedBox(height: 12),
            Text(label),
            Text('${(progress * 100).toInt()}%'),
            const SizedBox(height: 8),
            LinearProgressIndicator(value: progress),
          ],
        ),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.error});

  final String error;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Card(
      color: scheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.error, color: scheme.error),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Processing Error', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 4),
                  Text(error),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({
    required this.summary,
    required this.textLength,
    required this.onGenerateQuiz,
  });

  final String summary;
  final int textLength;
  final VoidCallback onGenerateQuiz;

  @override
  Widget build(BuildContext context) {
    final charsText = textLength > 0 ? '${(textLength / 1000).toStringAsFixed(1)}k chars' : null;

    return Card(
      color: Theme.of(context).colorScheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.summarize),
                const SizedBox(width: 8),
                Text('Summary', style: Theme.of(context).textTheme.titleLarge),
                const Spacer(),
                if (charsText != null) Chip(label: Text(charsText)),
              ],
            ),
            const SizedBox(height: 10),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surface,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(summary),
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: onGenerateQuiz,
                icon: const Icon(Icons.quiz),
                label: const Text('Generate Quiz'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ActionButtons extends StatelessWidget {
  const _ActionButtons({
    required this.hasSummary,
    required this.isLoading,
    required this.onRetry,
    required this.onClear,
  });

  final bool hasSummary;
  final bool isLoading;
  final VoidCallback onRetry;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        if (hasSummary)
          Expanded(
            child: FilledButton.icon(
              onPressed: isLoading ? null : onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Regenerate'),
            ),
          ),
        if (hasSummary) const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton.icon(
            onPressed: isLoading ? null : onClear,
            icon: const Icon(Icons.clear),
            label: Text(hasSummary ? 'Clear' : 'Start Over'),
          ),
        ),
      ],
    );
  }
}
