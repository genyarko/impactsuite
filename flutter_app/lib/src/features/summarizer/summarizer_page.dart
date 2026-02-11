import 'package:flutter/material.dart';

import '../../shared/placeholder_feature_page.dart';

class SummarizerPage extends StatelessWidget {
  const SummarizerPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const PlaceholderFeaturePage(
      title: 'Document Summarizer',
      description:
          'Add file picker + parser adapters and connect summary generation via the new Flutter AI service layer.',
    );
  }
}
