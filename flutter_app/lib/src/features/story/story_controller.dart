import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

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
    this.generateImages = true,
  });

  final String prompt;
  final StoryGenre genre;
  final StoryTarget targetAudience;
  final StoryLength length;
  final int exactPageCount;
  final List<String> characters;
  final String? setting;
  final String? theme;
  final bool generateImages;
}

class StoryPageData {
  const StoryPageData({
    required this.title,
    required this.content,
    this.imageDescription,
    this.imageUrl,
    this.imageBase64,
  });

  final String title;
  final String content;
  final String? imageDescription;
  final String? imageUrl;
  /// Base64-encoded image bytes (from Gemini Imagen).
  final String? imageBase64;

  bool get hasImage => imageUrl != null || imageBase64 != null;

  StoryPageData copyWith({
    String? title,
    String? content,
    String? imageDescription,
    String? imageUrl,
    String? imageBase64,
  }) {
    return StoryPageData(
      title: title ?? this.title,
      content: content ?? this.content,
      imageDescription: imageDescription ?? this.imageDescription,
      imageUrl: imageUrl ?? this.imageUrl,
      imageBase64: imageBase64 ?? this.imageBase64,
    );
  }
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
    List<StoryPageData>? pages,
  }) {
    return StoryData(
      id: id,
      title: title,
      genre: genre,
      targetAudience: targetAudience,
      pages: pages ?? this.pages,
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
  StoryController({
    required AiRepository repository,
    this.openAiApiKey = '',
    this.geminiApiKey = '',
  }) : _repository = repository;

  final AiRepository _repository;
  final String openAiApiKey;
  final String geminiApiKey;

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

  // ---------------------------------------------------------------------------
  // Story generation
  // ---------------------------------------------------------------------------

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
      for (final characterName in request.characters) {
        trackCharacterUsageByName(characterName);
      }

      // Phase 1 – generate story text via AI
      generationPhase = 'Writing your story...';
      notifyListeners();

      final prompt = _buildStoryPrompt(request);
      final result = await _repository.generate(
        AiGenerationRequest(
          prompt: prompt,
          temperature: 1.0,
          maxOutputTokens: 8192,
        ),
      );

      generationPhase = 'Processing story pages...';
      notifyListeners();

      final pages = _parseStoryResponse(result.text, request);
      final storyTitle = _extractTitle(result.text, request);

      // Simulate per-page progress so the UI updates
      for (var i = 0; i < pages.length; i++) {
        generationProgress = (current: i + 1, total: pages.length);
        generationPhase = 'Polishing page ${i + 1} of ${pages.length}...';
        notifyListeners();
        await Future<void>.delayed(const Duration(milliseconds: 80));
      }

      final shouldGenImages = request.generateImages;

      final story = StoryData(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        title: storyTitle,
        genre: request.genre,
        targetAudience: request.targetAudience,
        pages: pages,
        hasImages: false,
        createdAt: DateTime.now(),
      );

      _stories.insert(0, story);
      currentStory = story;
      showStoryList = false;
      isGenerating = false;
      notifyListeners();

      // Phase 2 – generate images in background (non-blocking)
      if (shouldGenImages) {
        _generateImagesForStory(story, request);
      }
    } catch (e) {
      // Fallback: try to create the story from a fallback prompt
      try {
        final pages = _createFallbackStory(request);
        final story = StoryData(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          title: _buildFallbackTitle(request),
          genre: request.genre,
          targetAudience: request.targetAudience,
          pages: pages,
          hasImages: false,
          createdAt: DateTime.now(),
        );
        _stories.insert(0, story);
        currentStory = story;
        showStoryList = false;
      } catch (_) {
        showStoryList = true;
        error = 'Could not generate the story. Please check your API key in Settings and try again.';
      }
    } finally {
      isGenerating = false;
      notifyListeners();
    }
  }

  // ---------------------------------------------------------------------------
  // AI prompt (matches Kotlin OnlineStoryGenerator)
  // ---------------------------------------------------------------------------

  String _buildStoryPrompt(StoryRequest request) {
    final targetPageCount = request.exactPageCount;

    final audienceContext = switch (request.targetAudience) {
      StoryTarget.kindergarten => 'ages 3-5, simple vocabulary, very short sentences',
      StoryTarget.elementary => 'ages 6-10, basic vocabulary, clear simple sentences',
      StoryTarget.middleSchool => 'ages 11-13, intermediate vocabulary, engaging plots',
      StoryTarget.highSchool => 'ages 14-18, advanced vocabulary, complex themes',
      StoryTarget.adult => 'adult audience, sophisticated language and themes',
    };

    final genreContext = switch (request.genre) {
      StoryGenre.adventure => 'exciting journey with challenges and discoveries',
      StoryGenre.fantasy => 'magical elements, mythical creatures, imaginary worlds',
      StoryGenre.mystery => 'puzzles to solve, clues to uncover, suspenseful atmosphere',
      StoryGenre.sciFi => 'futuristic technology, space exploration, scientific concepts',
      StoryGenre.educational => 'learning opportunities, factual information woven in',
    };

    final charactersText = request.characters.isNotEmpty
        ? 'Characters to include: ${request.characters.join(', ')}'
        : 'Create memorable, age-appropriate characters';

    final settingText = (request.setting?.isNotEmpty ?? false)
        ? 'Setting: ${request.setting}'
        : 'Choose an engaging setting that fits the genre';

    final themeText = (request.theme?.isNotEmpty ?? false)
        ? 'Theme/Message: ${request.theme}'
        : 'Include a positive, age-appropriate message';

    final promptSection = request.prompt.isNotEmpty
        ? '- User Prompt: "${request.prompt}"'
        : '- Create an original story using the characters and structure provided';

    return '''
You are a skilled children's story writer. Create an engaging ${request.genre.name} story for ${request.targetAudience.name} readers.

CRITICAL REQUIREMENT: YOU MUST CREATE EXACTLY $targetPageCount PAGES - NO MORE, NO LESS!

STORY REQUIREMENTS:
$promptSection
- Genre: ${request.genre.name} ($genreContext)
- Target Audience: ${request.targetAudience.name} ($audienceContext)
- MANDATORY LENGTH: EXACTLY $targetPageCount pages (this is non-negotiable)
- $charactersText
- $settingText
- $themeText

WRITING GUIDELINES:
- Each page should be substantial but appropriate for the target audience
- Create a compelling beginning, engaging middle, and satisfying conclusion
- Use vivid but age-appropriate descriptions
- Include dialogue to bring characters to life
- Ensure smooth transitions between pages
- Make each page end with a natural stopping point
- EACH PAGE MUST ADVANCE THE PLOT - do NOT repeat content between pages

OUTPUT FORMAT:
Return ONLY valid JSON in this exact format with EXACTLY $targetPageCount pages:
{
  "title": "Engaging Story Title",
  "pages": [
    {
      "pageNumber": 1,
      "title": "Chapter 1 Title",
      "content": "Full content for page 1. This should be substantial and engaging text appropriate for the target audience."
    },
    {
      "pageNumber": 2,
      "title": "Chapter 2 Title",
      "content": "Full content for page 2. Continue the story naturally from page 1."
    }
  ],
  "characters": ["Character 1", "Character 2"],
  "setting": "Brief description of the main setting"
}

REMINDER: Your response must contain EXACTLY $targetPageCount pages in the pages array!

Generate the complete story now:
''';
  }

  // ---------------------------------------------------------------------------
  // Response parsing
  // ---------------------------------------------------------------------------

  List<StoryPageData> _parseStoryResponse(String response, StoryRequest request) {
    // Try JSON parse first
    final jsonPages = _tryParseJson(response, request);
    if (jsonPages != null && jsonPages.isNotEmpty) {
      return jsonPages;
    }

    // Try manual regex extraction
    final manualPages = _tryManualExtraction(response);
    if (manualPages.isNotEmpty) {
      return manualPages;
    }

    // Fallback
    return _createFallbackStory(request);
  }

  List<StoryPageData>? _tryParseJson(String response, StoryRequest request) {
    try {
      final jsonString = _extractJson(response);
      final data = jsonDecode(jsonString) as Map<String, dynamic>;
      final pagesData = data['pages'] as List<dynamic>?;
      if (pagesData == null || pagesData.isEmpty) return null;

      return pagesData.asMap().entries.map((entry) {
        final i = entry.key;
        final page = entry.value as Map<String, dynamic>;
        return StoryPageData(
          title: (page['title'] as String?) ?? 'Chapter ${i + 1}',
          content: (page['content'] as String?) ?? '',
        );
      }).toList();
    } catch (_) {
      return null;
    }
  }

  List<StoryPageData> _tryManualExtraction(String response) {
    final pages = <StoryPageData>[];
    final pattern = RegExp(
      r'"pageNumber"\s*:\s*(\d+)[^}]*?"content"\s*:\s*"((?:[^"\\]|\\.)*?)"',
      dotAll: true,
    );
    for (final match in pattern.allMatches(response)) {
      final content = match.group(2)
              ?.replaceAll(r'\"', '"')
              .replaceAll(r'\n', '\n') ??
          '';
      pages.add(StoryPageData(
        title: 'Chapter ${pages.length + 1}',
        content: content,
      ));
    }
    return pages;
  }

  String _extractJson(String response) {
    var cleaned = response
        .replaceAll('```json', '')
        .replaceAll('```', '')
        .trim();
    final start = cleaned.indexOf('{');
    final end = cleaned.lastIndexOf('}');
    if (start != -1 && end != -1 && end > start) {
      cleaned = cleaned.substring(start, end + 1);
    }
    // Fix trailing commas
    cleaned = cleaned
        .replaceAll(RegExp(r',\s*]'), ']')
        .replaceAll(RegExp(r',\s*}'), '}');
    return cleaned;
  }

  String _extractTitle(String response, StoryRequest request) {
    try {
      final jsonString = _extractJson(response);
      final data = jsonDecode(jsonString) as Map<String, dynamic>;
      final title = data['title'] as String?;
      if (title != null && title.isNotEmpty) return title;
    } catch (_) {}
    return _buildFallbackTitle(request);
  }

  String _buildFallbackTitle(StoryRequest request) {
    final base = request.prompt.split(' ').take(4).join(' ').trim();
    if (base.isEmpty) return 'Untitled Story';
    return '${base[0].toUpperCase()}${base.substring(1)}';
  }

  // ---------------------------------------------------------------------------
  // Fallback story (offline / error case)
  // ---------------------------------------------------------------------------

  List<StoryPageData> _createFallbackStory(StoryRequest request) {
    final pages = <StoryPageData>[];
    final pageCount = request.exactPageCount;
    final prompt = request.prompt;
    final genre = request.genre;
    final setting = request.setting ?? 'a magical world';
    final characters = request.characters.isEmpty
        ? ['the brave hero']
        : request.characters;
    final characterText = characters.join(' and ');

    // Generate truly distinct pages with story progression
    final storyBeats = _getStoryBeats(pageCount, genre);
    for (var i = 0; i < pageCount; i++) {
      final beat = storyBeats[i % storyBeats.length];
      pages.add(StoryPageData(
        title: 'Chapter ${i + 1}: ${beat.title}',
        content: beat.buildContent(
          setting: setting,
          characters: characterText,
          prompt: prompt,
          pageIndex: i,
          totalPages: pageCount,
        ),
      ));
    }
    return pages;
  }

  List<_StoryBeat> _getStoryBeats(int pageCount, StoryGenre genre) {
    return [
      _StoryBeat(
        title: 'The Beginning',
        template: 'In {setting}, {characters} set out on a journey inspired by {prompt}. '
            'The air was filled with excitement as the first steps were taken into the unknown.',
      ),
      _StoryBeat(
        title: 'The Discovery',
        template: '{characters} stumbled upon something unexpected in {setting}. '
            'A hidden path revealed itself, hinting at mysteries connected to {prompt}.',
      ),
      _StoryBeat(
        title: 'The Challenge',
        template: 'A great obstacle blocked the way forward. {characters} had to use courage and wit '
            'to overcome the challenge that {setting} presented.',
      ),
      _StoryBeat(
        title: 'New Friends',
        template: 'Along the journey, {characters} met a surprising ally. Together, they shared stories '
            'and found common ground in their quest related to {prompt}.',
      ),
      _StoryBeat(
        title: 'The Turning Point',
        template: 'Everything changed when {characters} discovered a crucial secret hidden deep within {setting}. '
            'This revelation transformed their understanding of the entire adventure.',
      ),
      _StoryBeat(
        title: 'The Dark Hour',
        template: 'Doubt crept in as {characters} faced their greatest fear. The path ahead seemed impossible, '
            'but memories of why they began this journey gave them strength.',
      ),
      _StoryBeat(
        title: 'Rising Up',
        template: 'With renewed determination, {characters} pushed through the darkness. '
            'Every lesson learned along the way in {setting} prepared them for this moment.',
      ),
      _StoryBeat(
        title: 'The Climax',
        template: 'The final challenge arrived. {characters} stood at the heart of {setting}, '
            'facing everything that {prompt} had led them to. It was now or never.',
      ),
      _StoryBeat(
        title: 'Victory',
        template: 'Against all odds, {characters} succeeded. The world of {setting} was forever changed, '
            'and the lessons of {prompt} echoed through every corner.',
      ),
      _StoryBeat(
        title: 'The Return',
        template: '{characters} returned home, transformed by the adventure. Looking back at {setting}, '
            'they knew this was only the beginning of many more stories to come.',
      ),
    ];
  }

  // ---------------------------------------------------------------------------
  // Image generation (matches Kotlin StoryImageGenerator)
  // ---------------------------------------------------------------------------

  Future<void> _generateImagesForStory(StoryData story, StoryRequest request) async {
    final hasAnyImageKey = geminiApiKey.isNotEmpty || openAiApiKey.isNotEmpty;
    if (!hasAnyImageKey) {
      debugPrint('No API key available for image generation');
      return;
    }

    try {
      // Step 1: Generate image descriptions via AI
      generationPhase = 'Creating image descriptions...';
      notifyListeners();

      var descriptions = await _generateImageDescriptions(story);
      if (descriptions.isEmpty) {
        debugPrint('AI description generation failed, using page content as fallback');
        // Fallback: create simple descriptions from page titles and content
        descriptions = story.pages.map((page) {
          final snippet = page.content.length > 200
              ? page.content.substring(0, 200)
              : page.content;
          return '${page.title}. Scene: $snippet';
        }).toList();
      }

      debugPrint('Using ${descriptions.length} image descriptions');

      // Step 2: Generate actual images
      generationPhase = 'Generating illustrations...';
      notifyListeners();

      final updatedPages = <StoryPageData>[...story.pages];
      var anyImages = false;
      var consecutiveFailures = 0;

      for (var i = 0; i < updatedPages.length && consecutiveFailures < 3; i++) {
        final desc = i < descriptions.length ? descriptions[i] : null;
        if (desc == null) continue;

        generationPhase = 'Generating illustration ${i + 1} of ${updatedPages.length}...';
        notifyListeners();

        try {
          final imageResult = await _generateImage(desc);
          if (imageResult != null) {
            updatedPages[i] = updatedPages[i].copyWith(
              imageDescription: desc,
              imageBase64: imageResult.base64,
            );
            anyImages = true;
            consecutiveFailures = 0;
            debugPrint('Generated image for page ${i + 1}');
          } else {
            updatedPages[i] = updatedPages[i].copyWith(imageDescription: desc);
            consecutiveFailures++;
            debugPrint('Image generation returned null for page ${i + 1}');
          }
        } catch (e) {
          updatedPages[i] = updatedPages[i].copyWith(imageDescription: desc);
          consecutiveFailures++;
          debugPrint('Image generation error for page ${i + 1}: $e');
        }

        // Update the story after each image so user sees progress
        final progressStory = story.copyWith(pages: updatedPages, hasImages: anyImages);
        _updateCurrentStory(progressStory);
      }

      // Fill remaining pages with descriptions only
      for (var i = 0; i < updatedPages.length; i++) {
        if (updatedPages[i].imageDescription == null && i < descriptions.length) {
          updatedPages[i] = updatedPages[i].copyWith(imageDescription: descriptions[i]);
        }
      }

      final updatedStory = story.copyWith(pages: updatedPages, hasImages: anyImages);
      _updateCurrentStory(updatedStory);
    } catch (e) {
      debugPrint('Image generation failed: $e');
      error = 'Image generation encountered an error: $e';
    } finally {
      generationPhase = '';
      notifyListeners();
    }
  }

  /// Tries Gemini Imagen first, then DALL-E as fallback.
  Future<_ImageResult?> _generateImage(String description) async {
    // Try Gemini Imagen first (more widely available)
    if (geminiApiKey.isNotEmpty) {
      try {
        final result = await _generateImageWithGeminiImagen(description);
        if (result != null) return result;
      } catch (e) {
        debugPrint('Gemini Imagen failed, trying DALL-E: $e');
      }
    }

    // Fall back to DALL-E
    if (openAiApiKey.isNotEmpty) {
      try {
        final result = await _generateImageWithDALLE(description);
        if (result != null) return result;
        debugPrint('DALL-E returned null result');
      } catch (e) {
        debugPrint('DALL-E failed: $e');
      }
    }

    debugPrint('No image generation method succeeded');
    return null;
  }

  Future<_ImageResult?> _generateImageWithGeminiImagen(String description) async {
    if (geminiApiKey.isEmpty) return null;

    final imagePrompt =
        "Children's book illustration, cartoon style, bright and colorful: $description";

    try {
      // Try Imagen 3 model
      final url = Uri.parse(
        'https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict?key=$geminiApiKey',
      );
      final response = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'instances': [
            {'prompt': imagePrompt},
          ],
          'parameters': {
            'sampleCount': 1,
            'aspectRatio': '1:1',
            'personGeneration': 'allow_all',
            'safetySetting': 'block_only_high',
          },
        }),
      );

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final predictions = json['predictions'] as List<dynamic>?;
        if (predictions != null && predictions.isNotEmpty) {
          final prediction = predictions[0] as Map<String, dynamic>;
          final base64Data = prediction['bytesBase64Encoded'] as String?;
          if (base64Data != null && base64Data.isNotEmpty) {
            return _ImageResult(base64: base64Data);
          }
        }
      } else {
        debugPrint('Gemini Imagen error: ${response.statusCode} ${response.body}');
        // Try Gemini 2.0 Flash native image generation as second attempt
        return _generateImageWithGeminiFlash(description);
      }
    } catch (e) {
      debugPrint('Gemini Imagen request failed: $e');
      // Try Gemini Flash as fallback
      return _generateImageWithGeminiFlash(description);
    }

    return null;
  }

  Future<_ImageResult?> _generateImageWithGeminiFlash(String description) async {
    if (geminiApiKey.isEmpty) return null;

    final imagePrompt =
        "Generate a children's book illustration: $description. Make it colorful, cartoon style, and child-friendly.";

    try {
      final url = Uri.parse(
        'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=$geminiApiKey',
      );
      final response = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'contents': [
            {
              'parts': [
                {'text': imagePrompt},
              ],
            },
          ],
          'generationConfig': {
            'responseModalities': ['IMAGE', 'TEXT'],
            'maxOutputTokens': 4096,
          },
        }),
      );

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final candidates = json['candidates'] as List<dynamic>?;
        if (candidates != null && candidates.isNotEmpty) {
          final content = (candidates[0] as Map<String, dynamic>)['content'] as Map<String, dynamic>?;
          final parts = content?['parts'] as List<dynamic>?;
          if (parts != null) {
            for (final part in parts) {
              final partMap = part as Map<String, dynamic>;
              final inlineData = partMap['inlineData'] as Map<String, dynamic>?;
              if (inlineData != null) {
                final base64Data = inlineData['data'] as String?;
                if (base64Data != null && base64Data.isNotEmpty) {
                  return _ImageResult(base64: base64Data);
                }
              }
            }
          }
        }
      } else {
        debugPrint('Gemini Flash image error: ${response.statusCode} ${response.body}');
      }
    } catch (e) {
      debugPrint('Gemini Flash image request failed: $e');
    }

    return null;
  }

  Future<List<String>> _generateImageDescriptions(StoryData story) async {
    final audienceStyle = switch (story.targetAudience) {
      StoryTarget.kindergarten || StoryTarget.elementary =>
        'bright, colorful, child-friendly illustrations suitable for young children',
      StoryTarget.middleSchool =>
        'engaging, detailed illustrations suitable for middle school readers',
      _ => 'sophisticated, artistic illustrations suitable for older readers',
    };

    final genreStyle = switch (story.genre) {
      StoryGenre.adventure => 'dynamic action scenes, exciting landscapes',
      StoryGenre.fantasy => 'magical creatures, enchanted settings, mystical elements',
      StoryGenre.mystery => 'atmospheric, shadowy scenes with intriguing details',
      StoryGenre.sciFi => 'futuristic technology, space scenes, advanced settings',
      StoryGenre.educational => 'clear, informative illustrations supporting learning',
    };

    final pagesSection = story.pages.asMap().entries.map((entry) {
      final i = entry.key;
      final page = entry.value;
      return 'Page ${i + 1} - ${page.title}: ${page.content}';
    }).join('\n\n');

    final prompt = '''
You are an expert visual storytelling consultant. Create detailed visual descriptions for each page of this story that could be used to generate illustrations.

STORY DETAILS:
Title: ${story.title}
Genre: ${story.genre.name} ($genreStyle)
Target Audience: ${story.targetAudience.name} ($audienceStyle)

STORY PAGES:
$pagesSection

INSTRUCTIONS:
For each page, create a detailed visual description that captures:
1. The key scene or moment from that page
2. Character appearances, expressions, and positions
3. Setting details and atmosphere
4. Color palette and mood
5. Composition and visual focus

Each description should be 2-3 sentences and suitable for generating illustrations.
Make descriptions age-appropriate for ${story.targetAudience.name} readers.

FORMAT YOUR RESPONSE AS:
Page 1: [Visual description for page 1]
Page 2: [Visual description for page 2]
[Continue for all ${story.totalPages} pages]

Generate the visual descriptions now:
''';

    try {
      final result = await _repository.generate(
        AiGenerationRequest(
          prompt: prompt,
          temperature: 0.8,
          maxOutputTokens: 4096,
        ),
      );
      final parsed = _parseImageDescriptions(result.text, story.totalPages);
      debugPrint('Parsed ${parsed.length} image descriptions from AI response');
      return parsed;
    } catch (e) {
      debugPrint('Image description generation failed: $e');
      return [];
    }
  }

  List<String> _parseImageDescriptions(String script, int totalPages) {
    final results = <String>[];

    // Try JSON parse
    if (script.trim().startsWith('{') || script.contains('"Page')) {
      try {
        final cleaned = script.replaceAll('```json', '').replaceAll('```', '').trim();
        final start = cleaned.indexOf('{');
        final end = cleaned.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
          final data = jsonDecode(cleaned.substring(start, end + 1)) as Map<String, dynamic>;
          for (var i = 1; i <= totalPages; i++) {
            final desc = data['Page $i']?.toString().trim();
            if (desc != null && desc.isNotEmpty) results.add(desc);
          }
        }
      } catch (_) {}
    }

    // Fallback: regex
    if (results.isEmpty) {
      final regex = RegExp(r'Page\s+(\d+):\s+(.*?)(?=Page\s+\d+:|$)', dotAll: true);
      for (final match in regex.allMatches(script)) {
        final desc = match.group(2)?.trim();
        if (desc != null && desc.isNotEmpty) results.add(desc);
      }
    }

    return results.take(totalPages).toList();
  }

  Future<_ImageResult?> _generateImageWithDALLE(String description) async {
    if (openAiApiKey.isEmpty) return null;

    // Sanitize for content policy
    var sanitized = description;
    const replacements = {
      'trapped': 'surrounded by',
      'stuck in': 'standing near',
      'fear and shame': 'surprise and wonder',
      'crying': 'looking thoughtful',
      'frightened': 'curious',
      'scared': 'surprised',
    };
    for (final entry in replacements.entries) {
      sanitized = sanitized.replaceAll(
        RegExp(RegExp.escape(entry.key), caseSensitive: false),
        entry.value,
      );
    }

    // Truncate prompt to stay within DALL-E limits (max ~4000 chars)
    if (sanitized.length > 500) {
      sanitized = sanitized.substring(0, 500);
    }

    final imagePrompt =
        "Simple children's book illustration: $sanitized. Cartoon style, bright colors, safe for children.";

    try {
      final url = Uri.parse('https://api.openai.com/v1/images/generations');
      final response = await http.post(
        url,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $openAiApiKey',
        },
        body: jsonEncode({
          'model': 'dall-e-3',
          'prompt': imagePrompt,
          'n': 1,
          'size': '1024x1024',
          'quality': 'standard',
          'style': 'natural',
          'response_format': 'b64_json',
        }),
      );

      if (response.statusCode != 200) {
        debugPrint('DALL-E error: ${response.statusCode} ${response.body}');
        return null;
      }

      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final data = (json['data'] as List<dynamic>?)?.firstOrNull as Map<String, dynamic>?;
      final b64 = data?['b64_json'] as String?;
      if (b64 != null && b64.isNotEmpty) {
        return _ImageResult(base64: b64);
      }
      debugPrint('DALL-E response missing b64_json data');
      return null;
    } catch (e) {
      debugPrint('DALL-E request failed: $e');
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // Character management
  // ---------------------------------------------------------------------------

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
    if (index < 0) return;

    final character = _characters[index];
    _characters[index] = character.copyWith(
      useCount: character.useCount + 1,
      lastUsed: DateTime.now(),
    );
  }

  // ---------------------------------------------------------------------------
  // Story navigation & state
  // ---------------------------------------------------------------------------

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
    if (story == null || story.currentPage >= story.totalPages - 1) return;
    _updateCurrentStory(story.copyWith(currentPage: story.currentPage + 1));
  }

  void goToPreviousPage() {
    final story = currentStory;
    if (story == null || story.currentPage <= 0) return;
    _updateCurrentStory(story.copyWith(currentPage: story.currentPage - 1));
  }

  void goToPage(int page) {
    final story = currentStory;
    if (story == null || page < 0 || page >= story.totalPages) return;
    _updateCurrentStory(story.copyWith(currentPage: page));
  }

  void completeStory() {
    final story = currentStory;
    if (story == null) return;

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
}

// ---------------------------------------------------------------------------
// Image generation result
// ---------------------------------------------------------------------------

class _ImageResult {
  const _ImageResult({this.base64});

  /// Base64-encoded image bytes (from Gemini Imagen or DALL-E).
  final String? base64;
}

// ---------------------------------------------------------------------------
// Helper for fallback story beats
// ---------------------------------------------------------------------------

class _StoryBeat {
  const _StoryBeat({required this.title, required this.template});

  final String title;
  final String template;

  String buildContent({
    required String setting,
    required String characters,
    required String prompt,
    required int pageIndex,
    required int totalPages,
  }) {
    return template
        .replaceAll('{setting}', setting)
        .replaceAll('{characters}', characters)
        .replaceAll('{prompt}', prompt);
  }
}
