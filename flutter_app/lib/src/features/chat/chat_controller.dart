import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/drift/chat_drift_store.dart';
import '../../data/models/chat_models.dart';
import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

class ChatState {
  const ChatState({
    this.sessions = const <ChatSession>[],
    this.messages = const <ChatMessage>[],
    this.selectedSessionId,
    this.isLoading = false,
    this.error,
  });

  final List<ChatSession> sessions;
  final List<ChatMessage> messages;
  final String? selectedSessionId;
  final bool isLoading;
  final String? error;

  ChatSession? get selectedSession {
    if (selectedSessionId == null) {
      return null;
    }
    for (final session in sessions) {
      if (session.id == selectedSessionId) {
        return session;
      }
    }
    return null;
  }

  ChatState copyWith({
    List<ChatSession>? sessions,
    List<ChatMessage>? messages,
    String? selectedSessionId,
    bool clearSelectedSession = false,
    bool? isLoading,
    String? error,
    bool clearError = false,
  }) {
    return ChatState(
      sessions: sessions ?? this.sessions,
      messages: messages ?? this.messages,
      selectedSessionId: clearSelectedSession
          ? null
          : (selectedSessionId ?? this.selectedSessionId),
      isLoading: isLoading ?? this.isLoading,
      error: clearError ? null : (error ?? this.error),
    );
  }
}

class ChatController extends StateNotifier<ChatState> {
  ChatController({
    required ChatDriftStore store,
    required AiRepository repository,
  })  : _store = store,
        _repository = repository,
        super(const ChatState()) {
    _sessionsSub = _store.watchAllSessions().listen((sessions) {
      state = state.copyWith(sessions: sessions);
    });
  }

  final ChatDriftStore _store;
  final AiRepository _repository;
  StreamSubscription<List<ChatSession>>? _sessionsSub;
  StreamSubscription<List<ChatMessage>>? _messagesSub;

  Future<String> createNewSession() async {
    final now = DateTime.now();
    final id = now.microsecondsSinceEpoch.toString();
    final session = ChatSession(
      id: id,
      title: 'New Chat',
      createdAt: now,
      updatedAt: now,
      lastMessage: null,
    );

    await _store.upsertSession(session);
    await selectSession(id);
    return id;
  }

  Future<void> deleteSession(String sessionId) async {
    await _store.deleteSessionWithMessages(sessionId);
    if (state.selectedSessionId == sessionId) {
      state = state.copyWith(messages: const <ChatMessage>[], clearSelectedSession: true);
    }
  }

  Future<void> selectSession(String sessionId) async {
    state = state.copyWith(selectedSessionId: sessionId, clearError: true);
    await _messagesSub?.cancel();
    _messagesSub = _store.watchMessagesForSession(sessionId).listen((messages) {
      state = state.copyWith(messages: messages);
    });
  }

  Future<void> ensureSessionSelected({String? preferredSessionId}) async {
    if (preferredSessionId != null && preferredSessionId.isNotEmpty) {
      await selectSession(preferredSessionId);
      return;
    }

    if (state.selectedSessionId != null) {
      return;
    }

    final existing = state.sessions;
    if (existing.isNotEmpty) {
      await selectSession(existing.first.id);
      return;
    }

    await createNewSession();
  }

  Future<void> sendPrompt(String prompt) async {
    final trimmed = prompt.trim();
    final sessionId = state.selectedSessionId;

    if (trimmed.isEmpty || state.isLoading || sessionId == null) {
      return;
    }

    final now = DateTime.now();
    final userMessage = ChatMessage(
      id: '${now.microsecondsSinceEpoch}_user',
      sessionId: sessionId,
      content: trimmed,
      isUser: true,
      timestamp: now,
    );

    await _store.insertMessage(userMessage);
    await _store.upsertSession(
      (state.selectedSession ??
              ChatSession(
                id: sessionId,
                title: 'New Chat',
                createdAt: now,
                updatedAt: now,
              ))
          .copyWith(
        title: _deriveTitleFromPrompt(trimmed),
        updatedAt: now,
        lastMessage: trimmed,
      ),
    );

    state = state.copyWith(isLoading: true, clearError: true);

    try {
      final chunks = _repository.stream(
        AiGenerationRequest(prompt: '[chat] $trimmed'),
      );

      var hasChunk = false;
      final responseBuffer = StringBuffer();

      await for (final chunk in chunks) {
        if (chunk.trim().isEmpty) {
          continue;
        }
        hasChunk = true;
        responseBuffer.write(chunk);
      }

      if (!hasChunk) {
        final generated = await _repository.generate(
          AiGenerationRequest(prompt: '[chat] $trimmed'),
        );
        responseBuffer.write(generated.text);
      }

      final aiText = responseBuffer.toString().trim();
      if (aiText.isNotEmpty) {
        final aiTimestamp = DateTime.now();
        final aiMessage = ChatMessage(
          id: '${aiTimestamp.microsecondsSinceEpoch}_ai',
          sessionId: sessionId,
          content: aiText,
          isUser: false,
          timestamp: aiTimestamp,
        );

        await _store.insertMessage(aiMessage);
        await _store.upsertSession(
          (state.selectedSession ??
                  ChatSession(
                    id: sessionId,
                    title: _deriveTitleFromPrompt(trimmed),
                    createdAt: now,
                    updatedAt: aiTimestamp,
                  ))
              .copyWith(
            title: _deriveTitleFromPrompt(trimmed),
            updatedAt: aiTimestamp,
            lastMessage: aiText,
          ),
        );
      }

      state = state.copyWith(isLoading: false, clearError: true);
    } catch (_) {
      state = state.copyWith(
        isLoading: false,
        error: 'Unable to generate a response right now.',
      );
    }
  }

  String _deriveTitleFromPrompt(String prompt) {
    if (state.messages.isNotEmpty) {
      return state.selectedSession?.title ?? 'Chat';
    }
    return prompt.length > 32 ? '${prompt.substring(0, 32)}…' : prompt;
  }

  @override
  void dispose() {
    _sessionsSub?.cancel();
    _messagesSub?.cancel();
    super.dispose();
  }
}
