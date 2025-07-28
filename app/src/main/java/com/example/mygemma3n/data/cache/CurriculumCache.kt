package com.example.mygemma3n.data.cache

import android.content.Context
import com.example.mygemma3n.shared_utilities.OfflineRAG
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurriculumCache @Inject constructor() {
    
    data class CachedCurriculum(
        val data: JSONObject,
        val timestamp: Long,
        val topics: List<CurriculumTopic>
    )
    
    data class CurriculumTopic(
        val title: String,
        val subject: OfflineRAG.Subject,
        val phase: String,
        val gradeRange: String,
        val simplifiedTopics: List<String>
    )
    
    private val cache = mutableMapOf<OfflineRAG.Subject, CachedCurriculum>()
    private val cacheMutex = Mutex()
    private val cacheExpirationTime = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
    
    suspend fun getCurriculumTopics(
        context: Context, 
        subject: OfflineRAG.Subject
    ): List<CurriculumTopic> = cacheMutex.withLock {
        
        val cached = cache[subject]
        val now = System.currentTimeMillis()
        
        // Check if cache is valid
        if (cached != null && (now - cached.timestamp) < cacheExpirationTime) {
            Timber.d("Cache hit for subject: ${subject.name}")
            return@withLock cached.topics
        }
        
        // Cache miss or expired - load from file
        Timber.d("Cache miss for subject: ${subject.name}, loading from file")
        
        val fileName = getFileNameForSubject(subject)
        if (fileName == null) {
            Timber.w("No curriculum file for subject: ${subject.name}")
            return@withLock emptyList()
        }
        
        try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val topics = parseCurriculumJson(json, subject)
            
            // Cache the result
            cache[subject] = CachedCurriculum(
                data = json,
                timestamp = now,
                topics = topics
            )
            
            Timber.d("Successfully cached ${topics.size} topics for ${subject.name}")
            return@withLock topics
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum for ${subject.name}")
            return@withLock emptyList()
        }
    }
    
    private fun getFileNameForSubject(subject: OfflineRAG.Subject): String? {
        return when (subject) {
            OfflineRAG.Subject.SCIENCE -> "curriculum/science_curriculum.json"
            OfflineRAG.Subject.MATHEMATICS -> "curriculum/mathematics_curriculum.json"
            OfflineRAG.Subject.ENGLISH -> "curriculum/english_curriculum.json"
            OfflineRAG.Subject.LANGUAGE_ARTS -> "curriculum/english_curriculum.json"
            OfflineRAG.Subject.HISTORY -> "curriculum/history_curriculum.json"
            OfflineRAG.Subject.GEOGRAPHY -> "curriculum/geography_curriculum.json"
            OfflineRAG.Subject.ECONOMICS -> "curriculum/economics_curriculum.json"
            else -> null
        }
    }
    
    private fun parseCurriculumJson(json: JSONObject, subject: OfflineRAG.Subject): List<CurriculumTopic> {
        val topics = mutableListOf<CurriculumTopic>()
        
        listOf("PYP", "MYP", "DP").forEach { program ->
            if (!json.has(program)) return@forEach
            val programObj = json.getJSONObject(program)
            
            programObj.keys().forEach { phaseOrSubject ->
                val value = programObj.get(phaseOrSubject)
                when (value) {
                    is org.json.JSONArray -> {
                        // Direct array of topics
                        for (i in 0 until value.length()) {
                            val topicTitle = value.getString(i)
                            topics.add(
                                CurriculumTopic(
                                    title = topicTitle,
                                    subject = subject,
                                    phase = phaseOrSubject,
                                    gradeRange = extractGradeRange(phaseOrSubject),
                                    simplifiedTopics = parseTopicForFloatingBubbles(topicTitle)
                                )
                            )
                        }
                    }
                    is JSONObject -> {
                        // Nested object with more structure
                        if (value.has("Topics")) {
                            val topicsArray = value.getJSONArray("Topics")
                            for (i in 0 until topicsArray.length()) {
                                val topicTitle = topicsArray.getString(i)
                                topics.add(
                                    CurriculumTopic(
                                        title = topicTitle,
                                        subject = subject,
                                        phase = phaseOrSubject,
                                        gradeRange = extractGradeRange(phaseOrSubject),
                                        simplifiedTopics = parseTopicForFloatingBubbles(topicTitle)
                                    )
                                )
                            }
                        } else {
                            // Other nested structures
                            value.keys().forEach { subKey ->
                                val subValue = value.get(subKey)
                                if (subValue is org.json.JSONArray) {
                                    for (i in 0 until subValue.length()) {
                                        val topicTitle = subValue.getString(i)
                                        topics.add(
                                            CurriculumTopic(
                                                title = topicTitle,
                                                subject = subject,
                                                phase = "$phaseOrSubject - $subKey",
                                                gradeRange = extractGradeRange(phaseOrSubject),
                                                simplifiedTopics = parseTopicForFloatingBubbles(topicTitle)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return topics
    }
    
    private fun extractGradeRange(phase: String): String {
        return when {
            phase.contains("KG", ignoreCase = true) -> "K"
            phase.contains("Grade", ignoreCase = true) -> {
                val regex = Regex("Grade[s]?\\s*(\\d+)(?:[-–]\\s*(\\d+))?", RegexOption.IGNORE_CASE)
                val matchResult = regex.find(phase)
                if (matchResult != null) {
                    val start = matchResult.groupValues[1]
                    val end = matchResult.groupValues[2].takeIf { it.isNotEmpty() } ?: start
                    if (start == end) start else "$start-$end"
                } else "Unknown"
            }
            phase.contains("Phase", ignoreCase = true) -> {
                when {
                    phase.contains("Phase 1") -> "K"
                    phase.contains("Phase 2") -> "1-2"
                    phase.contains("Phase 3") -> "3-4"
                    phase.contains("Phase 4") -> "5-6"
                    else -> "Unknown"
                }
            }
            phase.contains("MYP", ignoreCase = true) -> {
                val regex = Regex("MYP\\s*(\\d+)(?:[-–]\\s*(\\d+))?", RegexOption.IGNORE_CASE)
                val matchResult = regex.find(phase)
                if (matchResult != null) {
                    val start = matchResult.groupValues[1]
                    val end = matchResult.groupValues[2].takeIf { it.isNotEmpty() } ?: start
                    val gradeStart = start.toInt() + 5 // MYP 1 = Grade 6
                    val gradeEnd = end.toInt() + 5
                    if (gradeStart == gradeEnd) gradeStart.toString() else "$gradeStart-$gradeEnd"
                } else "6-10"
            }
            phase.contains("DP", ignoreCase = true) -> "11-12"
            else -> "Unknown"
        }
    }
    
    /**
     * Parse complex topics into simpler floating bubble topics
     */
    private fun parseTopicForFloatingBubbles(rawTitle: String): List<String> {
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
    
    suspend fun clearCache() = cacheMutex.withLock {
        cache.clear()
        Timber.d("Curriculum cache cleared")
    }
    
    suspend fun clearSubjectCache(subject: OfflineRAG.Subject) = cacheMutex.withLock {
        cache.remove(subject)
        Timber.d("Cache cleared for subject: ${subject.name}")
    }
    
    suspend fun getCacheStats(): Map<String, Any> = cacheMutex.withLock {
        val stats = mutableMapOf<String, Any>()
        stats["totalCachedSubjects"] = cache.size
        stats["cacheDetails"] = cache.mapValues { (subject, cached) ->
            mapOf(
                "topicCount" to cached.topics.size,
                "cacheAge" to (System.currentTimeMillis() - cached.timestamp),
                "isExpired" to ((System.currentTimeMillis() - cached.timestamp) > cacheExpirationTime)
            )
        }
        return@withLock stats
    }
}