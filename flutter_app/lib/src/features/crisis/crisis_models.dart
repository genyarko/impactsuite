import 'dart:math' as math;

class CrisisContact {
  const CrisisContact({
    required this.service,
    required this.primaryNumber,
    required this.description,
    this.secondaryNumber,
    this.smsNumber,
  });

  final String service;
  final String primaryNumber;
  final String description;
  final String? secondaryNumber;
  final String? smsNumber;
}

class CrisisFacility {
  const CrisisFacility({
    required this.id,
    required this.name,
    required this.address,
    required this.latitude,
    required this.longitude,
    required this.estimatedMinutes,
  });

  final String id;
  final String name;
  final String address;
  final double latitude;
  final double longitude;
  final int estimatedMinutes;

  double distanceKmFrom(double originLat, double originLon) {
    const earthRadiusKm = 6371.0;
    final dLat = _toRadians(latitude - originLat);
    final dLon = _toRadians(longitude - originLon);
    final a = math.pow(math.sin(dLat / 2), 2) +
        math.cos(_toRadians(originLat)) *
            math.cos(_toRadians(latitude)) *
            math.pow(math.sin(dLon / 2), 2);
    final c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a));
    return earthRadiusKm * c;
  }

  double _toRadians(double value) => value * (math.pi / 180);
}

class QuickAction {
  const QuickAction({
    required this.id,
    required this.title,
    required this.icon,
  });

  final String id;
  final String title;
  final String icon;
}
