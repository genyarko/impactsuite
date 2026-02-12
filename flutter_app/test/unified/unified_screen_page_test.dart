import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:impactsuite_flutter/src/features/unified/unified_screen_page.dart';

void main() {
  testWidgets('shows unified empty state and quick commands', (tester) async {
    final router = GoRouter(
      routes: [
        GoRoute(path: '/', builder: (_, __) => const UnifiedScreenPage()),
        GoRoute(path: '/quiz', builder: (_, __) => const Scaffold(body: Text('Quiz'))),
        GoRoute(path: '/tutor', builder: (_, __) => const Scaffold(body: Text('Tutor'))),
        GoRoute(path: '/home', builder: (_, __) => const Scaffold(body: Text('Home'))),
        GoRoute(path: '/settings', builder: (_, __) => const Scaffold(body: Text('Settings'))),
        GoRoute(path: '/summarizer', builder: (_, __) => const Scaffold(body: Text('Summarizer'))),
        GoRoute(path: '/plant_scanner', builder: (_, __) => const Scaffold(body: Text('Scanner'))),
        GoRoute(path: '/live_caption', builder: (_, __) => const Scaffold(body: Text('Caption'))),
        GoRoute(path: '/cbt_coach', builder: (_, __) => const Scaffold(body: Text('CBT'))),
        GoRoute(path: '/story', builder: (_, __) => const Scaffold(body: Text('Story'))),
        GoRoute(path: '/crisis', builder: (_, __) => const Scaffold(body: Text('Crisis'))),
        GoRoute(path: '/analytics', builder: (_, __) => const Scaffold(body: Text('Analytics'))),
      ],
    );

    await tester.pumpWidget(ProviderScope(child: MaterialApp.router(routerConfig: router)));
    await tester.pumpAndSettle();

    expect(find.text('Unified AI Assistant'), findsOneWidget);
    expect(find.text('/quiz'), findsOneWidget);
    expect(find.textContaining('slash commands'), findsOneWidget);
  });

  testWidgets('slash command navigates to feature route', (tester) async {
    final router = GoRouter(
      routes: [
        GoRoute(path: '/', builder: (_, __) => const UnifiedScreenPage()),
        GoRoute(path: '/quiz', builder: (_, __) => const Scaffold(body: Text('Quiz page'))),
        GoRoute(path: '/home', builder: (_, __) => const Scaffold(body: Text('Home'))),
        GoRoute(path: '/settings', builder: (_, __) => const Scaffold(body: Text('Settings'))),
        GoRoute(path: '/summarizer', builder: (_, __) => const Scaffold(body: Text('Summarizer'))),
        GoRoute(path: '/plant_scanner', builder: (_, __) => const Scaffold(body: Text('Scanner'))),
        GoRoute(path: '/live_caption', builder: (_, __) => const Scaffold(body: Text('Caption'))),
        GoRoute(path: '/cbt_coach', builder: (_, __) => const Scaffold(body: Text('CBT'))),
        GoRoute(path: '/story', builder: (_, __) => const Scaffold(body: Text('Story'))),
        GoRoute(path: '/crisis', builder: (_, __) => const Scaffold(body: Text('Crisis'))),
        GoRoute(path: '/analytics', builder: (_, __) => const Scaffold(body: Text('Analytics'))),
        GoRoute(path: '/tutor', builder: (_, __) => const Scaffold(body: Text('Tutor'))),
      ],
    );

    await tester.pumpWidget(ProviderScope(child: MaterialApp.router(routerConfig: router)));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '/quiz');
    await tester.pumpAndSettle();

    expect(find.text('Quiz page'), findsOneWidget);
  });
}
