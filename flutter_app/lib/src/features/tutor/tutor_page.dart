import 'package:flutter/material.dart';

import '../../shared/placeholder_feature_page.dart';

class TutorPage extends StatelessWidget {
  const TutorPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const PlaceholderFeaturePage(
      title: 'AI Tutor',
      description:
          'Migrated surface for context-aware tutoring. Integrate your Gemma/Gemini repository '
          'behind a Dart service and stream response chunks to this page.',
    );
  }
}
