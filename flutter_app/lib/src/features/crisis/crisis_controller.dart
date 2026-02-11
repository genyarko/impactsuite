import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'crisis_models.dart';

class CrisisState {
  const CrisisState({
    this.isProcessing = false,
    this.response,
    this.error,
    this.queryText = '',
    this.showMap = false,
    this.isUsingOnlineService = false,
    this.contacts = const <CrisisContact>[],
    this.facilities = const <CrisisFacility>[],
    this.selectedContact,
  });

  final bool isProcessing;
  final String? response;
  final String? error;
  final String queryText;
  final bool showMap;
  final bool isUsingOnlineService;
  final List<CrisisContact> contacts;
  final List<CrisisFacility> facilities;
  final CrisisContact? selectedContact;

  CrisisState copyWith({
    bool? isProcessing,
    Object? response = _noChange,
    Object? error = _noChange,
    String? queryText,
    bool? showMap,
    bool? isUsingOnlineService,
    List<CrisisContact>? contacts,
    List<CrisisFacility>? facilities,
    Object? selectedContact = _noChange,
  }) {
    return CrisisState(
      isProcessing: isProcessing ?? this.isProcessing,
      response: identical(response, _noChange) ? this.response : response as String?,
      error: identical(error, _noChange) ? this.error : error as String?,
      queryText: queryText ?? this.queryText,
      showMap: showMap ?? this.showMap,
      isUsingOnlineService: isUsingOnlineService ?? this.isUsingOnlineService,
      contacts: contacts ?? this.contacts,
      facilities: facilities ?? this.facilities,
      selectedContact: identical(selectedContact, _noChange)
          ? this.selectedContact
          : selectedContact as CrisisContact?,
    );
  }
}

const _noChange = Object();

class CrisisController extends StateNotifier<CrisisState> {
  CrisisController() : super(const CrisisState());

  static const List<CrisisContact> _localContacts = [
    CrisisContact(
      service: 'police',
      primaryNumber: '191',
      secondaryNumber: '18555',
      description: 'Ghana Police Service',
    ),
    CrisisContact(
      service: 'fire',
      primaryNumber: '192',
      secondaryNumber: '0302772446',
      description: 'Ghana National Fire Service',
    ),
    CrisisContact(
      service: 'ambulance',
      primaryNumber: '193',
      secondaryNumber: '0302773906',
      description: 'National Ambulance Service',
    ),
    CrisisContact(
      service: 'emergency',
      primaryNumber: '112',
      description: 'National Emergency Number',
    ),
  ];

  static const List<CrisisFacility> _facilitySeed = [
    CrisisFacility(
      id: 'korle-bu',
      name: 'Korle Bu Teaching Hospital',
      address: 'Guggisberg Ave, Accra',
      latitude: 5.5367,
      longitude: -0.2298,
      estimatedMinutes: 14,
    ),
    CrisisFacility(
      id: '37-military',
      name: '37 Military Hospital',
      address: 'Liberation Rd, Accra',
      latitude: 5.5736,
      longitude: -0.1934,
      estimatedMinutes: 18,
    ),
    CrisisFacility(
      id: 'ugmc',
      name: 'University of Ghana Medical Centre',
      address: 'Legon, Accra',
      latitude: 5.6507,
      longitude: -0.1850,
      estimatedMinutes: 24,
    ),
  ];

  static const List<QuickAction> quickActions = [
    QuickAction(id: 'call_emergency', title: 'Call Emergency', icon: 'phone'),
    QuickAction(id: 'nearest_hospital', title: 'Nearest Hospital', icon: 'hospital'),
    QuickAction(id: 'first_aid', title: 'First Aid', icon: 'medical'),
    QuickAction(id: 'report_accident', title: 'Report Accident', icon: 'warning'),
  ];

  void setQuery(String value) {
    state = state.copyWith(queryText: value, error: null);
  }

  void toggleMap(bool value) {
    state = state.copyWith(showMap: value, error: null);
  }

  void selectContact(CrisisContact? contact) {
    state = state.copyWith(selectedContact: contact);
  }

  void runQuickAction(String actionId) {
    switch (actionId) {
      case 'call_emergency':
        state = state.copyWith(
          contacts: _localContacts,
          response: 'Emergency lines ready. Call 112 or 193 immediately for urgent help.',
          facilities: const [],
          error: null,
          showMap: false,
        );
        break;
      case 'nearest_hospital':
        _showNearestHospitals();
        break;
      case 'first_aid':
        state = state.copyWith(
          response:
              '🏥 First Aid Basics\n\n1. Ensure scene safety first.\n2. Check breathing and consciousness.\n3. Control bleeding with direct pressure.\n4. Keep patient warm and calm.\n5. Call 193/112 for severe symptoms.',
          contacts: const [],
          facilities: const [],
          error: null,
          showMap: false,
        );
        break;
      case 'report_accident':
        state = state.copyWith(
          response:
              '⚠️ Accident Reporting Guide\n\n• Move to a safe location.\n• Call 191 (Police) and 193 (Ambulance) if injuries exist.\n• Share exact location, number of victims, and hazards.\n• Take photos only when safe.\n• Stay available for responders.',
          contacts: const [],
          facilities: const [],
          error: null,
          showMap: false,
        );
        break;
      default:
        state = state.copyWith(error: 'Unknown action selected.');
    }
  }

  Future<void> submitQuery() async {
    final query = state.queryText.trim();
    if (query.isEmpty) {
      state = state.copyWith(error: 'Please describe your emergency.');
      return;
    }

    state = state.copyWith(isProcessing: true, error: null, response: null, showMap: false);
    await Future<void>.delayed(const Duration(milliseconds: 250));

    final lower = query.toLowerCase();

    final immediate = _immediateResponse(lower);
    if (immediate != null) {
      state = state.copyWith(isProcessing: false, response: immediate, contacts: const [], facilities: const []);
      return;
    }

    if (_isHospitalQuery(lower)) {
      _showNearestHospitals();
      return;
    }

    if (_isContactQuery(lower)) {
      final filtered = _filterContacts(lower);
      state = state.copyWith(
        isProcessing: false,
        contacts: filtered,
        facilities: const [],
        response: 'Emergency contacts matched your request. Tap one for call/SMS options.',
      );
      return;
    }

    if (_isFirstAidQuery(lower)) {
      state = state.copyWith(
        isProcessing: false,
        contacts: const [],
        facilities: const [],
        response:
            '🚨 Emergency Guidance\n\n• Protect life first: ensure scene safety.\n• If severe pain, chest pressure, heavy bleeding, breathing trouble, or unconsciousness: call 112/193 now.\n• Keep the person still, monitor breathing, and avoid giving food/drink unless advised by professionals.',
      );
      return;
    }

    state = state.copyWith(
      isProcessing: false,
      contacts: _localContacts,
      facilities: const [],
      response:
          'I can help with first aid, emergency contacts, and nearest hospitals. If this is life-threatening, call 112 immediately.',
    );
  }

  void _showNearestHospitals() {
    const userLat = 5.5600;
    const userLon = -0.2057;

    final sorted = [..._facilitySeed]..sort(
      (a, b) => a.distanceKmFrom(userLat, userLon).compareTo(b.distanceKmFrom(userLat, userLon)),
    );

    state = state.copyWith(
      isProcessing: false,
      facilities: sorted,
      contacts: const [],
      response:
          'Found ${sorted.length} nearby hospitals. Open map view for directions or tap directions from a facility card.',
    );
  }

  List<CrisisContact> _filterContacts(String lowerQuery) {
    if (lowerQuery.contains('police')) {
      return _localContacts.where((c) => c.service == 'police').toList();
    }
    if (lowerQuery.contains('fire')) {
      return _localContacts.where((c) => c.service == 'fire').toList();
    }
    if (lowerQuery.contains('ambulance') || lowerQuery.contains('medical')) {
      return _localContacts.where((c) => c.service == 'ambulance').toList();
    }
    return _localContacts;
  }

  bool _isHospitalQuery(String query) {
    const keywords = ['hospital', 'clinic', 'medical center', 'nearest', 'emergency room'];
    return keywords.any(query.contains);
  }

  bool _isContactQuery(String query) {
    const keywords = ['call', 'number', 'contact', 'phone', 'emergency number'];
    return keywords.any(query.contains);
  }

  bool _isFirstAidQuery(String query) {
    const keywords = [
      'first aid',
      'hurt',
      'pain',
      'injury',
      'bleeding',
      'burn',
      'cut',
      'fall',
      'fracture',
      'wound',
      'choke',
      'faint',
      'cpr',
    ];
    return keywords.any(query.contains);
  }

  String? _immediateResponse(String query) {
    if (query.contains('chest') && query.contains('pain')) {
      return '🚨 CHEST PAIN\n\nCall 193 or 112 immediately if symptoms are severe. Sit and rest, loosen tight clothing, and monitor breathing. Do not drive yourself.';
    }
    if (query.contains('bleeding')) {
      return '🩸 SEVERE BLEEDING\n\nApply direct pressure with a clean cloth, elevate the area if possible, and call 193 if bleeding is heavy or does not stop.';
    }
    if (query.contains('burn')) {
      return '🔥 BURN FIRST AID\n\nCool under running water for 10-20 minutes. Do not use ice or butter. Cover with clean dry cloth. Seek urgent care for large/deep burns.';
    }
    if (query.contains('choking')) {
      return '🆘 CHOKING\n\nIf person cannot speak/cough, start abdominal thrusts immediately and call 193. If unconscious, begin CPR and continue until help arrives.';
    }
    return null;
  }
}
