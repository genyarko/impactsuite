import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:impactsuite_flutter/src/app.dart';

void main() {
  testWidgets('app renders unified entry screen', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: ImpactSuiteApp()));
    await tester.pumpAndSettle();

    expect(find.text('Unified AI Assistant'), findsOneWidget);
  });
}
