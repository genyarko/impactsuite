import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/services/ai/ai_models.dart';
import '../../domain/services/ai/ai_repository.dart';

class AiConversationMessage {
  AiConversationMessage({
    required this.text,
    required this.isUser,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  final String text;
  final bool isUser;
  final DateTime timestamp;
}

class AiConversationState {
  const AiConversationState({
    this.messages = const <AiConversationMessage>[],
    this.isLoading = false,
    this.error,
  });

  final List<AiConversationMessage> messages;
  final bool isLoading;
  final String? error;

  AiConversationState copyWith({
    List<AiConversationMessage>? messages,
    bool? isLoading,
    String? error,
  }) {
    return AiConversationState(
      messages: messages ?? this.messages,
      isLoading: isLoading ?? this.isLoading,
      error: error,
    );
  }
}

class AiConversationController extends StateNotifier<AiConversationState> {
  AiConversationController({
    required AiRepository repository,
    required this.serviceType,
  })  : _repository = repository,
        super(const AiConversationState());

  final AiRepository _repository;
  final String serviceType;

  Future<void> sendPrompt(String prompt) async {
    final trimmed = prompt.trim();
    if (trimmed.isEmpty || state.isLoading) {
      return;
    }

    final userMessage = AiConversationMessage(text: trimmed, isUser: true);
    state = state.copyWith(
      messages: [...state.messages, userMessage],
      isLoading: true,
      error: null,
    );

    try {
      final chunks = _repository.stream(
        AiGenerationRequest(
          prompt: '[$serviceType] $trimmed',
        ),
      );

      var hasChunk = false;
      var responseBuffer = StringBuffer();

      await for (final chunk in chunks) {
        if (chunk.trim().isEmpty) {
          continue;
        }
        hasChunk = true;
        responseBuffer.write(chunk);
      }

      if (!hasChunk) {
        final generated = await _repository.generate(
          AiGenerationRequest(prompt: '[$serviceType] $trimmed'),
        );
        responseBuffer.write(generated.text);
      }

      final aiMessage = AiConversationMessage(
        text: responseBuffer.toString().trim(),
        isUser: false,
      );

      state = state.copyWith(
        messages: [...state.messages, aiMessage],
        isLoading: false,
        error: null,
      );
    } catch (_) {
      state = state.copyWith(
        isLoading: false,
        error: 'Unable to generate a response right now.',
      );
    }
  }
}
