package com.mygemma3n.aiapp.feature.summarizer

//import android.content.Context
//import android.net.Uri
//import com.mygemma3n.aiapp.data.UnifiedGemmaService
//import com.mygemma3n.aiapp.data.GeminiApiService
//import com.mygemma3n.aiapp.domain.repository.SettingsRepository
//import io.mockk.*
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.test.*
//import app.cash.turbine.test
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import org.junit.Assert.*
//import java.io.*
//
//@OptIn(ExperimentalCoroutinesApi::class)
//class SummarizerViewModelTest {
//
//    private lateinit var context: Context
//    private lateinit var gemmaService: UnifiedGemmaService
//    private lateinit var geminiApiService: GeminiApiService
//    private lateinit var settingsRepository: SettingsRepository
//    private lateinit var viewModel: SummarizerViewModel
//    private val testDispatcher = StandardTestDispatcher()
//
//    @Before
//    fun setup() {
//        Dispatchers.setMain(testDispatcher)
//
//        context = mockk(relaxed = true)
//        gemmaService = mockk(relaxed = true)
//        geminiApiService = mockk(relaxed = true)
//        settingsRepository = mockk(relaxed = true)
//
//        // Setup default mock behaviors
//        coEvery { gemmaService.isInitialized() } returns true
//        coEvery { gemmaService.generateTextAsync(any(), any()) } returns "Test summary of the document."
//
//        viewModel = SummarizerViewModel(context, gemmaService, geminiApiService, settingsRepository)
//    }
//
//    @After
//    fun tearDown() {
//        Dispatchers.resetMain()
//    }
//
//    @Test
//    fun `initial state is correct`() {
//        val initialState = viewModel.state.value
//
//        assertNull(initialState.fileName)
//        assertNull(initialState.summary)
//        assertFalse(initialState.isLoading)
//        assertNull(initialState.error)
//        assertEquals(0, initialState.extractedTextLength)
//        assertEquals(0f, initialState.processingProgress)
//    }
//
//    @Test
//    fun `processFile updates state correctly for successful processing`() = runTest {
//        // Arrange
//        val mockUri = mockk<Uri>()
//        every { mockUri.lastPathSegment } returns "path/test-document.txt"
//        every { mockUri.scheme } returns "content"
//
//        val testContent = "This is a test document with some content for summarization."
//        val inputStream = ByteArrayInputStream(testContent.toByteArray())
//
//        every { context.contentResolver.openInputStream(mockUri) } returns inputStream
//
//        // Act & Assert
//        viewModel.state.test {
//            viewModel.processFile(mockUri)
//
//            // Initial state
//            val initialState = awaitItem()
//            assertFalse(initialState.isLoading)
//
//            // Loading state
//            val loadingState = awaitItem()
//            assertTrue(loadingState.isLoading)
//            assertNull(loadingState.error)
//            assertNull(loadingState.summary)
//            assertEquals(0f, loadingState.processingProgress)
//
//            // Progress updates
//            var progressState = awaitItem()
//            assertTrue(progressState.isLoading)
//            assertTrue(progressState.processingProgress > 0f)
//
//            // Final success state
//            val finalState = awaitItem()
//            assertFalse(finalState.isLoading)
//            assertNull(finalState.error)
//            assertNotNull(finalState.summary)
//            assertEquals("test-document.txt", finalState.fileName)
//            assertEquals(testContent.length, finalState.extractedTextLength)
//            assertEquals(1.0f, finalState.processingProgress)
//        }
//    }
//
//    @Test
//    fun `processFile handles empty document error`() = runTest {
//        // Arrange
//        val mockUri = mockk<Uri>()
//        every { mockUri.lastPathSegment } returns "path/empty-document.txt"
//        every { mockUri.scheme } returns "content"
//
//        val emptyContent = ""
//        val inputStream = ByteArrayInputStream(emptyContent.toByteArray())
//        every { context.contentResolver.openInputStream(mockUri) } returns inputStream
//
//        // Act & Assert
//        viewModel.state.test {
//            viewModel.processFile(mockUri)
//
//            awaitItem() // initial
//            awaitItem() // loading
//
//            val errorState = awaitItem()
//            assertFalse(errorState.isLoading)
//            assertEquals("No text found in document", errorState.error)
//            assertNull(errorState.summary)
//            assertEquals(0f, errorState.processingProgress)
//        }
//    }
//
//    @Test
//    fun `processFile handles file access error`() = runTest {
//        // Arrange
//        val mockUri = mockk<Uri>()
//        every { mockUri.lastPathSegment } returns "path/inaccessible.txt"
//        every { context.contentResolver.openInputStream(mockUri) } throws IOException("File not found")
//
//        // Act & Assert
//        viewModel.state.test {
//            viewModel.processFile(mockUri)
//
//            awaitItem() // initial
//            awaitItem() // loading
//
//            val errorState = awaitItem()
//            assertFalse(errorState.isLoading)
//            assertTrue(errorState.error?.contains("File not found") == true)
//            assertEquals(0f, errorState.processingProgress)
//        }
//    }
//
//    @Test
//    fun `processFile handles AI model failure`() = runTest {
//        // Arrange
//        val mockUri = mockk<Uri>()
//        every { mockUri.lastPathSegment } returns "path/test.txt"
//        every { mockUri.scheme } returns "content"
//
//        val testContent = "Test content for AI processing."
//        val inputStream = ByteArrayInputStream(testContent.toByteArray())
//        every { context.contentResolver.openInputStream(mockUri) } returns inputStream
//
//        coEvery { gemmaService.generateResponse(any(), any()) } throws Exception("AI model failed")
//
//        // Act & Assert
//        viewModel.state.test {
//            viewModel.processFile(mockUri)
//
//            awaitItem() // initial
//            awaitItem() // loading
//            awaitItem() // progress
//
//            val errorState = awaitItem()
//            assertFalse(errorState.isLoading)
//            assertTrue(errorState.error?.contains("AI model failed") == true)
//        }
//    }
//
//    @Test
//    fun `processManualText processes text correctly`() = runTest {
//        // Arrange
//        val testText = "This is manually entered text that needs to be summarized by the AI model."
//
//        // Act & Assert
//        viewModel.state.test {
//            viewModel.processManualText(testText)
//
//            awaitItem() // initial
//            awaitItem() // loading
//
//            val finalState = awaitItem()
//            assertFalse(finalState.isLoading)
//            assertNull(finalState.error)
//            assertNotNull(finalState.summary)
//            assertEquals("Manual Input", finalState.fileName)
//            assertEquals(testText.length, finalState.extractedTextLength)
//        }
//    }
//
//    @Test
//    fun `processManualText handles empty text`() = runTest {
//        // Act & Assert
//        viewModel.state.test {
//            viewModel.processManualText("")
//
//            awaitItem() // initial
//            awaitItem() // loading
//
//            val errorState = awaitItem()
//            assertFalse(errorState.isLoading)
//            assertEquals("Please enter some text to summarize", errorState.error)
//        }
//    }
//
//    @Test
//    fun `clearError resets error state`() = runTest {
//        // Arrange - create error state
//        val mockUri = mockk<Uri>()
//        every { context.contentResolver.openInputStream(mockUri) } throws IOException("Test error")
//
//        viewModel.processFile(mockUri)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Verify error exists
//        assertNotNull(viewModel.state.value.error)
//
//        // Act
//        viewModel.clearError()
//
//        // Assert
//        assertNull(viewModel.state.value.error)
//    }
//
//    @Test
//    fun `retry processes last file again`() = runTest {
//        // Arrange
//        val mockUri = mockk<Uri>()
//        every { mockUri.lastPathSegment } returns "path/retry-test.txt"
//        every { mockUri.scheme } returns "content"
//
//        val testContent = "Content for retry test."
//        val inputStream1 = ByteArrayInputStream(testContent.toByteArray())
//        val inputStream2 = ByteArrayInputStream(testContent.toByteArray())
//
//        every { context.contentResolver.openInputStream(mockUri) } returnsMany listOf(inputStream1, inputStream2)
//
//        // First attempt fails
//        coEvery { gemmaService.generateResponse(any(), any()) } throws Exception("First attempt failed") andThen "Retry successful summary"
//
//        // Act - first attempt (should fail)
//        viewModel.processFile(mockUri)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Verify failure
//        assertTrue(viewModel.state.value.error?.contains("First attempt failed") == true)
//
//        // Act - retry
//        viewModel.state.test {
//            viewModel.retry()
//
//            awaitItem() // current error state
//            awaitItem() // loading state
//
//            val successState = awaitItem()
//            assertFalse(successState.isLoading)
//            assertNull(successState.error)
//            assertEquals("Retry successful summary", successState.summary)
//        }
//    }
//
//    @Test
//    fun `hierarchical summarization works for long text`() = runTest {
//        // Arrange
//        val longText = "This is a very long document. ".repeat(200) // ~6000 characters
//        val mockUri = mockk<Uri>()
//        every { mockUri.lastPathSegment } returns "path/long-document.txt"
//        every { mockUri.scheme } returns "content"
//
//        val inputStream = ByteArrayInputStream(longText.toByteArray())
//        every { context.contentResolver.openInputStream(mockUri) } returns inputStream
//
//        // Mock responses for hierarchical summarization
//        coEvery { gemmaService.generateResponse(match { it.contains("Key points") }, any()) } returns "Key points summary."
//        coEvery { gemmaService.generateResponse(match { it.contains("Combine") }, any()) } returns "Final hierarchical summary."
//
//        // Act
//        viewModel.state.test {
//            viewModel.processFile(mockUri)
//
//            awaitItem() // initial
//            awaitItem() // loading
//            awaitItem() // progress
//
//            val finalState = awaitItem()
//            assertEquals("Final hierarchical summary.", finalState.summary)
//        }
//
//        // Verify hierarchical processing was used
//        coVerify { gemmaService.generateResponse(match { it.contains("Key points") }, any()) }
//        coVerify { gemmaService.generateResponse(match { it.contains("Combine") }, any()) }
//    }
//
//    @Test
//    fun `text preprocessing works correctly`() {
//        // This is testing a private method, so we test it indirectly through processManualText
//        runTest {
//            val textWithExtraSpaces = "Text   with    extra     spaces\n\n\nand\t\ttabs\r\nand line breaks."
//
//            viewModel.state.test {
//                viewModel.processManualText(textWithExtraSpaces)
//
//                awaitItem() // initial
//                awaitItem() // loading
//                awaitItem() // final
//            }
//
//            // Verify the AI service received properly preprocessed text
//            coVerify {
//                gemmaService.generateResponse(
//                    match { prompt ->
//                        !prompt.contains("   ") && // No triple spaces
//                        !prompt.contains("\n\n\n") && // No triple line breaks
//                        !prompt.contains("\t\t") // No double tabs
//                    },
//                    any()
//                )
//            }
//        }
//    }
//
//    @Test
//    fun `model initialization is called when needed`() = runTest {
//        // Arrange
//        coEvery { gemmaService.isModelReady() } returns false andThen true
//
//        val mockUri = mockk<Uri>()
//        every { mockUri.lastPathSegment } returns "path/test.txt"
//        every { mockUri.scheme } returns "content"
//
//        val inputStream = ByteArrayInputStream("Test content".toByteArray())
//        every { context.contentResolver.openInputStream(mockUri) } returns inputStream
//
//        // Act
//        viewModel.processFile(mockUri)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Assert
//        coVerify { gemmaService.isModelReady() }
//        coVerify { gemmaService.initializeModel() }
//    }
//}