# G3N Android Application - Complete Documentation

## Table of Contents
1. [Overview](#overview)
2. [Features](#features)
3. [Architecture](#architecture)
4. [Installation & Setup](#installation--setup)
5. [Technical Implementation](#technical-implementation)
6. [Development Guide](#development-guide)
7. [API Integration](#api-integration)
8. [Testing](#testing)
9. [Performance Optimization](#performance-optimization)
10. [Troubleshooting](#troubleshooting)

## Overview

G3N is an advanced Android application that demonstrates the power of on-device generative AI using Google's Gemma 3n models. Built with Kotlin and Jetpack Compose, the app provides a comprehensive suite of AI-powered educational and productivity tools that work entirely offline while also supporting online capabilities for enhanced functionality.

### Key Highlights
- **100% Offline Capable**: Core functionality powered by on-device Gemma 3n models
- **Hybrid Online/Offline Architecture**: Seamlessly switches between local and cloud AI services
- **Educational Focus**: Comprehensive learning tools with adaptive curriculum
- **Privacy-First**: All sensitive operations can run locally without data transmission
- **Modern Android Architecture**: Built with latest Android development best practices

## Features

### 🎓 AI Tutor
**Location**: `app/src/main/java/com/example/mygemma3n/feature/tutor/`

The AI Tutor is the flagship feature providing personalized education assistance:

#### Core Capabilities:
- **Adaptive Learning Paths**: Content adjusts based on student performance and grade level (K-12)
- **Context Memory**: Maintains conversation history for natural follow-up questions
- **Subject Coverage**: Mathematics, Science, English, History, Geography, Computer Science, Economics
- **Response Streaming**: Real-time response generation with typing indicators
- **Progress Tracking**: Monitors learning analytics and knowledge gaps

#### Technical Implementation:
- **Offline RAG**: Local retrieval-augmented generation using vector embeddings
- **Curriculum Integration**: JSON-based curriculum files for structured learning
- **Prompt Management**: Dynamic prompt generation based on educational context
- **Performance Optimization**: Background processing and caching for smooth UX

### 📝 Live Caption & Translation
**Location**: `app/src/main/java/com/example/mygemma3n/feature/caption/`

Real-time audio processing with translation capabilities:

#### Features:
- **Real-time Transcription**: Converts speech to text using on-device models
- **Multi-language Support**: Translation between supported languages
- **Audio Visualization**: Live audio level indicators
- **Offline Processing**: Complete functionality without internet connection

#### Technical Stack:
- **Audio Processing**: Custom audio service with permission handling
- **Speech Recognition**: Android's built-in speech recognition with fallbacks
- **Translation Engine**: Integrated with offline translation models

### 🧠 Voice CBT Coach
**Location**: `app/src/main/java/com/example/mygemma3n/feature/cbt/`

Cognitive Behavioral Therapy assistance with robust hybrid functionality:

#### Capabilities:
- **Emotion Detection**: Analyzes voice patterns to identify emotional states
- **CBT Techniques**: Provides evidence-based therapeutic interventions
- **Session Management**: Tracks therapy sessions and progress
- **Privacy-Focused**: All analysis happens on-device when possible
- **Hybrid Processing**: Seamless fallback between offline and online AI services

#### Recent Improvements:
- **Enhanced UX**: Collapsible emotion and technique cards for better screen space
- **Robust Error Handling**: Comprehensive offline/online fallback system
- **User-Friendly Messages**: Clear, actionable error messages instead of technical errors
- **Smart Service Selection**: Automatic detection and switching between AI services
- **Audio Processing**: Improved voice recording and transcription reliability

#### Implementation Details:
- **Audio Analysis**: Voice pattern recognition for emotion detection
- **Therapeutic Algorithms**: Rule-based CBT technique suggestions
- **Session Persistence**: Local storage of therapy sessions
- **Fallback Architecture**: Smart routing between Gemma (offline) and cloud AI services
- **Error Recovery**: Graceful degradation and user guidance during failures

### 📊 Offline Quiz Generator
**Location**: `app/src/main/java/com/example/mygemma3n/feature/quiz/`

Intelligent quiz generation system with enhanced content integration:

#### Features:
- **Topic-Based Generation**: Creates quizzes from any subject area
- **Content Integration**: Generate quizzes directly from AI Tutor and Chat sessions
- **Multiple Choice Format**: Standardized quiz format with scoring
- **Performance Analytics**: Tracks quiz performance and learning gaps
- **Curriculum Integration**: Aligned with educational standards
- **Smart Navigation**: Seamless quiz creation from conversation content

#### Recent Enhancements:
- **Generate Quiz Buttons**: Added to AI Tutor and Chat screens for easy quiz creation
- **Content Manager**: QuizContentManager singleton for cross-screen content sharing
- **Hybrid Generation**: Online/offline AI-powered question generation
- **Question Type Intelligence**: Grade-appropriate question type selection

#### Technical Architecture:
- **AI Generation**: Uses Gemma models to create contextually relevant questions
- **Caching System**: Question history cache prevents duplication
- **Analytics Engine**: Comprehensive performance tracking
- **Offline Storage**: Room database for quiz persistence
- **Content Pipeline**: Automatic content analysis and topic extraction

### 📄 Document Summarizer
**Location**: `app/src/main/java/com/example/mygemma3n/feature/summarizer/`

Advanced document processing and summarization:

#### Supported Formats:
- PDF files with text extraction
- DOCX documents
- Plain text files
- Multi-page document handling

#### Features:
- **Intelligent Summarization**: Key point extraction with context preservation
- **Review Questions**: Auto-generated questions for comprehension testing
- **Text Extraction**: Robust parsing of various document formats
- **Offline Processing**: Complete functionality without cloud dependencies

### 💬 Chat Interface
**Location**: `app/src/main/java/com/example/mygemma3n/feature/chat/`

Open-ended conversational AI:

#### Features:
- **Natural Conversations**: Context-aware dialogue system
- **Chat History**: Persistent conversation storage
- **Multiple Chat Sessions**: Support for concurrent conversations
- **Hybrid Processing**: Online/offline mode switching

#### Technical Implementation:
- **Message Management**: Efficient chat message storage and retrieval
- **Context Handling**: Long-term conversation memory
- **UI Components**: Modern chat interface with message bubbles

### 📱 Image Classification
**Location**: `app/src/main/java/com/example/mygemma3n/feature/plant/`

On-device image recognition system:

#### Capabilities:
- **Plant Identification**: Specialized plant classification
- **Camera Integration**: CameraX integration for real-time capture
- **TensorFlow Lite**: Optimized on-device inference
- **Result Confidence**: Confidence scoring for classifications

#### Technical Stack:
- **MobileNet Architecture**: Efficient CNN for mobile devices
- **Image Processing**: Advanced preprocessing pipelines
- **Camera Utils**: Custom camera handling utilities

## 🌐 Accessibility & User Experience

### Accessibility Enhancements
**Location**: Throughout the application with focus on core screens

#### Comprehensive Accessibility Support:
- **Screen Reader Compatibility**: Complete content descriptions for all interactive elements
- **Semantic Navigation**: Proper role definitions and semantic properties for navigation elements
- **Keyboard Navigation**: Enhanced support for keyboard and switch navigation
- **State Awareness**: Context-sensitive descriptions that reflect current app state

#### Key Improvements:
- **Content Descriptions**: Added meaningful descriptions to all icons, buttons, and interactive elements
- **Navigation Semantics**: Feature cards and buttons include proper `Role.Button` semantics
- **Error Accessibility**: Error messages are screen reader friendly with clear guidance
- **Dynamic Descriptions**: State-aware descriptions (e.g., "Current emotion: neutral")

### User Experience Improvements
#### Error Message Enhancement:
- **User-Friendly Language**: Transformed technical error messages into clear, actionable guidance
- **Solution-Oriented**: Error messages now guide users toward solutions rather than just stating problems
- **Context-Aware**: Error messages adapt based on the specific feature and situation

#### Examples of Improved Messages:
- **Before**: `"Failed to initialize: ${e.message}"`
- **After**: `"Unable to start CBT Coach. Please check your internet connection and try again."`

- **Before**: `"Recording error: ${e.message}"`
- **After**: `"Recording failed. Please check microphone permissions and try again."`

### 🚨 Crisis Handbook
**Location**: `app/src/main/java/com/example/mygemma3n/feature/crisis/`

Emergency assistance and safety resources:

#### Features:
- **Function Calling**: AI-powered resource lookup
- **Location Services**: Local emergency service integration
- **Safety Protocols**: Evidence-based emergency procedures
- **Offline Resources**: Critical information available without connectivity

#### Implementation:
- **Emergency Database**: Local storage of critical safety information
- **Location Integration**: Google Places API for emergency services
- **Function Calling System**: Structured AI responses for emergency queries

### 📈 Story Mode (New)
**Location**: `app/src/main/java/com/example/mygemma3n/feature/story/`

Interactive storytelling with AI:

#### Features:
- **Creative Writing**: AI-generated stories and narratives
- **Image Generation**: Story illustration capabilities
- **Interactive Elements**: User-driven story progression
- **Token Tracking**: Usage monitoring for online services

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Jetpack        │  │   Navigation    │  │   Material   │ │
│  │  Compose UI     │  │   Component     │  │   Design 3   │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Domain Layer                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   ViewModels    │  │   Use Cases     │  │ Repositories │ │
│  │   (Hilt DI)     │  │                 │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │    Room      │  │  DataStore   │  │   AI/ML Services    │ │
│  │  Database    │  │ Preferences  │  │  (Gemma, TF Lite)   │ │
│  └──────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Module Structure

#### `:app` Module
Main application module containing all feature implementations:

- **Feature Packages**: Each major feature isolated in its own package
- **Shared Components**: Reusable UI components and utilities
- **Data Layer**: Room database, repositories, and data sources
- **DI Configuration**: Hilt modules for dependency injection

#### `:gemma3n_assetpack` Module
Dynamic feature module for model delivery:

- **Model Distribution**: Large AI models delivered via Google Play
- **Asset Management**: Handles model downloads and updates
- **Fallback Handling**: Downloads models when Play Asset Delivery unavailable

### Dependency Injection (Hilt)

**Location**: `app/src/main/java/com/example/mygemma3n/di/`

#### Key DI Modules:
- **AppModule**: Core application dependencies
- **GemmaServiceModule**: AI model service configuration
- **DatabaseModule**: Room database and DAO bindings
- **NetworkModule**: API services and HTTP clients
- **WorkerModule**: Background task dependencies

### Data Architecture

#### Room Database Schema
**Location**: `app/src/main/java/com/example/mygemma3n/data/local/`

**Entities**:
- `ChatEntity`: Chat message storage
- `QuizEntity`: Quiz questions and results
- `SubjectEntity`: Educational subject metadata
- `TutorEntity`: Tutoring session data
- `TokenUsageEntity`: API usage tracking
- `UserQuotaEntity`: User quota management

#### Repository Pattern
**Location**: `app/src/main/java/com/example/mygemma3n/data/repository/`

Abstraction layer between ViewModels and data sources:
- **Local Data**: Room database operations
- **Remote Data**: API service calls
- **Caching**: Intelligent data caching strategies
- **Offline Support**: Graceful degradation when offline

### AI/ML Integration

#### Unified AI Service
**Location**: `app/src/main/java/com/example/mygemma3n/data/unified_gemma_service.kt`

Central service managing all AI operations:
- **Model Management**: Loading and memory management of AI models
- **Request Routing**: Intelligent routing between online/offline services
- **Error Handling**: Graceful fallbacks and error recovery
- **Performance Optimization**: Batch processing and caching

#### Vector Database
**Location**: `app/src/main/java/com/example/mygemma3n/data/local/optimized_vector_database.kt`

Optimized storage for embeddings and RAG:
- **Embedding Storage**: Efficient vector storage and retrieval
- **Similarity Search**: Fast nearest neighbor search
- **Index Management**: Optimized indexing for query performance
- **Memory Optimization**: Chunked loading for large datasets

## Recent Development Updates

### Version 2.1.0 - August 2025

#### 🆕 Major Feature Additions:
- **Generate Quiz Feature**: Revolutionary quiz creation from AI conversations
  - One-click quiz generation from AI Tutor sessions
  - Smart content analysis and question extraction from chat sessions
  - Seamless navigation from conversation to quiz taking
  - Content preservation across screen transitions using QuizContentManager

#### 🔧 Critical Bug Fixes:
- **CBT Coach Reliability**: Complete overhaul of offline/online fallback system
  - Fixed crashes when offline models unavailable
  - Implemented robust error recovery mechanisms
  - Enhanced audio processing reliability
  - Smart service detection and automatic switching

#### 🎨 User Experience Enhancements:
- **Accessibility Improvements**: Comprehensive accessibility support implementation
  - Added complete content descriptions for screen readers
  - Enhanced navigation semantics with proper role definitions
  - Improved keyboard and switch navigation support
  - Context-aware accessibility descriptions

- **Error Message Redesign**: User-friendly error communication
  - Replaced technical error messages with actionable guidance
  - Solution-oriented error descriptions
  - Context-sensitive help suggestions

- **UI/UX Polish**: Enhanced interface design
  - Collapsible emotion and technique cards in CBT Coach
  - Improved navigation flow between features
  - Better visual hierarchy and information density

#### 🏗️ Technical Improvements:
- **Build System Optimization**: Production-ready deployment configuration
  - R8/ProGuard optimization for Play Store
  - Deobfuscation mapping for crash analysis
  - Asset pack delivery optimization

- **Hybrid AI Architecture**: Enhanced online/offline AI service management
  - Intelligent service selection based on availability
  - Graceful degradation patterns
  - Improved error recovery and fallback mechanisms

## Installation & Setup

### Prerequisites
- **Android Studio**: Giraffe (2022.3.1) or newer
- **Android SDK**: API Level 36 (Android 14)
- **Git LFS**: Required for model file handling
- **Device Requirements**: Minimum 4GB RAM recommended for AI features
- **Gradle**: Version 8.13 or newer

### Step-by-Step Installation

1. **Clone Repository**
   ```bash
   git clone https://github.com/your-repo/G3N.git
   cd G3N
   ```

2. **Setup Git LFS**
   ```bash
   git lfs install
   git lfs pull
   ```

3. **Configure Model Files**
   - Create directory: `app/src/main/assets/models/`
   - Download required models:
     - `gemma-3n-E2B-it-int4.task` (Primary Gemma model)
     - `universal_sentence_encoder.tflite` (Text embeddings)
   - Place files in the models directory

4. **API Configuration** (Optional for online features)
   - Copy `API_KEYS_SETUP.md` template
   - Configure API keys for:
     - Google Gemini API
     - Google Places API
     - Google Maps API

5. **Build Project**
   ```bash
   ./gradlew build
   ```

6. **Install on Device**
   ```bash
   ./gradlew installDebug
   ```

### Gradle Build Configuration

#### Version Catalog
**Location**: `gradle/libs.versions.toml`

Centralized dependency management with version catalogs:
- **Android Components**: Activity, Fragment, Lifecycle
- **Jetpack Compose**: UI toolkit and navigation
- **AI/ML Libraries**: TensorFlow Lite, MediaPipe, Google AI Edge
- **Architecture Components**: Room, Hilt, WorkManager

#### Build Scripts
**Location**: `app/build.gradle.kts`

Key build configurations:
- **Compile SDK**: 36 (Android 14)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **ProGuard**: R8 optimization for release builds

## Technical Implementation

### AI Model Integration

#### Model Loading and Management
**Location**: `app/src/main/java/com/example/mygemma3n/gemma/`

**Model Manager**: Central point for AI model lifecycle management
- **Lazy Loading**: Models loaded on demand to conserve memory
- **Memory Management**: Automatic model unloading when not in use
- **Error Recovery**: Fallback mechanisms for model loading failures
- **Version Management**: Support for model updates and rollbacks

#### Offline RAG Implementation
**Location**: `app/src/main/java/com/example/mygemma3n/shared_utilities/offlinerag.kt`

**Components**:
- **Document Chunking**: Intelligent text segmentation for optimal retrieval
- **Embedding Generation**: Local text-to-vector conversion
- **Vector Search**: Efficient similarity search for relevant context
- **Context Assembly**: Dynamic context window management

### Performance Optimization

#### Database Optimization
**Location**: `app/src/main/java/com/example/mygemma3n/data/optimization/`

**Strategies**:
- **Index Optimization**: Strategic database indexing for query performance
- **Connection Pooling**: Efficient database connection management
- **Batch Operations**: Grouped database operations for improved throughput
- **Cache Management**: Multi-level caching strategy

#### Memory Management
- **Model Caching**: Intelligent AI model memory management
- **Image Processing**: Optimized image loading and caching
- **Background Processing**: Proper thread management for heavy operations
- **Memory Profiling**: Built-in memory usage monitoring

### Network Architecture

#### Hybrid Online/Offline System
**Location**: `app/src/main/java/com/example/mygemma3n/feature/*/Online*Service.kt`

**Implementation**:
- **Connection Detection**: Automatic online/offline mode switching
- **Graceful Degradation**: Seamless fallback to offline capabilities
- **Sync Mechanisms**: Data synchronization when connectivity restored
- **Error Handling**: Comprehensive error recovery strategies

#### API Integration
- **Gemini API**: Google's latest generative AI service
- **Places API**: Location-based emergency services
- **Maps API**: Geographic data and navigation
- **Custom Endpoints**: Extensible API architecture

## Development Guide

### Code Organization

#### Feature-Based Architecture
Each feature is self-contained with its own:
- **UI Components**: Jetpack Compose screens and components
- **ViewModels**: State management and business logic
- **Repositories**: Data access abstraction
- **Services**: Feature-specific business services
- **Models**: Data classes and domain objects

#### Naming Conventions
- **Files**: PascalCase for classes, snake_case for resources
- **Packages**: lowercase with underscores for multi-word packages
- **Functions**: camelCase following Kotlin conventions
- **Constants**: UPPER_SNAKE_CASE in companion objects

### Adding New Features

1. **Create Feature Package**
   ```
   app/src/main/java/com/example/mygemma3n/feature/newfeature/
   ├── NewFeatureScreen.kt
   ├── NewFeatureViewModel.kt
   ├── NewFeatureRepository.kt
   └── NewFeatureModels.kt
   ```

2. **Implement Core Components**
   - **Screen**: Jetpack Compose UI
   - **ViewModel**: State management with Hilt injection
   - **Repository**: Data access layer
   - **Navigation**: Add to main navigation graph

3. **Database Integration** (if needed)
   - Create entity classes
   - Define DAO interfaces
   - Add to database module
   - Create migration scripts

4. **Testing**
   - Unit tests for ViewModels and repositories
   - UI tests for screens
   - Integration tests for complete flows

### Code Style Guidelines

#### Kotlin Conventions
- **Immutability**: Prefer `val` over `var`, immutable data classes
- **Null Safety**: Explicit null handling, avoid `!!` operator
- **Coroutines**: Use structured concurrency with proper scope management
- **Extensions**: Leverage Kotlin extension functions for utility methods

#### Compose Guidelines
- **State Management**: Use `remember`, `rememberSaveable` appropriately
- **Side Effects**: Proper use of `LaunchedEffect`, `DisposableEffect`
- **Performance**: Avoid unnecessary recompositions
- **Accessibility**: Implement proper content descriptions and semantics

## API Integration

### Google Services Integration

#### Gemini API Configuration
**Location**: `app/src/main/java/com/example/mygemma3n/data/gemini_api_service.kt`

**Features**:
- **Model Selection**: Dynamic model selection based on requirements
- **Token Management**: Usage tracking and quota enforcement
- **Error Handling**: Comprehensive error recovery and fallback
- **Rate Limiting**: Built-in rate limiting and retry mechanisms

#### Places API Integration
**Location**: `app/src/main/java/com/example/mygemma3n/data/GooglePlacesService.kt`

**Capabilities**:
- **Hospital Search**: Emergency medical facility location
- **Geographic Queries**: Location-based service discovery
- **Caching**: Local caching of frequently accessed places
- **Offline Fallback**: Static emergency contact database

### Cost Management

#### Token Tracking System
**Location**: `app/src/main/java/com/example/mygemma3n/service/CostCalculationService.kt`

**Features**:
- **Usage Analytics**: Detailed token consumption tracking
- **Cost Estimation**: Real-time cost calculation for API usage
- **Quota Management**: User-level quota enforcement
- **Billing Preparation**: Foundation for future monetization

#### Optimization Strategies
- **Prompt Optimization**: Efficient prompt engineering to minimize tokens
- **Caching**: Aggressive caching of API responses
- **Local Processing**: Prefer offline processing when possible
- **Batch Processing**: Group API calls for efficiency

## Testing

### Test Architecture

#### Unit Tests
**Location**: `app/src/test/java/com/example/mygemma3n/`

**Coverage Areas**:
- **ViewModels**: State management and business logic
- **Repositories**: Data access layer functionality
- **Utilities**: Helper functions and extensions
- **AI Services**: Model interaction and processing

#### Integration Tests
**Location**: `app/src/androidTest/java/com/example/mygemma3n/`

**Test Scenarios**:
- **Database Operations**: Room database CRUD operations
- **UI Workflows**: Complete user journey testing
- **AI Integration**: Model loading and inference testing
- **Network Layer**: API integration testing with mocking

#### Test Utilities
- **Mock Factories**: Standardized test data generation
- **Test Extensions**: Common testing utilities and assertions
- **AI Mocking**: Mock AI responses for consistent testing
- **Database Testing**: In-memory database for isolated tests

### Running Tests

```bash
# Unit tests
./gradlew test

# Integration tests (requires connected device)
./gradlew connectedAndroidTest

# Specific test class
./gradlew test --tests "com.example.mygemma3n.feature.quiz.QuizViewModelTest"

# Generate test coverage report
./gradlew jacocoTestReport
```

## Performance Optimization

### AI Model Optimization

#### Memory Management
- **Model Quantization**: INT4 quantized models for reduced memory footprint
- **Lazy Loading**: Load models only when needed
- **Memory Pooling**: Reuse model instances across features
- **Garbage Collection**: Explicit cleanup of model resources

#### Inference Optimization
- **Batch Processing**: Group similar requests for efficiency
- **Context Caching**: Cache processed context for repeated queries
- **Hardware Acceleration**: Leverage GPU/NPU when available
- **Temperature Caching**: Dynamic temperature calculation optimization

### Database Performance

#### Query Optimization
- **Strategic Indexing**: Optimize queries with proper indexing
- **Query Planning**: Analyze and optimize complex queries
- **Connection Management**: Efficient database connection handling
- **Transaction Batching**: Group related operations

#### Caching Strategy
- **Multi-Level Caching**: Memory, disk, and network caching layers
- **Cache Invalidation**: Intelligent cache refresh strategies
- **Prefetching**: Anticipatory data loading
- **Compression**: Compressed storage for large datasets

### UI Performance

#### Compose Optimization
- **Recomposition Minimization**: Optimize state management
- **List Performance**: Efficient lazy list implementations
- **Animation Optimization**: Smooth animations with proper threading
- **Memory Management**: Image loading and caching optimization

## Troubleshooting

### Common Issues

#### Model Loading Failures
**Symptoms**: App crashes on feature access, "Model not available" errors

**Solutions**:
1. Verify model files are in correct location: `app/src/main/assets/models/`
2. Check file permissions and integrity
3. Ensure sufficient device memory (4GB+ recommended)
4. Clear app data and restart

#### API Integration Issues
**Symptoms**: Online features not working, authentication errors

**Solutions**:
1. Verify API keys are correctly configured
2. Check network connectivity and firewall settings
3. Review API quota limits and usage
4. Enable offline fallback mode in settings

#### Performance Issues
**Symptoms**: Slow response times, UI lag, memory warnings

**Solutions**:
1. Monitor memory usage in developer options
2. Clear cache data periodically
3. Close unused background apps
4. Consider using offline mode for better performance

#### Database Issues
**Symptoms**: Data loss, corruption errors, slow queries

**Solutions**:
1. Clear app data to reset database
2. Check available storage space
3. Review database migration logs
4. Verify Room schema matches entity definitions

### Debug Tools

#### Logging System
**Location**: `app/src/main/java/com/example/mygemma3n/common/logging/`

**Features**:
- **Structured Logging**: Consistent log format across features
- **Performance Metrics**: Built-in performance monitoring
- **Error Tracking**: Comprehensive error logging and reporting
- **Debug Modes**: Different logging levels for development/production

#### Performance Monitoring
- **AI Model Metrics**: Inference time and memory usage tracking
- **Database Performance**: Query execution time monitoring
- **Network Metrics**: API response time and error rate tracking
- **UI Performance**: Frame rate and jank detection

### Development Tools

#### Useful Gradle Tasks
```bash
# Dependency analysis
./gradlew dependencyUpdates

# Build analysis
./gradlew build --profile

# Memory analysis
./gradlew installDebug -Pandroid.enableProfilers=true

# Asset verification
./gradlew verifyAssets
```

#### Android Studio Integration
- **Database Inspector**: View Room database contents
- **Layout Inspector**: Debug Compose UI hierarchy
- **Memory Profiler**: Monitor memory usage patterns
- **Network Inspector**: Debug API calls and responses

---

## Conclusion

G3N represents a comprehensive implementation of on-device AI capabilities in a modern Android application. The hybrid online/offline architecture ensures reliable functionality while the modular design allows for easy extension and maintenance. The application serves as both a functional educational tool and a reference implementation for AI-powered mobile applications.

For additional support or contributions, refer to the project's GitHub repository or contact the development team.

---

## 📚 Related Documentation

### Additional Resources:
- **LATEST_UPDATES_SUMMARY.md** - Comprehensive summary of recent feature additions and improvements
- **CLAUDE.md** - Development guidelines and project architecture notes
- **API_KEYS_SETUP.md** - Step-by-step API configuration instructions
- **SPEECH_RESTORATION_ROADMAP.md** - Audio feature development roadmap
- **UNIVERSAL_EMBEDDER_RESTORATION_GUIDE.md** - Text embedding system setup guide

### Quick Reference:
- **Recent Features**: See `LATEST_UPDATES_SUMMARY.md` for detailed changelog
- **Build Issues**: Check `CLAUDE.md` for build commands and troubleshooting
- **API Setup**: Follow `API_KEYS_SETUP.md` for online feature configuration
- **Architecture**: Review architecture diagrams and patterns in this document

---

*Generated with Claude Code - Last updated: August 2025*

**Major Update**: Version 2.1.0 includes Generate Quiz feature, enhanced CBT Coach reliability, comprehensive accessibility improvements, and user-friendly error messages. See `LATEST_UPDATES_SUMMARY.md` for complete details.