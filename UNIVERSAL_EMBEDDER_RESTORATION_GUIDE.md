# Universal Sentence Encoder Restoration Guide

## Current Issue
The Universal Sentence Encoder model was removed for size optimization, but the app needs it for:
- Text similarity features in the Tutor
- RAG (Retrieval Augmented Generation) 
- Semantic search functionality

## Quick Fix Solutions

### Option 1: Add Model File Manually (Recommended)
1. **Download the model** from one of these sources:
   ```
   # Original TensorFlow Hub model (you'll need to convert to .tflite)
   https://tfhub.dev/google/universal-sentence-encoder/4
   
   # Pre-converted .tflite version (find from TensorFlow model zoo)
   https://www.tensorflow.org/lite/models
   ```

2. **Place the file** at:
   ```
   app/src/main/assets/models/universal_sentence_encoder.tflite
   ```

3. **Build and test**:
   ```bash
   ./gradlew assembleDebug
   ```

### Option 2: Use Git History (If Available)
```bash
# Check if the model exists in git history
git log --name-only | grep -i universal

# If found, restore from a previous commit
git show HEAD~5:app/src/main/assets/models/universal_sentence_encoder.tflite > app/src/main/assets/models/universal_sentence_encoder.tflite
```

### Option 3: Download Alternative Embedding Model
Use a smaller alternative like:
- **MobileBERT** (smaller, ~25MB vs ~6MB)
- **DistilBERT** (faster inference)
- **TinyBERT** (smallest, ~3MB)

## Current App Behavior

### ✅ What Works Now:
- App launches without crashing
- Mock embeddings provide basic functionality
- All other AI features work (Gemma, image classification)

### ⚠️ What's Limited:
- Semantic search quality is poor (uses hash-based mock embeddings)
- Text similarity features are inaccurate
- RAG quality is reduced

## Size Impact

| Option | AAB Size | Download Quality |
|--------|----------|-----------------|
| No embedding model | 97.9MB | Mock embeddings |
| With Universal Sentence Encoder | ~103.8MB | High-quality embeddings |
| With smaller alternative | ~100-102MB | Good embeddings |

All options remain **well under the 150MB target** ✅

## Integration with Existing Download System

The app already has a `ModelDownloadService` that you mentioned shows "all models available." To properly integrate:

1. **Add embedding model** to the existing download system
2. **Update ModelDownloadScreen** to include Universal Sentence Encoder
3. **Show download progress** when first needed

## Recommended Immediate Action

**Add the model file manually** to restore full functionality:

1. Find or download `universal_sentence_encoder.tflite` (~5.9MB)
2. Place in `app/src/main/assets/models/`
3. Test the tutor feature again

The app will then have:
- **Small initial download**: 103.8MB (still under 150MB target)
- **Full embedding functionality**: High-quality text similarity
- **Best user experience**: No degraded features

## Technical Implementation Note

The current code has smart fallbacks:
- **First**: Checks assets for bundled model
- **Second**: Checks downloaded models directory  
- **Fallback**: Uses mock embeddings (current behavior)

Once you add the model file, it will automatically use the real embeddings and provide full functionality.