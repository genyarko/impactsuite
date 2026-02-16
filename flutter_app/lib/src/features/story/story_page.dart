import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'story_controller.dart';
import 'story_providers.dart';

class StoryPage extends ConsumerWidget {
  const StoryPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(storyControllerProvider);
    final story = controller.currentStory;

    return Scaffold(
      appBar: AppBar(
        title: Text(controller.showStoryList ? 'Story Mode' : (story?.title ?? 'Reading Story')),
        leading: !controller.showStoryList
            ? IconButton(
                onPressed: controller.backToStoryList,
                icon: const Icon(Icons.arrow_back),
              )
            : null,
        actions: [
          if (controller.showStoryList)
            IconButton(
              onPressed: controller.showStreaks,
              tooltip: 'Reading streaks',
              icon: const Icon(Icons.emoji_events),
            ),
        ],
      ),
      body: Stack(
        children: [
          Positioned.fill(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 250),
              child: _buildBody(controller),
            ),
          ),
          if (controller.badgeNotification case final badge?)
            Positioned(
              top: 24,
              left: 24,
              right: 24,
              child: Card(
                color: Theme.of(context).colorScheme.tertiaryContainer,
                child: ListTile(
                  leading: Text(badge.icon, style: const TextStyle(fontSize: 24)),
                  title: Text('Badge unlocked: ${badge.title}'),
                  trailing: IconButton(
                    onPressed: controller.dismissBadgeNotification,
                    icon: const Icon(Icons.close),
                  ),
                ),
              ),
            ),
          if (controller.error case final error?)
            Positioned(
              left: 16,
              right: 16,
              bottom: 16,
              child: Card(
                color: Theme.of(context).colorScheme.errorContainer,
                child: ListTile(
                  title: Text(error),
                  trailing: TextButton(
                    onPressed: controller.clearError,
                    child: const Text('Dismiss'),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildBody(StoryController controller) {
    if (controller.showStreakScreen) {
      return _ReadingStreakScreen(controller: controller);
    }

    if (controller.isGenerating) {
      return _StoryGenerationProgress(controller: controller);
    }

    if (controller.showStoryList) {
      return _StoryListScreen(controller: controller);
    }

    final story = controller.currentStory;
    if (story == null) {
      return const SizedBox.shrink();
    }

    return _StoryReadingScreen(controller: controller, story: story);
  }
}

class _StoryListScreen extends StatelessWidget {
  const _StoryListScreen({required this.controller});

  final StoryController controller;

  @override
  Widget build(BuildContext context) {
    final stories = controller.allStories;

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (controller.readingStats.currentStreak > 0 ||
              controller.readingStats.unlockedBadges.isNotEmpty)
            _ReadingSummaryCard(controller: controller),
          FilledButton.icon(
            onPressed: () async {
              final request = await showDialog<StoryRequest>(
                context: context,
                builder: (_) => _CreateStoryDialog(controller: controller),
              );
              if (request != null) {
                await controller.generateStory(request);
              }
            },
            icon: const Icon(Icons.add),
            label: const Text('Create New Story'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => _CharacterManagementPage(controller: controller),
                ),
              );
            },
            icon: const Icon(Icons.person),
            label: const Text('Manage Characters'),
          ),
          const SizedBox(height: 16),
          if (controller.isLoading)
            const Center(child: CircularProgressIndicator())
          else if (stories.isEmpty)
            Expanded(
              child: Card(
                child: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.menu_book, size: 64, color: Theme.of(context).colorScheme.primary),
                        const SizedBox(height: 12),
                        Text('No stories yet', style: Theme.of(context).textTheme.headlineSmall),
                        const SizedBox(height: 8),
                        const Text(
                          'Create your first story to get started.',
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            )
          else
            Expanded(
              child: ListView.separated(
                itemCount: stories.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (context, index) {
                  final story = stories[index];
                  final progress = (story.currentPage + 1) / story.totalPages;
                  return Card(
                    child: ListTile(
                      onTap: () => controller.loadStory(story.id),
                      title: Text(story.title),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${story.genre.name} • ${story.targetAudience.name}${story.hasImages ? ' • Illustrated' : ''}',
                          ),
                          const SizedBox(height: 8),
                          LinearProgressIndicator(value: progress),
                          const SizedBox(height: 4),
                          Text('Page ${story.currentPage + 1}/${story.totalPages}'),
                          if (story.isCompleted)
                            const Text('Completed', style: TextStyle(fontWeight: FontWeight.w600)),
                        ],
                      ),
                      trailing: IconButton(
                        onPressed: () => controller.deleteStory(story.id),
                        icon: const Icon(Icons.delete_outline),
                      ),
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}

class _StoryGenerationProgress extends StatelessWidget {
  const _StoryGenerationProgress({required this.controller});

  final StoryController controller;

  @override
  Widget build(BuildContext context) {
    final p = controller.generationProgress;
    final ratio = p.total == 0 ? null : p.current / p.total;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.auto_stories, size: 56),
            const SizedBox(height: 20),
            Text(controller.generationPhase, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            if (ratio == null)
              const CircularProgressIndicator()
            else ...[
              Text('Page ${p.current} of ${p.total}'),
              const SizedBox(height: 8),
              LinearProgressIndicator(value: ratio),
            ],
          ],
        ),
      ),
    );
  }
}

class _StoryReadingScreen extends StatelessWidget {
  const _StoryReadingScreen({required this.controller, required this.story});

  final StoryController controller;
  final StoryData story;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= 800) {
          return _BookReadingScreen(controller: controller, story: story);
        }
        return _MobileReadingScreen(controller: controller, story: story);
      },
    );
  }
}

class _MobileReadingScreen extends StatelessWidget {
  const _MobileReadingScreen({required this.controller, required this.story});

  final StoryController controller;
  final StoryData story;

  @override
  Widget build(BuildContext context) {
    final page = story.pages[story.currentPage];

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Row(
            children: [
              ChoiceChip(
                selected: controller.isReadingAloud,
                label: Text(controller.isReadingAloud ? 'Reading Aloud' : 'Read Aloud'),
                onSelected: (_) {
                  if (controller.isReadingAloud) {
                    controller.stopReadingAloud();
                  } else {
                    controller.startReadingAloud();
                  }
                },
              ),
              const SizedBox(width: 8),
              FilterChip(
                selected: controller.autoReadAloud,
                label: const Text('Auto Read'),
                onSelected: (_) => controller.toggleAutoReadAloud(),
              ),
              if (controller.generationPhase.isNotEmpty) ...[
                const SizedBox(width: 8),
                Expanded(
                  child: _AnimatedGenerationPhase(text: controller.generationPhase),
                ),
              ],
            ],
          ),
          const SizedBox(height: 12),
          Expanded(
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(page.title, style: Theme.of(context).textTheme.headlineSmall),
                    const SizedBox(height: 10),
                    if (page.hasImage) ...[
                      _StoryImage(page: page, pageIndex: story.currentPage, controller: controller),
                      const SizedBox(height: 12),
                    ] else if (page.imageDescription != null) ...[
                      _ImageDescriptionBox(
                        description: page.imageDescription!,
                        pageIndex: story.currentPage,
                        controller: controller,
                      ),
                      const SizedBox(height: 12),
                    ],
                    Expanded(
                      child: SingleChildScrollView(
                        child: Text(
                          page.content,
                          style: Theme.of(context).textTheme.bodyLarge,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              IconButton(
                onPressed: story.currentPage == 0 ? null : controller.goToPreviousPage,
                icon: const Icon(Icons.arrow_back),
              ),
              Expanded(
                child: Slider(
                  value: story.currentPage.toDouble(),
                  min: 0,
                  max: (story.totalPages - 1).toDouble(),
                  divisions: story.totalPages > 1 ? story.totalPages - 1 : 1,
                  label: 'Page ${story.currentPage + 1}',
                  onChanged: (value) => controller.goToPage(value.round()),
                ),
              ),
              IconButton(
                onPressed: story.currentPage >= story.totalPages - 1 ? null : controller.goToNextPage,
                icon: const Icon(Icons.arrow_forward),
              ),
            ],
          ),
          Text('Page ${story.currentPage + 1} / ${story.totalPages}'),
          const SizedBox(height: 6),
          if (story.currentPage >= story.totalPages - 1)
            FilledButton.icon(
              onPressed: story.isCompleted ? null : controller.completeStory,
              icon: const Icon(Icons.check_circle),
              label: Text(story.isCompleted ? 'Story completed' : 'Complete Story'),
            ),
        ],
      ),
    );
  }
}

class _BookReadingScreen extends StatefulWidget {
  const _BookReadingScreen({required this.controller, required this.story});

  final StoryController controller;
  final StoryData story;

  @override
  State<_BookReadingScreen> createState() => _BookReadingScreenState();
}

class _BookReadingScreenState extends State<_BookReadingScreen> {
  late final PageController _pageController;
  final FocusNode _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: widget.story.currentPage);
  }

  @override
  void didUpdateWidget(_BookReadingScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.story.currentPage != widget.story.currentPage) {
      final target = widget.story.currentPage;
      if (_pageController.hasClients && _pageController.page?.round() != target) {
        _pageController.animateToPage(
          target,
          duration: const Duration(milliseconds: 400),
          curve: Curves.easeInOut,
        );
      }
    }
  }

  @override
  void dispose() {
    _pageController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _handleKeyEvent(KeyEvent event) {
    if (event is! KeyDownEvent) return;
    if (event.logicalKey == LogicalKeyboardKey.arrowRight ||
        event.logicalKey == LogicalKeyboardKey.pageDown) {
      if (widget.story.currentPage < widget.story.totalPages - 1) {
        widget.controller.goToNextPage();
      }
    } else if (event.logicalKey == LogicalKeyboardKey.arrowLeft ||
        event.logicalKey == LogicalKeyboardKey.pageUp) {
      if (widget.story.currentPage > 0) {
        widget.controller.goToPreviousPage();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isDark = theme.brightness == Brightness.dark;
    final creamColor = isDark
        ? colorScheme.surfaceContainerHigh
        : const Color(0xFFFAF6F0);
    final spineColor = isDark
        ? colorScheme.outlineVariant.withValues(alpha: 0.3)
        : Colors.brown.withValues(alpha: 0.15);

    return KeyboardListener(
      focusNode: _focusNode,
      autofocus: true,
      onKeyEvent: _handleKeyEvent,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        child: Column(
          children: [
            // Top toolbar
            Row(
              children: [
                ChoiceChip(
                  selected: widget.controller.isReadingAloud,
                  label: Text(widget.controller.isReadingAloud ? 'Reading Aloud' : 'Read Aloud'),
                  onSelected: (_) {
                    if (widget.controller.isReadingAloud) {
                      widget.controller.stopReadingAloud();
                    } else {
                      widget.controller.startReadingAloud();
                    }
                  },
                ),
                const SizedBox(width: 8),
                FilterChip(
                  selected: widget.controller.autoReadAloud,
                  label: const Text('Auto Read'),
                  onSelected: (_) => widget.controller.toggleAutoReadAloud(),
                ),
                if (widget.controller.generationPhase.isNotEmpty) ...[
                  const SizedBox(width: 8),
                  Expanded(
                    child: _AnimatedGenerationPhase(text: widget.controller.generationPhase),
                  ),
                ],
                const Spacer(),
                Text(
                  'Page ${widget.story.currentPage + 1} of ${widget.story.totalPages}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            // Book container
            Expanded(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 1200, maxHeight: 700),
                  child: Stack(
                    children: [
                      // Book body with shadow
                      Container(
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(8),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.18),
                              blurRadius: 24,
                              offset: const Offset(0, 8),
                            ),
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.08),
                              blurRadius: 8,
                              offset: const Offset(0, 2),
                            ),
                          ],
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(8),
                          child: PageView.builder(
                            controller: _pageController,
                            itemCount: widget.story.totalPages,
                            onPageChanged: (index) {
                              if (widget.story.currentPage != index) {
                                widget.controller.goToPage(index);
                              }
                            },
                            itemBuilder: (context, index) {
                              final page = widget.story.pages[index];
                              return Container(
                                color: creamColor,
                                child: Row(
                                  children: [
                                    // Left page - illustration
                                    Expanded(
                                      child: _BookLeftPage(
                                        page: page,
                                        pageIndex: index,
                                        controller: widget.controller,
                                        creamColor: creamColor,
                                        isDark: isDark,
                                      ),
                                    ),
                                    // Center spine
                                    Container(
                                      width: 2,
                                      decoration: BoxDecoration(
                                        gradient: LinearGradient(
                                          begin: Alignment.centerLeft,
                                          end: Alignment.centerRight,
                                          colors: [
                                            spineColor,
                                            spineColor.withValues(alpha: 0.05),
                                          ],
                                        ),
                                      ),
                                    ),
                                    // Right page - text
                                    Expanded(
                                      child: _BookRightPage(
                                        page: page,
                                        pageNumber: index + 1,
                                        totalPages: widget.story.totalPages,
                                        isDark: isDark,
                                      ),
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),
                        ),
                      ),
                      // Left click zone for previous page
                      if (widget.story.currentPage > 0)
                        Positioned(
                          left: 0,
                          top: 0,
                          bottom: 0,
                          width: 60,
                          child: MouseRegion(
                            cursor: SystemMouseCursors.click,
                            child: GestureDetector(
                              onTap: widget.controller.goToPreviousPage,
                              child: Container(
                                color: Colors.transparent,
                                alignment: Alignment.centerLeft,
                                padding: const EdgeInsets.only(left: 8),
                                child: Icon(
                                  Icons.chevron_left,
                                  color: colorScheme.onSurface.withValues(alpha: 0.3),
                                ),
                              ),
                            ),
                          ),
                        ),
                      // Right click zone for next page
                      if (widget.story.currentPage < widget.story.totalPages - 1)
                        Positioned(
                          right: 0,
                          top: 0,
                          bottom: 0,
                          width: 60,
                          child: MouseRegion(
                            cursor: SystemMouseCursors.click,
                            child: GestureDetector(
                              onTap: widget.controller.goToNextPage,
                              child: Container(
                                color: Colors.transparent,
                                alignment: Alignment.centerRight,
                                padding: const EdgeInsets.only(right: 8),
                                child: Icon(
                                  Icons.chevron_right,
                                  color: colorScheme.onSurface.withValues(alpha: 0.3),
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            // Complete story button on last page
            if (widget.story.currentPage >= widget.story.totalPages - 1)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: FilledButton.icon(
                  onPressed: widget.story.isCompleted ? null : widget.controller.completeStory,
                  icon: const Icon(Icons.check_circle),
                  label: Text(widget.story.isCompleted ? 'Story completed' : 'Complete Story'),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _BookLeftPage extends StatelessWidget {
  const _BookLeftPage({
    required this.page,
    required this.pageIndex,
    required this.controller,
    required this.creamColor,
    required this.isDark,
  });

  final StoryPageData page;
  final int pageIndex;
  final StoryController controller;
  final Color creamColor;
  final bool isDark;

  @override
  Widget build(BuildContext context) {
    if (page.hasImage) {
      return _BookImage(page: page);
    }

    final placeholderColor = isDark ? Colors.grey[850] : const Color(0xFFF0EBE3);

    if (page.imageDescription != null) {
      return Container(
        color: placeholderColor,
        padding: const EdgeInsets.all(32),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.image_outlined,
                size: 48,
                color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
              ),
              const SizedBox(height: 16),
              Text(
                page.imageDescription!,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  fontStyle: FontStyle.italic,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 16),
              _RegenerateImageButton(pageIndex: pageIndex, controller: controller),
            ],
          ),
        ),
      );
    }

    // No image at all — decorative placeholder with regenerate option
    return Container(
      color: placeholderColor,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.auto_stories,
              size: 64,
              color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.2),
            ),
            const SizedBox(height: 16),
            _RegenerateImageButton(pageIndex: pageIndex, controller: controller),
          ],
        ),
      ),
    );
  }
}

class _BookImage extends StatelessWidget {
  const _BookImage({required this.page});

  final StoryPageData page;

  @override
  Widget build(BuildContext context) {
    if (page.imageBase64 != null) {
      try {
        final bytes = base64Decode(page.imageBase64!);
        return Image.memory(
          bytes,
          width: double.infinity,
          height: double.infinity,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => _fallback(context),
        );
      } catch (_) {
        return _fallback(context);
      }
    }

    if (page.imageUrl != null) {
      return Image.network(
        page.imageUrl!,
        width: double.infinity,
        height: double.infinity,
        fit: BoxFit.cover,
        loadingBuilder: (context, child, loadingProgress) {
          if (loadingProgress == null) return child;
          return Container(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: const Center(child: CircularProgressIndicator()),
          );
        },
        errorBuilder: (_, __, ___) => _fallback(context),
      );
    }

    return _fallback(context);
  }

  Widget _fallback(BuildContext context) {
    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Center(
        child: Icon(
          Icons.image_outlined,
          size: 48,
          color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.4),
        ),
      ),
    );
  }
}

class _BookRightPage extends StatelessWidget {
  const _BookRightPage({
    required this.page,
    required this.pageNumber,
    required this.totalPages,
    required this.isDark,
  });

  final StoryPageData page;
  final int pageNumber;
  final int totalPages;
  final bool isDark;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 28, 32, 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Chapter title
          Text(
            page.title,
            style: theme.textTheme.headlineSmall?.copyWith(
              fontFamily: 'Georgia',
              fontWeight: FontWeight.w600,
              letterSpacing: 0.3,
            ),
          ),
          const SizedBox(height: 4),
          Divider(
            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
            thickness: 0.5,
          ),
          const SizedBox(height: 12),
          // Story text
          Expanded(
            child: SingleChildScrollView(
              child: Text(
                page.content,
                style: theme.textTheme.bodyLarge?.copyWith(
                  fontFamily: 'Georgia',
                  height: 1.7,
                  letterSpacing: 0.15,
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          // Page number
          Align(
            alignment: Alignment.bottomRight,
            child: Text(
              '$pageNumber',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
                fontStyle: FontStyle.italic,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StoryImage extends StatelessWidget {
  const _StoryImage({required this.page, required this.pageIndex, required this.controller});

  final StoryPageData page;
  final int pageIndex;
  final StoryController controller;

  void _openFullScreen(BuildContext context) {
    if (!page.hasImage) return;
    Navigator.of(context).push(
      PageRouteBuilder<void>(
        opaque: false,
        barrierColor: Colors.black87,
        barrierDismissible: true,
        pageBuilder: (context, animation, secondaryAnimation) {
          return _FullScreenImageViewer(page: page);
        },
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          return FadeTransition(opacity: animation, child: child);
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final fallback = _ImageDescriptionBox(
      description: page.imageDescription ?? 'Illustration for this page',
      pageIndex: pageIndex,
      controller: controller,
    );

    final screenWidth = MediaQuery.sizeOf(context).width;
    // Scale image height based on screen width: ~55% of width, clamped between 180–400
    final imageHeight = (screenWidth * 0.55).clamp(180.0, 400.0);

    // Base64 image (from Gemini Imagen or DALL-E)
    if (page.imageBase64 != null) {
      try {
        final bytes = base64Decode(page.imageBase64!);
        return GestureDetector(
          onTap: () => _openFullScreen(context),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Image.memory(
              bytes,
              width: double.infinity,
              height: imageHeight,
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) => fallback,
            ),
          ),
        );
      } catch (_) {
        return fallback;
      }
    }

    // Network URL (legacy)
    if (page.imageUrl != null) {
      return GestureDetector(
        onTap: () => _openFullScreen(context),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Image.network(
            page.imageUrl!,
            width: double.infinity,
            height: imageHeight,
            fit: BoxFit.cover,
            loadingBuilder: (context, child, loadingProgress) {
              if (loadingProgress == null) return child;
              return Container(
                width: double.infinity,
                height: imageHeight,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                ),
                child: const Center(child: CircularProgressIndicator()),
              );
            },
            errorBuilder: (_, __, ___) => fallback,
          ),
        ),
      );
    }

    return fallback;
  }
}

class _FullScreenImageViewer extends StatefulWidget {
  const _FullScreenImageViewer({required this.page});

  final StoryPageData page;

  @override
  State<_FullScreenImageViewer> createState() => _FullScreenImageViewerState();
}

class _FullScreenImageViewerState extends State<_FullScreenImageViewer> {
  final TransformationController _transformController = TransformationController();

  @override
  void dispose() {
    _transformController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: GestureDetector(
        onTap: () => Navigator.of(context).pop(),
        child: Stack(
          children: [
            // Full-screen interactive image
            Positioned.fill(
              child: InteractiveViewer(
                transformationController: _transformController,
                minScale: 0.5,
                maxScale: 4.0,
                child: Center(
                  child: _buildImage(),
                ),
              ),
            ),
            // Close button
            Positioned(
              top: MediaQuery.of(context).padding.top + 8,
              right: 8,
              child: IconButton.filled(
                onPressed: () => Navigator.of(context).pop(),
                style: IconButton.styleFrom(
                  backgroundColor: Colors.black54,
                  foregroundColor: Colors.white,
                ),
                icon: const Icon(Icons.close),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildImage() {
    if (widget.page.imageBase64 != null) {
      try {
        final bytes = base64Decode(widget.page.imageBase64!);
        return Image.memory(
          bytes,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => _errorWidget(),
        );
      } catch (_) {
        return _errorWidget();
      }
    }

    if (widget.page.imageUrl != null) {
      return Image.network(
        widget.page.imageUrl!,
        fit: BoxFit.contain,
        loadingBuilder: (context, child, loadingProgress) {
          if (loadingProgress == null) return child;
          return const Center(child: CircularProgressIndicator(color: Colors.white));
        },
        errorBuilder: (_, __, ___) => _errorWidget(),
      );
    }

    return _errorWidget();
  }

  Widget _errorWidget() {
    return const Icon(Icons.broken_image, size: 64, color: Colors.white54);
  }
}

class _ImageDescriptionBox extends StatelessWidget {
  const _ImageDescriptionBox({
    required this.description,
    this.pageIndex,
    this.controller,
  });

  final String description;
  final int? pageIndex;
  final StoryController? controller;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Icon(Icons.image, color: Theme.of(context).colorScheme.onSurfaceVariant),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  description,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        fontStyle: FontStyle.italic,
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
              ),
            ],
          ),
          if (controller != null && pageIndex != null) ...[
            const SizedBox(height: 8),
            _RegenerateImageButton(pageIndex: pageIndex!, controller: controller!),
          ],
        ],
      ),
    );
  }
}

class _RegenerateImageButton extends StatefulWidget {
  const _RegenerateImageButton({required this.pageIndex, required this.controller});

  final int pageIndex;
  final StoryController controller;

  @override
  State<_RegenerateImageButton> createState() => _RegenerateImageButtonState();
}

class _RegenerateImageButtonState extends State<_RegenerateImageButton> {
  bool _isRegenerating = false;

  Future<void> _regenerate() async {
    setState(() => _isRegenerating = true);
    await widget.controller.regenerateImageForPage(widget.pageIndex);
    if (mounted) setState(() => _isRegenerating = false);
  }

  @override
  Widget build(BuildContext context) {
    // Hide while the initial story image generation is in progress
    if (widget.controller.generationPhase.isNotEmpty && !_isRegenerating) {
      return const SizedBox.shrink();
    }

    if (_isRegenerating) {
      return const SizedBox(
        height: 36,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
            SizedBox(width: 8),
            Text('Generating...'),
          ],
        ),
      );
    }

    return TextButton.icon(
      onPressed: _regenerate,
      icon: const Icon(Icons.refresh, size: 18),
      label: const Text('Regenerate Image'),
    );
  }
}

/// Animated pulsing text shown during image generation.
class _AnimatedGenerationPhase extends StatefulWidget {
  const _AnimatedGenerationPhase({required this.text});

  final String text;

  @override
  State<_AnimatedGenerationPhase> createState() => _AnimatedGenerationPhaseState();
}

class _AnimatedGenerationPhaseState extends State<_AnimatedGenerationPhase>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _opacity;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);
    _opacity = Tween<double>(begin: 0.4, end: 1.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return FadeTransition(
      opacity: _opacity,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 12,
            height: 12,
            child: CircularProgressIndicator(
              strokeWidth: 1.5,
              color: colorScheme.primary,
            ),
          ),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              widget.text,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

class _CreateStoryDialog extends StatefulWidget {
  const _CreateStoryDialog({required this.controller});

  final StoryController controller;

  @override
  State<_CreateStoryDialog> createState() => _CreateStoryDialogState();
}

class _CreateStoryDialogState extends State<_CreateStoryDialog> {
  final _promptController = TextEditingController();
  final _charactersController = TextEditingController();
  final _settingController = TextEditingController();
  final _themeController = TextEditingController();

  StoryGenre _genre = StoryGenre.fantasy;
  StoryTarget _target = StoryTarget.elementary;
  double _pageCount = 7;
  bool _generateImages = true;
  final Set<String> _selectedCharacterIds = <String>{};

  @override
  void initState() {
    super.initState();
    _promptController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _promptController.dispose();
    _charactersController.dispose();
    _settingController.dispose();
    _themeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final savedCharacters = widget.controller.characters;

    return AlertDialog(
      title: const Text('Create Story'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _promptController,
              maxLines: 2,
              decoration: const InputDecoration(labelText: 'Story idea'),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<StoryGenre>(
              value: _genre,
              decoration: const InputDecoration(labelText: 'Genre'),
              items: StoryGenre.values
                  .map((genre) => DropdownMenuItem(value: genre, child: Text(genre.name)))
                  .toList(),
              onChanged: (value) => setState(() => _genre = value ?? _genre),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<StoryTarget>(
              value: _target,
              decoration: const InputDecoration(labelText: 'Target audience'),
              items: StoryTarget.values
                  .map((target) => DropdownMenuItem(value: target, child: Text(target.name)))
                  .toList(),
              onChanged: (value) => setState(() => _target = value ?? _target),
            ),
            const SizedBox(height: 12),
            Text('Story length: ${_pageCount.round()} pages'),
            Slider(
              value: _pageCount,
              min: 3,
              max: 20,
              divisions: 17,
              onChanged: (value) => setState(() => _pageCount = value),
            ),
            SwitchListTile(
              title: const Text('Include Illustrations'),
              subtitle: const Text('Generate AI images for each page'),
              value: _generateImages,
              onChanged: (value) => setState(() => _generateImages = value),
              contentPadding: EdgeInsets.zero,
            ),
            TextField(
              controller: _charactersController,
              decoration: const InputDecoration(
                labelText: 'Additional characters (comma separated, optional)',
              ),
            ),
            if (savedCharacters.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('Select from character library', style: Theme.of(context).textTheme.labelLarge),
              const SizedBox(height: 4),
              Wrap(
                spacing: 6,
                children: [
                  for (final character in savedCharacters)
                    FilterChip(
                      selected: _selectedCharacterIds.contains(character.id),
                      label: Text(character.name),
                      onSelected: (selected) {
                        setState(() {
                          if (selected) {
                            _selectedCharacterIds.add(character.id);
                          } else {
                            _selectedCharacterIds.remove(character.id);
                          }
                        });
                      },
                    ),
                ],
              ),
            ],
            const SizedBox(height: 8),
            TextField(
              controller: _settingController,
              decoration: const InputDecoration(labelText: 'Setting (optional)'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _themeController,
              decoration: const InputDecoration(labelText: 'Theme / message (optional)'),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
        FilledButton(
          onPressed: _promptController.text.trim().isEmpty
              ? null
              : () {
                  final selectedCharacterNames = widget.controller.characters
                      .where((character) => _selectedCharacterIds.contains(character.id))
                      .map((character) => character.name)
                      .toList(growable: false);
                  final extraCharacters = _charactersController.text
                      .split(',')
                      .map((character) => character.trim())
                      .where((character) => character.isNotEmpty)
                      .toList(growable: false);
                  final request = StoryRequest(
                    prompt: _promptController.text.trim(),
                    genre: _genre,
                    targetAudience: _target,
                    length: _pageCount <= 6
                        ? StoryLength.short
                        : _pageCount <= 13
                        ? StoryLength.medium
                        : StoryLength.long,
                    exactPageCount: _pageCount.round(),
                    characters: [...selectedCharacterNames, ...extraCharacters],
                    setting: _settingController.text.trim().isEmpty ? null : _settingController.text.trim(),
                    theme: _themeController.text.trim().isEmpty ? null : _themeController.text.trim(),
                    generateImages: _generateImages,
                  );
                  Navigator.of(context).pop(request);
                },
          child: const Text('Create Story'),
        ),
      ],
    );
  }
}

class _CharacterManagementPage extends StatefulWidget {
  const _CharacterManagementPage({required this.controller});

  final StoryController controller;

  @override
  State<_CharacterManagementPage> createState() => _CharacterManagementPageState();
}

class _CharacterManagementPageState extends State<_CharacterManagementPage> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onControllerUpdate);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onControllerUpdate);
    super.dispose();
  }

  void _onControllerUpdate() {
    if (!mounted) return;
    setState(() {});
  }

  Future<void> _openEditor({StoryCharacter? character}) async {
    final saved = await Navigator.of(context).push<StoryCharacter>(
      MaterialPageRoute<StoryCharacter>(
        builder: (_) => _CharacterEditorPage(character: character),
      ),
    );
    if (saved != null) {
      widget.controller.saveCharacter(saved);
    }
  }

  @override
  Widget build(BuildContext context) {
    final characters = widget.controller.characters;

    return Scaffold(
      appBar: AppBar(title: const Text('Manage Characters')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openEditor(),
        icon: const Icon(Icons.add),
        label: const Text('Create Character'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: characters.isEmpty
            ? const Center(child: Text('No characters yet. Create your first character.'))
            : ListView.separated(
                itemCount: characters.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (context, index) {
                  final character = characters[index];
                  return Card(
                    child: ListTile(
                      title: Text(character.name),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(character.physicalDescription),
                          Text(character.personalityDescription),
                          Text('Role: ${character.role.name} • Used ${character.useCount} times'),
                        ],
                      ),
                      trailing: PopupMenuButton<String>(
                        onSelected: (value) {
                          if (value == 'edit') {
                            _openEditor(character: character);
                          }
                          if (value == 'delete') {
                            widget.controller.deleteCharacter(character.id);
                          }
                        },
                        itemBuilder: (_) => const [
                          PopupMenuItem(value: 'edit', child: Text('Edit')),
                          PopupMenuItem(value: 'delete', child: Text('Delete')),
                        ],
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}

class _CharacterEditorPage extends StatefulWidget {
  const _CharacterEditorPage({this.character});

  final StoryCharacter? character;

  @override
  State<_CharacterEditorPage> createState() => _CharacterEditorPageState();
}

class _CharacterEditorPageState extends State<_CharacterEditorPage> {
  late final TextEditingController _nameController;
  late final TextEditingController _appearanceController;
  late final TextEditingController _traitsController;
  late final TextEditingController _abilitiesController;
  late final TextEditingController _backstoryController;
  late final TextEditingController _goalsController;
  late final TextEditingController _catchphraseController;

  late CharacterRole _role;
  late CharacterGender _gender;
  late CharacterAgeGroup _ageGroup;

  @override
  void initState() {
    super.initState();
    final c = widget.character;
    _nameController = TextEditingController(text: c?.name ?? '');
    _appearanceController = TextEditingController(text: c?.appearance ?? '');
    _traitsController = TextEditingController(text: (c?.personalityTraits ?? const <String>[]).join(', '));
    _abilitiesController = TextEditingController(text: (c?.specialAbilities ?? const <String>[]).join(', '));
    _backstoryController = TextEditingController(text: c?.backstory ?? '');
    _goalsController = TextEditingController(text: c?.goals ?? '');
    _catchphraseController = TextEditingController(text: c?.catchphrase ?? '');

    _role = c?.role ?? CharacterRole.protagonist;
    _gender = c?.gender ?? CharacterGender.unspecified;
    _ageGroup = c?.ageGroup ?? CharacterAgeGroup.child;

    _nameController.addListener(_rebuild);
  }

  @override
  void dispose() {
    _nameController.dispose();
    _appearanceController.dispose();
    _traitsController.dispose();
    _abilitiesController.dispose();
    _backstoryController.dispose();
    _goalsController.dispose();
    _catchphraseController.dispose();
    super.dispose();
  }

  void _rebuild() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final isEditing = widget.character != null;

    return Scaffold(
      appBar: AppBar(
        title: Text(isEditing ? 'Edit Character' : 'Create Character'),
        actions: [
          TextButton(
            onPressed: _nameController.text.trim().isEmpty
                ? null
                : () {
                    final updated = StoryCharacter(
                      id: widget.character?.id ?? DateTime.now().microsecondsSinceEpoch.toString(),
                      name: _nameController.text.trim(),
                      role: _role,
                      gender: _gender,
                      ageGroup: _ageGroup,
                      appearance: _appearanceController.text.trim(),
                      personalityTraits: _splitList(_traitsController.text),
                      specialAbilities: _splitList(_abilitiesController.text),
                      backstory: _backstoryController.text.trim(),
                      goals: _goalsController.text.trim(),
                      catchphrase: _catchphraseController.text.trim(),
                      useCount: widget.character?.useCount ?? 0,
                      lastUsed: widget.character?.lastUsed,
                    );
                    Navigator.of(context).pop(updated);
                  },
            child: const Text('Save'),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(controller: _nameController, decoration: const InputDecoration(labelText: 'Name *')),
          const SizedBox(height: 12),
          DropdownButtonFormField<CharacterRole>(
            value: _role,
            decoration: const InputDecoration(labelText: 'Role'),
            items: CharacterRole.values
                .map((role) => DropdownMenuItem(value: role, child: Text(role.name)))
                .toList(),
            onChanged: (value) => setState(() => _role = value ?? _role),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<CharacterGender>(
            value: _gender,
            decoration: const InputDecoration(labelText: 'Gender'),
            items: CharacterGender.values
                .map((gender) => DropdownMenuItem(value: gender, child: Text(gender.name)))
                .toList(),
            onChanged: (value) => setState(() => _gender = value ?? _gender),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<CharacterAgeGroup>(
            value: _ageGroup,
            decoration: const InputDecoration(labelText: 'Age Group'),
            items: CharacterAgeGroup.values
                .map((group) => DropdownMenuItem(value: group, child: Text(group.name)))
                .toList(),
            onChanged: (value) => setState(() => _ageGroup = value ?? _ageGroup),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _appearanceController,
            decoration: const InputDecoration(labelText: 'Appearance'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _traitsController,
            decoration: const InputDecoration(labelText: 'Personality traits (comma separated)'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _abilitiesController,
            decoration: const InputDecoration(labelText: 'Special abilities (comma separated)'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _backstoryController,
            maxLines: 3,
            decoration: const InputDecoration(labelText: 'Backstory'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _goalsController,
            maxLines: 2,
            decoration: const InputDecoration(labelText: 'Goals / motivation'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _catchphraseController,
            decoration: const InputDecoration(labelText: 'Catchphrase (optional)'),
          ),
        ],
      ),
    );
  }

  List<String> _splitList(String raw) {
    return raw
        .split(',')
        .map((entry) => entry.trim())
        .where((entry) => entry.isNotEmpty)
        .toList(growable: false);
  }
}

class _ReadingSummaryCard extends StatelessWidget {
  const _ReadingSummaryCard({required this.controller});

  final StoryController controller;

  @override
  Widget build(BuildContext context) {
    final stats = controller.readingStats;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Reading streak: ${stats.currentStreak} day(s)'),
            Text('Stories completed: ${stats.totalCompletedStories}'),
            Text('Pages read: ${stats.totalPagesRead}'),
            if (stats.unlockedBadges.isNotEmpty) ...[
              const SizedBox(height: 6),
              Wrap(
                spacing: 8,
                children: [for (final badge in stats.unlockedBadges) Chip(label: Text('${badge.icon} ${badge.title}'))],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ReadingStreakScreen extends StatelessWidget {
  const _ReadingStreakScreen({required this.controller});

  final StoryController controller;

  @override
  Widget build(BuildContext context) {
    final stats = controller.readingStats;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Reading Streaks & Achievements', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              leading: const Icon(Icons.local_fire_department),
              title: Text('${stats.currentStreak}-day streak'),
              subtitle: Text('Daily goal: ${controller.currentGoal.dailyMinutes} min'),
            ),
          ),
          Card(
            child: ListTile(
              leading: const Icon(Icons.menu_book),
              title: Text('${stats.totalPagesRead} pages read'),
              subtitle: Text('${stats.totalCompletedStories} stories completed'),
            ),
          ),
          const SizedBox(height: 8),
          const Text('Update reading goal'),
          Slider(
            value: controller.currentGoal.dailyMinutes.toDouble(),
            min: 10,
            max: 60,
            divisions: 10,
            label: '${controller.currentGoal.dailyMinutes} min',
            onChanged: (value) => controller.updateReadingGoal(value.round()),
          ),
          FilledButton(
            onPressed: controller.hideStreaks,
            child: const Text('Back to Story Mode'),
          ),
        ],
      ),
    );
  }
}
