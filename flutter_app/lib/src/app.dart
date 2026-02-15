import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'features/analytics/learning_analytics_page.dart';
import 'features/caption/live_caption_page.dart';
import 'features/cbt/cbt_coach_page.dart';
import 'features/chat/chat_list_page.dart';
import 'features/chat/chat_page.dart';
import 'features/crisis/crisis_page.dart';
import 'features/home/home_page.dart';
import 'features/plant/image_classification_page.dart';
import 'features/quiz/quiz_analytics_page.dart';
import 'features/quiz/quiz_page.dart';
import 'features/settings/settings_page.dart';
import 'features/story/story_page.dart';
import 'features/summarizer/summarizer_page.dart';
import 'features/tutor/tutor_page.dart';
import 'features/unified/unified_screen_page.dart';

class ImpactSuiteApp extends StatelessWidget {
  const ImpactSuiteApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'ImpactSuite',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        useMaterial3: true,
      ),
      routerConfig: _router,
    );
  }
}

final _router = GoRouter(
  routes: [
    ShellRoute(
      builder: (context, state, child) => AppScaffold(child: child),
      routes: [
        GoRoute(path: '/', builder: (_, __) => const UnifiedScreenPage()),
        GoRoute(path: '/home', builder: (_, __) => const HomePage()),
        GoRoute(path: '/tutor', builder: (_, __) => const TutorPage()),
        GoRoute(path: '/chat', builder: (_, __) => const ChatListPage()),
        GoRoute(path: '/chat/:sessionId', builder: (_, state) => ChatPage(sessionId: state.pathParameters['sessionId']!)),
        GoRoute(path: '/live_caption', builder: (_, __) => const LiveCaptionPage()),
        GoRoute(path: '/quiz', builder: (_, state) => QuizPage(sourceText: state.extra as String?)),
        GoRoute(path: '/quiz/analytics', builder: (_, __) => const QuizAnalyticsPage()),
        GoRoute(path: '/cbt_coach', builder: (_, __) => const CbtCoachPage()),
        GoRoute(path: '/plant_scanner', builder: (_, __) => const ImageClassificationPage()),
        GoRoute(path: '/summarizer', builder: (_, __) => const SummarizerPage()),
        GoRoute(path: '/story', builder: (_, __) => const StoryPage()),
        GoRoute(path: '/crisis', builder: (_, __) => const CrisisPage()),
        GoRoute(path: '/settings', builder: (_, __) => const SettingsPage()),
        GoRoute(path: '/analytics', builder: (_, __) => const LearningAnalyticsPage()),
      ],
    ),
  ],
);

class AppScaffold extends StatelessWidget {
  const AppScaffold({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: child);
  }
}
