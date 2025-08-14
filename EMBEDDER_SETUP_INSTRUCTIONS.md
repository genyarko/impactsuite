# Universal Sentence Encoder Setup Instructions

## ✅ Updated Code
The TextEmbeddingService has been updated to:
1. **Check the new location first**: `app/src/main/ml/universal_sentence_encoder.tflite`
2. **Fallback to old location**: `app/src/main/assets/models/universal_sentence_encoder.tflite`
3. **Auto-download from your emergency URL** if model not found
4. **Use mock embeddings** as final fallback

## 🚀 Quick Setup Options

### Option 1: Place Model in New Location (Recommended)
```bash
# Create the ml directory
mkdir -p app/src/main/ml

# Download from your emergency link and place the model
# You can download manually from:
# https://www.dropbox.com/scl/fi/01ipcfyvti1miwafptgie/universal_sentence_encoder.tflite?rlkey=fuqzrqc9673ejb6ul4szd4oaz&st=lue33dpr&dl=0

# Place the downloaded file at:
app/src/main/ml/universal_sentence_encoder.tflite
```

### Option 2: Let App Auto-Download
- **Do nothing** - the app will automatically download the model from your Dropbox URL when first needed
- The downloaded model is cached for future use
- This adds ~5.9MB to the app on first use

### Option 3: Place Model in Old Location  
```bash
# Place the model in the old location
app/src/main/assets/models/universal_sentence_encoder.tflite
```

## 📱 Current App Behavior

### When Tutor Feature is Used:
1. **Checks `ml/`** directory first
2. **Checks `models/`** directory if not found in ml/
3. **Downloads from Dropbox URL** if not found anywhere
4. **Uses mock embeddings** if download fails
5. **Caches downloaded model** for future use

### Size Impact:
| Scenario | Initial AAB Size | After First Use |
|----------|------------------|-----------------|
| Model bundled in ml/ | ~103.8MB | 103.8MB |
| Auto-download | 97.9MB | ~103.8MB |
| Mock embeddings only | 97.9MB | 97.9MB |

## 🎯 Recommended Action
**Place the model in `app/src/main/ml/universal_sentence_encoder.tflite`** for:
- ✅ Immediate full functionality 
- ✅ No download delays
- ✅ Reliable offline operation
- ✅ Still under 150MB target (103.8MB)

## 🧪 Testing
After placing the model file:
```bash
# Build and test
./gradlew assembleDebug

# Look for these log messages:
# "Copied embedding model from ml/ assets to cache"
# "TextEmbedder initialized successfully"
```

When you use the Tutor feature and select a topic, you should see proper embeddings instead of mock ones.

## 🔧 Technical Details
- **Emergency URL**: Uses `dl=1` parameter for direct download
- **Caching**: Downloads stored in `context.filesDir/models/`
- **Thread-safe**: Uses mutex to prevent concurrent downloads
- **Graceful fallback**: App never crashes due to missing model

The app is now ready to work with the Universal Sentence Encoder in the new location!