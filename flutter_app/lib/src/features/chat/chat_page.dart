import 'package:flutter/material.dart';

import '../../shared/placeholder_feature_page.dart';

class ChatPage extends StatelessWidget {
  const ChatPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const PlaceholderFeaturePage(
      title: 'Chat',
      description:
          'General assistant chat surface. Replace with your conversation history model and '
          'streaming response UI from the Kotlin implementation.',
    );
  }
}
