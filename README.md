# ImpactSuite

ImpactSuite is an Android application showcasing how on‑device generative AI models can power helpful
experiences entirely offline.  The app is written in Kotlin using Jetpack Compose and leverages
Google's **Gemma 3n** models via Google AI Edge LiteRT and MediaPipe.

## Features

- **Live Caption & Translation** – streams microphone audio into a lightweight Gemma model to produce captions with optional translation.
- **Offline Quiz Generator** – generates multiple choice quizzes from a topic using Gemma and stores results in Room.
- **Voice CBT Coach** – records audio, detects emotion and suggests Cognitive Behavioural Therapy techniques while maintaining a local conversation history.
- **Image Classification** – uses CameraX with a TensorFlow Lite classifier for on‑device image recognition.
- **Crisis Handbook** – answers safety questions and links to local resources using function calling.

The app is a single module project (`:app`) with an additional dynamic asset pack (`:gemma3n_assetpack`) used for delivering large AI models on demand.

## Building

1. Clone the repository and open it in Android Studio **Giraffe** or newer.
2. Ensure that the Android SDK for API 36 is installed.
3. Because model files are tracked with Git&nbsp;LFS, run `git lfs install` if required.
4. Place the required `.tflite` or `.task` files into
   `app/src/main/assets/models/` (the directory is excluded from git).  The exact
   filenames must match those expected by `checkModelAvailability` in
   `MainActivity.kt`.
5. Connect an Android device or start an emulator and run the **app** configuration.

If Google Play services are available the model can also be delivered via the
`gemma3n_assetpack` dynamic feature at first launch.

## Project Structure

```
app/                  Main application module with Compose UI and feature code
  └─ src/main/java/com/example/mygemma3n
                        ├─ feature/...
                        ├─ gemma/...
                        └─ repository/... etc.

gemma3n_assetpack/    Dynamic asset pack containing optional model files
```

Important libraries used include:

- **Jetpack Compose** for UI
- **Hilt** for dependency injection
- **Room** and **DataStore** for local persistence
- **WorkManager** for background tasks
- **TensorFlow Lite**, **MediaPipe** and **Google AI Edge** for model execution

## Model Assets

All `.tflite` shards or `.task` bundles must be placed in
`app/src/main/assets/models/` using the filenames referenced by the application.
These files are ignored by git, so each developer must supply them locally.
Large model files are configured for Git LFS to avoid bloating the repository.

## License

This project is provided for educational purposes and has no specific license.