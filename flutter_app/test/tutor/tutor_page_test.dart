import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_models.dart';
import 'package:impactsuite_flutter/src/domain/services/ai/ai_repository.dart';
import 'package:impactsuite_flutter/src/features/ai/ai_providers.dart';
import 'package:impactsuite_flutter/src/features/tutor/tutor_page.dart';

void main() {
  testWidgets('shows welcome subjects and starts a tutoring session', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          unifiedAiRepositoryProvider.overrideWithValue(
            _FakeAiRepository(streamChunks: const ['Great! Let us begin.']),
          ),
        ],
        child: const MaterialApp(home: Scaffold(body: TutorPage())),
      ),
    );

    expect(find.text('Welcome to AI Tutor'), findsOneWidget);
    expect(find.text('Mathematics'), findsOneWidget);

    await tester.tap(find.text('Mathematics'));
    await tester.pumpAndSettle();

    expect(find.text('Start Learning Mathematics'), findsOneWidget);

    await tester.tap(find.text('Start Learning Mathematics'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Mathematics • Practice Problems'), findsOneWidget);
    expect(find.text('Great! Let us begin.'), findsOneWidget);
  });
}

class _FakeAiRepository implements AiRepository {
  _FakeAiRepository({required this.streamChunks});

  final List<String> streamChunks;

  @override
  Future<AiGenerationResult> generate(AiGenerationRequest request) async {
    return const AiGenerationResult(
      provider: AiProvider.gemini,
      text: 'fallback',
      model: 'test-model',
    );
  }

  @override
  Stream<String> stream(AiGenerationRequest request) async* {
    for (final chunk in streamChunks) {
      yield chunk;
    }
  }
}
