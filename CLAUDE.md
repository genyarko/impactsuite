# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

G3N is an Android application showcasing on-device generative AI capabilities using Google's Gemma 3n models. The app is written in Kotlin with Jetpack Compose and runs entirely offline, featuring:

- **AI Tutor** – personalized lesson sessions powered by generative AI
- **Live Caption & Translation** – real-time audio transcription with translation
- **Offline Quiz Generator** – generates multiple choice quizzes from topics
- **Voice CBT Coach** – emotion detection and Cognitive Behavioral Therapy techniques
- **Document Summarizer** – extracts and summarizes text from PDFs, DOCX, TXT files
- **Chat** – open-ended conversation with on-device AI
- **Image Classification** – on-device image recognition using TensorFlow Lite
- **Crisis Handbook** – safety resources with function calling
- **Voice Commands** – hands-free app navigation with "Hi Hi" wake word activation, including back/close navigation and app exit

## Build Commands

### Essential Build Commands
```bash
# Build the project
./gradlew build

# Run debug build on connected device/emulator
./gradlew installDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Check for dependency updates
./gradlew dependencyUpdates
```

### Model Requirements
Model files must be placed in `app/src/main/assets/models/` with exact filenames expected by `checkModelAvailability` in `MainActivity.kt`. Required files:
- `gemma-3n-E2B-it-int4.task` (Gemma 3n model)
- `universal_sentence_encoder.tflite` (sentence embeddings)

### Git LFS Setup
```bash
git lfs install  # Required for model files
```

## Architecture Overview

### Module Structure
- **`:app`** - Main application module with UI and feature implementations
- **`:gemma3n_assetpack`** - Dynamic asset pack for model delivery via Google Play

### Key Architectural Components

#### Dependency Injection (Hilt)
- All modules use Hilt for dependency injection
- Main DI configuration in `di/app_module_di.kt`
- Feature-specific modules for specialized dependencies

#### Data Layer
- **Room Database** - Local persistence for chats, quizzes, subjects, analytics
- **DataStore** - User preferences and settings
- **Repository Pattern** - Data access abstraction layer
- **Vector Database** - Optimized storage for embeddings and RAG functionality

#### AI/ML Integration
- **Gemma 3n Models** - Via Google AI Edge LiteRT and MediaPipe
- **TensorFlow Lite** - Image classification and text embeddings
- **Unified Services** - `UnifiedGemmaService` centralizes model interactions
- **Offline RAG** - Local retrieval-augmented generation capabilities

#### Feature Organization
Each major feature has its own package under `feature/`:
- `chat/` - Conversation interface with AI
- `quiz/` - Educational quiz generation and analytics
- `tutor/` - AI tutoring with adaptive content
- `cbt/` - Cognitive Behavioral Therapy coaching
- `caption/` - Live audio captioning
- `crisis/` - Emergency resources and safety
- `plant/` - Image classification for plants
- `summarizer/` - Document text extraction and summarization
- `voice/` - Voice command system with wake word detection and natural language processing

#### Navigation & UI
- **Jetpack Compose** - Modern declarative UI
- **Navigation Compose** - Screen navigation
- **Material 3** - Design system implementation
- **CameraX** - Camera integration for image features

## Development Guidelines

### Code Conventions
- Follow Kotlin coding standards with ktlint
- Use Compose for all UI components
- Implement proper error handling for offline AI operations
- Maintain separation between UI, domain, and data layers

### AI Model Integration
- Always check model availability before use via `checkModelAvailability()`
- Handle model loading failures gracefully with fallback options
- Use `UnifiedGemmaService` for consistent model interactions
- Implement proper resource cleanup for TensorFlow Lite models

### Database Operations
- Use Repository pattern for all data access
- Implement proper coroutine scoping for async operations
- Handle database migrations carefully when schema changes
- Use Room's built-in threading for database operations

### Performance Considerations
- Models run on-device - optimize for mobile performance
- Use background threads for heavy AI computations
- Implement proper caching for embeddings and model outputs
- Monitor memory usage during model inference

### Testing
- Unit tests in `src/test/`
- Instrumented tests in `src/androidTest/`
- Mock AI model responses for consistent testing
- Test offline functionality thoroughly

## Common Development Tasks

### Adding New AI Features
1. Create feature package under `feature/`
2. Define data models and repository interfaces
3. Implement UI with Compose
4. Add navigation routes
5. Configure dependency injection
6. Add proper error handling for offline scenarios

### Modifying AI Prompts
- Prompts stored in `assets/quiz_prompts.json` and `assets/tutor_prompts/`
- Use `PromptManager` classes for dynamic prompt generation
- Test prompt changes with actual model responses

### Database Schema Changes
1. Update entity classes in `data/local/entities/`
2. Create migration scripts in Room database configuration
3. Update DAOs and repositories as needed
4. Test migrations thoroughly

### Adding New Dependencies
- Update `gradle/libs.versions.toml` for version catalog
- Add dependencies to appropriate `build.gradle.kts` files
- Configure ProGuard rules if needed for release builds

## Voice Command System

The app includes a comprehensive voice command system that allows hands-free navigation and feature activation.

### Voice Command Usage
1. **Wake Word Activation**: Say "Hi Hi" to activate voice command mode
2. **Command Execution**: Follow with any supported command (see examples below)
3. **Continuous Listening**: System runs in background when enabled in settings

### Navigation Control Examples
- **"Hi Hi go back"** → Navigates to previous screen in the navigation stack
- **"Hi Hi close"** → Closes current screen (same as back button)
- **"Hi Hi close camera"** → Closes camera/scanner if currently open
- **"Hi Hi close quiz"** → Closes quiz generator if currently open
- **"Hi Hi close app"** → Exits the entire application (works from any screen)

### Supported Voice Commands

#### Navigation Commands
- **Quiz Generator**: "Open quiz", "Quiz generator", "Create quiz"
- **Chat**: "Open chat", "Start chat", "Unified chat", "Go to chat"
- **Image Scanner**: "Take picture", "Camera", "Plant scanner", "Scan image"
- **Text Scanner**: "Scan text", "OCR", "Text recognition", "Document scan"
- **AI Tutor**: "Open tutor", "Tutoring", "Lesson", "Teach me"
- **Summarizer**: "Summarize", "Document", "Open summarizer"
- **CBT Coach**: "Open CBT", "Mental health", "Therapy", "Wellness coach"
- **Live Caption**: "Live caption", "Transcribe", "Subtitles", "Caption audio"
- **Crisis Handbook**: "Crisis", "Emergency", "Crisis help", "Safety"
- **Analytics**: "Analytics", "Stats", "Dashboard", "Progress"
- **Chat History**: "Chat list", "Chat history", "Conversations"
- **Story Mode**: "Open story mode", "Story", "Stories"
- **Settings**: "Settings", "Preferences", "Options"
- **Home**: "Home", "Go home", "Main screen"

#### Navigation Control Commands
- **Go Back**: "Go back", "Back", "Navigate back", "Previous"
- **Close Screen**: "Close", "Exit", "Escape", "Close screen"
- **Close Quiz**: "Close quiz", "Exit quiz", "Quit quiz"
- **Close Camera**: "Close camera", "Exit scanner", "Stop camera"
- **Close CBT**: "Close CBT", "Exit therapy", "Quit CBT"  
- **Close App**: "Close app", "Exit app", "Quit application" (only from unified screen)

#### Feature Commands (Contextual)
- **Generate Quiz**: "Generate quiz from this", "Make quiz from content"
- **Summarize Content**: "Summarize this", "What does this say"
- **Translate**: "Translate this", "Translate to English"
- **Take Photo**: "Take photo", "Capture", "Snap"

#### System Commands
- **Help**: "Help", "What can you do", "Show commands"
- **Stop**: "Stop listening", "Cancel", "Never mind"
- **Theme**: "Dark mode", "Light mode", "Switch theme"

### Voice Command Architecture

#### Key Components
- **VoiceCommandService**: Background foreground service for continuous wake word listening
- **VoiceCommandManager**: Command parsing, fuzzy matching, and execution logic
- **VoiceCommandViewModel**: UI state management and service coordination
- **VoiceCommandUI**: Floating button overlay and status indicators
- **VoiceCommand**: Sealed class hierarchy defining all supported commands

#### Technical Features
- **Wake Word Detection**: Uses Android SpeechRecognizer for "Hi Hi" detection
- **Fuzzy Matching**: Levenshtein distance algorithm for command recognition
- **Silent Operation**: Audio focus management and system sound suppression
- **Continuous Listening**: Background service with foreground notification
- **Natural Language**: Multiple patterns per command for flexibility
- **Error Recovery**: Automatic retry and graceful error handling

### Voice Command Implementation

#### Adding New Voice Commands
1. **Define Command**: Add new command object to `VoiceCommand.kt` sealed class
2. **Add Patterns**: Define recognition patterns and examples
3. **Implement Execution**: Add execution logic to `VoiceCommandManager.kt`
4. **Update Command List**: Include in `getAllCommands()` method
5. **Test Recognition**: Verify pattern matching and execution

#### Command Pattern Matching
- **Exact Match**: Highest confidence for exact text matches
- **Contains Match**: High confidence for partial matches
- **Fuzzy Match**: Medium confidence using similarity algorithms
- **Word Match**: Pattern matching for individual words
- **Parameter Extraction**: Support for commands with variables

### Voice Command Configuration
- **Haptic Feedback**: Vibration on wake word detection
- **Audio Feedback**: Optional sound notifications (disabled by default)
- **Continuous Listening**: Background wake word monitoring
- **Command Timeout**: Configurable timeout for command recognition
- **Language Support**: Configurable speech recognition language