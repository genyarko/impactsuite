import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import 'crisis_controller.dart';
import 'crisis_models.dart';
import 'crisis_providers.dart';

class CrisisPage extends ConsumerWidget {
  const CrisisPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(crisisControllerProvider);
    final controller = ref.read(crisisControllerProvider.notifier);

    return SafeArea(
      child: Column(
        children: [
          _TopBar(onBack: () => Navigator.maybePop(context)),
          Expanded(
            child: state.showMap
                ? _MapView(
                    facilities: state.facilities,
                    onBack: () => controller.toggleMap(false),
                  )
                : _MainContent(state: state, controller: controller),
          ),
          if (state.isProcessing)
            const LinearProgressIndicator(
              minHeight: 2,
            ),
        ],
      ),
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({required this.onBack});

  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.errorContainer,
      child: ListTile(
        leading: IconButton(onPressed: onBack, icon: const Icon(Icons.arrow_back)),
        title: Row(
          children: [
            Icon(Icons.warning, color: scheme.error),
            const SizedBox(width: 8),
            const Text('Crisis Handbook'),
          ],
        ),
      ),
    );
  }
}

class _MainContent extends StatelessWidget {
  const _MainContent({required this.state, required this.controller});

  final CrisisState state;
  final CrisisController controller;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          color: Theme.of(context).colorScheme.errorContainer,
          child: InkWell(
            onTap: () => _launchUri(context, Uri.parse('tel:112')),
            child: const Padding(
              padding: EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(Icons.phone, size: 32),
                  SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Emergency: 112', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                      Text('Tap for immediate help'),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        const Text('Quick Actions', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        const SizedBox(height: 10),
        SizedBox(
          height: 108,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: CrisisController.quickActions.length,
            separatorBuilder: (_, __) => const SizedBox(width: 12),
            itemBuilder: (context, index) {
              final action = CrisisController.quickActions[index];
              return _QuickActionCard(
                action: action,
                onTap: () => controller.runQuickAction(action.id),
              );
            },
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          minLines: 1,
          maxLines: 3,
          onChanged: controller.setQuery,
          decoration: InputDecoration(
            labelText: 'Describe your emergency',
            hintText: 'e.g., severe headache, car accident, chest pain',
            border: const OutlineInputBorder(),
            suffixIcon: IconButton(
              onPressed: state.queryText.trim().isEmpty || state.isProcessing ? null : controller.submitQuery,
              icon: const Icon(Icons.send),
            ),
          ),
        ),
        if (state.error != null) ...[
          const SizedBox(height: 12),
          Text(state.error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
        if (state.contacts.isNotEmpty) ...[
          const SizedBox(height: 18),
          const Text('Emergency Contacts', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          ...state.contacts.map((contact) => _ContactCard(contact: contact, onTap: () => controller.selectContact(contact))),
        ],
        if (state.facilities.isNotEmpty) ...[
          const SizedBox(height: 18),
          Row(
            children: [
              const Expanded(
                child: Text('Nearby Facilities', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              ),
              TextButton.icon(
                onPressed: () => controller.toggleMap(true),
                icon: const Icon(Icons.map),
                label: const Text('Show Map'),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ...state.facilities.map((facility) => _FacilityCard(facility: facility)),
        ],
        if (state.response != null) ...[
          const SizedBox(height: 18),
          Card(
            color: Theme.of(context).colorScheme.secondaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.info),
                      const SizedBox(width: 8),
                      const Expanded(
                        child: Text('Emergency Guidance', style: TextStyle(fontWeight: FontWeight.bold)),
                      ),
                      Icon(
                        state.isUsingOnlineService ? Icons.cloud : Icons.computer,
                        size: 16,
                        color: state.isUsingOnlineService ? Colors.green : Colors.cyan,
                      ),
                      const SizedBox(width: 4),
                      Text(state.isUsingOnlineService ? 'Online' : 'Offline'),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(state.response!),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _QuickActionCard extends StatelessWidget {
  const _QuickActionCard({required this.action, required this.onTap});

  final QuickAction action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 100,
      child: Card(
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(_iconForAction(action.icon), size: 30),
                const SizedBox(height: 6),
                Text(action.title, textAlign: TextAlign.center, style: const TextStyle(fontSize: 12)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ContactCard extends StatelessWidget {
  const _ContactCard({required this.contact, required this.onTap});

  final CrisisContact contact;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        onTap: () async {
          onTap();
          await showDialog<void>(
            context: context,
            builder: (_) => _ContactDialog(contact: contact),
          );
        },
        leading: const CircleAvatar(child: Icon(Icons.phone)),
        title: Text(contact.description),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Primary: ${contact.primaryNumber}'),
            if (contact.secondaryNumber != null) Text('Secondary: ${contact.secondaryNumber}'),
          ],
        ),
        trailing: const Icon(Icons.chevron_right),
      ),
    );
  }
}

class _ContactDialog extends StatelessWidget {
  const _ContactDialog({required this.contact});

  final CrisisContact contact;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(contact.description),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextButton.icon(
            onPressed: () => _launchUri(context, Uri.parse('tel:${contact.primaryNumber}')),
            icon: const Icon(Icons.call),
            label: Text('Primary: ${contact.primaryNumber}'),
          ),
          if (contact.secondaryNumber != null)
            TextButton.icon(
              onPressed: () => _launchUri(context, Uri.parse('tel:${contact.secondaryNumber}')),
              icon: const Icon(Icons.call),
              label: Text('Secondary: ${contact.secondaryNumber}'),
            ),
          if (contact.smsNumber != null)
            TextButton.icon(
              onPressed: () => _launchUri(context, Uri.parse('sms:${contact.smsNumber}')),
              icon: const Icon(Icons.sms),
              label: Text('SMS: ${contact.smsNumber}'),
            ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Close')),
      ],
    );
  }
}

class _FacilityCard extends StatelessWidget {
  const _FacilityCard({required this.facility});

  final CrisisFacility facility;

  @override
  Widget build(BuildContext context) {
    const userLat = 5.5600;
    const userLon = -0.2057;
    final km = facility.distanceKmFrom(userLat, userLon);

    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const CircleAvatar(child: Icon(Icons.local_hospital)),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(facility.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                      Text(
                        '${facility.address} • ${km.toStringAsFixed(1)} km • ~${facility.estimatedMinutes} min',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                if (facility.specialization != 'general')
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Chip(
                      label: Text(facility.specialization.toUpperCase(), style: const TextStyle(fontSize: 10)),
                      backgroundColor: _specializationColor(facility.specialization),
                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      visualDensity: VisualDensity.compact,
                      padding: EdgeInsets.zero,
                      labelPadding: const EdgeInsets.symmetric(horizontal: 6),
                    ),
                  ),
                if (facility.rating > 0) ...[
                  Icon(Icons.star, size: 14, color: Colors.amber.shade700),
                  const SizedBox(width: 2),
                  Text(facility.rating.toStringAsFixed(1), style: const TextStyle(fontSize: 12)),
                  const SizedBox(width: 8),
                ],
                if (facility.beds > 0) ...[
                  const Icon(Icons.bed, size: 14),
                  const SizedBox(width: 2),
                  Text('${facility.beds} beds', style: const TextStyle(fontSize: 12)),
                ],
                const Spacer(),
                if (facility.phone.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.phone, size: 20),
                    tooltip: 'Call ${facility.phone}',
                    onPressed: () => _launchUri(context, Uri.parse('tel:${facility.phone}')),
                    visualDensity: VisualDensity.compact,
                  ),
                IconButton(
                  icon: const Icon(Icons.directions, size: 20),
                  tooltip: 'Directions',
                  onPressed: () {
                    final uri = Uri.parse(
                      'https://www.google.com/maps/dir/?api=1&destination=${facility.latitude},${facility.longitude}',
                    );
                    _launchUri(context, uri);
                  },
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static Color _specializationColor(String spec) {
    switch (spec) {
      case 'trauma':
        return Colors.red.shade100;
      case 'cardiac':
        return Colors.pink.shade100;
      case 'stroke':
        return Colors.purple.shade100;
      case 'burn':
        return Colors.orange.shade100;
      default:
        return Colors.grey.shade200;
    }
  }
}

class _MapView extends StatelessWidget {
  const _MapView({required this.facilities, required this.onBack});

  final List<CrisisFacility> facilities;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Card(
          margin: const EdgeInsets.all(16),
          child: ListTile(
            leading: const Icon(Icons.map),
            title: const Text('Emergency Facilities Map'),
            subtitle: const Text('Map preview with launch-to-directions support.'),
            trailing: IconButton(onPressed: onBack, icon: const Icon(Icons.close)),
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: facilities.length,
            itemBuilder: (context, index) => _FacilityCard(facility: facilities[index]),
          ),
        ),
      ],
    );
  }
}

IconData _iconForAction(String icon) {
  switch (icon) {
    case 'phone':
      return Icons.phone;
    case 'hospital':
      return Icons.local_hospital;
    case 'medical':
      return Icons.medical_services;
    case 'warning':
      return Icons.warning;
    default:
      return Icons.emergency;
  }
}

const _allowedSchemes = {'tel', 'sms', 'https', 'http', 'geo'};

Future<void> _launchUri(BuildContext context, Uri uri) async {
  if (!_allowedSchemes.contains(uri.scheme)) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Unsupported link type')),
      );
    }
    return;
  }

  final launched = await launchUrl(uri, mode: LaunchMode.externalApplication);
  if (!launched && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Unable to open: $uri')),
    );
  }
}
