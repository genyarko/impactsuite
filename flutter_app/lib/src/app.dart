import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'features/caption/live_caption_page.dart';
import 'features/chat/chat_page.dart';
import 'features/crisis/crisis_page.dart';
import 'features/home/home_page.dart';
import 'features/plant/image_classification_page.dart';
import 'features/quiz/quiz_page.dart';
import 'features/settings/settings_page.dart';
import 'features/story/story_page.dart';
import 'features/summarizer/summarizer_page.dart';
import 'features/tutor/tutor_page.dart';

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
        GoRoute(path: '/', builder: (_, __) => const HomePage()),
        GoRoute(path: '/tutor', builder: (_, __) => const TutorPage()),
        GoRoute(path: '/chat', builder: (_, __) => const ChatPage()),
        GoRoute(path: '/live_caption', builder: (_, __) => const LiveCaptionPage()),
        GoRoute(path: '/quiz', builder: (_, __) => const QuizPage()),
        GoRoute(path: '/plant_scanner', builder: (_, __) => const ImageClassificationPage()),
        GoRoute(path: '/summarizer', builder: (_, __) => const SummarizerPage()),
        GoRoute(path: '/story', builder: (_, __) => const StoryPage()),
        GoRoute(path: '/crisis', builder: (_, __) => const CrisisPage()),
        GoRoute(path: '/settings', builder: (_, __) => const SettingsPage()),
      ],
    ),
  ],
);

class AppScaffold extends StatelessWidget {
  const AppScaffold({required this.child, super.key});

  final Widget child;

  static const _destinations = [
    (label: 'Home', icon: Icons.home, route: '/'),
    (label: 'Tutor', icon: Icons.school, route: '/tutor'),
    (label: 'Chat', icon: Icons.chat, route: '/chat'),
    (label: 'Quiz', icon: Icons.quiz, route: '/quiz'),
    (label: 'Summary', icon: Icons.description, route: '/summarizer'),
    (label: 'Story', icon: Icons.menu_book, route: '/story'),
    (label: 'Crisis', icon: Icons.health_and_safety, route: '/crisis'),
    (label: 'Settings', icon: Icons.settings, route: '/settings'),
  ];

  @override
  Widget build(BuildContext context) {
    final String location = GoRouterState.of(context).uri.path;
    final selectedIndex = _destinations.indexWhere((d) => d.route == location);

    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: selectedIndex < 0 ? 0 : selectedIndex,
        onDestinationSelected: (index) => context.go(_destinations[index].route),
        destinations: [
          for (final d in _destinations)
            NavigationDestination(icon: Icon(d.icon), label: d.label),
        ],
      ),
    );
  }
}
