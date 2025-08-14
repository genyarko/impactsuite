# GEMINI.md

This file provides guidance to Gemini when working with code in this repository.

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