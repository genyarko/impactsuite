import 'package:camera/camera.dart';
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

class ImageClassificationPage extends ConsumerStatefulWidget {
  const ImageClassificationPage({super.key});

  @override
  ConsumerState<ImageClassificationPage> createState() =>
      _ImageClassificationPageState();
}

class _ImageClassificationPageState
    extends ConsumerState<ImageClassificationPage> with WidgetsBindingObserver {
  CameraController? _cameraController;
  bool _isCameraInitialized = false;
  String? _cameraError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initializeCamera();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _cameraController?.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final controller = _cameraController;
    if (controller == null || !controller.value.isInitialized) return;

    if (state == AppLifecycleState.inactive) {
      controller.dispose();
      _cameraController = null;
      setState(() {
        _isCameraInitialized = false;
      });
    } else if (state == AppLifecycleState.resumed) {
      _initializeCamera();
    }
  }

  Future<void> _initializeCamera() async {
    try {
      final cameras = await availableCameras();
      if (cameras.isEmpty) {
        setState(() {
          _cameraError = 'No cameras available on this device.';
        });
        return;
      }

      final camera = cameras.first;
      final controller = CameraController(
        camera,
        ResolutionPreset.medium,
        enableAudio: false,
      );

      _cameraController = controller;
      await controller.initialize();

      if (!mounted) return;
      setState(() {
        _isCameraInitialized = true;
        _cameraError = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _cameraError = 'Camera initialization failed. You can still upload images.';
      });
    }
  }

  Future<void> _captureAndClassify() async {
    final controller = _cameraController;
    if (controller == null || !controller.value.isInitialized) return;

    final classificationController =
        ref.read(_imageClassificationControllerProvider);
    if (classificationController.state.isLoading) return;

    try {
      final xFile = await controller.takePicture();
      final bytes = await xFile.readAsBytes();
      final name = xFile.name;

      if (classificationController.state.isOcrMode) {
        await classificationController.analyzeImageForOcr(
          bytes,
          imageName: name,
        );
      } else {
        await classificationController.analyzeImageBytes(
          bytes,
          imageName: name,
        );
      }
    } catch (_) {
      classificationController.setError('Failed to capture photo.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(_imageClassificationControllerProvider);
    final state = controller.state;

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Header row with title and OCR toggle
          Row(
            children: [
              Expanded(
                child: Text(
                  state.isOcrMode
                      ? 'OCR Text Scanner'
                      : 'Plant & Image Classification',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
              ),
              IconButton(
                tooltip: state.isOcrMode
                    ? 'Switch to classification'
                    : 'Switch to OCR',
                onPressed: () {
                  ref
                      .read(_imageClassificationControllerProvider)
                      .toggleOcrMode();
                },
                icon: Icon(
                    state.isOcrMode ? Icons.grass : Icons.text_fields),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            state.isOcrMode
                ? 'Capture or pick an image to extract printed or handwritten text.'
                : 'Capture or pick an image to classify plants, food, or household objects.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),

          // Camera preview card
          Card(
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: [
                // Camera preview or fallback
                AspectRatio(
                  aspectRatio: _isCameraInitialized
                      ? _cameraController!.value.aspectRatio
                      : 4 / 3,
                  child: _isCameraInitialized
                      ? CameraPreview(_cameraController!)
                      : Container(
                          color: Colors.black87,
                          child: Center(
                            child: _cameraError != null
                                ? Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: Column(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        const Icon(Icons.no_photography,
                                            size: 48,
                                            color: Colors.white54),
                                        const SizedBox(height: 8),
                                        Text(
                                          _cameraError!,
                                          style: const TextStyle(
                                              color: Colors.white70),
                                          textAlign: TextAlign.center,
                                        ),
                                      ],
                                    ),
                                  )
                                : const CircularProgressIndicator(
                                    color: Colors.white),
                          ),
                        ),
                ),

                // Action buttons
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      // Capture button
                      Expanded(
                        child: FilledButton.icon(
                          onPressed: (state.isLoading || !_isCameraInitialized)
                              ? null
                              : _captureAndClassify,
                          icon: state.isLoading
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2),
                                )
                              : const Icon(Icons.camera_alt),
                          label: Text(state.isLoading
                              ? 'Analyzing...'
                              : 'Capture'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      // Upload button
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: state.isLoading
                              ? null
                              : () {
                                  ref
                                      .read(
                                          _imageClassificationControllerProvider)
                                      .pickAndAnalyzeImage();
                                },
                          icon: const Icon(Icons.upload_file),
                          label: const Text('Upload'),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Selected image name
          if (state.selectedImageName != null) ...[
            const SizedBox(height: 8),
            Row(
              children: [
                Icon(
                  state.isOcrMode ? Icons.text_fields : Icons.photo_camera,
                  size: 16,
                  color: Theme.of(context).colorScheme.primary,
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    state.selectedImageName!,
                    style: Theme.of(context).textTheme.bodySmall,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ],

          // Error card
          if (state.error != null) ...[
            const SizedBox(height: 12),
            Card(
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    Icon(Icons.error,
                        color: Theme.of(context).colorScheme.error),
                    const SizedBox(width: 8),
                    Expanded(child: Text(state.error!)),
                  ],
                ),
              ),
            ),
          ],

          // OCR result
          if (state.isOcrMode && state.extractedText != null) ...[
            const SizedBox(height: 12),
            _OcrResultCard(text: state.extractedText!),
          ],

          // Classification result
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
    final confidencePercent =
        (result.diagnosis.confidence * 100).toStringAsFixed(1);
    final icon = result.isFood ? Icons.restaurant : Icons.grass;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, color: Theme.of(context).colorScheme.primary),
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

            // Food items breakdown
            if (result.isFood && result.foodItems.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(
                'Detected food items',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              for (final item in result.foodItems)
                Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Row(
                    children: [
                      Expanded(
                        flex: 3,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(item.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                            if (item.estimatedServing.isNotEmpty)
                              Text(item.estimatedServing, style: Theme.of(context).textTheme.bodySmall),
                          ],
                        ),
                      ),
                      Expanded(
                        flex: 4,
                        child: Text(
                          '${item.calories} cal  P: ${item.proteinGrams.toStringAsFixed(1)}g  C: ${item.carbsGrams.toStringAsFixed(1)}g  F: ${item.fatGrams.toStringAsFixed(1)}g',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ),
                    ],
                  ),
                ),
            ],

            // Total nutrition summary
            if (result.isFood && result.totalNutrition != null) ...[
              const SizedBox(height: 12),
              const Divider(),
              const SizedBox(height: 8),
              Text(
                'Total nutrition estimate',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              _NutritionGrid(nutrition: result.totalNutrition!),
            ],

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

class _NutritionGrid extends StatelessWidget {
  const _NutritionGrid({required this.nutrition});

  final NutritionSummary nutrition;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 12,
      runSpacing: 8,
      children: [
        _NutritionChip(label: 'Calories', value: '${nutrition.calories}', unit: 'kcal', color: Colors.orange),
        _NutritionChip(label: 'Protein', value: nutrition.proteinGrams.toStringAsFixed(1), unit: 'g', color: Colors.red),
        _NutritionChip(label: 'Carbs', value: nutrition.carbsGrams.toStringAsFixed(1), unit: 'g', color: Colors.blue),
        _NutritionChip(label: 'Fat', value: nutrition.fatGrams.toStringAsFixed(1), unit: 'g', color: Colors.amber),
        if (nutrition.fiberGrams > 0)
          _NutritionChip(label: 'Fiber', value: nutrition.fiberGrams.toStringAsFixed(1), unit: 'g', color: Colors.green),
        if (nutrition.sugarGrams > 0)
          _NutritionChip(label: 'Sugar', value: nutrition.sugarGrams.toStringAsFixed(1), unit: 'g', color: Colors.pink),
      ],
    );
  }
}

class _NutritionChip extends StatelessWidget {
  const _NutritionChip({
    required this.label,
    required this.value,
    required this.unit,
    required this.color,
  });

  final String label;
  final String value;
  final String unit;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: color.withValues(alpha: 0.12),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '$value $unit',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
          Text(label, style: Theme.of(context).textTheme.bodySmall),
        ],
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
                Icon(Icons.text_fields,
                    color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 8),
                Text('Extracted Text',
                    style: Theme.of(context).textTheme.titleMedium),
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
