import 'package:flutter/services.dart';

import '../../domain/services/ai/ai_models.dart';

class PlatformAiChannels {
  PlatformAiChannels({
    MethodChannel? methodChannel,
    EventChannel? streamChannel,
  })  : _methodChannel = methodChannel ?? const MethodChannel(_methodChannelName),
        _streamChannel = streamChannel ?? const EventChannel(_streamChannelName);

  static const _methodChannelName = 'impactsuite/ai/methods';
  static const _streamChannelName = 'impactsuite/ai/stream';

  final MethodChannel _methodChannel;
  final EventChannel _streamChannel;

  Future<void> initializeGemini({
    required String apiKey,
    required String model,
    required double temperature,
    required int topK,
    required double topP,
    required int maxOutputTokens,
  }) {
    return _methodChannel.invokeMethod<void>('initializeGemini', {
      'apiKey': apiKey,
      'model': model,
      'temperature': temperature,
      'topK': topK,
      'topP': topP,
      'maxOutputTokens': maxOutputTokens,
    });
  }

  Future<String> generateGeminiText(AiGenerationRequest request) async {
    final result = await _methodChannel.invokeMethod<String>('generateGeminiText', {
      'prompt': request.prompt,
      'model': request.model,
      'temperature': request.temperature,
      'topK': request.topK,
      'topP': request.topP,
      'maxOutputTokens': request.maxOutputTokens,
      'imageBase64': request.imageBase64,
    });

    return result ?? '';
  }

  Future<void> initializeOpenAi({
    required String apiKey,
    required String model,
    required double temperature,
    required int maxOutputTokens,
  }) {
    return _methodChannel.invokeMethod<void>('initializeOpenAi', {
      'apiKey': apiKey,
      'model': model,
      'temperature': temperature,
      'maxOutputTokens': maxOutputTokens,
    });
  }

  Future<String> generateOpenAiText(AiGenerationRequest request) async {
    final result = await _methodChannel.invokeMethod<String>('generateOpenAiText', {
      'prompt': request.prompt,
      'model': request.model,
      'temperature': request.temperature,
      'maxOutputTokens': request.maxOutputTokens,
      'imageBase64': request.imageBase64,
    });

    return result ?? '';
  }

  Future<String> generateGemmaText(AiGenerationRequest request) async {
    final result = await _methodChannel.invokeMethod<String>('generateGemmaText', {
      'prompt': request.prompt,
      'model': request.model,
      'temperature': request.temperature,
      'topK': request.topK,
      'topP': request.topP,
      'maxOutputTokens': request.maxOutputTokens,
      'imageBase64': request.imageBase64,
    });

    return result ?? '';
  }

  Stream<String> streamText({
    required AiProvider provider,
    required AiGenerationRequest request,
  }) {
    return _streamChannel.receiveBroadcastStream({
      'provider': provider.name,
      'prompt': request.prompt,
      'model': request.model,
      'temperature': request.temperature,
      'topK': request.topK,
      'topP': request.topP,
      'maxOutputTokens': request.maxOutputTokens,
      'imageBase64': request.imageBase64,
    }).map((event) => event?.toString() ?? '');
  }
}
