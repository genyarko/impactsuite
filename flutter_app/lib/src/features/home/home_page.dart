import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../data/local/preferences/app_settings_store.dart';
import '../../data/local/preferences/quiz_preferences_store.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _isLoadingSettings = true;
  AppSettings _appSettings = const AppSettings();

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefsStore = SharedPreferencesStore(SharedPreferencesAsync());
    final settingsStore = AppSettingsStore(prefsStore);
    final settings = await settingsStore.read();

    if (!mounted) {
      return;
    }

    setState(() {
      _appSettings = settings;
      _isLoadingSettings = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final modelName = _appSettings.enableOfflineModel ? 'Gemma (on-device preferred)' : 'Online AI provider';

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        children: [
          Center(
            child: Column(
              children: [
                Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        colorScheme.primary,
                        colorScheme.primaryContainer,
                      ],
                    ),
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    'G3N',
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      color: colorScheme.onPrimary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'AI Assistant',
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  'Powered by Gemma',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          if (!_isLoadingSettings)
            Card(
              color: colorScheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(Icons.check_circle, color: colorScheme.primary, size: 24),
                    const SizedBox(width: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'AI Model Ready',
                          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            color: colorScheme.onPrimaryContainer,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Text(
                          modelName,
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: colorScheme.onPrimaryContainer.withValues(alpha: 0.75),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 8),
          Text(
            'Features',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 12),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _featureItems.length,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              crossAxisSpacing: 12,
              mainAxisSpacing: 12,
              childAspectRatio: 1,
            ),
            itemBuilder: (context, index) {
              final feature = _featureItems[index];
              return _FeatureCard(
                feature: feature,
                isLoading: _isLoadingSettings,
                onTap: () {
                  if (feature.enabled) {
                    context.go(feature.route);
                    return;
                  }

                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('${feature.title} is not available yet in Flutter.')),
                  );
                },
              );
            },
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => context.go('/settings'),
            icon: const Icon(Icons.settings),
            label: const Text('Settings'),
          ),
        ],
      ),
    );
  }
}

class _FeatureCard extends StatelessWidget {
  const _FeatureCard({
    required this.feature,
    required this.isLoading,
    required this.onTap,
  });

  final HomeFeatureItem feature;
  final bool isLoading;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Semantics(
      button: true,
      enabled: feature.enabled,
      label: feature.enabled ? 'Open ${feature.title}' : '${feature.title} is not available',
      child: Card(
        color: feature.enabled ? colorScheme.surfaceContainerLow : colorScheme.surfaceContainerLowest,
        elevation: feature.enabled ? 6 : 2,
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: feature.enabled ? onTap : onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                TweenAnimationBuilder<double>(
                  tween: Tween(begin: 1, end: isLoading ? 1.1 : 1),
                  curve: Curves.easeInOut,
                  duration: const Duration(milliseconds: 1000),
                  builder: (context, scale, child) => Transform.scale(scale: scale, child: child),
                  child: Icon(
                    feature.icon,
                    size: 32,
                    color: feature.enabled
                        ? colorScheme.primary
                        : colorScheme.onSurface.withValues(alpha: 0.6),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  feature.title,
                  textAlign: TextAlign.center,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: feature.enabled
                        ? colorScheme.onSurface
                        : colorScheme.onSurface.withValues(alpha: 0.6),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class HomeFeatureItem {
  const HomeFeatureItem({
    required this.title,
    required this.route,
    required this.icon,
    required this.enabled,
  });

  final String title;
  final String route;
  final IconData icon;
  final bool enabled;
}

const _featureItems = <HomeFeatureItem>[
  HomeFeatureItem(title: 'AI Tutor', route: '/tutor', icon: Icons.school, enabled: true),
  HomeFeatureItem(title: 'Live Caption', route: '/live_caption', icon: Icons.closed_caption, enabled: true),
  HomeFeatureItem(title: 'Quiz Generator', route: '/quiz', icon: Icons.quiz, enabled: true),
  HomeFeatureItem(title: 'CBT Coach', route: '/cbt_coach', icon: Icons.psychology, enabled: false),
  HomeFeatureItem(title: 'Chat', route: '/chat', icon: Icons.chat, enabled: true),
  HomeFeatureItem(title: 'Summarizer', route: '/summarizer', icon: Icons.summarize, enabled: true),
  HomeFeatureItem(
    title: 'Image Classification',
    route: '/plant_scanner',
    icon: Icons.photo_camera,
    enabled: true,
  ),
  HomeFeatureItem(title: 'Crisis Handbook', route: '/crisis', icon: Icons.local_hospital, enabled: true),
  HomeFeatureItem(title: 'Learning Analytics', route: '/analytics', icon: Icons.analytics, enabled: false),
  HomeFeatureItem(title: 'Story Mode', route: '/story', icon: Icons.menu_book, enabled: true),
];
