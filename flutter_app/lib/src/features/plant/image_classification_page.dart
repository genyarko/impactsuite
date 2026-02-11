import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../ai/ai_providers.dart';
import 'image_classification_controller.dart';

final _imageClassificationControllerProvider =
    ChangeNotifierProvider.autoDispose<ImageClassificationController>((ref) {
      return ImageClassificationController(
        repository: ref.watch(unifiedAiRepositoryProvider),
      );
    });

class ImageClassificationPage extends ConsumerWidget {
  const ImageClassificationPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(_imageClassificationControllerProvider);
    final state = controller.state;

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  state.isOcrMode ? 'OCR Text Scanner' : 'Plant & Image Classification',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
              ),
              IconButton(
                tooltip: state.isOcrMode ? 'Switch to classification' : 'Switch to OCR',
                onPressed: () {
                  ref.read(_imageClassificationControllerProvider).toggleOcrMode();
                },
                icon: Icon(state.isOcrMode ? Icons.grass : Icons.text_fields),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            state.isOcrMode
                ? 'Pick an image to extract printed or handwritten text.'
                : 'Pick an image to classify plants, food, or household objects. Results include confidence and actionable guidance.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        state.isOcrMode ? Icons.text_fields : Icons.photo_camera,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          state.selectedImageName ?? 'No image selected',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: state.isLoading
                        ? null
                        : () {
                            ref.read(_imageClassificationControllerProvider).pickAndAnalyzeImage();
                          },
                    icon: state.isLoading
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.search),
                    label: Text(
                      state.isLoading
                          ? 'Analyzing...'
                          : state.isOcrMode
                              ? 'Select image and extract text'
                              : 'Select image and classify',
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (state.error != null) ...[
            const SizedBox(height: 12),
            Card(
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    Icon(Icons.error, color: Theme.of(context).colorScheme.error),
                    const SizedBox(width: 8),
                    Expanded(child: Text(state.error!)),
                  ],
                ),
              ),
            ),
          ],
          if (state.isOcrMode && state.extractedText != null) ...[
            const SizedBox(height: 12),
            _OcrResultCard(text: state.extractedText!),
          ],
          if (!state.isOcrMode && state.result != null) ...[
            const SizedBox(height: 12),
            _ResultCard(result: state.result!),
          ],
        ],
      ),
    );
  }
}

class _ResultCard extends StatelessWidget {
  const _ResultCard({required this.result});

  final ImageClassificationResult result;

  @override
  Widget build(BuildContext context) {
    final confidencePercent = (result.diagnosis.confidence * 100).toStringAsFixed(1);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.grass, color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    result.diagnosis.label,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                Chip(label: Text('$confidencePercent%')),
              ],
            ),
            const SizedBox(height: 12),
            Text(result.description),
            const SizedBox(height: 8),
            Text(
              result.explanation,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Recommended actions',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            const SizedBox(height: 6),
            for (final action in result.recommendedActions)
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('• '),
                    Expanded(child: Text(action)),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _OcrResultCard extends StatelessWidget {
  const _OcrResultCard({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.text_fields, color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 8),
                Text('Extracted Text', style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: 10),
            SelectableText(text),
          ],
        ),
      ),
    );
  }
}
