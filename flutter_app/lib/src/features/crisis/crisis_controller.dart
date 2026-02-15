import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';
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
  CrisisController({required AiRepository repository})
      : _repository = repository,
        super(const CrisisState());

  final AiRepository _repository;

  // ── Emergency contacts (8) ──────────────────────────────────────────

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
    CrisisContact(
      service: 'poison',
      primaryNumber: '0302665065',
      description: 'Poison Control Centre',
    ),
    CrisisContact(
      service: 'disaster',
      primaryNumber: '0302772446',
      smsNumber: '1070',
      description: 'National Disaster Management (NADMO)',
    ),
    CrisisContact(
      service: 'domestic_violence',
      primaryNumber: '0551000900',
      description: 'Domestic Violence & Victim Support (DOVVSU)',
    ),
    CrisisContact(
      service: 'mental_health',
      primaryNumber: '0244846701',
      description: 'Mental Health Authority Helpline',
    ),
  ];

  // ── Facilities (~24) ────────────────────────────────────────────────

  static const List<CrisisFacility> _facilitySeed = [
    // Accra (5)
    CrisisFacility(
      id: 'korle-bu',
      name: 'Korle Bu Teaching Hospital',
      address: 'Guggisberg Ave, Accra',
      latitude: 5.5367,
      longitude: -0.2298,
      estimatedMinutes: 14,
      phone: '0302673200',
      specialization: 'general',
      beds: 2000,
      rating: 4.2,
    ),
    CrisisFacility(
      id: '37-military',
      name: '37 Military Hospital',
      address: 'Liberation Rd, Accra',
      latitude: 5.5736,
      longitude: -0.1934,
      estimatedMinutes: 18,
      phone: '0302776111',
      specialization: 'trauma',
      beds: 600,
      rating: 4.3,
    ),
    CrisisFacility(
      id: 'ugmc',
      name: 'University of Ghana Medical Centre',
      address: 'Legon, Accra',
      latitude: 5.6507,
      longitude: -0.1850,
      estimatedMinutes: 24,
      phone: '0555185185',
      specialization: 'general',
      beds: 650,
      rating: 4.5,
    ),
    CrisisFacility(
      id: 'ridge',
      name: 'Ridge Hospital',
      address: 'Castle Rd, Accra',
      latitude: 5.5568,
      longitude: -0.1969,
      estimatedMinutes: 16,
      phone: '0302228483',
      specialization: 'general',
      beds: 420,
      rating: 4.0,
    ),
    CrisisFacility(
      id: 'police-hospital',
      name: 'Police Hospital',
      address: 'Ring Rd East, Accra',
      latitude: 5.5640,
      longitude: -0.1889,
      estimatedMinutes: 20,
      phone: '0302773906',
      specialization: 'trauma',
      beds: 200,
      rating: 3.8,
    ),
    // Kumasi (4)
    CrisisFacility(
      id: 'kath',
      name: 'Komfo Anokye Teaching Hospital',
      address: 'Bantama, Kumasi',
      latitude: 6.6940,
      longitude: -1.6277,
      estimatedMinutes: 180,
      phone: '0322022301',
      specialization: 'general',
      beds: 1200,
      rating: 4.1,
    ),
    CrisisFacility(
      id: 'kumasi-south',
      name: 'Kumasi South Hospital',
      address: 'Atonsu, Kumasi',
      latitude: 6.6600,
      longitude: -1.6250,
      estimatedMinutes: 185,
      phone: '0322039870',
      specialization: 'general',
      beds: 250,
      rating: 3.7,
    ),
    CrisisFacility(
      id: 'manhyia',
      name: 'Manhyia District Hospital',
      address: 'Manhyia, Kumasi',
      latitude: 6.7130,
      longitude: -1.6160,
      estimatedMinutes: 182,
      phone: '0322022487',
      specialization: 'general',
      beds: 180,
      rating: 3.6,
    ),
    CrisisFacility(
      id: 'suntreso',
      name: 'Suntreso Government Hospital',
      address: 'Suntreso, Kumasi',
      latitude: 6.7010,
      longitude: -1.6440,
      estimatedMinutes: 183,
      phone: '0322024225',
      specialization: 'general',
      beds: 200,
      rating: 3.5,
    ),
    // Takoradi (2)
    CrisisFacility(
      id: 'effia-nkwanta',
      name: 'Effia Nkwanta Regional Hospital',
      address: 'Sekondi-Takoradi',
      latitude: 4.9230,
      longitude: -1.7560,
      estimatedMinutes: 210,
      phone: '0312022100',
      specialization: 'general',
      beds: 400,
      rating: 3.9,
    ),
    CrisisFacility(
      id: 'takoradi-hospital',
      name: 'Takoradi Hospital',
      address: 'Market Circle, Takoradi',
      latitude: 4.8960,
      longitude: -1.7610,
      estimatedMinutes: 215,
      phone: '0312022200',
      specialization: 'general',
      beds: 150,
      rating: 3.5,
    ),
    // Cape Coast (2)
    CrisisFacility(
      id: 'cape-coast-th',
      name: 'Cape Coast Teaching Hospital',
      address: 'Interberton, Cape Coast',
      latitude: 5.1104,
      longitude: -1.2450,
      estimatedMinutes: 130,
      phone: '0332132174',
      specialization: 'general',
      beds: 400,
      rating: 4.0,
    ),
    CrisisFacility(
      id: 'ucc-hospital',
      name: 'UCC Hospital',
      address: 'University of Cape Coast',
      latitude: 5.1150,
      longitude: -1.2900,
      estimatedMinutes: 135,
      phone: '0332137460',
      specialization: 'general',
      beds: 100,
      rating: 3.8,
    ),
    // Tamale (2)
    CrisisFacility(
      id: 'tth',
      name: 'Tamale Teaching Hospital',
      address: 'Tamale',
      latitude: 9.4010,
      longitude: -0.8440,
      estimatedMinutes: 480,
      phone: '0372022455',
      specialization: 'general',
      beds: 450,
      rating: 3.9,
    ),
    CrisisFacility(
      id: 'tamale-west',
      name: 'Tamale West Hospital',
      address: 'Vittin, Tamale',
      latitude: 9.3920,
      longitude: -0.8600,
      estimatedMinutes: 485,
      phone: '0372023100',
      specialization: 'general',
      beds: 120,
      rating: 3.4,
    ),
    // Ho (2)
    CrisisFacility(
      id: 'ho-municipal',
      name: 'Ho Municipal Hospital',
      address: 'Ho',
      latitude: 6.6000,
      longitude: 0.4710,
      estimatedMinutes: 160,
      phone: '0362026291',
      specialization: 'general',
      beds: 200,
      rating: 3.6,
    ),
    CrisisFacility(
      id: 'ho-teaching',
      name: 'Ho Teaching Hospital',
      address: 'Trafalgar, Ho',
      latitude: 6.6080,
      longitude: 0.4690,
      estimatedMinutes: 162,
      phone: '0362027500',
      specialization: 'general',
      beds: 300,
      rating: 3.8,
    ),
    // Koforidua (2)
    CrisisFacility(
      id: 'koforidua-regional',
      name: 'Eastern Regional Hospital',
      address: 'Koforidua',
      latitude: 6.0940,
      longitude: -0.2610,
      estimatedMinutes: 80,
      phone: '0342022003',
      specialization: 'general',
      beds: 350,
      rating: 3.7,
    ),
    CrisisFacility(
      id: 'st-josephs',
      name: "St. Joseph's Hospital",
      address: 'Koforidua',
      latitude: 6.0900,
      longitude: -0.2560,
      estimatedMinutes: 82,
      phone: '0342023651',
      specialization: 'general',
      beds: 150,
      rating: 3.9,
    ),
    // Tema (2)
    CrisisFacility(
      id: 'tema-general',
      name: 'Tema General Hospital',
      address: 'Community 1, Tema',
      latitude: 5.6690,
      longitude: -0.0166,
      estimatedMinutes: 35,
      phone: '0303206161',
      specialization: 'general',
      beds: 300,
      rating: 3.8,
    ),
    CrisisFacility(
      id: 'lekma',
      name: 'LEKMA Hospital',
      address: 'Teshie, Accra',
      latitude: 5.5850,
      longitude: -0.1100,
      estimatedMinutes: 28,
      phone: '0302716994',
      specialization: 'general',
      beds: 120,
      rating: 3.5,
    ),
    // Sunyani (1)
    CrisisFacility(
      id: 'sunyani-regional',
      name: 'Sunyani Regional Hospital',
      address: 'Sunyani',
      latitude: 7.3350,
      longitude: -2.3360,
      estimatedMinutes: 300,
      phone: '0352027358',
      specialization: 'general',
      beds: 250,
      rating: 3.6,
    ),
    // Bolgatanga (1)
    CrisisFacility(
      id: 'bolga-regional',
      name: 'Upper East Regional Hospital',
      address: 'Bolgatanga',
      latitude: 10.7870,
      longitude: -0.8500,
      estimatedMinutes: 600,
      phone: '0382022058',
      specialization: 'general',
      beds: 200,
      rating: 3.5,
    ),
    // Wa (1)
    CrisisFacility(
      id: 'wa-regional',
      name: 'Wa Regional Hospital',
      address: 'Wa',
      latitude: 10.0600,
      longitude: -2.5000,
      estimatedMinutes: 540,
      phone: '0392022025',
      specialization: 'general',
      beds: 180,
      rating: 3.4,
    ),
  ];

  static const List<QuickAction> quickActions = [
    QuickAction(id: 'call_emergency', title: 'Call Emergency', icon: 'phone'),
    QuickAction(id: 'nearest_hospital', title: 'Nearest Hospital', icon: 'hospital'),
    QuickAction(id: 'first_aid', title: 'First Aid', icon: 'medical'),
    QuickAction(id: 'report_accident', title: 'Report Accident', icon: 'warning'),
  ];

  // ── Public API ──────────────────────────────────────────────────────

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

    // 1. Immediate keyword responses (sync, fastest)
    final immediate = _immediateResponse(lower);
    if (immediate != null) {
      state = state.copyWith(isProcessing: false, response: immediate, contacts: const [], facilities: const []);
      return;
    }

    // 2. Hospital queries
    if (_isHospitalQuery(lower)) {
      _showNearestHospitals();
      return;
    }

    // 3. Contact queries
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

    // 4. First aid queries — try AI, fallback to hardcoded
    if (_isFirstAidQuery(lower)) {
      final aiResponse = await _generateFirstAid(query);
      state = state.copyWith(
        isProcessing: false,
        isUsingOnlineService: aiResponse != null,
        contacts: const [],
        facilities: const [],
        response: aiResponse ??
            '🚨 Emergency Guidance\n\n'
                '• Protect life first: ensure scene safety.\n'
                '• If severe pain, chest pressure, heavy bleeding, breathing trouble, or unconsciousness: call 112/193 now.\n'
                '• Keep the person still, monitor breathing, and avoid giving food/drink unless advised by professionals.',
      );
      return;
    }

    // 5. General query — try AI, fallback to generic
    final aiResponse = await _generateAiResponse(query);
    state = state.copyWith(
      isProcessing: false,
      isUsingOnlineService: aiResponse != null,
      contacts: _localContacts,
      facilities: const [],
      response: aiResponse ??
          'I can help with first aid, emergency contacts, and nearest hospitals. If this is life-threatening, call 112 immediately.',
    );
  }

  // ── AI generation ───────────────────────────────────────────────────

  Future<String?> _generateAiResponse(String query) async {
    try {
      final result = await _repository.generate(
        AiGenerationRequest(
          prompt: 'You are an emergency response assistant for Ghana. '
              'Provide clear, actionable guidance for this emergency situation. '
              'Include relevant Ghana emergency numbers (112, 191, 192, 193). '
              'Keep response concise and prioritize life-saving actions.\n\n'
              'User query: $query',
          temperature: 0.3,
          maxOutputTokens: 512,
        ),
      );
      return result.text.trim().isEmpty ? null : result.text.trim();
    } catch (_) {
      return null;
    }
  }

  Future<String?> _generateFirstAid(String query) async {
    try {
      final result = await _repository.generate(
        AiGenerationRequest(
          prompt: 'You are a first aid expert assistant. '
              'Provide step-by-step first aid instructions for this situation. '
              'Use numbered steps. Include warnings about when to seek professional help. '
              'Be specific and practical. Do not diagnose — only provide first aid guidance.\n\n'
              'Situation: $query',
          temperature: 0.3,
          maxOutputTokens: 512,
        ),
      );
      return result.text.trim().isEmpty ? null : result.text.trim();
    } catch (_) {
      return null;
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────

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
          'Found ${sorted.length} nearby hospitals. Open map view for directions or tap a facility card for details.',
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
    if (lowerQuery.contains('poison')) {
      return _localContacts.where((c) => c.service == 'poison').toList();
    }
    if (lowerQuery.contains('domestic') ||
        lowerQuery.contains('violence') ||
        lowerQuery.contains('abuse')) {
      return _localContacts.where((c) => c.service == 'domestic_violence').toList();
    }
    if (lowerQuery.contains('mental') ||
        lowerQuery.contains('suicide') ||
        lowerQuery.contains('depression')) {
      return _localContacts.where((c) => c.service == 'mental_health').toList();
    }
    if (lowerQuery.contains('disaster') ||
        lowerQuery.contains('flood') ||
        lowerQuery.contains('earthquake')) {
      return _localContacts.where((c) => c.service == 'disaster').toList();
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
      'sprain',
      'bite',
      'sting',
      'swallow',
      'poison',
    ];
    return keywords.any(query.contains);
  }

  String? _immediateResponse(String query) {
    if (query.contains('chest') && query.contains('pain')) {
      return '🚨 CHEST PAIN\n\nCall 193 or 112 immediately if symptoms are severe. '
          'Sit and rest, loosen tight clothing, and monitor breathing. Do not drive yourself.';
    }
    if (query.contains('bleeding')) {
      return '🩸 SEVERE BLEEDING\n\nApply direct pressure with a clean cloth, '
          'elevate the area if possible, and call 193 if bleeding is heavy or does not stop.';
    }
    if (query.contains('burn')) {
      return '🔥 BURN FIRST AID\n\nCool under running water for 10-20 minutes. '
          'Do not use ice or butter. Cover with clean dry cloth. Seek urgent care for large/deep burns.';
    }
    if (query.contains('choking')) {
      return '🆘 CHOKING\n\nIf person cannot speak/cough, start abdominal thrusts immediately '
          'and call 193. If unconscious, begin CPR and continue until help arrives.';
    }
    if (query.contains('faint') || query.contains('unconscious')) {
      return '😵 FAINTING / UNCONSCIOUSNESS\n\nLay person on their back, elevate legs. '
          'Check for breathing. If not breathing, call 193 and begin CPR. '
          'If breathing, place in recovery position and monitor.';
    }
    if (query.contains('fracture') || query.contains('broken bone')) {
      return '🦴 FRACTURE\n\nDo not move the injured limb. Immobilize with a splint if available. '
          'Apply ice wrapped in cloth. Call 193 for suspected spine/hip/thigh fractures. '
          'Go to nearest hospital for X-ray.';
    }
    if ((query.contains('knee') || query.contains('fall') || query.contains('bike')) &&
        (query.contains('hurt') || query.contains('pain') || query.contains('injury'))) {
      return '🏃 FALL / KNEE INJURY\n\nApply RICE: Rest, Ice (20 min on, 20 min off), '
          'Compression with bandage, Elevation above heart level. '
          'Seek medical attention if unable to bear weight or severe swelling.';
    }
    if (query.contains('breathing') && (query.contains('difficult') || query.contains('trouble') || query.contains('hard'))) {
      return '😤 BREATHING DIFFICULTY\n\nSit upright, loosen tight clothing, stay calm. '
          'If severe: call 193/112 immediately. Use inhaler if prescribed. '
          'Do not give food/drink. Monitor until help arrives.';
    }
    if (query.contains('poison') && (query.contains('swallow') || query.contains('drink') || query.contains('ate'))) {
      return '☠️ POISONING\n\nCall Poison Control: 0302665065 or 112 immediately. '
          'Do NOT induce vomiting unless directed by medical professional. '
          'Save the container/label. Note time and amount ingested.';
    }
    if (query.contains('seizure') || query.contains('convulsion')) {
      return '⚡ SEIZURE\n\nClear the area of hard objects. Do NOT hold the person down '
          'or put anything in their mouth. Time the seizure. '
          'Call 193 if seizure lasts >5 minutes or person does not regain consciousness.';
    }
    return null;
  }
}
