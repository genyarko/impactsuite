# Google Cloud Speech Restoration Roadmap

## Current Status
- ✅ Size optimization completed: 155MB → 97.9MB (37.2% reduction)
- ✅ Google Cloud Speech dependency re-added with conflict exclusions
- ⚠️ SpeechRecognitionService currently has mock implementation

## Remaining Tasks to Complete Speech Integration

### 1. Restore SpeechRecognitionService Implementation
**File:** `app/src/main/java/com/example/mygemma3n/data/speech_recognition_service.kt`

**Actions needed:**
- Replace mock `transcribeAudioData()` with actual Google Cloud Speech API calls
- Restore the streaming transcription methods:
  - `transcribeLiveCaptions()`
  - `transcribeUtterances()`
- Re-implement the full REST API integration that was removed

**Key code sections to restore:**
```kotlin
// Add back imports:
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.SpeechClient
import okhttp3.OkHttpClient
import org.json.JSONObject
import com.example.mygemma3n.config.ApiConfiguration

// Restore the complex streaming engine and REST helper methods
```

### 2. Resolve Protobuf Conflicts (if they occur)
**Current exclusions in build.gradle.kts:**
- `com.google.firebase:protolite-well-known-types`
- `com.google.api.grpc:proto-google-common-protos`
- `com.google.protobuf:protobuf-javalite`

**If build fails with duplicate classes:**
1. Try adding more specific exclusions to Google Cloud Speech dependency
2. Consider using `configurations.all { resolutionStrategy.force() }` for specific protobuf versions
3. May need to exclude additional Firebase dependencies causing conflicts

### 3. Test Speech Features
**Features to verify work correctly:**
- Live Caption & Translation (uses `transcribeLiveCaptions()`)
- CBT Voice recordings (uses `transcribeUtterances()`)
- Any other features using `transcribeAudioData()`

**Test files to check:**
- `app/src/main/java/com/example/mygemma3n/feature/caption/live_caption_view_model.kt:47`
- Other files found in previous Grep search for SpeechRecognitionService

### 4. Size Impact Assessment
**After restoration:**
1. Build release AAB: `./gradlew bundleRelease -x lintVitalAnalyzeRelease`
2. Check final size: should still be significantly under 150MB target
3. If size exceeds target, consider:
   - Additional ProGuard rules for Google Cloud Speech
   - Selective feature flags to disable speech in certain builds
   - Further dependency optimization

### 5. Error Handling & Edge Cases
**Ensure robust handling of:**
- Network connectivity issues
- API key validation failures
- Audio format/codec issues
- Streaming interruptions

## Build Commands Reference
```bash
# Debug build (should work)
./gradlew assembleDebug

# Release build with lint skip (if file locks occur)
./gradlew bundleRelease -x lintVitalAnalyzeRelease --no-daemon

# Check for dependency conflicts
./gradlew app:dependencies --configuration releaseRuntimeClasspath
```

## Potential Issues to Watch For

### 1. Protobuf Version Conflicts
- Firebase vs Google Cloud libraries using different protobuf versions
- Solution: Use `resolutionStrategy.force()` to standardize versions

### 2. ProGuard/R8 Issues
- Speech API classes being obfuscated incorrectly
- Solution: Add `-keep` rules for Google Cloud Speech classes

### 3. Size Regression
- Google Cloud Speech adds ~30-40MB
- Solution: Verify aggressive minification is working properly

## Success Criteria
- [ ] Release build completes successfully
- [ ] Speech recognition features work in live app
- [ ] Final AAB size remains under 150MB
- [ ] No runtime crashes in speech-related features
- [ ] API calls succeed with valid keys

## Notes
- Mock implementation preserved current app stability during optimization
- Size reduction of 58MB achieved even with large dependencies
- Core AI features (Gemma, image classification) unaffected by changes