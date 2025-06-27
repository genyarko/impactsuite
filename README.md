2 :edge_ai — LiteRT bootstrap & model-swap
object AiRepo {
private const val TWO_B = "gemma_3n_2b.gemma"
private const val FOUR_B = "gemma_3n_4b.gemma"

private val prefs = PreferenceDataStoreFactory
.create { context.dataStoreFile("ai_prefs") }

private suspend fun activeModel() =
prefs.data.first()[booleanPreferencesKey("use4B")] ?: false

suspend fun interpreter(context: Context): InterpreterApi {
val modelFile = File(context.filesDir, if (activeModel()) FOUR_B else TWO_B)

    val opts = InterpreterApi.Options().apply {
      setRuntime(Runtime.GOOGLE_PLAY_SERVICES) // automatic GPU / NNAPI match
      addDelegate(GpuDelegate.Options())       // GPU fall-back :contentReference[oaicite:6]{index=6}
      setCacheDir(File(context.cacheDir, "ple_cache")) // PLE reuse :contentReference[oaicite:7]{index=7}
    }

    return InterpreterApi.createFromFile(modelFile, opts)
}
}


3 Feature slices (Compose)
3.1 Live Caption + Translation (:feature_caption)
kotlin
Copy
Edit
@Composable
fun CaptionScreen(viewModel: CaptionVM = hiltViewModel()) {
val state by viewModel.state.collectAsState()
Surface { Text(state.caption, style = MaterialTheme.typography.titleLarge) }
}

class CaptionVM @Inject constructor(
private val ai: AiRepo, private val audio: MicStream
) : ViewModel() {
val state = MutableStateFlow(CaptionState())

init {
viewModelScope.launch {
ai.interpreter(appContext).use { model ->
audio.frames.collect { wav ->
val result = model.run(wav) as GemmaCaptionOutput
state.update { it.copy(caption = result.text) }
}
}
}
}
}
Send partial 1-second WAV windows to 2 B model for ≤ 300 ms E2E latency on Tensor /G3 class devices.
ai.google.dev
ai.google.dev

Switch to 4 B for post-processing translation when device is idle.

3.2 Offline quiz generator (:feature_quiz)
kotlin
Copy
Edit
suspend fun generateQuiz(topic: String): List<FlashCard> =
ai.interpreter(appCtx).use { model ->
val prompt = "Generate five MCQs on $topic with answers only."
model.run(prompt).parseFlashCards()
}
Persist to Room; a WorkManager periodic task uploads when network ↔ Supabase available.
developer.android.com
github.com

3.3 Voice CBT coach (:feature_coach)
Android SpeechRecognizer → Gemma 3n dialogue → TextToSpeech.

Keep conversation history in DataStore Proto for privacy-first storage.
developer.android.com

3.4 CameraX plant-disease scanner (:feature_scanner)
kotlin
Copy
Edit
val analysis = ImageAnalysis.Builder()
.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
.build().also {
it.setAnalyzer(executor) { imageProxy ->
val bitmap = imageProxy.toBitmap()
val result = plantModel.run(bitmap).bestLabel()
onResult(result)
imageProxy.close()
}
}
Leverage an existing TFLite leaf-disease model (≤ 72 MB) and LiteRT interpreter in GPU FP16 mode for 30 fps preview.
github.com
developers.googleblog.com
link.springer.com

3.5 Crisis handbook Q&A (:feature_handbook)
Gemma 3n’s function-calling schema lets you map answers to openHandbook(sectionId) or callHotline(code) for local de-escalation steps.
developers.googleblog.com

4 :data layer
kotlin
Copy
Edit
@Entity
data class Quiz(
@PrimaryKey(autoGenerate = true) val id: Long = 0,
val topic: String,
val questionsJson: String,
val synced: Boolean = false
)

class SyncWorker(ctx: Context, params: WorkerParameters) :
CoroutineWorker(ctx, params) {
override suspend fun doWork(): Result {
db.quizDao().unsynced().forEach { supabase.upsert(it) }
return Result.success()
}
}