package com.example.mygemma3n.feature.summarizer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.GeminiApiConfig
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.mygemma3n.domain.repository.SettingsRepository
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
import kotlinx.coroutines.flow.first
import org.apache.poi.xwpf.usermodel.XWPFDocument
import timber.log.Timber
import java.io.*
import javax.inject.Inject
import androidx.core.graphics.createBitmap

@HiltViewModel
class SummarizerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val geminiApiService: GeminiApiService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SummarizerState())
    val state: StateFlow<SummarizerState> = _state.asStateFlow()

    private var originalText: String? = null

    // Token-aware chunk sizes (assuming ~4 chars per token)
    private val MAX_PROMPT_TOKENS = 400 // Leave room for instructions
    private val MAX_INPUT_CHARS = MAX_PROMPT_TOKENS * 4 - 200 // ~1400 chars for content
    private val EXTRACTIVE_SUMMARY_LENGTH = 1200 // First pass: extract key sentences
    private val FINAL_SUMMARY_LENGTH = 800 // Final summary length
    private val MAX_TEXT_LENGTH = 6000 // Further reduced to prevent truncation
    private val SENTENCE_CHUNK_SIZE = MAX_INPUT_CHARS // Token-aware chunking

    // Cache for model initialization
    private var isModelReady = false

    /* ---------- Helper Methods ---------- */

    private fun hasNetworkConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun shouldUseOnlineService(): Boolean {
        return try {
            val useOnlineService = settingsRepository.useOnlineServiceFlow.first()
            val hasApiKey = settingsRepository.apiKeyFlow.first().isNotBlank()
            val hasNetwork = hasNetworkConnection()
            
            Timber.d("shouldUseOnlineService: useOnlineService=$useOnlineService, hasApiKey=$hasApiKey, hasNetwork=$hasNetwork")
            
            useOnlineService && hasApiKey && hasNetwork
        } catch (e: Exception) {
            Timber.w(e, "Error checking service preference, defaulting to offline")
            false
        }
    }

    private suspend fun initializeApiServiceIfNeeded() {
        if (!geminiApiService.isInitialized()) {
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (apiKey.isNotBlank()) {
                try {
                    geminiApiService.initialize(GeminiApiConfig(apiKey = apiKey))
                    Timber.d("GeminiApiService initialized successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize GeminiApiService")
                    throw e
                }
            } else {
                throw IllegalStateException("API key not found")
            }
        }
    }

    private suspend fun generateSummaryWithService(text: String): String {
        val useOnline = shouldUseOnlineService()
        Timber.d("generateSummaryWithService: shouldUseOnlineService = $useOnline")
        
        return if (useOnline) {
            try {
                Timber.d("Using online service for summarization")
                initializeApiServiceIfNeeded()
                geminiApiService.generateTextComplete(
                    "Summarize the following text in a clear, concise manner. " +
                    "Focus on the main points and key information:\n\n$text",
                    "summarizer"
                )
            } catch (e: Exception) {
                Timber.w(e, "Online service failed, falling back to offline")
                generateSummaryOffline(text)
            }
        } else {
            Timber.d("Using offline service for summarization")
            generateSummaryOffline(text)
        }
    }

    private suspend fun generateSummaryOffline(text: String): String {
        if (!isModelReady) {
            gemmaService.initializeBestAvailable()
            isModelReady = true
        }
        return gemmaService.generateTextAsync(
            "Summarize the following text in a clear, concise manner. " +
            "Focus on the main points and key information:\n\n$text"
        )
    }

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
                // Pre-initialize model if not ready
                if (!isModelReady) {
                    _state.update { it.copy(processingProgress = 0.1f) }
                    initializeModelIfNeeded()
                }

                // Extract text with progress updates
                val text = extractTextWithProgress(uri)
                if (text.isBlank()) {
                    throw IllegalStateException("No text found in document")
                }

                originalText = text
                val processedText = preprocessText(text)
                Timber.d("Extracted ${text.length} characters, processed to ${processedText.length}")

                _state.update { it.copy(processingProgress = 0.4f) }

                // Use hierarchical summarization for better performance
                val summary = generateHierarchicalSummary(processedText)

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

    private suspend fun initializeModelIfNeeded() {
        if (shouldUseOnlineService()) {
            initializeApiServiceIfNeeded()
        } else {
            if (!gemmaService.isInitialized()) {
                gemmaService.initializeBestAvailable()
                isModelReady = true
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

    /* ---------- Text Extraction (unchanged) ---------- */

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

    /* ---------- Optimized Text Processing ---------- */

    private fun preprocessText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\x00-\\x1F\\x7F-\\x9F]"), "")
            .trim()
            .take(MAX_TEXT_LENGTH) // Reduced for faster processing
    }

    suspend fun processText(text: String) {
        _state.update { it.copy(isLoading = true, error = null, processingProgress = 0f) }
        try {
            if (!isModelReady) {
                initializeModelIfNeeded()
            }

            val processedText = preprocessText(text)
            _state.update { it.copy(processingProgress = 0.3f) }

            val summary = generateHierarchicalSummary(processedText)

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

    /* ---------- Hierarchical Summarization Strategy ---------- */

    private suspend fun generateHierarchicalSummary(text: String): String = coroutineScope {
        Timber.d("Starting hierarchical summarization for ${text.length} characters")

        // Step 1: For short texts, summarize directly
        if (text.length <= FINAL_SUMMARY_LENGTH * 2) {
            _state.update { it.copy(processingProgress = 0.7f) }
            return@coroutineScope generateDirectSummary(text)
        }

        // Step 2: Extractive summarization first (faster)
        _state.update { it.copy(processingProgress = 0.5f) }
        val extractiveSummary = performExtractiveSummary(text)
        Timber.d("Extractive summary: ${extractiveSummary.length} characters")

        // Step 3: Abstractive summarization on extracted content
        _state.update { it.copy(processingProgress = 0.8f) }
        val finalSummary = if (extractiveSummary.length > FINAL_SUMMARY_LENGTH) {
            generateAbstractiveSummary(extractiveSummary)
        } else {
            generateDirectSummary(extractiveSummary)
        }

        return@coroutineScope finalSummary
    }

    private fun performExtractiveSummary(text: String): String {
        // Simple sentence scoring and extraction
        val sentences = text.split(Regex("[.!?]+")).filter { it.trim().length > 20 }

        if (sentences.size <= 5) return text

        // Score sentences by keyword frequency and position
        val wordFreq = text.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 3 }
            .groupingBy { it }
            .eachCount()

        val scoredSentences = sentences.mapIndexed { index, sentence ->
            val words = sentence.lowercase().split(Regex("\\W+"))
            val score = words.sumOf { wordFreq[it] ?: 0 } / words.size.toDouble() +
                    (if (index < sentences.size * 0.3) 0.5 else 0.0) // Boost early sentences
            sentence to score
        }.sortedByDescending { it.second }

        // Take top sentences up to target length (token-aware)
        val selectedSentences = mutableListOf<String>()
        var currentLength = 0

        for ((sentence, _) in scoredSentences) {
            val sentenceLength = sentence.length
            if (currentLength + sentenceLength <= EXTRACTIVE_SUMMARY_LENGTH) {
                selectedSentences.add(sentence.trim())
                currentLength += sentenceLength
            }
            // Stop early if we have enough quality content
            if (selectedSentences.size >= 8 || currentLength >= EXTRACTIVE_SUMMARY_LENGTH * 0.8) break
        }

        return selectedSentences.joinToString(". ") + "."
    }

    private suspend fun generateDirectSummary(text: String): String {
        // Ensure we don't exceed token limits
        val truncatedText = if (text.length > MAX_INPUT_CHARS) {
            text.take(MAX_INPUT_CHARS) + "..."
        } else text

        val prompt = "Summarize in 3-4 sentences: $truncatedText"

        Timber.d("Direct summary prompt length: ${prompt.length} chars (~${prompt.length/4} tokens)")

        return generateSummaryWithService(prompt).trim()
    }

    private suspend fun generateAbstractiveSummary(extractedText: String): String {
        if (extractedText.length <= MAX_INPUT_CHARS) {
            return generateDirectSummary(extractedText)
        }

        val chunks = extractedText.chunked(MAX_INPUT_CHARS)
        val chunkSummaries = mutableListOf<String>()

        Timber.d("Processing ${chunks.size} chunks sequentially to avoid model reinitialization")

        for (chunk in chunks.take(3)) {          // limit to 3 chunks
            val summary = withContext(Dispatchers.Default) {  // ✅ replaces async/await
                val prompt = "Key points in 2 sentences: $chunk"
                Timber.d("Chunk prompt length: ${prompt.length} chars (~${prompt.length / 4} tokens)")

                generateSummaryWithService(prompt).trim()
            }
            chunkSummaries.add(summary)
        }

        val combinedSummary = chunkSummaries.joinToString(" ")
        val finalText = if (combinedSummary.length > MAX_INPUT_CHARS) {
            combinedSummary.take(MAX_INPUT_CHARS) + "..."
        } else combinedSummary

        val finalPrompt = "Combine into 3-4 sentences: $finalText"
        Timber.d("Final prompt length: ${finalPrompt.length} chars (~${finalPrompt.length / 4} tokens)")

        return generateSummaryWithService(finalPrompt).trim()
    }

}

/* ---------- State Definition ---------- */

data class SummarizerState(
    val fileName: String? = null,
    val summary: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val extractedTextLength: Int = 0,
    val processingProgress: Float = 0f
)