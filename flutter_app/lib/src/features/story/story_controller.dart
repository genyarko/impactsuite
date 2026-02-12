import 'dart:math';

import 'package:flutter/foundation.dart';

enum StoryGenre { fantasy, adventure, mystery, sciFi, educational }

enum StoryTarget { kindergarten, elementary, middleSchool, highSchool, adult }

enum StoryLength { short, medium, long }

enum CharacterRole { protagonist, sidekick, mentor, antagonist, guide }

enum CharacterGender { male, female, nonBinary, unspecified }

enum CharacterAgeGroup { child, teen, youngAdult, adult, elderly }

class StoryCharacter {
  const StoryCharacter({
    required this.id,
    required this.name,
    required this.role,
    required this.gender,
    required this.ageGroup,
    required this.appearance,
    required this.personalityTraits,
    required this.specialAbilities,
    required this.backstory,
    required this.goals,
    this.catchphrase = '',
    this.useCount = 0,
    this.lastUsed,
  });

  final String id;
  final String name;
  final CharacterRole role;
  final CharacterGender gender;
  final CharacterAgeGroup ageGroup;
  final String appearance;
  final List<String> personalityTraits;
  final List<String> specialAbilities;
  final String backstory;
  final String goals;
  final String catchphrase;
  final int useCount;
  final DateTime? lastUsed;

  StoryCharacter copyWith({
    String? id,
    String? name,
    CharacterRole? role,
    CharacterGender? gender,
    CharacterAgeGroup? ageGroup,
    String? appearance,
    List<String>? personalityTraits,
    List<String>? specialAbilities,
    String? backstory,
    String? goals,
    String? catchphrase,
    int? useCount,
    DateTime? lastUsed,
  }) {
    return StoryCharacter(
      id: id ?? this.id,
      name: name ?? this.name,
      role: role ?? this.role,
      gender: gender ?? this.gender,
      ageGroup: ageGroup ?? this.ageGroup,
      appearance: appearance ?? this.appearance,
      personalityTraits: personalityTraits ?? this.personalityTraits,
      specialAbilities: specialAbilities ?? this.specialAbilities,
      backstory: backstory ?? this.backstory,
      goals: goals ?? this.goals,
      catchphrase: catchphrase ?? this.catchphrase,
      useCount: useCount ?? this.useCount,
      lastUsed: lastUsed ?? this.lastUsed,
    );
  }

  String get physicalDescription =>
      '${ageGroup.name} ${gender.name} with ${appearance.isEmpty ? 'distinctive appearance' : appearance}';

  String get personalityDescription {
    if (personalityTraits.isEmpty) {
      return 'Personality still evolving';
    }
    return 'Known for being ${personalityTraits.take(3).join(', ')}';
  }
}

class StoryRequest {
  const StoryRequest({
    required this.prompt,
    required this.genre,
    required this.targetAudience,
    required this.length,
    required this.exactPageCount,
    this.characters = const [],
    this.setting,
    this.theme,
  });

  final String prompt;
  final StoryGenre genre;
  final StoryTarget targetAudience;
  final StoryLength length;
  final int exactPageCount;
  final List<String> characters;
  final String? setting;
  final String? theme;
}

class StoryPageData {
  const StoryPageData({
    required this.title,
    required this.content,
    this.imageDescription,
  });

  final String title;
  final String content;
  final String? imageDescription;
}

class StoryData {
  const StoryData({
    required this.id,
    required this.title,
    required this.genre,
    required this.targetAudience,
    required this.pages,
    this.currentPage = 0,
    this.isCompleted = false,
    this.hasImages = false,
    required this.createdAt,
  });

  final String id;
  final String title;
  final StoryGenre genre;
  final StoryTarget targetAudience;
  final List<StoryPageData> pages;
  final int currentPage;
  final bool isCompleted;
  final bool hasImages;
  final DateTime createdAt;

  int get totalPages => pages.length;

  StoryData copyWith({
    int? currentPage,
    bool? isCompleted,
    bool? hasImages,
  }) {
    return StoryData(
      id: id,
      title: title,
      genre: genre,
      targetAudience: targetAudience,
      pages: pages,
      currentPage: currentPage ?? this.currentPage,
      isCompleted: isCompleted ?? this.isCompleted,
      hasImages: hasImages ?? this.hasImages,
      createdAt: createdAt,
    );
  }
}

class ReadingBadge {
  const ReadingBadge({required this.title, required this.icon});

  final String title;
  final String icon;
}

class ReadingGoal {
  const ReadingGoal({required this.dailyMinutes});

  final int dailyMinutes;
}

class ReadingStats {
  const ReadingStats({
    this.currentStreak = 0,
    this.totalCompletedStories = 0,
    this.totalPagesRead = 0,
    this.unlockedBadges = const [],
  });

  final int currentStreak;
  final int totalCompletedStories;
  final int totalPagesRead;
  final List<ReadingBadge> unlockedBadges;

  ReadingStats copyWith({
    int? currentStreak,
    int? totalCompletedStories,
    int? totalPagesRead,
    List<ReadingBadge>? unlockedBadges,
  }) {
    return ReadingStats(
      currentStreak: currentStreak ?? this.currentStreak,
      totalCompletedStories: totalCompletedStories ?? this.totalCompletedStories,
      totalPagesRead: totalPagesRead ?? this.totalPagesRead,
      unlockedBadges: unlockedBadges ?? this.unlockedBadges,
    );
  }
}

class StoryController extends ChangeNotifier {
  final List<StoryData> _stories = [];
  final List<StoryCharacter> _characters = [
    const StoryCharacter(
      id: 'preset-1',
      name: 'Luna Brightspark',
      role: CharacterRole.protagonist,
      gender: CharacterGender.female,
      ageGroup: CharacterAgeGroup.teen,
      appearance: 'silver braid and curious green eyes',
      personalityTraits: ['brave', 'curious', 'kind'],
      specialAbilities: ['starlight magic'],
      backstory: 'An apprentice mapmaker who found a glowing compass.',
      goals: 'Uncover forgotten constellations and help her village.',
      catchphrase: 'Let the stars guide us!',
    ),
    const StoryCharacter(
      id: 'preset-2',
      name: 'Tomo Gearleaf',
      role: CharacterRole.sidekick,
      gender: CharacterGender.male,
      ageGroup: CharacterAgeGroup.youngAdult,
      appearance: 'freckles, copper goggles, and patched overalls',
      personalityTraits: ['funny', 'loyal', 'inventive'],
      specialAbilities: ['gadget engineering'],
      backstory: 'A tinkerer who turns scrap into brilliant inventions.',
      goals: 'Build tools that keep friends safe on every quest.',
      catchphrase: 'I can fix that in five minutes.',
    ),
  ];

  bool showStoryList = true;
  bool showStreakScreen = false;
  bool isGenerating = false;
  bool isLoading = false;
  bool isReadingAloud = false;
  bool autoReadAloud = false;
  String generationPhase = 'Planning story...';
  ({int current, int total}) generationProgress = (current: 0, total: 0);
  String? error;
  StoryData? currentStory;
  ReadingGoal currentGoal = const ReadingGoal(dailyMinutes: 20);
  ReadingStats readingStats = const ReadingStats();
  ReadingBadge? badgeNotification;

  List<StoryData> get allStories => List.unmodifiable(_stories);
  List<StoryCharacter> get characters => List.unmodifiable(_characters);

  Future<void> generateStory(StoryRequest request) async {
    if (request.prompt.trim().isEmpty || isGenerating) {
      return;
    }

    isGenerating = true;
    showStoryList = false;
    error = null;
    generationProgress = (current: 0, total: request.exactPageCount);
    generationPhase = 'Understanding your prompt...';
    notifyListeners();

    try {
      await Future<void>.delayed(const Duration(milliseconds: 350));
      generationPhase = 'Building chapter outline...';
      notifyListeners();

      for (final characterName in request.characters) {
        trackCharacterUsageByName(characterName);
      }

      final pages = <StoryPageData>[];
      for (var index = 0; index < request.exactPageCount; index++) {
        await Future<void>.delayed(const Duration(milliseconds: 90));
        generationProgress = (current: index + 1, total: request.exactPageCount);
        generationPhase = 'Writing page ${index + 1} of ${request.exactPageCount}...';
        pages.add(
          StoryPageData(
            title: 'Chapter ${index + 1}',
            content: _buildPageContent(index: index, request: request),
            imageDescription:
                'An illustrated scene for chapter ${index + 1} featuring ${request.characters.isEmpty ? 'the story hero' : request.characters.first}.',
          ),
        );
        notifyListeners();
      }

      generationPhase = 'Polishing final story...';
      await Future<void>.delayed(const Duration(milliseconds: 250));

      final story = StoryData(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        title: _buildStoryTitle(request),
        genre: request.genre,
        targetAudience: request.targetAudience,
        pages: pages,
        hasImages: request.targetAudience.index <= StoryTarget.middleSchool.index,
        createdAt: DateTime.now(),
      );

      _stories.insert(0, story);
      currentStory = story;
      showStoryList = false;
    } catch (_) {
      showStoryList = true;
      error = 'Could not generate the story. Please try again.';
    } finally {
      isGenerating = false;
      notifyListeners();
    }
  }

  void saveCharacter(StoryCharacter character) {
    if (character.name.trim().isEmpty) {
      error = 'Character name is required.';
      notifyListeners();
      return;
    }

    final index = _characters.indexWhere((existing) => existing.id == character.id);
    if (index >= 0) {
      _characters[index] = character;
    } else {
      _characters.insert(0, character);
    }
    notifyListeners();
  }

  void deleteCharacter(String characterId) {
    _characters.removeWhere((character) => character.id == characterId);
    notifyListeners();
  }

  void trackCharacterUsageByName(String characterName) {
    final index = _characters.indexWhere(
      (character) => character.name.toLowerCase() == characterName.toLowerCase(),
    );
    if (index < 0) {
      return;
    }

    final character = _characters[index];
    _characters[index] = character.copyWith(
      useCount: character.useCount + 1,
      lastUsed: DateTime.now(),
    );
  }

  void loadStory(String storyId) {
    StoryData? story;
    for (final candidate in _stories) {
      if (candidate.id == storyId) {
        story = candidate;
        break;
      }
    }
    if (story == null) {
      error = 'Story not found.';
      notifyListeners();
      return;
    }

    currentStory = story;
    showStoryList = false;
    notifyListeners();
  }

  void deleteStory(String storyId) {
    _stories.removeWhere((story) => story.id == storyId);
    if (currentStory?.id == storyId) {
      currentStory = null;
      showStoryList = true;
    }
    notifyListeners();
  }

  void backToStoryList() {
    stopReadingAloud();
    showStreakScreen = false;
    showStoryList = true;
    currentStory = null;
    notifyListeners();
  }

  void showStreaks() {
    showStreakScreen = true;
    notifyListeners();
  }

  void hideStreaks() {
    showStreakScreen = false;
    notifyListeners();
  }

  void goToNextPage() {
    final story = currentStory;
    if (story == null || story.currentPage >= story.totalPages - 1) {
      return;
    }
    _updateCurrentStory(story.copyWith(currentPage: story.currentPage + 1));
  }

  void goToPreviousPage() {
    final story = currentStory;
    if (story == null || story.currentPage <= 0) {
      return;
    }
    _updateCurrentStory(story.copyWith(currentPage: story.currentPage - 1));
  }

  void goToPage(int page) {
    final story = currentStory;
    if (story == null || page < 0 || page >= story.totalPages) {
      return;
    }
    _updateCurrentStory(story.copyWith(currentPage: page));
  }

  void completeStory() {
    final story = currentStory;
    if (story == null) {
      return;
    }

    final completedStory = story.copyWith(isCompleted: true, currentPage: story.totalPages - 1);
    _updateCurrentStory(completedStory);

    final updatedBadges = <ReadingBadge>[...readingStats.unlockedBadges];
    ReadingBadge? unlocked;

    final completedCount = readingStats.totalCompletedStories + 1;
    if (completedCount == 1 && updatedBadges.every((b) => b.title != 'First Story')) {
      unlocked = const ReadingBadge(title: 'First Story', icon: '📘');
      updatedBadges.add(unlocked);
    } else if (completedCount == 5 && updatedBadges.every((b) => b.title != 'Book Explorer')) {
      unlocked = const ReadingBadge(title: 'Book Explorer', icon: '🏆');
      updatedBadges.add(unlocked);
    }

    readingStats = readingStats.copyWith(
      totalCompletedStories: completedCount,
      totalPagesRead: readingStats.totalPagesRead + story.totalPages,
      currentStreak: max(1, readingStats.currentStreak + 1),
      unlockedBadges: updatedBadges,
    );
    badgeNotification = unlocked;
    notifyListeners();
  }

  void dismissBadgeNotification() {
    badgeNotification = null;
    notifyListeners();
  }

  void startReadingAloud() {
    isReadingAloud = true;
    notifyListeners();
  }

  void stopReadingAloud() {
    isReadingAloud = false;
    notifyListeners();
  }

  void toggleAutoReadAloud() {
    autoReadAloud = !autoReadAloud;
    notifyListeners();
  }

  void updateReadingGoal(int minutes) {
    currentGoal = ReadingGoal(dailyMinutes: minutes);
    notifyListeners();
  }

  void clearError() {
    error = null;
    notifyListeners();
  }

  void _updateCurrentStory(StoryData updatedStory) {
    currentStory = updatedStory;
    final index = _stories.indexWhere((s) => s.id == updatedStory.id);
    if (index >= 0) {
      _stories[index] = updatedStory;
    }
    notifyListeners();
  }

  String _buildStoryTitle(StoryRequest request) {
    final base = request.prompt.split(' ').take(3).join(' ').trim();
    if (base.isEmpty) {
      return 'Untitled Story';
    }
    return '${base[0].toUpperCase()}${base.substring(1)}';
  }

  String _buildPageContent({required int index, required StoryRequest request}) {
    final setting = request.setting?.trim().isNotEmpty == true
        ? request.setting!.trim()
        : 'a magical world';
    final theme = request.theme?.trim().isNotEmpty == true
        ? request.theme!.trim()
        : 'kindness and curiosity';
    final characterText = request.characters.isEmpty
        ? 'a brave learner'
        : request.characters.join(', ');

    return 'In $setting, $characterText continued the journey inspired by "${request.prompt}". '
        'On this part of the adventure, they discovered that $theme matters in real life. '
        'They solved one small challenge, reflected on what they learned, and prepared for the next chapter.';
  }
}
