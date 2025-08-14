# Document Summarizer Test Documentation

This directory contains comprehensive tests for the Document Summarizer feature of MyGemma3N.

## Test Coverage Overview

### 1. SummarizerViewModelTest.kt - Unit Tests
**Coverage**: Core ViewModel functionality and state management

**Test Categories**:
- **Initial State**: Verifies correct initial state values
- **Successful Processing**: Tests complete file processing workflow
- **Error Handling**: Various error scenarios (empty files, AI failures, file access issues)
- **Manual Text Processing**: Direct text input functionality
- **State Management**: Error clearing, retry mechanism
- **Hierarchical Summarization**: Long text processing with chunking
- **Text Preprocessing**: Whitespace and formatting cleanup
- **Model Initialization**: AI model setup and readiness checking

**Key Test Methods**:
- `initial state is correct`
- `processFile updates state correctly for successful processing`
- `processFile handles empty document error`
- `processFile handles AI model failure`
- `processManualText processes text correctly`
- `hierarchical summarization works for long text`
- `retry processes last file again`

### 2. SummarizerIntegrationTest.kt - Integration Tests
**Coverage**: File processing with real Android context and filesystem operations

**Test Categories**:
- **Text File Processing**: End-to-end .txt file processing
- **Large Document Handling**: Documents >6000 characters triggering hierarchical processing
- **Empty File Handling**: Proper error handling for empty documents
- **Special Characters**: Unicode, emojis, mathematical symbols
- **Concurrent Processing**: Multiple file requests handling
- **Progress Tracking**: Progress indicator functionality
- **Retry Functionality**: Recovery from failures

**Key Test Methods**:
- `testTextFileProcessing`
- `testLargeTextFileProcessing`
- `testEmptyFileHandling`
- `testFileWithSpecialCharacters`
- `testConcurrentFileProcessing`
- `testProgressTracking`
- `testRetryFunctionality`

### 3. SummarizerEdgeCaseTest.kt - Edge Cases & Error Handling
**Coverage**: Boundary conditions, error scenarios, and robustness testing

**Test Categories**:
- **Text Length Limits**: Extremely long text truncation (>10,000 chars)
- **Invalid Input**: Whitespace-only text, malformed URIs
- **Character Encoding**: Unicode, emojis, special symbols
- **AI Service Issues**: Timeouts, empty responses, initialization failures
- **Memory Pressure**: Large document memory handling
- **Rapid State Changes**: Quick successive operations
- **Concurrent Operations**: Multiple simultaneous requests
- **Stream Corruption**: Corrupted input stream handling

**Key Test Methods**:
- `test extremely long text truncation`
- `test unicode and emoji text processing`
- `test AI service timeout handling`
- `test memory pressure simulation`
- `test rapid state changes`
- `test concurrent error and retry handling`

## Test Architecture

### Dependencies
```kotlin
// Testing Framework
//testImplementation 'junit:junit:4.13.2'
//testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
//
//// Android Testing
//androidTestImplementation 'androidx.test.ext:junit:1.1.5'
//androidTestImplementation 'androidx.test:runner:1.5.2'
//
//// Mocking
//testImplementation 'io.mockk:mockk:1.13.8'
//
//// State Testing
//testImplementation 'app.cash.turbine:turbine:1.0.0'
```

### Mock Strategy
- **Context**: Mocked for unit tests, real for integration tests
- **UnifiedGemmaService**: Always mocked to control AI responses
- **File System**: Real temporary files for integration tests
- **Uri**: Mocked for specific test scenarios

### Test Data Management
- **Unit Tests**: Use in-memory data and mocked streams
- **Integration Tests**: Create temporary files in app cache directory
- **Cleanup**: All tests properly clean up temporary files

## Running Tests

### Individual Test Classes
```bash
# Unit tests only
./gradlew test --tests "SummarizerViewModelTest"

# Integration tests only  
./gradlew connectedAndroidTest --tests "SummarizerIntegrationTest"

# Edge case tests only
./gradlew test --tests "SummarizerEdgeCaseTest"
```

### Complete Test Suite
```bash
# Run all summarizer tests
./gradlew test --tests "SummarizerTestSuite"

# Run with coverage report
./gradlew testDebugUnitTestCoverage
```

## Test Scenarios Covered

### ✅ Happy Path Scenarios
- [x] Successful text file processing
- [x] Manual text input processing  
- [x] Progress tracking during processing
- [x] Hierarchical summarization for long documents
- [x] Special character handling (Unicode, emojis)

### ✅ Error Handling Scenarios
- [x] Empty document processing
- [x] File access permission errors
- [x] AI model initialization failures
- [x] AI generation timeouts
- [x] Corrupted input streams
- [x] Malformed URIs

### ✅ Edge Cases
- [x] Extremely long text (>10,000 characters)
- [x] Whitespace-only input
- [x] Concurrent file processing requests
- [x] Rapid state changes
- [x] Memory pressure scenarios
- [x] Network connectivity issues (simulated)

### ✅ Performance & Robustness
- [x] Large file processing (>6000 chars)
- [x] Memory efficient text processing
- [x] Proper cleanup and resource management
- [x] Cancellation handling
- [x] Retry mechanism functionality

## Coverage Goals

**Target Coverage**: 90%+ for all Summarizer components

**Current Coverage Areas**:
- ✅ **State Management**: All state transitions tested
- ✅ **File Processing**: All supported file types covered
- ✅ **Error Handling**: Comprehensive error scenario coverage
- ✅ **AI Integration**: All AI service interactions mocked and tested
- ✅ **Edge Cases**: Boundary conditions and invalid inputs
- ✅ **Performance**: Memory and processing efficiency verified

## Future Test Enhancements

### Potential Additions
- [ ] **PDF Processing Tests**: When PDF support is fully implemented
- [ ] **DOCX Processing Tests**: When Word document support is added
- [ ] **Performance Benchmarks**: Processing time measurements
- [ ] **Memory Usage Tests**: Memory consumption validation
- [ ] **Accessibility Tests**: Screen reader compatibility
- [ ] **Localization Tests**: Multi-language text processing

### Test Improvements
- [ ] **Property-Based Testing**: Generate random text inputs
- [ ] **Stress Testing**: Very large document processing
- [ ] **Real Device Testing**: Testing on various Android devices
- [ ] **Battery Impact Testing**: Power consumption measurement

## Debugging Failed Tests

### Common Issues
1. **Timeout Errors**: Increase test timeout values in async operations
2. **Mock Setup**: Verify all required mocks are properly configured
3. **File Cleanup**: Ensure temporary files are deleted after tests
4. **Coroutine Testing**: Use proper test dispatchers for coroutine tests

### Debug Tips
```kotlin
// Enable verbose logging in tests
@Before
fun enableLogging() {
    Timber.plant(Timber.DebugTree())
}

// Add delays to observe state changes
delay(100) // Use sparingly in tests

// Verify mock interactions
coVerify(exactly = 1) { gemmaService.generateResponse(any(), any()) }
```

## Test Data Examples

### Sample Text Content

[//]: # (```kotlin)

[//]: # (val shortText = "Brief document for testing.")

[//]: # (val mediumText = "This is a moderate length document...".repeat&#40;10&#41;)

[//]: # (val longText = "Very long document content...".repeat&#40;100&#41;)

[//]: # (val unicodeText = "🌟 Welcome! Café résumé naïve 中文 العربية")

[//]: # (val specialCharsText = "@#$%^&*&#40;&#41;_+-=[]{}|;:'"<>,.?/")

[//]: # (```)

### Mock Responses
```kotlin
// Successful summarization
coEvery { gemmaService.generateResponse(any(), any()) } returns "Concise summary of document."

// Hierarchical processing
coEvery { gemmaService.generateResponse(match { it.contains("Key points") }, any()) } returns "Key points extracted."
coEvery { gemmaService.generateResponse(match { it.contains("Combine") }, any()) } returns "Final combined summary."

// Error simulation
coEvery { gemmaService.generateResponse(any(), any()) } throws Exception("AI processing failed")
```

This comprehensive test suite ensures the Document Summarizer feature is robust, reliable, and handles all expected use cases and edge conditions properly.