import 'package:flutter_test/flutter_test.dart';
import 'package:impactsuite_flutter/src/features/crisis/crisis_controller.dart';

void main() {
  test('quick action nearest hospital populates facilities', () {
    final controller = CrisisController();

    controller.runQuickAction('nearest_hospital');

    expect(controller.state.facilities, isNotEmpty);
    expect(controller.state.response, contains('nearby hospitals'));
  });

  test('submitQuery for contact returns emergency contacts', () async {
    final controller = CrisisController();
    controller.setQuery('need police contact number');

    await controller.submitQuery();

    expect(controller.state.contacts, isNotEmpty);
    expect(controller.state.contacts.first.service, 'police');
    expect(controller.state.isProcessing, isFalse);
  });

  test('submitQuery for chest pain returns immediate emergency response', () async {
    final controller = CrisisController();
    controller.setQuery('I have chest pain and shortness of breath');

    await controller.submitQuery();

    expect(controller.state.response, contains('CHEST PAIN'));
    expect(controller.state.contacts, isEmpty);
  });
}
