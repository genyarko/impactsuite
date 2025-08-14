package com.mygemma3n.aiapp.feature.plant
//
//import android.content.Context
//import android.graphics.Bitmap
//import app.cash.turbine.test
//import com.mygemma3n.aiapp.data.UnifiedGemmaService
//import com.mygemma3n.aiapp.shared_utilities.MobileNetV5Encoder
//import io.mockk.*
//import kotlinx.coroutines.*
//import kotlinx.coroutines.test.*
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import org.junit.Assert.*
//import java.io.*
//
///**
// * Comprehensive edge case and error handling tests for Plant Classification
// */
//@OptIn(ExperimentalCoroutinesApi::class)
//class PlantClassificationEdgeCaseTest {
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
//        // Default mock setup
//        coEvery { gemmaService.isModelReady() } returns true
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns """
//            {"label": "Unknown", "confidence": 0.1, "plantSpecies": "N/A", "disease": "N/A", "severity": "N/A", "recommendations": []}
//        """.trimIndent()
//
//        setupMockDatabase()
//        viewModel = PlantScannerViewModel(context, gemmaService, mobileNetEncoder, plantDatabase)
//    }
//
//    @After
//    fun tearDown() {
//        Dispatchers.resetMain()
//    }
//
//    private fun setupMockDatabase() {
//        val mockPlantInfoDao = mockk<PlantInfoDao>(relaxed = true)
//        val mockPlantDiseaseDao = mockk<PlantDiseaseDao>(relaxed = true)
//        val mockScanHistoryDao = mockk<ScanHistoryDao>(relaxed = true)
//
//        coEvery { mockPlantInfoDao.getByScientificName(any()) } returns null
//        coEvery { mockPlantDiseaseDao.getByName(any()) } returns null
//        coEvery { mockScanHistoryDao.getAllScans() } returns emptyList()
//        coEvery { mockScanHistoryDao.insertScan(any()) } returns Unit
//
//        every { plantDatabase.plantInfoDao() } returns mockPlantInfoDao
//        every { plantDatabase.plantDiseaseDao() } returns mockPlantDiseaseDao
//        every { plantDatabase.scanHistoryDao() } returns mockScanHistoryDao
//    }
//
//    @Test
//    fun `test extremely small image processing`() = runTest {
//        // Create 1x1 pixel image
//        val tinyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("tiny", ".jpg")
//
//        try {
//            viewModel.scanState.test {
//                viewModel.analyzeImage(tinyBitmap, testFile)
//
//                awaitItem() // initial
//                awaitItem() // analyzing
//
//                val finalState = awaitItem()
//                assertFalse(finalState.isAnalyzing)
//                // Should handle tiny images without crashing
//                assertNotNull(finalState.currentAnalysis)
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test corrupted bitmap processing`() = runTest {
//        // Create a bitmap and then simulate corruption by setting pixels to null
//        val corruptedBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("corrupted", ".jpg")
//
//        // Simulate bitmap corruption by recycling it
//        corruptedBitmap.recycle()
//
//        try {
//            viewModel.scanState.test {
//                viewModel.analyzeImage(corruptedBitmap, testFile)
//
//                awaitItem() // initial
//                awaitItem() // analyzing
//
//                val errorState = awaitItem()
//                assertFalse(errorState.isAnalyzing)
//                assertNotNull(errorState.error)
//                assertTrue(errorState.error?.contains("recycled") == true ||
//                          errorState.error?.contains("bitmap") == true)
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test AI model timeout handling`() = runTest {
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("timeout", ".jpg")
//
//        // Simulate timeout
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } coAnswers {
//            delay(Long.MAX_VALUE) // Infinite delay to simulate timeout
//            "Never reached"
//        }
//
//        try {
//            val job = launch {
//                viewModel.analyzeImage(testBitmap, testFile)
//            }
//
//            // Wait briefly then cancel to simulate timeout
//            delay(100)
//            job.cancel()
//
//            assertTrue("Job should be cancelled", job.isCancelled)
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test malformed JSON responses`() = runTest {
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("malformed", ".jpg")
//
//        val malformedResponses = listOf(
//            "Not JSON at all",
//            "{incomplete json",
//            "{'invalid': 'quotes'}",
//            "{}",  // Empty JSON
//            """{"label": "Test", "confidence": "not_a_number"}""",  // Invalid data types
//            """{"missing_required_fields": true}""",
//            "null"
//        )
//
//        try {
//            for (response in malformedResponses) {
//                coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns response
//
//                viewModel.scanState.test {
//                    viewModel.analyzeImage(testBitmap, testFile)
//
//                    awaitItem() // initial
//                    awaitItem() // analyzing
//
//                    val result = awaitItem()
//                    assertFalse("Should complete processing for response: $response", result.isAnalyzing)
//
//                    // Should either have error or fallback analysis
//                    assertTrue("Should handle malformed response: $response",
//                        result.error != null || result.currentAnalysis != null)
//                }
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test AI model initialization failure`() = runTest {
//        coEvery { gemmaService.isModelReady() } returns false
//        coEvery { gemmaService.initializeModel() } throws Exception("Model files not found")
//
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("init_fail", ".jpg")
//
//        try {
//            viewModel.scanState.test {
//                viewModel.analyzeImage(testBitmap, testFile)
//
//                awaitItem() // initial
//                awaitItem() // analyzing
//
//                val errorState = awaitItem()
//                assertFalse(errorState.isAnalyzing)
//                assertTrue(errorState.error?.contains("Model files not found") == true)
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test MobileNet encoder failure`() = runTest {
//        coEvery { mobileNetEncoder.isModelLoaded() } returns false
//        coEvery { mobileNetEncoder.encodeImage(any()) } throws Exception("TensorFlow Lite error")
//
//        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("mobilenet_fail", ".jpg")
//
//        try {
//            // Should still work without MobileNet features
//            viewModel.analyzeImage(testBitmap, testFile)
//            testDispatcher.scheduler.advanceUntilIdle()
//
//            val finalState = viewModel.scanState.value
//            // Should not crash even if MobileNet fails
//            assertFalse(finalState.isAnalyzing)
//
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test database connection failure`() = runTest {
//        // Mock database failure
//        every { plantDatabase.plantInfoDao() } throws Exception("Database connection failed")
//        every { plantDatabase.scanHistoryDao() } throws Exception("Database connection failed")
//
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("db_fail", ".jpg")
//
//        try {
//            viewModel.scanState.test {
//                viewModel.analyzeImage(testBitmap, testFile)
//
//                awaitItem() // initial
//                awaitItem() // analyzing
//
//                val finalState = awaitItem()
//                assertFalse(finalState.isAnalyzing)
//
//                // Should still complete analysis even if database fails
//                assertNotNull(finalState.currentAnalysis)
//
//                // But enrichment data won't be available
//                assertNull(finalState.currentAnalysis?.plantInfo)
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test very high confidence with questionable results`() = runTest {
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("high_conf", ".jpg")
//
//        // Mock suspiciously high confidence for non-plant
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns """
//            {
//                "label": "Smartphone",
//                "confidence": 0.99,
//                "plantSpecies": "N/A",
//                "disease": "N/A",
//                "severity": "N/A",
//                "recommendations": ["This is clearly not a plant but the confidence is very high"]
//            }
//        """.trimIndent()
//
//        try {
//            viewModel.scanState.test {
//                viewModel.analyzeImage(testBitmap, testFile)
//
//                awaitItem() // initial
//                awaitItem() // analyzing
//
//                val finalState = awaitItem()
//                val analysis = finalState.currentAnalysis!!
//
//                assertEquals("Smartphone", analysis.label)
//                assertEquals(0.99f, analysis.confidence, 0.01f)
//                assertNull(analysis.plantSpecies) // Should be null for non-plants
//                assertNull(analysis.disease)
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test extremely low confidence results`() = runTest {
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("low_conf", ".jpg")
//
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns """
//            {
//                "label": "Unidentifiable blur",
//                "confidence": 0.01,
//                "plantSpecies": "Unknown",
//                "disease": "Cannot determine",
//                "severity": "Unknown",
//                "recommendations": ["Image quality too poor for accurate identification"]
//            }
//        """.trimIndent()
//
//        try {
//            viewModel.scanState.test {
//                viewModel.analyzeImage(testBitmap, testFile)
//
//                awaitItem() // initial
//                awaitItem() // analyzing
//
//                val finalState = awaitItem()
//                val analysis = finalState.currentAnalysis!!
//
//                assertEquals(0.01f, analysis.confidence, 0.01f)
//                assertTrue(analysis.recommendations.any { it.contains("quality") })
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test invalid file reference`() = runTest {
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val invalidFile = File("/nonexistent/path/invalid.jpg")
//
//        viewModel.scanState.test {
//            viewModel.analyzeImage(testBitmap, invalidFile)
//
//            awaitItem() // initial
//            awaitItem() // analyzing
//
//            val finalState = awaitItem()
//            // Should still process the bitmap even if file path is invalid
//            assertFalse(finalState.isAnalyzing)
//            assertNotNull(finalState.currentAnalysis)
//        }
//    }
//
//    @Test
//    fun `test rapid successive analysis requests`() = runTest {
//        val bitmaps = List(5) { Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888) }
//        val files = List(5) { File.createTempFile("rapid_$it", ".jpg") }
//
//        try {
//            // Mock slow processing
//            coEvery { gemmaService.generateResponseMultimodal(any(), any()) } coAnswers {
//                delay(100)
//                """{"label": "Plant", "confidence": 0.8, "plantSpecies": "Test", "disease": "None", "severity": "Healthy", "recommendations": []}"""
//            }
//
//            // Send rapid requests
//            bitmaps.forEachIndexed { index, bitmap ->
//                viewModel.analyzeImage(bitmap, files[index])
//                delay(10) // Very short delay between requests
//            }
//
//            testDispatcher.scheduler.advanceUntilIdle()
//
//            // Only the last request should complete
//            coVerify(exactly = 1) { gemmaService.generateResponseMultimodal(any(), any()) }
//
//            val finalState = viewModel.scanState.value
//            assertFalse(finalState.isAnalyzing)
//            assertNotNull(finalState.currentAnalysis)
//
//        } finally {
//            files.forEach { it.delete() }
//        }
//    }
//
//    @Test
//    fun `test memory pressure scenarios`() = runTest {
//        // Simulate memory pressure by creating large bitmaps
//        val largeBitmaps = mutableListOf<Bitmap>()
//
//        try {
//            repeat(3) { index ->
//                val largeBitmap = Bitmap.createBitmap(2000, 2000, Bitmap.Config.ARGB_8888)
//                largeBitmaps.add(largeBitmap)
//
//                val testFile = File.createTempFile("memory_$index", ".jpg")
//
//                viewModel.scanState.test {
//                    viewModel.analyzeImage(largeBitmap, testFile)
//
//                    awaitItem() // initial
//                    awaitItem() // analyzing
//
//                    val finalState = awaitItem()
//                    assertFalse("Should handle large bitmap $index", finalState.isAnalyzing)
//
//                    // Should either succeed or fail gracefully
//                    assertTrue("Should have result or error for bitmap $index",
//                        finalState.currentAnalysis != null || finalState.error != null)
//                }
//
//                testFile.delete()
//            }
//        } finally {
//            // Clean up bitmaps to free memory
//            largeBitmaps.forEach { bitmap ->
//                if (!bitmap.isRecycled) {
//                    bitmap.recycle()
//                }
//            }
//        }
//    }
//
//    @Test
//    fun `test network interruption simulation`() = runTest {
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("network", ".jpg")
//
//        // Simulate network-related errors (even though processing is offline)
//        coEvery { gemmaService.generateResponseMultimodal(any(), any()) } throws
//            java.net.SocketTimeoutException("Network timeout")
//
//        try {
//            viewModel.scanState.test {
//                viewModel.analyzeImage(testBitmap, testFile)
//
//                awaitItem() // initial
//                awaitItem() // analyzing
//
//                val errorState = awaitItem()
//                assertFalse(errorState.isAnalyzing)
//                assertTrue(errorState.error?.contains("timeout") == true)
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//
//    @Test
//    fun `test unusual image dimensions`() = runTest {
//        val unusualDimensions = listOf(
//            Pair(1, 1000),    // Very tall and narrow
//            Pair(1000, 1),    // Very wide and short
//            Pair(1, 1),       // Single pixel
//            Pair(3000, 4000), // Very large
//            Pair(17, 23)      // Odd dimensions
//        )
//
//        for ((width, height) in unusualDimensions) {
//            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//            val testFile = File.createTempFile("unusual_${width}x${height}", ".jpg")
//
//            try {
//                viewModel.scanState.test {
//                    viewModel.analyzeImage(bitmap, testFile)
//
//                    awaitItem() // initial
//                    awaitItem() // analyzing
//
//                    val finalState = awaitItem()
//                    assertFalse("Should handle ${width}x${height} image", finalState.isAnalyzing)
//
//                    // Should either succeed or fail gracefully
//                    assertTrue("Should handle unusual dimensions ${width}x${height}",
//                        finalState.currentAnalysis != null || finalState.error != null)
//                }
//            } finally {
//                testFile.delete()
//            }
//        }
//    }
//
//    @Test
//    fun `test scan history with corrupted data`() = runTest {
//        // Mock corrupted scan history data
//        val corruptedScans = listOf(
//            ScanHistoryEntity(
//                id = "corrupt1",
//                timestamp = -1L, // Invalid timestamp
//                species = "",    // Empty species
//                confidence = 2.0f, // Invalid confidence > 1.0
//                disease = null,
//                severity = null,
//                recommendations = "not_valid_json", // Invalid JSON
//                imageUri = "invalid://uri"
//            ),
//            ScanHistoryEntity(
//                id = "corrupt2",
//                timestamp = System.currentTimeMillis(),
//                species = "Valid Species",
//                confidence = Float.NaN, // NaN confidence
//                disease = "Disease",
//                severity = "Severe",
//                recommendations = """["Valid", null, "recommendations"]""", // Null in array
//                imageUri = null
//            )
//        )
//
//        coEvery { plantDatabase.scanHistoryDao().getAllScans() } returns corruptedScans
//
//        viewModel.scanState.test {
//            viewModel.loadScanHistory()
//
//            val initialState = awaitItem()
//            val updatedState = awaitItem()
//
//            // Should handle corrupted data gracefully
//            assertNotNull(updatedState.scanHistory)
//
//            // Corrupted entries might be filtered out or have default values
//            updatedState.scanHistory.forEach { analysis ->
//                assertTrue("Confidence should be valid",
//                    analysis.confidence.isFinite() && analysis.confidence >= 0f)
//                assertNotNull("Species should not be null", analysis.plantSpecies)
//            }
//        }
//    }
//
//    @Test
//    fun `test JSON response with unexpected structure`() = runTest {
//        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
//        val testFile = File.createTempFile("unexpected", ".jpg")
//
//        val unexpectedResponses = listOf(
//            // Array instead of object
//            """["label", "confidence", "species"]""",
//
//            // Nested structure
//            """{"result": {"analysis": {"label": "Plant", "confidence": 0.8}}}""",
//
//            // Extra unexpected fields
//            """{"label": "Plant", "confidence": 0.8, "unexpected_field": {"nested": "data"}, "another_field": [1,2,3]}""",
//
//            // Mixed data types
//            """{"label": 123, "confidence": "high", "plantSpecies": true, "disease": null, "recommendations": "single_string"}"""
//        )
//
//        try {
//            for (response in unexpectedResponses) {
//                coEvery { gemmaService.generateResponseMultimodal(any(), any()) } returns response
//
//                viewModel.scanState.test {
//                    viewModel.analyzeImage(testBitmap, testFile)
//
//                    awaitItem() // initial
//                    awaitItem() // analyzing
//
//                    val result = awaitItem()
//                    assertFalse("Should handle unexpected JSON structure", result.isAnalyzing)
//
//                    // Should either parse what it can or return error
//                    assertTrue("Should handle unexpected response gracefully",
//                        result.currentAnalysis != null || result.error != null)
//                }
//            }
//        } finally {
//            testFile.delete()
//        }
//    }
//}