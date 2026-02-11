import 'package:flutter/material.dart';

import '../../shared/placeholder_feature_page.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const PlaceholderFeaturePage(
      title: 'Settings',
      description:
          'Migrate DataStore-backed Kotlin settings to SharedPreferences or Drift/Hive depending '
          'on persistence needs.',
    );
  }
}
