class StoryPreferences {
  const StoryPreferences({
    required this.topics,
    required this.readingLevel,
    required this.sessionMinutes,
  });

  final List<String> topics;
  final int readingLevel;
  final int sessionMinutes;
}

class StoryRecommendation {
  const StoryRecommendation({
    required this.theme,
    required this.suggestedLength,
  });

  final String theme;
  final int suggestedLength;
}

class StoryRecommendationService {
  const StoryRecommendationService();

  StoryRecommendation recommend(StoryPreferences preferences) {
    final theme = preferences.topics.isEmpty ? 'friendship' : preferences.topics.first;
    final suggestedLength = switch (preferences.sessionMinutes) {
      <= 5 => 300,
      <= 10 => 600,
      <= 20 => 900,
      _ => 1200,
    };

    return StoryRecommendation(
      theme: theme,
      suggestedLength: suggestedLength + (preferences.readingLevel * 25),
    );
  }
}
