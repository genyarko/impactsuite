import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:impactsuite_flutter/src/app.dart';

void main() {
  testWidgets('app renders home title', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: ImpactSuiteApp()));

    expect(find.text('ImpactSuite Flutter'), findsOneWidget);
  });
}
