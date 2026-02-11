import 'package:flutter/material.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(24),
        children: const [
          Text('ImpactSuite Flutter', style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold)),
          SizedBox(height: 16),
          Text(
            'This is the Flutter rewrite entry point for the original Kotlin app. '
            'Each tab maps to a feature area and can be migrated incrementally.',
          ),
        ],
      ),
    );
  }
}
