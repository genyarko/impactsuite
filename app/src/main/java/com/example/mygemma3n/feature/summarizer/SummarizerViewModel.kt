package com.example.mygemma3n.feature.summarizer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.feature.quiz.EnhancedPromptManager
import com.example.mygemma3n.feature.quiz.Question
import com.example.mygemma3n.feature.quiz.QuestionType
import com.example.mygemma3n.feature.quiz.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

@HiltViewModel
class SummarizerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val promptManager: EnhancedPromptManager
) : ViewModel() {

    private val _state = MutableStateFlow(SummarizerState())
    val state: StateFlow<SummarizerState> = _state.asStateFlow()

    private var originalText: String? = null

    fun processFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, summary = null, questions = emptyList()) }
            try {
                val text = extractText(uri)
                originalText = text
                val summary = generateSummary(text)
                val questions = generateQuestions(summary.ifBlank { text })
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
                _state.update { it.copy(fileName = name, summary = summary, questions = questions, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to process file")
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun retry() {
        originalText?.let { text ->
            processText(text)
        }
    }

    fun clear() {
        originalText = null
        _state.value = SummarizerState()
    }

    private fun processText(text: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val summary = generateSummary(text)
                val questions = generateQuestions(summary.ifBlank { text })
                _state.update { it.copy(summary = summary, questions = questions, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun extractText(uri: Uri): String {
        val type = context.contentResolver.getType(uri) ?: ""
        context.contentResolver.openInputStream(uri)?.use { input ->
            return when {
                type.contains("pdf") || uri.path?.endsWith(".pdf") == true -> {
                    PDDocument.load(input).use { doc ->
                        PDFTextStripper().getText(doc)
                    }
                }
                type.contains("wordprocessingml") || uri.path?.endsWith(".docx") == true -> {
                    XWPFDocument(input).use { doc ->
                        doc.paragraphs.joinToString("\n") { it.text }
                    }
                }
                else -> {
                    BufferedReader(InputStreamReader(input)).use { it.readText() }
                }
            }
        }
        return ""
    }

    private suspend fun generateSummary(text: String): String {
        if (!gemmaService.isInitialized()) {
            gemmaService.initializeBestAvailable()
        }
        val prompt = """Summarize the following text in a short paragraph:\n$text""".trimIndent()
        return gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(maxTokens = 120, temperature = 0.5f)
        ).trim()
    }

    private suspend fun generateQuestions(text: String): List<Question> {
        val (instructions, example) = promptManager.getVariedQuestionPrompt(
            QuestionType.MULTIPLE_CHOICE,
            com.example.mygemma3n.feature.quiz.Subject.GENERAL,
            "",
            Difficulty.MEDIUM
        )
        val prompt = """$instructions\n$text\nReturn an array of 3 questions like: $example""".trimIndent()
        val raw = gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(maxTokens = 300, temperature = 0.7f)
        )
        return parseQuestions(raw)
    }

    private fun parseQuestions(raw: String): List<Question> {
        val cleaned = raw
            .removePrefix("```json").removeSuffix("```")
            .trim()
        return try {
            val arr = JSONArray(cleaned)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                Question(
                    questionText = obj.optString("question"),
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = obj.optJSONArray("options")?.let { o ->
                        List(o.length()) { j -> o.getString(j) }
                    } ?: emptyList(),
                    correctAnswer = obj.optString("correctAnswer"),
                    explanation = obj.optString("explanation", ""),
                    difficulty = Difficulty.MEDIUM
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse questions")
            emptyList()
        }
    }
}

data class SummarizerState(
    val fileName: String? = null,
    val summary: String? = null,
    val questions: List<Question> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
