import 'package:flutter/material.dart';

import 'story_controller.dart';

class StoryPage extends StatefulWidget {
  const StoryPage({super.key});

  @override
  State<StoryPage> createState() => _StoryPageState();
}

class _StoryPageState extends State<StoryPage> {
  late final StoryController _controller;

  @override
  void initState() {
    super.initState();
    _controller = StoryController()..addListener(_onControllerChanged);
  }

  @override
  void dispose() {
    _controller
      ..removeListener(_onControllerChanged)
      ..dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    if (!mounted) {
      return;
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final story = _controller.currentStory;

    return Scaffold(
      appBar: AppBar(
        title: Text(_controller.showStoryList ? 'Story Mode' : (story?.title ?? 'Reading Story')),
        leading: !_controller.showStoryList
            ? IconButton(
                onPressed: _controller.backToStoryList,
                icon: const Icon(Icons.arrow_back),
              )
            : null,
        actions: [
          if (_controller.showStoryList)
            IconButton(
              onPressed: _controller.showStreaks,
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
              child: _buildBody(),
            ),
          ),
          if (_controller.badgeNotification case final badge?)
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
                    onPressed: _controller.dismissBadgeNotification,
                    icon: const Icon(Icons.close),
                  ),
                ),
              ),
            ),
          if (_controller.error case final error?)
            Positioned(
              left: 16,
              right: 16,
              bottom: 16,
              child: Card(
                color: Theme.of(context).colorScheme.errorContainer,
                child: ListTile(
                  title: Text(error),
                  trailing: TextButton(
                    onPressed: _controller.clearError,
                    child: const Text('Dismiss'),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_controller.showStreakScreen) {
      return _ReadingStreakScreen(controller: _controller);
    }

    if (_controller.isGenerating) {
      return _StoryGenerationProgress(controller: _controller);
    }

    if (_controller.showStoryList) {
      return _StoryListScreen(controller: _controller);
    }

    final story = _controller.currentStory;
    if (story == null) {
      return const SizedBox.shrink();
    }

    return _StoryReadingScreen(controller: _controller, story: story);
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
                builder: (_) => const _CreateStoryDialog(),
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
              showDialog<void>(
                context: context,
                builder: (_) => const AlertDialog(
                  title: Text('Character Management'),
                  content: Text(
                    'Character creator is next in migration queue. '
                    'Story creation already supports character prompts.',
                  ),
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
                    if (page.imageDescription != null) ...[
                      const SizedBox(height: 10),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(12),
                          color: Theme.of(context).colorScheme.surfaceContainerHighest,
                        ),
                        child: Text(page.imageDescription!),
                      ),
                    ],
                    const SizedBox(height: 12),
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
                  divisions: story.totalPages - 1,
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

class _CreateStoryDialog extends StatefulWidget {
  const _CreateStoryDialog();

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
            TextField(
              controller: _charactersController,
              decoration: const InputDecoration(labelText: 'Characters (optional)'),
            ),
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
                    characters: _charactersController.text
                        .split(',')
                        .map((character) => character.trim())
                        .where((character) => character.isNotEmpty)
                        .toList(),
                    setting: _settingController.text.trim().isEmpty ? null : _settingController.text.trim(),
                    theme: _themeController.text.trim().isEmpty ? null : _themeController.text.trim(),
                  );
                  Navigator.of(context).pop(request);
                },
          child: const Text('Create Story'),
        ),
      ],
    );
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
          Text('Update reading goal'),
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
