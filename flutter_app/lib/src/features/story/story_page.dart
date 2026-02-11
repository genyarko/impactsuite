import 'package:flutter/material.dart';

import '../../shared/placeholder_feature_page.dart';

class StoryPage extends StatelessWidget {
  const StoryPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const PlaceholderFeaturePage(
      title: 'Story Creator',
      description:
          'Story templates, character builder, and image generation queues should be ported as '
          'feature modules under /lib/src/features/story.',
    );
  }
}
