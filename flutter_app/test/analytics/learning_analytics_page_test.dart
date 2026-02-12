import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/features/analytics/learning_analytics_page.dart';

void main() {
  testWidgets('renders analytics dashboard sections', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: LearningAnalyticsPage(),
        ),
      ),
    );

    expect(find.text('Quiz Analytics Dashboard'), findsOneWidget);
    expect(find.text('Learning Overview'), findsOneWidget);
    expect(find.text('Weekly Progress'), findsOneWidget);
    expect(find.text('Subject Performance'), findsOneWidget);
    expect(find.text('Frequently Missed Questions'), findsOneWidget);
    expect(find.text('Topic Coverage'), findsOneWidget);
    expect(find.text('Concept Mastery'), findsOneWidget);
    expect(find.text('Performance by Difficulty'), findsOneWidget);
  });
}
