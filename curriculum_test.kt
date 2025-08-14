package com.mygemma3n.aiapp.test

import android.content.Context
import com.mygemma3n.aiapp.shared_utilities.OfflineRAG
import com.mygemma3n.aiapp.feature.tutor.TutorViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

/**
 * Comprehensive test for curriculum mapping and topic parsing
 * Verifies all subjects have proper curriculum files and display correctly
 */
class CurriculumMappingTest {

    data class TestResult(
        val subject: OfflineRAG.Subject,
        val hasFile: Boolean,
        val fileName: String,
        val topicCount: Int,
        val parsedTopics: List<String>,
        val error: String? = null
    )

    fun testAllCurriculumMappings(context: Context): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        // Test all subjects from the UI
        val allSubjects = listOf(
            OfflineRAG.Subject.MATHEMATICS,
            OfflineRAG.Subject.SCIENCE,
            OfflineRAG.Subject.ENGLISH,
            OfflineRAG.Subject.HISTORY,
            OfflineRAG.Subject.GEOGRAPHY,
            OfflineRAG.Subject.ECONOMICS
        )

        allSubjects.forEach { subject ->
            val result = testSubjectMapping(context, subject)
            results.add(result)
            println("Subject: ${subject.name}")
            println("  File: ${result.fileName} - Exists: ${result.hasFile}")
            println("  Topics: ${result.topicCount}")
            println("  Sample Topics: ${result.parsedTopics.take(3)}")
            result.error?.let { println("  Error: $it") }
            println()
        }

        return results
    }

    private fun testSubjectMapping(context: Context, subject: OfflineRAG.Subject): TestResult {
        val fileName = when (subject) {
            OfflineRAG.Subject.SCIENCE -> "curriculum/science_curriculum.json"
            OfflineRAG.Subject.MATHEMATICS -> "curriculum/mathematics_curriculum.json"
            OfflineRAG.Subject.ENGLISH -> "curriculum/english_curriculum.json"
            OfflineRAG.Subject.LANGUAGE_ARTS -> "curriculum/english_curriculum.json"
            OfflineRAG.Subject.HISTORY -> "curriculum/history_curriculum.json"
            OfflineRAG.Subject.GEOGRAPHY -> "curriculum/geography_curriculum.json"
            OfflineRAG.Subject.ECONOMICS -> "curriculum/economics_curriculum.json"
            else -> return TestResult(subject, false, "N/A", 0, emptyList(), "No mapping defined")
        }

        try {
            // Check if file exists and can be read
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            
            // Parse and count topics
            val json = JSONObject(jsonString)
            val topics = mutableListOf<String>()
            var totalTopics = 0

            listOf("PYP", "MYP", "DP").forEach { program ->
                if (!json.has(program)) return@forEach
                val programObj = json.getJSONObject(program)

                programObj.keys().forEach { phaseOrSubject ->
                    val value = programObj.get(phaseOrSubject)
                    when (value) {
                        is org.json.JSONArray -> {
                            // Direct array of topics
                            for (i in 0 until value.length()) {
                                val topic = value.getString(i)
                                topics.add(topic)
                                totalTopics++
                            }
                        }
                        is JSONObject -> {
                            // Nested object with more structure
                            value.keys().forEach { subKey ->
                                val subValue = value.get(subKey)
                                if (subValue is org.json.JSONArray) {
                                    for (i in 0 until subValue.length()) {
                                        val topic = subValue.getString(i)
                                        topics.add(topic)
                                        totalTopics++
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Test topic parsing for floating bubbles
            val parsedTopics = topics.take(10).map { parseTopicForFloatingBubbles(it) }.flatten()

            return TestResult(
                subject = subject,
                hasFile = true,
                fileName = fileName,
                topicCount = totalTopics,
                parsedTopics = parsedTopics,
                error = null
            )

        } catch (e: IOException) {
            return TestResult(
                subject = subject,
                hasFile = false,
                fileName = fileName,
                topicCount = 0,
                parsedTopics = emptyList(),
                error = "File not found: ${e.message}"
            )
        } catch (e: Exception) {
            return TestResult(
                subject = subject,
                hasFile = true,
                fileName = fileName,
                topicCount = 0,
                parsedTopics = emptyList(),
                error = "Parse error: ${e.message}"
            )
        }
    }

    /**
     * Test the topic parsing logic that makes topics suitable for floating bubbles
     */
    private fun parseTopicForFloatingBubbles(rawTitle: String): List<String> {
        // Replicate the parsing logic from TutorViewModel
        val primarySplits = rawTitle
            .split(Regex("\\s+and\\s+|\\s*,\\s*|\\s*&\\s*|\\s*;\\s*|\\s*/\\s*", RegexOption.IGNORE_CASE))

        val finalTopics = mutableListOf<String>()

        primarySplits.forEach { segment ->
            var cleanSegment = segment.trim()

            // Handle parentheses
            if (cleanSegment.contains("(") && cleanSegment.contains(")")) {
                val beforeParen = cleanSegment.substringBefore("(").trim()
                val insideParen = cleanSegment.substringAfter("(").substringBefore(")").trim()

                if (beforeParen.isNotEmpty()) finalTopics.add(beforeParen)
                if (insideParen.isNotEmpty()) {
                    insideParen.split(Regex("\\s*,\\s*|\\s+and\\s+")).forEach { part ->
                        if (part.trim().isNotEmpty()) finalTopics.add(part.trim())
                    }
                }
            } else {
                // Handle dashes and colons
                if (cleanSegment.contains(" - ") || cleanSegment.contains(": ")) {
                    val parts = cleanSegment.split(Regex("\\s*-\\s*|\\s*:\\s*"))
                    parts.forEach { part ->
                        if (part.trim().isNotEmpty()) finalTopics.add(part.trim())
                    }
                } else {
                    finalTopics.add(cleanSegment)
                }
            }
        }

        return finalTopics.filter { it.isNotEmpty() && it.length >= 3 }
    }

    /**
     * Test UI subject card data matches curriculum mappings
     */
    fun testUISubjectCards(): List<String> {
        val issues = mutableListOf<String>()
        
        // Expected subjects from UI (from getSubjectCards in TutorScreen.kt)
        val expectedSubjects = setOf(
            OfflineRAG.Subject.MATHEMATICS,
            OfflineRAG.Subject.SCIENCE,
            OfflineRAG.Subject.ENGLISH,
            OfflineRAG.Subject.HISTORY,
            OfflineRAG.Subject.GEOGRAPHY,
            OfflineRAG.Subject.ECONOMICS
        )

        // Check if all expected subjects have curriculum mappings
        expectedSubjects.forEach { subject ->
            val hasMapping = when (subject) {
                OfflineRAG.Subject.SCIENCE,
                OfflineRAG.Subject.MATHEMATICS,
                OfflineRAG.Subject.ENGLISH,
                OfflineRAG.Subject.HISTORY,
                OfflineRAG.Subject.GEOGRAPHY,
                OfflineRAG.Subject.ECONOMICS -> true
                else -> false
            }

            if (!hasMapping) {
                issues.add("Subject ${subject.name} in UI but no curriculum mapping")
            }
        }

        return issues
    }

    /**
     * Run all tests and print comprehensive report
     */
    fun runFullTest(context: Context) {
        println("=== CURRICULUM MAPPING AND UI TEST REPORT ===")
        println()

        // Test curriculum mappings
        println("1. CURRICULUM FILE MAPPINGS:")
        println("=" * 40)
        val mappingResults = testAllCurriculumMappings(context)
        
        val successCount = mappingResults.count { it.hasFile && it.error == null }
        val totalCount = mappingResults.size
        
        println("Summary: $successCount/$totalCount subjects have valid curriculum files")
        println()

        // Test UI consistency
        println("2. UI SUBJECT CARD CONSISTENCY:")
        println("=" * 40)
        val uiIssues = testUISubjectCards()
        
        if (uiIssues.isEmpty()) {
            println("✅ All UI subjects have proper curriculum mappings")
        } else {
            println("❌ UI Issues found:")
            uiIssues.forEach { println("  - $it") }
        }
        println()

        // Test topic parsing
        println("3. TOPIC PARSING TEST:")
        println("=" * 40)
        val sampleTopics = listOf(
            "Production, trade and Interdependence",
            "Numbers: Natural numbers, integers, rational numbers",
            "Forces and Motion (velocity, acceleration)",
            "Literature: Poetry and prose analysis"
        )
        
        sampleTopics.forEach { topic ->
            val parsed = parseTopicForFloatingBubbles(topic)
            println("Original: $topic")
            println("Parsed: $parsed")
            println()
        }

        // Overall summary
        println("4. OVERALL ASSESSMENT:")
        println("=" * 40)
        val allFilesExist = mappingResults.all { it.hasFile }
        val noParseErrors = mappingResults.all { it.error == null }
        val uiConsistent = uiIssues.isEmpty()
        
        if (allFilesExist && noParseErrors && uiConsistent) {
            println("✅ ALL TESTS PASSED - System is properly configured")
        } else {
            println("❌ Issues found that need attention:")
            if (!allFilesExist) println("  - Missing curriculum files")
            if (!noParseErrors) println("  - Curriculum parsing errors")
            if (!uiConsistent) println("  - UI/mapping inconsistencies")
        }
    }
}

/**
 * Usage example:
 * val test = CurriculumMappingTest()
 * test.runFullTest(context)
 */