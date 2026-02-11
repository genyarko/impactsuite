import 'package:flutter/material.dart';

import '../../shared/placeholder_feature_page.dart';

class QuizPage extends StatelessWidget {
  const QuizPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const PlaceholderFeaturePage(
      title: 'Quiz Generator',
      description:
          'Offline/online quiz generation can be ported by moving prompt generation and history '
          'tracking into pure Dart domain classes.',
    );
  }
}
