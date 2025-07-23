package com.example.mygemma3n.feature.summarizer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.apache.poi.xwpf.usermodel.XWPFDocument
import timber.log.Timber
import java.io.*
import javax.inject.Inject
import androidx.core.graphics.createBitmap

@HiltViewModel
class SummarizerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService
) : ViewModel() {

    private val _state = MutableStateFlow(SummarizerState())
    val state: StateFlow<SummarizerState> = _state.asStateFlow()

    private var originalText: String? = null

    // Performance optimization: Process text in chunks
    private val CHUNK_SIZE = 1000 // Characters per chunk for better performance
    private val MAX_TEXT_LENGTH = 10000 // Maximum text to process
    private val SUMMARY_CHUNK_SIZE = 500 // Smaller chunks for summary generation

    /* ---------- Public API ---------- */

    fun processFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(
                isLoading = true,
                error = null,
                summary = null,
                processingProgress = 0f
            )}

            try {
                // Extract text with progress updates
                val text = extractTextWithProgress(uri)
                if (text.isBlank()) {
                    throw IllegalStateException("No text found in document")
                }

                originalText = text
                val processedText = preprocessText(text)
                Timber.d("Extracted ${text.length} characters, processed to ${processedText.length}")

                // Update progress
                _state.update { it.copy(processingProgress = 0.5f) }

                // Generate summary with chunking for better performance
                val summary = generateOptimizedSummary(processedText)
                _state.update { it.copy(processingProgress = 0.9f) }

                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"

                _state.update {
                    it.copy(
                        fileName = name,
                        summary = summary,
                        isLoading = false,
                        extractedTextLength = text.length,
                        processingProgress = 1.0f
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing file")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error",
                        processingProgress = 0f
                    )
                }
            }
        }
    }

    fun clear() {
        originalText = null
        _state.value = SummarizerState()
    }

    fun retry() {
        originalText?.let { text ->
            viewModelScope.launch {
                processText(text)
            }
        }
    }

    /* ---------- Text Extraction with Alternative PDF Support ---------- */

    private suspend fun extractTextWithProgress(uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: ""
        val name = uri.lastPathSegment?.lowercase() ?: ""

        when {
            mime.contains("pdf") || name.endsWith(".pdf") ->
                extractPdfTextWithFallbacks(uri)
            mime.contains("wordprocessingml") || name.endsWith(".docx") ->
                context.contentResolver.openInputStream(uri)?.use { extractDocxText(it) } ?: ""
            else ->
                context.contentResolver.openInputStream(uri)?.use { extractPlainText(it) } ?: ""
        }
    }

    private suspend fun extractPdfTextWithFallbacks(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            return@withContext extractPdfTextWithMlKit(uri)
        } catch (e: Exception) {
            Timber.w(e, "ML Kit extraction failed")
            throw IllegalStateException("ML Kit extraction failed: ${e.message}")
        }
    }

    private suspend fun extractPdfTextWithMlKit(uri: Uri): String = withContext(Dispatchers.IO) {
        val pdfDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Unable to open file descriptor")

        PdfRenderer(pdfDescriptor).use { renderer ->
            val stringBuilder = StringBuilder()

            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val bitmap = createBitmap(page.width, page.height)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val image = InputImage.fromBitmap(bitmap, 0)

                    val result = Tasks.await(recognizer.process(image))
                    stringBuilder.append(result.text).append("\n")

                    bitmap.recycle()
                }
            }

            return@withContext stringBuilder.toString().trim()
        }
    }

    private fun extractDocxText(inputStream: InputStream): String {
        return try {
            XWPFDocument(inputStream).use { doc ->
                doc.paragraphs.joinToString("\n") { it.text }
            }
        } catch (e: Exception) {
            Timber.e(e, "DOCX extraction failed")
            throw IllegalStateException("DOCX extraction failed: ${e.message}")
        }
    }

    private fun extractPlainText(inputStream: InputStream): String {
        return BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
    }

    /* ---------- Text Processing & Optimization ---------- */

    private fun preprocessText(text: String): String {
        // Clean and normalize text for better processing
        return text
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .replace(Regex("[\\x00-\\x1F\\x7F-\\x9F]"), "") // Remove control characters
            .trim()
            .take(MAX_TEXT_LENGTH) // Limit text length for performance
    }

    suspend fun processText(text: String) {
        _state.update { it.copy(isLoading = true, error = null, processingProgress = 0f) }
        try {
            val processedText = preprocessText(text)
            _state.update { it.copy(processingProgress = 0.5f) }

            val summary = generateOptimizedSummary(processedText)
            _state.update { it.copy(processingProgress = 0.9f) }

            _state.update {
                it.copy(
                    summary = summary,
                    isLoading = false,
                    processingProgress = 1.0f
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Error",
                    processingProgress = 0f
                )
            }
        }
    }

    /* ---------- Optimized Gemma Generation ---------- */

    private suspend fun generateOptimizedSummary(text: String): String = coroutineScope {
        if (!gemmaService.isInitialized()) {
            gemmaService.initializeBestAvailable()
        }

        // For short texts, summarize directly
        if (text.length <= SUMMARY_CHUNK_SIZE * 2) {
            val prompt = buildString {
                appendLine("Summarize this text in 2-3 concise sentences:")
                appendLine()
                appendLine(text)
            }

            return@coroutineScope gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 150,
                    temperature = 0.3f, // Lower temperature for more focused summaries
                    topK = 20
                )
            ).trim()
        }

        // For longer texts, use chunking strategy
        val chunks = text.chunked(SUMMARY_CHUNK_SIZE)
        val chunkSummaries = mutableListOf<String>()

        // Process chunks in parallel for better performance
        val deferredSummaries = chunks.map { chunk ->
            async(Dispatchers.Default) {
                val prompt = "Summarize in 1-2 sentences: $chunk"
                gemmaService.generateTextAsync(
                    prompt,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 80,
                        temperature = 0.3f
                    )
                ).trim()
            }
        }

        chunkSummaries.addAll(deferredSummaries.awaitAll())

        // Combine chunk summaries into final summary
        val combinedSummary = chunkSummaries.joinToString(" ")
        val finalPrompt = buildString {
            appendLine("Create a coherent 3-4 sentence summary from these points:")
            appendLine()
            appendLine(combinedSummary)
        }

        return@coroutineScope gemmaService.generateTextAsync(
            finalPrompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 200,
                temperature = 0.4f
            )
        ).trim()
    }
}

/* ---------- Simplified State for Summary Only ---------- */

data class SummarizerState(
    val fileName: String? = null,
    val summary: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val extractedTextLength: Int = 0,
    val processingProgress: Float = 0f
)