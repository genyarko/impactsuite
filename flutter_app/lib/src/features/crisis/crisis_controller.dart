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
              '⚠️ Accident Reporting Guide\n\n'
              '**Immediate Steps:**\n'
              '1. Ensure your own safety first — move away from traffic or hazards.\n'
              '2. Check for injuries. If anyone is hurt, call 193 (Ambulance) immediately.\n'
              '3. Call 191 (Police) to report the accident.\n'
              '4. If there is fire or fuel spill, call 192 (Fire Service).\n\n'
              '**Information to Provide:**\n'
              '• Your exact location (road name, landmark, GPS coordinates)\n'
              '• Number of vehicles and people involved\n'
              '• Type and severity of injuries\n'
              '• Any hazards (fuel leak, blocked road, downed power lines)\n\n'
              '**While Waiting for Help:**\n'
              '• Do NOT move severely injured people unless in immediate danger\n'
              '• Apply direct pressure to any bleeding wounds\n'
              '• Keep injured persons warm and calm\n'
              '• Turn off vehicle ignitions if safe to do so\n'
              '• Set up warning triangles or use hazard lights\n'
              '• Take photos of the scene, vehicle positions, and damage\n'
              '• Exchange details with other parties (name, phone, insurance, plate number)\n\n'
              '**After the Incident:**\n'
              '• Obtain a police report from the nearest station\n'
              '• Notify your insurance company within 24 hours\n'
              '• Seek medical evaluation even if you feel fine — some injuries appear later',
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
      state = state.copyWith(
        isProcessing: false,
        response: immediate,
        contacts: const [],
        facilities: const [],
      );
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

    // 4. First aid queries — try AI, fallback to offline knowledge base
    if (_isFirstAidQuery(lower)) {
      final aiResponse = await _generateFirstAid(query);
      if (aiResponse != null) {
        state = state.copyWith(
          isProcessing: false,
          isUsingOnlineService: true,
          contacts: const [],
          facilities: const [],
          response: aiResponse,
        );
        return;
      }
      // Offline: use specific first-aid knowledge base
      final offlineResponse = _offlineFirstAidResponse(lower);
      state = state.copyWith(
        isProcessing: false,
        isUsingOnlineService: false,
        contacts: const [],
        facilities: const [],
        response: offlineResponse,
      );
      return;
    }

    // 5. General query — try AI, then offline knowledge base
    final aiResponse = await _generateAiResponse(query);
    if (aiResponse != null) {
      state = state.copyWith(
        isProcessing: false,
        isUsingOnlineService: true,
        contacts: const [],
        facilities: const [],
        response: aiResponse,
      );
      return;
    }

    // Offline: match against comprehensive offline knowledge base
    final offlineResponse = _offlineGeneralResponse(lower);
    state = state.copyWith(
      isProcessing: false,
      isUsingOnlineService: false,
      contacts: const [],
      facilities: const [],
      response: offlineResponse,
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
    // Use multi-word phrases to avoid false positives (e.g. "call for help" is not a contact query)
    const exactPhrases = [
      'emergency number',
      'phone number',
      'contact number',
      'call police',
      'call ambulance',
      'call fire',
      'call emergency',
      'emergency contact',
      'hotline',
      'helpline',
    ];
    if (exactPhrases.any(query.contains)) return true;
    // Single-word match only if that's essentially the whole query
    const singleKeywords = ['number', 'contact', 'phone'];
    final words = query.split(RegExp(r'\s+'));
    return words.length <= 3 && singleKeywords.any(query.contains);
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

  // ── Offline knowledge base ──────────────────────────────────────────

  String _offlineFirstAidResponse(String query) {
    // Specific first-aid topics with detailed offline guidance
    if (query.contains('cpr') || query.contains('not breathing') || query.contains('cardiac')) {
      return '❤️ CPR (Cardiopulmonary Resuscitation)\n\n'
          '1. Check for responsiveness — tap shoulders and shout.\n'
          '2. Call 193 / 112 immediately (or ask someone to call).\n'
          '3. Place person on a firm, flat surface on their back.\n'
          '4. Place the heel of one hand on the center of the chest, other hand on top.\n'
          '5. Push hard and fast — at least 5 cm deep, 100-120 compressions per minute.\n'
          '6. After 30 compressions, tilt head back, lift chin, give 2 rescue breaths.\n'
          '7. Continue 30:2 cycles until help arrives or person starts breathing.\n\n'
          '⚠️ If untrained, do hands-only CPR (continuous chest compressions without breaths).';
    }
    if (query.contains('sprain') || query.contains('ankle') || query.contains('twist')) {
      return '🦶 Sprain / Twisted Joint\n\n'
          'Follow the **RICE** method:\n'
          '1. **Rest** — Stop using the injured area immediately.\n'
          '2. **Ice** — Apply ice wrapped in cloth for 20 minutes on, 20 minutes off.\n'
          '3. **Compression** — Wrap with an elastic bandage (not too tight).\n'
          '4. **Elevation** — Raise the limb above heart level to reduce swelling.\n\n'
          '**Seek medical help if:**\n'
          '• You cannot bear weight on the joint\n'
          '• The area is numb or deformed\n'
          '• Swelling is severe or does not improve after 48 hours\n'
          '• You heard a popping sound at the time of injury';
    }
    if (query.contains('bite') || query.contains('snake') || query.contains('dog')) {
      return '🐍 Animal / Snake Bite\n\n'
          '**For snake bites:**\n'
          '1. Keep calm and still — movement spreads venom faster.\n'
          '2. Remove jewelry/tight clothing near the bite.\n'
          '3. Immobilize the bitten limb at or below heart level.\n'
          '4. Call 193 / 112 immediately.\n'
          '5. Do NOT cut the wound, suck out venom, or apply a tourniquet.\n'
          '6. Try to remember the snake\'s appearance for identification.\n\n'
          '**For dog/animal bites:**\n'
          '1. Wash the wound thoroughly with soap and running water for 10 minutes.\n'
          '2. Apply antiseptic if available.\n'
          '3. Cover with a clean bandage.\n'
          '4. Seek medical attention for rabies vaccination assessment.\n'
          '5. Report the animal to local authorities.';
    }
    if (query.contains('sting') || query.contains('bee') || query.contains('wasp') || query.contains('scorpion')) {
      return '🐝 Sting First Aid\n\n'
          '1. Remove the stinger by scraping sideways with a flat edge (credit card). Do NOT squeeze.\n'
          '2. Wash with soap and water.\n'
          '3. Apply a cold compress to reduce swelling (20 minutes on, 20 off).\n'
          '4. Take an antihistamine if available for itching/swelling.\n\n'
          '**Call 193 / 112 immediately if:**\n'
          '• Difficulty breathing or swallowing\n'
          '• Swelling of face, lips, or throat\n'
          '• Dizziness, rapid heartbeat, or nausea\n'
          '• History of severe allergic reactions\n'
          'These are signs of anaphylaxis — a life-threatening emergency.';
    }
    if (query.contains('wound') || query.contains('cut') || query.contains('laceration')) {
      return '🩹 Wound / Cut Care\n\n'
          '1. Wash your hands before touching the wound.\n'
          '2. Apply firm, direct pressure with a clean cloth to stop bleeding.\n'
          '3. Once bleeding slows, gently clean the wound with clean water.\n'
          '4. Remove any visible debris (do NOT remove deeply embedded objects).\n'
          '5. Apply antibiotic ointment if available.\n'
          '6. Cover with a sterile bandage or clean cloth.\n\n'
          '**Seek medical help if:**\n'
          '• The cut is deep (> 1 cm), wide, or has jagged edges\n'
          '• Bleeding does not stop after 10 minutes of pressure\n'
          '• The wound is on the face, hand, or over a joint\n'
          '• There is dirt or debris you cannot remove\n'
          '• You have not had a tetanus shot in the last 5 years';
    }
    if (query.contains('drown') || query.contains('water') || query.contains('swim')) {
      return '🌊 Drowning / Water Emergency\n\n'
          '1. Call 193 / 112 immediately.\n'
          '2. Do NOT enter the water unless trained — throw a rope, float, or reach with a pole.\n'
          '3. Once the person is out of water, check for breathing.\n'
          '4. If not breathing, begin CPR immediately (30 chest compressions, 2 breaths).\n'
          '5. If breathing, place in the recovery position (on their side).\n'
          '6. Keep the person warm — cover with blankets or dry clothing.\n'
          '7. Stay with them until emergency services arrive.\n\n'
          '⚠️ Even if the person seems fine, they must see a doctor — secondary drowning can occur hours later.';
    }
    if (query.contains('electr') || query.contains('shock') || query.contains('power line')) {
      return '⚡ Electrical Injury\n\n'
          '1. Do NOT touch the person if they are still in contact with the electrical source.\n'
          '2. Turn off the power source if safely possible.\n'
          '3. Call 193 / 112 immediately.\n'
          '4. If the person is free from the source and not breathing, begin CPR.\n'
          '5. Treat any burns with cool running water.\n'
          '6. Do NOT move the person — there may be spinal injuries.\n\n'
          '⚠️ All electrical injuries need medical evaluation — internal damage may not be visible.';
    }
    if (query.contains('head') && (query.contains('injury') || query.contains('hit') || query.contains('hurt'))) {
      return '🤕 Head Injury\n\n'
          '1. Keep the person still. Do NOT move the neck.\n'
          '2. Apply gentle pressure with a clean cloth if bleeding from the scalp.\n'
          '3. Apply a cold compress to reduce swelling.\n'
          '4. Monitor for signs of concussion: confusion, vomiting, drowsiness, unequal pupils.\n\n'
          '**Call 193 / 112 immediately if:**\n'
          '• Loss of consciousness (even briefly)\n'
          '• Persistent vomiting\n'
          '• Clear fluid from nose or ears\n'
          '• Seizure\n'
          '• Worsening headache\n'
          '• Confusion or unusual behavior';
    }
    if (query.contains('heat') && (query.contains('stroke') || query.contains('exhaust'))) {
      return '🌡️ Heat Stroke / Heat Exhaustion\n\n'
          '**Heat Stroke (EMERGENCY — Call 193/112):**\n'
          '• Body temp above 40°C, hot/dry skin, confusion, unconsciousness\n'
          '• Move to shade or cool area immediately\n'
          '• Cool rapidly: wet sheets, fan, ice packs to neck/armpits/groin\n'
          '• Do NOT give fluids if unconscious\n\n'
          '**Heat Exhaustion:**\n'
          '• Heavy sweating, weakness, nausea, dizziness, cool/pale skin\n'
          '• Move to a cool place and lie down\n'
          '• Sip cool water slowly\n'
          '• Apply cool, wet cloths to skin\n'
          '• If symptoms worsen or last > 1 hour, call 193';
    }

    // Generic first aid fallback (still useful, not just contacts)
    return '🚨 First Aid Guidance\n\n'
        '**Assess the Situation:**\n'
        '1. Ensure the scene is safe for you and the patient.\n'
        '2. Check for responsiveness — tap and shout "Are you okay?"\n'
        '3. Call 193 (Ambulance) or 112 for severe symptoms.\n\n'
        '**Common Actions:**\n'
        '• **Bleeding:** Apply firm pressure with a clean cloth.\n'
        '• **Burns:** Cool under running water for 10-20 minutes.\n'
        '• **Fractures:** Immobilize the limb; do not try to realign.\n'
        '• **Choking:** Back blows and abdominal thrusts.\n'
        '• **Unconscious but breathing:** Recovery position (on their side).\n'
        '• **Not breathing:** Begin CPR (30 compressions, 2 breaths).\n\n'
        '**Do NOT:**\n'
        '• Move someone with a suspected spinal injury\n'
        '• Remove embedded objects from wounds\n'
        '• Give food or drink to an unconscious person\n\n'
        'For specific guidance, try searching for the exact injury (e.g., "snake bite", "sprain", "head injury").';
  }

  String _offlineGeneralResponse(String query) {
    // ── Mental health & emotional crisis ──
    if (query.contains('suicide') || query.contains('kill myself') || query.contains('want to die') || query.contains('end my life')) {
      return '💙 You Are Not Alone\n\n'
          'If you or someone you know is in immediate danger, call 112 now.\n\n'
          '**Crisis Support:**\n'
          '• Mental Health Authority Helpline: 0244846701\n'
          '• National Emergency: 112\n\n'
          '**What to do right now:**\n'
          '1. Stay in a safe place. Remove access to harmful objects.\n'
          '2. Talk to someone you trust — a friend, family member, or counselor.\n'
          '3. Focus on getting through the next hour, then the next.\n'
          '4. You don\'t have to face this alone — help is available.\n\n'
          '**If someone else is at risk:**\n'
          '• Stay with them. Do not leave them alone.\n'
          '• Listen without judgment.\n'
          '• Call 112 or take them to the nearest hospital.\n'
          '• Remove any means of self-harm if safe to do so.';
    }
    if (query.contains('panic') || query.contains('anxiety') || query.contains('panic attack')) {
      return '😰 Panic Attack / Severe Anxiety\n\n'
          '**Immediate Steps:**\n'
          '1. Find a safe, quiet place to sit down.\n'
          '2. **Breathe slowly:** Inhale for 4 counts, hold for 4, exhale for 6.\n'
          '3. **Ground yourself (5-4-3-2-1):** Name 5 things you see, 4 you can touch, 3 you hear, 2 you smell, 1 you taste.\n'
          '4. Remind yourself: "This will pass. I am safe."\n'
          '5. Unclench your jaw and relax your shoulders.\n\n'
          '**After the episode:**\n'
          '• Drink water slowly.\n'
          '• Rest — panic attacks are physically exhausting.\n'
          '• Talk to someone you trust about how you feel.\n'
          '• If attacks are recurring, contact the Mental Health Authority Helpline: 0244846701.\n\n'
          '⚠️ If chest pain or difficulty breathing persists, call 193 — it could be a medical emergency.';
    }
    if (query.contains('depression') || query.contains('depressed') || query.contains('hopeless') || query.contains('sad')) {
      return '💙 Coping with Depression\n\n'
          '**Right now:**\n'
          '• You are not weak for feeling this way — depression is a real condition.\n'
          '• Reach out to someone: Mental Health Authority Helpline: 0244846701\n\n'
          '**Helpful steps:**\n'
          '1. Talk to someone you trust about how you feel.\n'
          '2. Try to maintain daily routines (meals, sleep, hygiene).\n'
          '3. Step outside for even 10 minutes — sunlight and movement help.\n'
          '4. Avoid alcohol and drugs — they worsen depression.\n'
          '5. Be patient with yourself — recovery takes time.\n\n'
          '**Seek professional help if:**\n'
          '• Feelings persist for more than 2 weeks\n'
          '• You are unable to work, eat, or sleep\n'
          '• You have thoughts of self-harm\n'
          '• You feel unable to cope\n\n'
          'You deserve support. Please reach out.';
    }
    if (query.contains('mental health') || query.contains('stress') || query.contains('overwhelm')) {
      return '🧠 Mental Health Support\n\n'
          '**Immediate help:**\n'
          '• Mental Health Authority Helpline: 0244846701\n\n'
          '**Coping strategies:**\n'
          '1. Take slow, deep breaths (4 in, 4 hold, 6 out).\n'
          '2. Step away from the stressor if possible.\n'
          '3. Talk to someone — sharing reduces the burden.\n'
          '4. Physical activity, even a short walk, helps release tension.\n'
          '5. Limit caffeine and screen time before bed.\n\n'
          '**When to seek professional help:**\n'
          '• Persistent sadness, anger, or numbness\n'
          '• Difficulty functioning at work or home\n'
          '• Changes in sleep or appetite lasting > 2 weeks\n'
          '• Using substances to cope';
    }

    // ── Domestic violence & abuse ──
    if (query.contains('domestic') || query.contains('violence') || query.contains('abuse') ||
        query.contains('hitting') || query.contains('beaten') || query.contains('abusive')) {
      return '🛡️ Domestic Violence / Abuse\n\n'
          '**If you are in immediate danger, call 112 or 191 (Police).**\n\n'
          '**Support contacts:**\n'
          '• DOVVSU (Domestic Violence & Victim Support): 0551000900\n'
          '• Ghana Police: 191\n\n'
          '**Safety steps:**\n'
          '1. If you can leave safely, go to a trusted friend, family, or shelter.\n'
          '2. Keep emergency numbers memorized or saved under a code name.\n'
          '3. Pack an emergency bag: ID, money, medications, phone charger.\n'
          '4. Tell a trusted person about your situation.\n'
          '5. Document incidents: dates, injuries, photos (store securely).\n\n'
          '**Remember:**\n'
          '• Abuse is never your fault.\n'
          '• You deserve to be safe.\n'
          '• Help is available — you don\'t have to face this alone.';
    }

    // ── Natural disasters ──
    if (query.contains('flood') || query.contains('flooding')) {
      return '🌊 Flood Safety\n\n'
          '**During a flood:**\n'
          '1. Move to higher ground immediately.\n'
          '2. Do NOT walk, swim, or drive through flood waters.\n'
          '3. Stay away from bridges over fast-moving water.\n'
          '4. Call 112 or NADMO: 0302772446 if trapped.\n\n'
          '**After a flood:**\n'
          '• Do not return home until authorities say it is safe.\n'
          '• Avoid floodwater — it may be contaminated or electrically charged.\n'
          '• Boil or treat all water before drinking.\n'
          '• Watch for snakes and other animals displaced by the flood.\n'
          '• Report damaged infrastructure (power lines, bridges) to authorities.';
    }
    if (query.contains('fire') && !query.contains('fire service')) {
      return '🔥 Fire Emergency\n\n'
          '**If a building is on fire:**\n'
          '1. Call 192 (Fire Service) and 112 immediately.\n'
          '2. Alert everyone in the building — shout "FIRE!"\n'
          '3. Get out immediately. Do NOT stop to collect belongings.\n'
          '4. Crawl low if there is smoke — air is cleaner near the floor.\n'
          '5. Feel doors before opening — if hot, use another exit.\n'
          '6. Once out, stay out. Meet at a designated assembly point.\n\n'
          '**If your clothes catch fire:**\n'
          '• STOP, DROP, and ROLL.\n\n'
          '**If trapped:**\n'
          '• Close doors between you and the fire.\n'
          '• Seal gaps under doors with wet cloth.\n'
          '• Signal from a window.\n'
          '• Call 192 and tell them your location.';
    }
    if (query.contains('earthquake')) {
      return '🏚️ Earthquake Safety\n\n'
          '**During an earthquake:**\n'
          '1. **DROP** — Get down on hands and knees.\n'
          '2. **COVER** — Get under a sturdy desk or table. Protect your head and neck.\n'
          '3. **HOLD ON** — Hold on until the shaking stops.\n'
          '4. If no shelter: move to an interior wall, cover your head.\n'
          '5. Stay away from windows, mirrors, and heavy objects.\n\n'
          '**If outdoors:**\n'
          '• Move to an open area away from buildings, power lines, trees.\n\n'
          '**After the earthquake:**\n'
          '• Check for injuries and call 193/112 if needed.\n'
          '• Be prepared for aftershocks.\n'
          '• Check for gas leaks (smell) and structural damage.\n'
          '• Do not enter damaged buildings.\n'
          '• Contact NADMO: 0302772446 for disaster response.';
    }
    if (query.contains('storm') || query.contains('thunder') || query.contains('lightning') || query.contains('rainstorm')) {
      return '⛈️ Storm & Lightning Safety\n\n'
          '**During a storm:**\n'
          '1. Go indoors immediately. A solid building is safest.\n'
          '2. Stay away from windows and doors.\n'
          '3. Do NOT use wired electronics or plumbing during lightning.\n'
          '4. Unplug sensitive electronics.\n\n'
          '**If caught outdoors:**\n'
          '• Do NOT shelter under isolated trees.\n'
          '• Crouch low with feet together — minimize ground contact.\n'
          '• Move away from metal objects, water, and high ground.\n'
          '• If in a vehicle, stay inside with windows closed.\n\n'
          '**If someone is struck by lightning:**\n'
          '• Call 193 / 112 immediately.\n'
          '• It is safe to touch them — they do not carry a charge.\n'
          '• Begin CPR if they are not breathing.';
    }

    // ── Common emergencies ──
    if (query.contains('robbery') || query.contains('theft') || query.contains('rob') || query.contains('stolen')) {
      return '🚨 Robbery / Theft\n\n'
          '**If it is happening now:**\n'
          '1. Do NOT resist — your life is more valuable than possessions.\n'
          '2. Try to stay calm and comply with demands.\n'
          '3. Observe details: appearance, clothing, vehicle, direction of escape.\n'
          '4. Call 191 (Police) as soon as it is safe.\n\n'
          '**After a robbery:**\n'
          '• Do not touch anything at the scene — preserve evidence.\n'
          '• Write down everything you remember immediately.\n'
          '• File a report at the nearest police station.\n'
          '• If injured, call 193 (Ambulance) or go to the nearest hospital.\n'
          '• Notify your bank if cards or financial information were taken.';
    }
    if (query.contains('accident') || query.contains('crash') || query.contains('collision')) {
      return '🚗 Road Accident\n\n'
          '**Immediate Steps:**\n'
          '1. Move to safety if possible.\n'
          '2. Call 191 (Police) and 193 (Ambulance) if there are injuries.\n'
          '3. Turn on hazard lights and set up warnings.\n\n'
          '**Help the injured:**\n'
          '• Do NOT move someone with suspected spinal injury.\n'
          '• Apply direct pressure to bleeding wounds.\n'
          '• Keep injured persons warm and reassured.\n'
          '• Monitor breathing until help arrives.\n\n'
          '**Document the scene:**\n'
          '• Photos of vehicle positions, damage, road conditions\n'
          '• Exchange details: names, phones, insurance, plate numbers\n'
          '• Get witness contact information\n'
          '• Obtain a police report';
    }
    if (query.contains('food') && (query.contains('poison') || query.contains('sick') || query.contains('vomit'))) {
      return '🤢 Food Poisoning\n\n'
          '**Symptoms:** Nausea, vomiting, diarrhea, stomach cramps, fever.\n\n'
          '**What to do:**\n'
          '1. Sip clear fluids (water, oral rehydration salts) in small amounts.\n'
          '2. Rest and avoid solid food until vomiting stops.\n'
          '3. Avoid dairy, caffeine, alcohol, and fatty foods.\n'
          '4. Wash hands frequently to prevent spreading.\n\n'
          '**Seek medical help (call 193 or visit hospital) if:**\n'
          '• Vomiting blood or bloody diarrhea\n'
          '• Unable to keep fluids down for > 24 hours\n'
          '• High fever (above 38.5°C)\n'
          '• Signs of dehydration: dark urine, dizziness, dry mouth\n'
          '• Symptoms in a child under 5, elderly person, or pregnant woman\n\n'
          '⚠️ If you suspect chemical poisoning, call Poison Control: 0302665065';
    }
    if (query.contains('asthma') || query.contains('inhaler') || query.contains('wheez')) {
      return '🫁 Asthma Attack\n\n'
          '1. Help the person sit upright (do NOT lie down).\n'
          '2. Help them use their reliever inhaler (usually blue) — 1 puff every 30-60 seconds, up to 10 puffs.\n'
          '3. Stay calm and reassure them.\n'
          '4. Loosen tight clothing.\n\n'
          '**Call 193 / 112 if:**\n'
          '• No inhaler is available\n'
          '• Symptoms do not improve after 10 puffs\n'
          '• The person cannot speak, eat, or drink\n'
          '• Lips or fingernails turn blue\n'
          '• The person is getting worse rapidly\n\n'
          '⚠️ If the person becomes unconscious, begin CPR and call 193 immediately.';
    }
    if (query.contains('allerg') || query.contains('anaphyla') || query.contains('swell')) {
      return '🚨 Allergic Reaction / Anaphylaxis\n\n'
          '**Mild reaction (rash, itching, mild swelling):**\n'
          '• Take an antihistamine if available.\n'
          '• Apply a cold compress to the affected area.\n'
          '• Monitor for worsening symptoms.\n\n'
          '**Severe / Anaphylaxis (EMERGENCY):**\n'
          '• Call 193 / 112 immediately.\n'
          '• Use an EpiPen if available (inject into outer thigh).\n'
          '• Help the person lie down with legs elevated.\n'
          '• If difficulty breathing, let them sit upright.\n'
          '• Loosen tight clothing.\n'
          '• Begin CPR if breathing stops.\n\n'
          '**Warning signs of anaphylaxis:**\n'
          '• Swelling of face, lips, throat, or tongue\n'
          '• Difficulty breathing or wheezing\n'
          '• Rapid or weak pulse\n'
          '• Dizziness or fainting\n'
          '• Nausea, vomiting, or diarrhea';
    }
    if (query.contains('heart attack') || query.contains('cardiac')) {
      return '❤️ Suspected Heart Attack\n\n'
          '**Call 193 / 112 IMMEDIATELY.**\n\n'
          '**While waiting:**\n'
          '1. Have the person sit down and rest (semi-upright is best).\n'
          '2. Give aspirin (300 mg) if available and not allergic — chew, don\'t swallow whole.\n'
          '3. Loosen tight clothing.\n'
          '4. Monitor breathing and consciousness.\n'
          '5. If they become unconscious and stop breathing, begin CPR.\n\n'
          '**Recognize the signs:**\n'
          '• Chest pain or pressure (may spread to arm, jaw, neck, back)\n'
          '• Shortness of breath\n'
          '• Cold sweat, nausea, lightheadedness\n'
          '• Women may have atypical symptoms: fatigue, back pain, nausea\n\n'
          '⚠️ Do NOT let the person drive themselves. Do NOT delay calling for help.';
    }
    if (query.contains('stroke')) {
      return '🧠 Suspected Stroke — Act FAST\n\n'
          '**Call 193 / 112 IMMEDIATELY. Every minute matters.**\n\n'
          '**Use FAST to recognize a stroke:**\n'
          '• **F**ace — Ask them to smile. Does one side droop?\n'
          '• **A**rms — Ask them to raise both arms. Does one drift down?\n'
          '• **S**peech — Ask them to repeat a phrase. Is speech slurred?\n'
          '• **T**ime — If ANY of these: call 193 immediately.\n\n'
          '**While waiting:**\n'
          '1. Note the exact time symptoms started (critical for treatment).\n'
          '2. Have them lie down with head slightly elevated.\n'
          '3. Do NOT give food, drink, or medication.\n'
          '4. If unconscious but breathing, place in recovery position.\n'
          '5. If not breathing, begin CPR.\n'
          '6. Be ready to report symptoms to paramedics.';
    }
    if (query.contains('diabetic') || query.contains('blood sugar') || query.contains('hypogly') || query.contains('insulin')) {
      return '💉 Diabetic Emergency\n\n'
          '**Low Blood Sugar (Hypoglycemia):**\n'
          'Signs: shakiness, sweating, confusion, irritability, pale skin.\n'
          '1. Give sugar immediately: juice, regular soda, glucose tablets, or candy.\n'
          '2. If they improve, follow with a proper meal or snack.\n'
          '3. If unconscious: Do NOT give food/drink. Place in recovery position and call 193.\n\n'
          '**High Blood Sugar (Hyperglycemia):**\n'
          'Signs: excessive thirst, frequent urination, fruity breath, nausea.\n'
          '1. Help them take their prescribed insulin if they have it.\n'
          '2. Give water to prevent dehydration.\n'
          '3. Call 193 if they become confused, vomit, or lose consciousness.\n\n'
          '⚠️ If unsure whether blood sugar is high or low, giving sugar is safer — low blood sugar is more immediately dangerous.';
    }
    if (query.contains('child') || query.contains('baby') || query.contains('infant') || query.contains('toddler')) {
      return '👶 Child / Infant Emergency\n\n'
          '**Choking (infant under 1 year):**\n'
          '1. Place baby face-down on your forearm, supporting the head.\n'
          '2. Give 5 firm back blows between the shoulder blades.\n'
          '3. Turn baby over. Give 5 chest thrusts (2 fingers on breastbone).\n'
          '4. Repeat until object is cleared or baby becomes unconscious.\n'
          '5. If unconscious, call 193 and begin infant CPR.\n\n'
          '**High fever in children:**\n'
          '• Give age-appropriate paracetamol (NOT aspirin for children).\n'
          '• Sponge with lukewarm water (NOT cold).\n'
          '• Keep child lightly dressed.\n'
          '• Give plenty of fluids.\n'
          '• Seek medical help if fever > 39°C or child is under 3 months.\n\n'
          '**Dehydration (from diarrhea/vomiting):**\n'
          '• Give oral rehydration salts (ORS) in small, frequent sips.\n'
          '• Call 193 if child is lethargic, has no tears, or no wet diapers for 6+ hours.';
    }
    if (query.contains('pregnan') || query.contains('labour') || query.contains('labor') || query.contains('delivery') || query.contains('giving birth')) {
      return '🤰 Pregnancy Emergency\n\n'
          '**Call 193 / 112 immediately for:**\n'
          '• Heavy vaginal bleeding\n'
          '• Severe abdominal pain\n'
          '• Water breaking before 37 weeks\n'
          '• Seizures or loss of consciousness\n'
          '• No fetal movement for extended period\n\n'
          '**If labor is imminent (baby is coming):**\n'
          '1. Call 193 and stay on the line for guidance.\n'
          '2. Have the mother lie down on a clean surface.\n'
          '3. Support the baby\'s head as it emerges — do NOT pull.\n'
          '4. Clear the baby\'s mouth and nose gently.\n'
          '5. Place baby skin-to-skin on the mother\'s chest and cover both.\n'
          '6. Do NOT cut the umbilical cord — wait for medical help.\n'
          '7. Keep both warm until paramedics arrive.';
    }
    if (query.contains('malaria') || query.contains('fever')) {
      return '🦟 Fever / Suspected Malaria\n\n'
          '**Symptoms of malaria:** High fever, chills, sweating, headache, body aches, nausea.\n\n'
          '**What to do:**\n'
          '1. Take temperature if possible.\n'
          '2. Give paracetamol for fever (follow dosage instructions).\n'
          '3. Drink plenty of fluids to prevent dehydration.\n'
          '4. Apply lukewarm (not cold) wet cloths to forehead and body.\n'
          '5. **Get tested** — visit the nearest health facility for a malaria test.\n\n'
          '**Seek urgent medical care if:**\n'
          '• Fever above 39°C / 102°F\n'
          '• Confusion or altered consciousness\n'
          '• Difficulty breathing\n'
          '• Severe vomiting or unable to eat/drink\n'
          '• Child under 5 with fever\n'
          '• Pregnant woman with fever\n\n'
          '⚠️ Malaria can be fatal if untreated. Early diagnosis and treatment saves lives.';
    }
    if (query.contains('cholera') || query.contains('diarr')) {
      return '💧 Cholera / Severe Diarrhea\n\n'
          '**Signs:** Watery diarrhea, vomiting, rapid dehydration, leg cramps.\n\n'
          '**Immediate actions:**\n'
          '1. Give oral rehydration salts (ORS) — small, frequent sips.\n'
          '2. If no ORS: mix 1 liter clean water + 6 teaspoons sugar + ½ teaspoon salt.\n'
          '3. Continue giving fluids even if vomiting — give smaller amounts more frequently.\n'
          '4. Seek medical attention immediately — cholera can kill within hours.\n\n'
          '**Prevention:**\n'
          '• Drink only treated or boiled water.\n'
          '• Wash hands with soap frequently.\n'
          '• Cook food thoroughly.\n'
          '• Avoid raw vegetables and unpeeled fruits.\n\n'
          '**Call 193 / visit hospital if:** Unable to keep fluids down, very weak, sunken eyes, no urine output.';
    }

    // ── Fallback: helpful offline response with guidance ──
    return '📋 Emergency Guidance\n\n'
        'I\'m currently offline and couldn\'t find a specific guide for your query.\n\n'
        '**General emergency steps:**\n'
        '1. If life-threatening, call **112** (National Emergency) or **193** (Ambulance) now.\n'
        '2. Ensure your own safety before helping others.\n'
        '3. Check the person\'s breathing and consciousness.\n'
        '4. Control any bleeding with direct pressure.\n'
        '5. Keep the person calm and warm until help arrives.\n\n'
        '**Try searching for specific topics:**\n'
        '• First aid: "burn", "bleeding", "choking", "CPR", "sprain"\n'
        '• Medical: "heart attack", "stroke", "asthma", "allergy"\n'
        '• Mental health: "panic attack", "depression", "stress"\n'
        '• Disasters: "flood", "fire", "earthquake", "storm"\n'
        '• Safety: "accident", "robbery", "domestic violence"\n'
        '• Children: "baby choking", "child fever"\n'
        '• Other: "pregnancy", "malaria", "food poisoning", "snake bite"\n\n'
        '**Quick Actions** above provide instant access to emergency contacts and nearest hospitals.';
  }
}
