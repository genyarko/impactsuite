package com.mygemma3n.aiapp.feature.plant
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import androidx.test.platform.app.InstrumentationRegistry
//import com.mygemma3n.aiapp.data.UnifiedGemmaService
//import com.mygemma3n.aiapp.shared_utilities.MobileNetV5Encoder
//import kotlinx.coroutines.test.*
//import org.junit.Before
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.junit.Assert.*
//import java.io.*
//import java.util.concurrent.TimeUnit
//
///**
// * Integration tests for Plant Image Processing that test real image processing
// * with Android context but mocked AI services
// */
//@RunWith(AndroidJUnit4::class)
//class PlantImageProcessingIntegrationTest {
//
//    private lateinit var context: Context
//    private lateinit var gemmaService: UnifiedGemmaService
//    private lateinit var mobileNetEncoder: MobileNetV5Encoder
//    private lateinit var plantDatabase: PlantDatabase
//    private lateinit var viewModel: PlantScannerViewModel
//
//    @Before
//    fun setup() {
//        context = InstrumentationRegistry.getInstrumentation().targetContext
//
//        // Mock AI services with realistic responses
//        gemmaService = mockk(relaxed = true)
//        mobileNetEncoder = mockk(relaxed = true)
//        plantDatabase = mockk(relaxed = true)
//
//        setupMockResponses()
//        setupMockDatabase()
//
//        viewModel = PlantScannerViewModel(context, gemmaService, mobileNetEncoder, plantDatabase)
//    }
//
//    private fun setupMockResponses() {
//        coEvery { gemmaService.isModelReady() } returns true
//        coEvery { gemmaService.initializeModel() } returns Unit
//
//        // Mock successful plant identification
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns """
//            {
//                "label": "Common Rose",
//                "confidence": 0.89,
//                "plantSpecies": "Rosa gallica",
//                "disease": "None",
//                "severity": "Healthy",
//                "recommendations": [
//                    "Water regularly but avoid overwatering",
//                    "Ensure 6+ hours of direct sunlight daily",
//                    "Prune dead flowers to encourage new blooms"
//                ]
//            }
//        """.trimIndent()
//
//        coEvery { mobileNetEncoder.isModelLoaded() } returns true
//        coEvery { mobileNetEncoder.encodeImage(any()) } returns FloatArray(1280) { 0.1f }
//    }
//
//    private fun setupMockDatabase() {
//        val mockPlantInfoDao = mockk<PlantInfoDao>(relaxed = true)
//        val mockPlantDiseaseDao = mockk<PlantDiseaseDao>(relaxed = true)
//        val mockScanHistoryDao = mockk<ScanHistoryDao>(relaxed = true)
//
//        // Mock plant information
//        coEvery { mockPlantInfoDao.getByScientificName("Rosa gallica") } returns PlantInfo(
//            scientificName = "Rosa gallica",
//            commonNames = listOf("French Rose", "Common Rose"),
//            family = "Rosaceae",
//            nativeRegion = "Europe",
//            wateringNeeds = "Moderate",
//            sunlightNeeds = "Full sun",
//            soilType = "Well-drained loamy soil",
//            growthRate = "Medium",
//            maxHeight = "1-1.5 meters",
//            toxicity = "Non-toxic to humans",
//            companionPlants = listOf("Lavender", "Marigold", "Clematis")
//        )
//
//        coEvery { mockScanHistoryDao.getAllScans() } returns emptyList()
//
//        every { plantDatabase.plantInfoDao() } returns mockPlantInfoDao
//        every { plantDatabase.plantDiseaseDao() } returns mockPlantDiseaseDao
//        every { plantDatabase.scanHistoryDao() } returns mockScanHistoryDao
//    }
//
//    @Test
//    fun testRealImageProcessingWorkflow() = runTest {
//        // Create a test image bitmap (simulating camera capture)
//        val testBitmap = createTestPlantImage()
//        val imageFile = File(context.cacheDir, "test_plant_${System.currentTimeMillis()}.jpg")
//
//        try {
//            // Save bitmap to file (simulating camera capture)
//            imageFile.outputStream().use { out ->
//                testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
//            }
//
//            // Process the image
//            viewModel.analyzeImage(testBitmap, imageFile)
//
//            // Wait for processing to complete
//            waitForAnalysisCompletion()
//
//            // Verify results
//            val finalState = viewModel.scanState.value
//            assertFalse("Analysis should be complete", finalState.isAnalyzing)
//            assertNull("Should have no errors", finalState.error)
//            assertNotNull("Should have analysis results", finalState.currentAnalysis)
//
//            val analysis = finalState.currentAnalysis!!
//            assertEquals("Common Rose", analysis.label)
//            assertEquals("Rosa gallica", analysis.plantSpecies)
//            assertEquals(0.89f, analysis.confidence, 0.01f)
//            assertEquals("None", analysis.disease)
//            assertEquals("Healthy", analysis.severity)
//            assertTrue("Should have recommendations", analysis.recommendations.isNotEmpty())
//
//            // Verify enriched data from database
//            assertNotNull("Should have plant info", analysis.plantInfo)
//            assertEquals("Rosaceae", analysis.plantInfo?.family)
//            assertEquals("Moderate", analysis.plantInfo?.wateringNeeds)
//
//            // Verify AI service was called with processed image
//            coVerify { gemmaService.generateResponseMultimodal(any(), any()) }
//
//            // Verify scan was saved to history
//            coVerify { plantDatabase.scanHistoryDao().insertScan(any()) }
//
//        } finally {
//            if (imageFile.exists()) {
//                imageFile.delete()
//            }
//        }
//    }
//
//    @Test
//    fun testLargeImageResizing() = runTest {
//        // Create a large bitmap (simulating high-resolution camera image)
//        val largeBitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
//        fillBitmapWithTestPattern(largeBitmap)
//
//        val imageFile = File(context.cacheDir, "large_test_${System.currentTimeMillis()}.jpg")
//
//        try {
//            imageFile.outputStream().use { out ->
//                largeBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
//            }
//
//            viewModel.analyzeImage(largeBitmap, imageFile)
//            waitForAnalysisCompletion()
//
//            val finalState = viewModel.scanState.value
//            assertFalse("Large image processing should complete", finalState.isAnalyzing)
//            assertNull("Should handle large images without error", finalState.error)
//            assertNotNull("Should produce analysis results", finalState.currentAnalysis)
//
//            // Verify that the image was resized before processing
//            coVerify {
//                gemmaService.generateResponseMultimodal(
//                    any(),
//                    match { bitmap ->
//                        bitmap.width <= 512 && bitmap.height <= 512
//                    }
//                )
//            }
//
//        } finally {
//            if (imageFile.exists()) {
//                imageFile.delete()
//            }
//        }
//    }
//
//    @Test
//    fun testDifferentImageFormats() = runTest {
//        // Test with different bitmap configurations
//        val configurations = listOf(
//            Bitmap.Config.ARGB_8888,
//            Bitmap.Config.RGB_565
//        )
//
//        for (config in configurations) {
//            val bitmap = Bitmap.createBitmap(300, 300, config)
//            fillBitmapWithTestPattern(bitmap)
//
//            val imageFile = File(context.cacheDir, "format_test_${config}_${System.currentTimeMillis()}.jpg")
//
//            try {
//                imageFile.outputStream().use { out ->
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
//                }
//
//                viewModel.analyzeImage(bitmap, imageFile)
//                waitForAnalysisCompletion()
//
//                val finalState = viewModel.scanState.value
//                assertFalse("Should process ${config} format", finalState.isAnalyzing)
//                assertNull("Should handle ${config} without error", finalState.error)
//
//            } finally {
//                if (imageFile.exists()) {
//                    imageFile.delete()
//                }
//            }
//        }
//    }
//
//    @Test
//    fun testImageFileIOOperations() = runTest {
//        val testBitmap = createTestPlantImage()
//        val imageFile = File(context.cacheDir, "io_test_${System.currentTimeMillis()}.jpg")
//
//        try {
//            // Test file creation and writing
//            assertTrue("Should create image file", imageFile.createNewFile())
//
//            imageFile.outputStream().use { out ->
//                val success = testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
//                assertTrue("Should compress and save bitmap", success)
//            }
//
//            assertTrue("Image file should exist", imageFile.exists())
//            assertTrue("Image file should have content", imageFile.length() > 0)
//
//            // Test file reading
//            val loadedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
//            assertNotNull("Should load bitmap from file", loadedBitmap)
//
//            // Process the loaded image
//            viewModel.analyzeImage(loadedBitmap, imageFile)
//            waitForAnalysisCompletion()
//
//            assertNotNull("Should analyze loaded image", viewModel.scanState.value.currentAnalysis)
//
//        } finally {
//            if (imageFile.exists()) {
//                imageFile.delete()
//            }
//        }
//    }
//
//    @Test
//    fun testConcurrentImageProcessing() = runTest {
//        val bitmap1 = createTestPlantImage()
//        val bitmap2 = createTestPlantImage()
//        val file1 = File(context.cacheDir, "concurrent1_${System.currentTimeMillis()}.jpg")
//        val file2 = File(context.cacheDir, "concurrent2_${System.currentTimeMillis()}.jpg")
//
//        try {
//            // Save both images
//            file1.outputStream().use { out ->
//                bitmap1.compress(Bitmap.CompressFormat.JPEG, 90, out)
//            }
//            file2.outputStream().use { out ->
//                bitmap2.compress(Bitmap.CompressFormat.JPEG, 90, out)
//            }
//
//            // Mock slower processing
//            coEvery { gemmaService.generateResponseMultimodal(any(), any()) } coAnswers {
//                kotlinx.coroutines.delay(200)
//                """{"label": "Test Plant", "confidence": 0.8, "plantSpecies": "Test species", "disease": "None", "severity": "Healthy", "recommendations": []}"""
//            }
//
//            // Start concurrent processing
//            viewModel.analyzeImage(bitmap1, file1)
//            kotlinx.coroutines.delay(50) // Small delay
//            viewModel.analyzeImage(bitmap2, file2) // Should cancel first one
//
//            waitForAnalysisCompletion()
//
//            // Verify only one analysis completed
//            coVerify(exactly = 1) { gemmaService.generateResponseMultimodal(any(), any()) }
//
//            val finalState = viewModel.scanState.value
//            assertNotNull("Should have one completed analysis", finalState.currentAnalysis)
//
//        } finally {
//            if (file1.exists()) file1.delete()
//            if (file2.exists()) file2.delete()
//        }
//    }
//
//    @Test
//    fun testMemoryEfficientProcessing() = runTest {
//        // Create multiple images to test memory handling
//        val imageFiles = mutableListOf<File>()
//
//        try {
//            repeat(5) { index ->
//                val bitmap = createTestPlantImage()
//                val file = File(context.cacheDir, "memory_test_$index.jpg")
//
//                file.outputStream().use { out ->
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
//                }
//                imageFiles.add(file)
//
//                // Process each image sequentially
//                viewModel.analyzeImage(bitmap, file)
//                waitForAnalysisCompletion()
//
//                // Verify each processing completed successfully
//                val state = viewModel.scanState.value
//                assertFalse("Processing $index should complete", state.isAnalyzing)
//                assertNull("Processing $index should have no errors", state.error)
//            }
//
//            // Verify all AI calls were made
//            coVerify(exactly = 5) { gemmaService.generateResponseMultimodal(any(), any()) }
//
//        } finally {
//            imageFiles.forEach { file ->
//                if (file.exists()) file.delete()
//            }
//        }
//    }
//
//    @Test
//    fun testScanHistoryPersistence() = runTest {
//        val testBitmap = createTestPlantImage()
//        val imageFile = File(context.cacheDir, "history_test_${System.currentTimeMillis()}.jpg")
//
//        try {
//            imageFile.outputStream().use { out ->
//                testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
//            }
//
//            // Mock scan history with previous scans
//            val existingScans = listOf(
//                ScanHistoryEntity(
//                    id = "prev_1",
//                    timestamp = System.currentTimeMillis() - 86400000,
//                    species = "Previous Plant",
//                    confidence = 0.85f,
//                    disease = "None",
//                    severity = "Healthy",
//                    recommendations = """["Previous recommendation"]""",
//                    imageUri = "file://previous.jpg"
//                )
//            )
//
//            coEvery { plantDatabase.scanHistoryDao().getAllScans() } returns existingScans
//
//            // Load existing history
//            viewModel.loadScanHistory()
//
//            // Process new image
//            viewModel.analyzeImage(testBitmap, imageFile)
//            waitForAnalysisCompletion()
//
//            // Verify new scan was added to history
//            coVerify {
//                plantDatabase.scanHistoryDao().insertScan(
//                    match<ScanHistoryEntity> { scan ->
//                        scan.species == "Rosa gallica" && scan.confidence == 0.89f
//                    }
//                )
//            }
//
//            val finalState = viewModel.scanState.value
//            assertEquals("Should have existing history", 1, finalState.scanHistory.size)
//
//        } finally {
//            if (imageFile.exists()) {
//                imageFile.delete()
//            }
//        }
//    }
//
//    @Test
//    fun testImageCompressionQuality() = runTest {
//        val originalBitmap = createTestPlantImage()
//        val qualities = listOf(50, 75, 90, 100)
//
//        for (quality in qualities) {
//            val imageFile = File(context.cacheDir, "quality_${quality}_${System.currentTimeMillis()}.jpg")
//
//            try {
//                imageFile.outputStream().use { out ->
//                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
//                }
//
//                val compressedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
//                assertNotNull("Should decode quality $quality image", compressedBitmap)
//
//                viewModel.analyzeImage(compressedBitmap, imageFile)
//                waitForAnalysisCompletion()
//
//                val state = viewModel.scanState.value
//                assertFalse("Should process quality $quality", state.isAnalyzing)
//                assertNull("Should handle quality $quality without error", state.error)
//
//            } finally {
//                if (imageFile.exists()) {
//                    imageFile.delete()
//                }
//            }
//        }
//    }
//
//    private fun createTestPlantImage(): Bitmap {
//        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
//        fillBitmapWithTestPattern(bitmap)
//        return bitmap
//    }
//
//    private fun fillBitmapWithTestPattern(bitmap: Bitmap) {
//        // Create a simple test pattern that simulates plant-like colors
//        val width = bitmap.width
//        val height = bitmap.height
//        val pixels = IntArray(width * height)
//
//        for (y in 0 until height) {
//            for (x in 0 until width) {
//                val index = y * width + x
//
//                // Create a gradient with green tones (simulating plant colors)
//                val green = (255 * (x.toFloat() / width)).toInt()
//                val red = (128 * (y.toFloat() / height)).toInt()
//                val blue = 64
//
//                pixels[index] = (255 shl 24) or (red shl 16) or (green shl 8) or blue
//            }
//        }
//
//        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
//    }
//
//    private suspend fun waitForAnalysisCompletion() {
//        val timeout = TimeUnit.SECONDS.toMillis(10)
//        val startTime = System.currentTimeMillis()
//
//        while (viewModel.scanState.value.isAnalyzing &&
//               (System.currentTimeMillis() - startTime) < timeout) {
//            kotlinx.coroutines.delay(50)
//        }
//
//        // Additional small delay to ensure state updates are complete
//        kotlinx.coroutines.delay(100)
//    }
//}