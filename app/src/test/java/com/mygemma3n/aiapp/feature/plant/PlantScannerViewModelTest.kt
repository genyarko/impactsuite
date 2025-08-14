package com.mygemma3n.aiapp.feature.plant

//import android.content.Context
//import android.graphics.Bitmap
//import app.cash.turbine.test
//import com.mygemma3n.aiapp.data.UnifiedGemmaService
//import com.mygemma3n.aiapp.shared_utilities.MobileNetV5Encoder
//import io.mockk.*
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.test.*
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import org.junit.Assert.*
//import java.io.File
//
//@OptIn(ExperimentalCoroutinesApi::class)
//class PlantScannerViewModelTest {
//
//    private lateinit var context: Context
//    private lateinit var gemmaService: UnifiedGemmaService
//    private lateinit var mobileNetEncoder: MobileNetV5Encoder
//    private lateinit var plantDatabase: PlantDatabase
//    private lateinit var viewModel: PlantScannerViewModel
//    private val testDispatcher = StandardTestDispatcher()
//
//    @Before
//    fun setup() {
//        Dispatchers.setMain(testDispatcher)
//
//        context = mockk(relaxed = true)
//        gemmaService = mockk(relaxed = true)
//        mobileNetEncoder = mockk(relaxed = true)
//        plantDatabase = mockk(relaxed = true)
//
//        // Setup default mock behaviors
//        coEvery { gemmaService.isModelReady() } returns true
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns """
//            {
//                "label": "Rose",
//                "confidence": 0.95,
//                "plantSpecies": "Rosa damascena",
//                "disease": "None",
//                "severity": "Healthy",
//                "recommendations": ["Water regularly", "Ensure good sunlight"]
//            }
//        """.trimIndent()
//
//        coEvery { mobileNetEncoder.isModelLoaded() } returns true
//        every { plantDatabase.plantInfoDao() } returns mockk(relaxed = true)
//        every { plantDatabase.plantDiseaseDao() } returns mockk(relaxed = true)
//        every { plantDatabase.scanHistoryDao() } returns mockk(relaxed = true)
//
//        viewModel = PlantScannerViewModel(context, gemmaService, mobileNetEncoder, plantDatabase)
//    }
//
//    @After
//    fun tearDown() {
//        Dispatchers.resetMain()
//    }
//
//    @Test
//    fun `initial state is correct`() {
//        val initialState = viewModel.scanState.value
//
//        assertFalse(initialState.isAnalyzing)
//        assertNull(initialState.currentAnalysis)
//        assertTrue(initialState.scanHistory.isEmpty())
//        assertNull(initialState.error)
//    }
//
//    @Test
//    fun `analyzeImage processes bitmap successfully`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        // Act & Assert
//        viewModel.scanState.test {
//            viewModel.analyzeImage(testBitmap, testImageFile)
//
//            // Initial state
//            val initialState = awaitItem()
//            assertFalse(initialState.isAnalyzing)
//
//            // Analyzing state
//            val analyzingState = awaitItem()
//            assertTrue(analyzingState.isAnalyzing)
//            assertNull(analyzingState.error)
//
//            // Final state with results
//            val finalState = awaitItem()
//            assertFalse(finalState.isAnalyzing)
//            assertNull(finalState.error)
//            assertNotNull(finalState.currentAnalysis)
//
//            val analysis = finalState.currentAnalysis!!
//            assertEquals("Rose", analysis.label)
//            assertEquals(0.95f, analysis.confidence, 0.01f)
//            assertEquals("Rosa damascena", analysis.plantSpecies)
//            assertEquals("None", analysis.disease)
//            assertEquals("Healthy", analysis.severity)
//            assertEquals(2, analysis.recommendations.size)
//        }
//
//        // Verify AI service was called
//        coVerify { gemmaService.generateResponseMultimodal(any(), any()) }
//
//        // Cleanup
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `analyzeImage handles AI service error`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } throws Exception("AI processing failed")
//
//        // Act & Assert
//        viewModel.scanState.test {
//            viewModel.analyzeImage(testBitmap, testImageFile)
//
//            awaitItem() // initial
//            awaitItem() // analyzing
//
//            val errorState = awaitItem()
//            assertFalse(errorState.isAnalyzing)
//            assertTrue(errorState.error?.contains("AI processing failed") == true)
//            assertNull(errorState.currentAnalysis)
//        }
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `analyzeImage handles malformed JSON response`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns "Invalid JSON response"
//
//        // Act & Assert
//        viewModel.scanState.test {
//            viewModel.analyzeImage(testBitmap, testImageFile)
//
//            awaitItem() // initial
//            awaitItem() // analyzing
//
//            val errorState = awaitItem()
//            assertFalse(errorState.isAnalyzing)
//            assertNotNull(errorState.error)
//            assertTrue(errorState.error?.contains("parsing") == true ||
//                      errorState.error?.contains("JSON") == true)
//        }
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `analyzeImage enriches results with database information`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        val mockPlantInfo = PlantInfo(
//            scientificName = "Rosa damascena",
//            commonNames = listOf("Damask Rose", "Rose of Castile"),
//            family = "Rosaceae",
//            nativeRegion = "Asia",
//            wateringNeeds = "Moderate",
//            sunlightNeeds = "Full sun",
//            soilType = "Well-drained",
//            growthRate = "Medium",
//            maxHeight = "1-2 meters",
//            toxicity = "Non-toxic"
//        )
//
//        val mockPlantInfoDao = mockk<PlantInfoDao>()
//        coEvery { mockPlantInfoDao.getByScientificName("Rosa damascena") } returns mockPlantInfo
//        every { plantDatabase.plantInfoDao() } returns mockPlantInfoDao
//
//        // Act & Assert
//        viewModel.scanState.test {
//            viewModel.analyzeImage(testBitmap, testImageFile)
//
//            awaitItem() // initial
//            awaitItem() // analyzing
//
//            val finalState = awaitItem()
//            val analysis = finalState.currentAnalysis!!
//
//            // Verify database enrichment occurred
//            assertNotNull(analysis.plantInfo)
//            assertEquals("Rosaceae", analysis.plantInfo?.family)
//            assertEquals("Moderate", analysis.plantInfo?.wateringNeeds)
//        }
//
//        coVerify { mockPlantInfoDao.getByScientificName("Rosa damascena") }
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `analyzeImage handles disease detection`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns """
//            {
//                "label": "Rose with Black Spot",
//                "confidence": 0.87,
//                "plantSpecies": "Rosa damascena",
//                "disease": "Black Spot",
//                "severity": "Moderate",
//                "recommendations": ["Apply fungicide", "Improve air circulation", "Remove affected leaves"]
//            }
//        """.trimIndent()
//
//        val mockDiseaseInfo = PlantDisease(
//            id = "black_spot",
//            name = "Black Spot",
//            scientificName = "Diplocarpon rosae",
//            affectedPlants = listOf("Roses"),
//            symptoms = listOf("Black spots on leaves", "Yellowing leaves"),
//            causes = listOf("Fungal infection", "High humidity"),
//            treatments = listOf("Fungicide application", "Remove infected parts"),
//            preventiveMeasures = listOf("Good air circulation", "Avoid overhead watering"),
//            severity = "Moderate"
//        )
//
//        val mockDiseaseDao = mockk<PlantDiseaseDao>()
//        coEvery { mockDiseaseDao.getByName("Black Spot") } returns mockDiseaseInfo
//        every { plantDatabase.plantDiseaseDao() } returns mockDiseaseDao
//
//        // Act & Assert
//        viewModel.scanState.test {
//            viewModel.analyzeImage(testBitmap, testImageFile)
//
//            awaitItem() // initial
//            awaitItem() // analyzing
//
//            val finalState = awaitItem()
//            val analysis = finalState.currentAnalysis!!
//
//            assertEquals("Black Spot", analysis.disease)
//            assertEquals("Moderate", analysis.severity)
//            assertNotNull(analysis.diseaseInfo)
//            assertEquals("Diplocarpon rosae", analysis.diseaseInfo?.scientificName)
//        }
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `analyzeImage saves scan to history`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        val mockScanHistoryDao = mockk<ScanHistoryDao>(relaxed = true)
//        every { plantDatabase.scanHistoryDao() } returns mockScanHistoryDao
//
//        // Act
//        viewModel.analyzeImage(testBitmap, testImageFile)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Assert
//        coVerify {
//            mockScanHistoryDao.insertScan(
//                match<ScanHistoryEntity> { scan ->
//                    scan.species == "Rosa damascena" &&
//                    scan.confidence == 0.95f &&
//                    scan.disease == "None"
//                }
//            )
//        }
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `loadScanHistory retrieves historical scans`() = runTest {
//        // Arrange
//        val mockHistoryItems = listOf(
//            ScanHistoryEntity(
//                id = "1",
//                timestamp = System.currentTimeMillis(),
//                species = "Rosa damascena",
//                confidence = 0.95f,
//                disease = "None",
//                severity = "Healthy",
//                recommendations = """["Water regularly"]""",
//                imageUri = "file://test.jpg"
//            ),
//            ScanHistoryEntity(
//                id = "2",
//                timestamp = System.currentTimeMillis() - 86400000,
//                species = "Tulipa gesneriana",
//                confidence = 0.88f,
//                disease = "Tulip Fire",
//                severity = "Severe",
//                recommendations = """["Remove infected bulbs"]""",
//                imageUri = "file://test2.jpg"
//            )
//        )
//
//        val mockScanHistoryDao = mockk<ScanHistoryDao>()
//        coEvery { mockScanHistoryDao.getAllScans() } returns mockHistoryItems
//        every { plantDatabase.scanHistoryDao() } returns mockScanHistoryDao
//
//        // Act & Assert
//        viewModel.scanState.test {
//            viewModel.loadScanHistory()
//
//            val initialState = awaitItem()
//            val updatedState = awaitItem()
//
//            assertEquals(2, updatedState.scanHistory.size)
//            assertEquals("Rosa damascena", updatedState.scanHistory[0].plantSpecies)
//            assertEquals("Tulipa gesneriana", updatedState.scanHistory[1].plantSpecies)
//        }
//    }
//
//    @Test
//    fun `clearError resets error state`() = runTest {
//        // Arrange - create error state
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } throws Exception("Test error")
//
//        viewModel.analyzeImage(testBitmap, testImageFile)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Verify error exists
//        assertNotNull(viewModel.scanState.value.error)
//
//        // Act
//        viewModel.clearError()
//
//        // Assert
//        assertNull(viewModel.scanState.value.error)
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `bitmap preprocessing works correctly`() = runTest {
//        // Arrange
//        val largeBitmap = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        // Act
//        viewModel.analyzeImage(largeBitmap, testImageFile)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Assert - Verify that the bitmap was preprocessed before being sent to AI
//        coVerify {
//            gemmaService.generateResponseMultimodal(
//                any(),
//                match { image ->
//                    // The image should be resized to Gemma-compatible dimensions
//                    image.width <= 512 && image.height <= 512
//                }
//            )
//        }
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `model initialization is checked before analysis`() = runTest {
//        // Arrange
//        coEvery { gemmaService.isModelReady() } returns false andThen true
//        coEvery { gemmaService.initializeModel() } returns Unit
//
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        // Act
//        viewModel.analyzeImage(testBitmap, testImageFile)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Assert
//        coVerify { gemmaService.isModelReady() }
//        coVerify { gemmaService.initializeModel() }
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `concurrent analysis requests are handled properly`() = runTest {
//        // Arrange
//        val bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val bitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val file1 = File.createTempFile("test1", ".jpg")
//        val file2 = File.createTempFile("test2", ".jpg")
//
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } coAnswers {
//            kotlinx.coroutines.delay(100)
//            """{"label": "Test Plant", "confidence": 0.8, "plantSpecies": "Test species", "disease": "None", "severity": "Healthy", "recommendations": []}"""
//        }
//
//        // Act - Start two analyses concurrently
//        viewModel.analyzeImage(bitmap1, file1)
//        viewModel.analyzeImage(bitmap2, file2) // Should cancel the first one
//
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Assert - Only one analysis should complete
//        coVerify(exactly = 1) { gemmaService.generateResponseMultimodal(any(), any()) }
//
//        file1.delete()
//        file2.delete()
//    }
//
//    @Test
//    fun `non-plant images are handled correctly`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns """
//            {
//                "label": "Car",
//                "confidence": 0.92,
//                "plantSpecies": "N/A",
//                "disease": "N/A",
//                "severity": "N/A",
//                "recommendations": ["This appears to be a vehicle, not a plant"]
//            }
//        """.trimIndent()
//
//        // Act & Assert
//        viewModel.scanState.test {
//            viewModel.analyzeImage(testBitmap, testImageFile)
//
//            awaitItem() // initial
//            awaitItem() // analyzing
//
//            val finalState = awaitItem()
//            val analysis = finalState.currentAnalysis!!
//
//            assertEquals("Car", analysis.label)
//            assertNull(analysis.plantSpecies) // Should be null for non-plants
//            assertNull(analysis.disease)
//            assertTrue(analysis.recommendations.any { it.contains("vehicle") })
//        }
//
//        testImageFile.delete()
//    }
//
//    @Test
//    fun `MobileNet encoder integration works correctly`() = runTest {
//        // Arrange
//        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
//        val testImageFile = File.createTempFile("test", ".jpg")
//
//        val mockFeatures = FloatArray(1280) { 0.5f }
//        coEvery { mobileNetEncoder.encodeImage(any()) } returns mockFeatures
//
//        // Act
//        viewModel.analyzeImage(testBitmap, testImageFile)
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Assert
//        coVerify { mobileNetEncoder.isModelLoaded() }
//
//        testImageFile.delete()
//    }
//}