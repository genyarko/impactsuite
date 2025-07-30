package com.example.mygemma3n.feature.quiz

import android.content.Context
import com.example.mygemma3n.data.UnifiedGemmaService
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class CurriculumAwareQuizGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val gson: Gson
) {

    data class CurriculumTopic(
        val title: String,
        val gradeRange: String,
        val phase: String,
        val subtopics: List<String> = emptyList(),
        val countries: List<String> = emptyList(), // Empty list means applies to all countries
        val countrySpecific: Map<String, String> = emptyMap() // Country-specific variations
    )

    /**
     * Generate a quiz based on curriculum and grade level
     */
    suspend fun generateCurriculumBasedQuestions(
        subject: Subject,
        gradeLevel: Int,
        topic: String,
        count: Int,
        difficulty: Difficulty,
        country: String? = null,
        previousQuestions: List<String> = emptyList()
    ): List<Question> = withContext(Dispatchers.IO) {

        // Load curriculum topics for the subject and grade
        val curriculumTopics = loadCurriculumForGrade(subject, gradeLevel, country)

        // Find relevant topics or use the provided topic
        // 1. Build the list (existing code)
        var relevantTopics = if (topic.isBlank()) {
            curriculumTopics.shuffled().take(5)
        } else {
            curriculumTopics.filter { it.title.contains(topic, ignoreCase = true) }
                .ifEmpty { listOf(CurriculumTopic(topic, "", "")) }
        }

// 2. Fallback if still empty  ↓↓↓  (add this block)
        if (relevantTopics.isEmpty()) {
            Timber.w("No curriculum topics for $subject grade $gradeLevel – using generic fallback")
            relevantTopics += listOf(
                CurriculumTopic(
                    title = "General Knowledge",
                    gradeRange = "Grade $gradeLevel",
                    phase = ""
                )
            )
        }


        Timber.d("Found ${relevantTopics.size} relevant topics for grade $gradeLevel")

        val questions = mutableListOf<Question>()
        val questionTypes = selectQuestionTypesForGrade(gradeLevel, count)
        
        // Shuffle topics for each quiz session to vary question order
        val shuffledTopics = relevantTopics.shuffled()

        for (i in 0 until count) {
            val selectedTopic = shuffledTopics[i % shuffledTopics.size]
            val questionType = questionTypes[i]

            try {
                val question = generateSingleCurriculumQuestion(
                    subject = subject,
                    topic = selectedTopic,
                    gradeLevel = gradeLevel,
                    questionType = questionType,
                    difficulty = adjustDifficultyForGrade(difficulty, gradeLevel),
                    country = country,
                    previousQuestions = previousQuestions + questions.map { it.questionText },
                    attemptNumber = i
                )

                questions.add(question)

            } catch (e: Exception) {
                Timber.e(e, "Failed to generate question, using curriculum fallback")

                // Use curriculum-aware fallback
                val fallback = generateCurriculumFallback(
                    subject = subject,
                    topic = selectedTopic,
                    gradeLevel = gradeLevel,
                    questionType = questionType,
                    difficulty = difficulty,
                    country = country
                )
                questions.add(fallback)
            }
        }

        return@withContext questions
    }

    /**
     * Load curriculum topics appropriate for the grade level
     */
    /** Load curriculum topics appropriate for the grade level */
    private suspend fun loadCurriculumForGrade(
        subject: Subject,
        gradeLevel: Int,
        country: String? = null
    ): List<CurriculumTopic> = withContext(Dispatchers.IO) {

        // ── Correct relative paths ──────────────────────────────────────────────
        val fileName = when (subject) {
            Subject.SCIENCE                 -> "curriculum/science_curriculum.json"
            Subject.MATHEMATICS             -> "curriculum/mathematics_curriculum.json"
            Subject.ENGLISH,
            Subject.LANGUAGE_ARTS           -> "curriculum/english_curriculum.json"
            Subject.HISTORY                 -> "curriculum/history_curriculum.json"
            Subject.GEOGRAPHY               -> "curriculum/geography_curriculum.json"
            Subject.ECONOMICS               -> "curriculum/economics_curriculum.json"
            Subject.COMPUTER_SCIENCE        -> "curriculum/computer_science_curriculum.json"
            else                            -> return@withContext emptyList()
        }

        try {
            val jsonString = context.assets
                .open(fileName)                       // ✅ finds the file now
                .bufferedReader()
                .use { it.readText() }

            val topics = mutableListOf<CurriculumTopic>()
            when (subject) {
                Subject.SCIENCE          -> parseScience(jsonString, gradeLevel, topics)
                Subject.MATHEMATICS      -> parseMathematics(jsonString, gradeLevel, topics)
                Subject.ENGLISH,
                Subject.LANGUAGE_ARTS    -> parseEnglish(jsonString, gradeLevel, topics)
                Subject.HISTORY          -> parseHistory(jsonString, gradeLevel, topics)
                Subject.GEOGRAPHY        -> parseGeography(jsonString, gradeLevel, topics)
                Subject.ECONOMICS        -> parseEconomics(jsonString, gradeLevel, topics)
                Subject.COMPUTER_SCIENCE -> parseComputerScience(jsonString, gradeLevel, topics)
                else -> Unit
            }

            // Filter topics by country if specified
            val filteredTopics = if (country != null) {
                topics.filter { topic ->
                    // Include topic if:
                    // 1. No countries specified (applies to all)
                    // 2. Country is in the topic's countries list
                    // 3. Topic has country-specific variations for this country
                    topic.countries.isEmpty() || 
                    topic.countries.contains(country) ||
                    topic.countrySpecific.containsKey(country)
                }
            } else {
                topics
            }

            Timber.d("Loaded ${filteredTopics.size} topics for $subject grade $gradeLevel" + 
                    if (country != null) " (filtered for $country)" else "")
            filteredTopics
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum for $subject")
            emptyList()
        }
    }


    private fun parseScience(jsonString: String, gradeLevel: Int, topics: MutableList<CurriculumTopic>) {
        val json = JSONObject(jsonString)

        when (gradeLevel) {
            0 -> addTopicsFromArray(json, "PYP", "Phase 1 (KG)", topics)
            1, 2 -> addTopicsFromArray(json, "PYP", "Phase 2 (Grades 1-2)", topics)
            3, 4 -> addTopicsFromArray(json, "PYP", "Phase 3 (Grades 3-4)", topics)
            5 -> addTopicsFromArray(json, "PYP", "Phase 4 (Grades 5-6)", topics)  // Only grade 5
            6 -> addTopicsFromArray(json, "MYP", "MYP 1 (Grade 6)", topics)      // Now reachable
            7 -> addTopicsFromArray(json, "MYP", "MYP 2 (Grade 7)", topics)
            8 -> addTopicsFromArray(json, "MYP", "MYP 3 (Grade 8)", topics)
            9, 10 -> {
                // For grades 9-10, get topics from Biology, Chemistry, Physics
                val myp45 = json.getJSONObject("MYP").getJSONObject("MYP 4–5 (Grades 9–10)")
                listOf("Biology", "Chemistry", "Physics").forEach { subject ->
                    if (myp45.has(subject)) {
                        val subjectTopics = myp45.getJSONArray(subject)
                        for (i in 0 until subjectTopics.length()) {
                            topics.add(CurriculumTopic(
                                title = subjectTopics.getString(i),
                                gradeRange = "Grades 9-10",
                                phase = "MYP 4-5 $subject"
                            ))
                        }
                    }
                }
            }
            11, 12 -> {
                // DP topics
                val dp = json.getJSONObject("DP")
                listOf("Biology", "Chemistry", "Physics").forEach { subject ->
                    if (dp.has(subject)) {
                        val subjectTopics = dp.getJSONArray(subject)
                        for (i in 0 until subjectTopics.length()) {
                            topics.add(CurriculumTopic(
                                title = subjectTopics.getString(i),
                                gradeRange = "Grades 11-12",
                                phase = "DP $subject"
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun parseMathematics(jsonString: String, gradeLevel: Int, topics: MutableList<CurriculumTopic>) {
        val json = JSONObject(jsonString)

        when (gradeLevel) {
            0 -> addTopicsFromArray(json, "PYP", "Phase 1 (KG)", topics)
            1, 2 -> addTopicsFromArray(json, "PYP", "Phase 2 (Grades 1-2)", topics)
            3, 4 -> addTopicsFromArray(json, "PYP", "Phase 3 (Grades 3-4)", topics)
            5 -> addTopicsFromArray(json, "PYP", "Phase 4 (Grades 5-6)", topics)  // Only grade 5
            6 -> addTopicsFromArray(json, "MYP", "MYP 1 (Grade 6)", topics)      // Now reachable
            7 -> addTopicsFromArray(json, "MYP", "MYP 2 (Grade 7)", topics)
            8 -> addTopicsFromArray(json, "MYP", "MYP 3 (Grade 8)", topics)
            9 -> addTopicsFromArray(json, "MYP", "MYP 4 (Grade 9)", topics)
            10 -> addTopicsFromArray(json, "MYP", "MYP 5 (Grade 10)", topics)
            11, 12 -> {
                val dp = json.getJSONObject("DP")
                listOf("AA (Analysis & Approaches)", "AI (Applications & Interpretation)").forEach { course ->
                    if (dp.has(course)) {
                        val courseTopics = dp.getJSONArray(course)
                        for (i in 0 until courseTopics.length()) {
                            topics.add(CurriculumTopic(
                                title = courseTopics.getString(i),
                                gradeRange = "Grades 11-12",
                                phase = "DP $course"
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun parseEnglish(jsonString: String, gradeLevel: Int, topics: MutableList<CurriculumTopic>) {
        val json = JSONObject(jsonString).getJSONObject("programme")

        when (gradeLevel) {
            0, 1, 2, 3, 4, 5 -> {
                // PYP - get strands and phases
                val pyp = json.getJSONObject("PYP")
                val strands = pyp.getJSONObject("strands")

                strands.keys().forEach { strand ->
                    val phases = strands.getJSONObject(strand)
                    val phase = when (gradeLevel) {
                        0, 1 -> "Phase 1"
                        2 -> "Phase 2"
                        3 -> "Phase 3"
                        4 -> "Phase 4"
                        else -> "Phase 5"
                    }

                    if (phases.has(phase)) {
                        topics.add(CurriculumTopic(
                            title = "$strand - ${phases.getString(phase).take(50)}...",
                            gradeRange = "Grade $gradeLevel",
                            phase = "PYP $phase"
                        ))
                    }
                }
            }
            6, 7, 8, 9, 10 -> {
                // MYP units
                val myp = json.getJSONObject("MYP")
                val units = myp.getJSONObject("units")
                val gradeKey = "Grade $gradeLevel"

                if (units.has(gradeKey)) {
                    val gradeUnits = units.getJSONArray(gradeKey)
                    for (i in 0 until gradeUnits.length()) {
                        topics.add(CurriculumTopic(
                            title = gradeUnits.getString(i),
                            gradeRange = gradeKey,
                            phase = "MYP"
                        ))
                    }
                }
            }
            11, 12 -> {
                // DP themes
                val dp = json.getJSONObject("DP")
                if (dp.has("Language A")) {
                    val langA = dp.getJSONObject("Language A")
                    langA.keys().forEach { course ->
                        val courseData = langA.getJSONObject(course)
                        topics.add(CurriculumTopic(
                            title = "$course - ${courseData.getString("themes")}",
                            gradeRange = "Grades 11-12",
                            phase = "DP Language A"
                        ))
                    }
                }
            }
        }
    }

    private fun parseHistory(jsonString: String, gradeLevel: Int, topics: MutableList<CurriculumTopic>) {
        val json = JSONObject(jsonString)

        when (gradeLevel) {
            0 -> addTopicsFromArray(json, "PYP", "Phase 1 (KG)", topics)
            1, 2 -> addTopicsFromArray(json, "PYP", "Phase 2 (Grades 1-2)", topics)
            3, 4 -> addTopicsFromArray(json, "PYP", "Phase 3 (Grades 3-4)", topics)
            5 -> addTopicsFromArray(json, "PYP", "Phase 4 (Grade 5)", topics)

            6 -> addTopicsFromArray(json, "MYP", "MYP 1 (Grade 6)", topics)
            7 -> addTopicsFromArray(json, "MYP", "MYP 2 (Grade 7)", topics)
            8 -> addTopicsFromArray(json, "MYP", "MYP 3 (Grade 8)", topics)

            9, 10 -> {
                val myp45 = json.getJSONObject("MYP").getJSONObject("MYP 4–5 (Grades 9–10)")
                val yearKey = if (gradeLevel == 9) "Year 4 Topics" else "Year 5 Topics"
                if (myp45.has(yearKey)) {
                    val topicsArray = myp45.getJSONArray(yearKey)
                    for (i in 0 until topicsArray.length()) {
                        topics.add(CurriculumTopic(
                            title = topicsArray.getString(i),
                            gradeRange = "Grade $gradeLevel",
                            phase = "MYP $yearKey"
                        ))
                    }
                }
            }

            11, 12 -> {
                val dp = json.getJSONObject("DP")

                val prescribed = dp.optJSONArray("Prescribed Subjects") ?: JSONArray()
                for (i in 0 until prescribed.length()) {
                    topics.add(CurriculumTopic(
                        title = prescribed.getString(i),
                        gradeRange = "Grades 11-12",
                        phase = "DP Prescribed Subject"
                    ))
                }

                val world = dp.optJSONArray("World History Topics") ?: JSONArray()
                for (i in 0 until world.length()) {
                    topics.add(CurriculumTopic(
                        title = world.getString(i),
                        gradeRange = "Grades 11-12",
                        phase = "DP World History"
                    ))
                }

                val hl = dp.optJSONArray("Higher Level Options") ?: JSONArray()
                for (i in 0 until hl.length()) {
                    topics.add(CurriculumTopic(
                        title = hl.getString(i),
                        gradeRange = "Grades 11-12",
                        phase = "DP HL Option"
                    ))
                }

                val ia = dp.optJSONArray("Internal Assessment") ?: JSONArray()
                for (i in 0 until ia.length()) {
                    topics.add(CurriculumTopic(
                        title = ia.getString(i),
                        gradeRange = "Grades 11-12",
                        phase = "DP IA"
                    ))
                }
            }
        }
    }

    private fun parseGeography(jsonString: String, gradeLevel: Int, topics: MutableList<CurriculumTopic>) {
        val json = JSONObject(jsonString)

        when (gradeLevel) {
            0 -> addTopicsFromArray(json, "PYP", "Phase 1 (KG)", topics)
            1, 2 -> addTopicsFromArray(json, "PYP", "Phase 2 (Grades 1-2)", topics)
            3, 4 -> addTopicsFromArray(json, "PYP", "Phase 3 (Grades 3-4)", topics)
            5 -> addTopicsFromArray(json, "PYP", "Phase 4 (Grades 5-6)", topics)  // Only grade 5
            6 -> addTopicsFromArray(json, "MYP", "MYP 1 (Grade 6)", topics)      // Now reachable
            7 -> addTopicsFromArray(json, "MYP", "MYP 2 (Grade 7)", topics)
            8 -> addTopicsFromArray(json, "MYP", "MYP 3 (Grade 8)", topics)
            9, 10 -> {
                // Handle MYP 4-5 with nested structure
                val myp = json.getJSONObject("MYP")
                if (myp.has("MYP 4–5 (Grades 9–10)")) {
                    val myp45 = myp.getJSONObject("MYP 4–5 (Grades 9–10)")
                    if (myp45.has("Topics")) {
                        val topicsArray = myp45.getJSONArray("Topics")
                        for (i in 0 until topicsArray.length()) {
                            topics.add(CurriculumTopic(
                                title = topicsArray.getString(i),
                                gradeRange = "Grades 9-10",
                                phase = "MYP 4-5"
                            ))
                        }
                    }
                }
            }
            11, 12 -> {
                // DP Geography topics - handle nested structure properly
                val dp = json.getJSONObject("DP")

                // Core themes
                if (dp.has("Core")) {
                    val coreArray = dp.getJSONArray("Core")
                    for (i in 0 until coreArray.length()) {
                        topics.add(CurriculumTopic(
                            title = coreArray.getString(i),
                            gradeRange = "Grades 11-12",
                            phase = "DP Core"
                        ))
                    }
                }

                // Optional themes - handle nested object structure
                if (dp.has("Optional Themes")) {
                    val optionalThemes = dp.getJSONObject("Optional Themes")
                    optionalThemes.keys().forEach { themeKey ->
                        val themeArray = optionalThemes.getJSONArray(themeKey)
                        for (i in 0 until themeArray.length()) {
                            topics.add(CurriculumTopic(
                                title = "$themeKey: ${themeArray.getString(i)}",
                                gradeRange = "Grades 11-12",
                                phase = "DP Optional Theme"
                            ))
                        }
                    }
                }

                // HL Extension - handle nested object structure
                if (dp.has("HL Extension")) {
                    val hlExtension = dp.getJSONObject("HL Extension")
                    hlExtension.keys().forEach { extensionKey ->
                        val extensionArray = hlExtension.getJSONArray(extensionKey)
                        for (i in 0 until extensionArray.length()) {
                            topics.add(CurriculumTopic(
                                title = "$extensionKey: ${extensionArray.getString(i)}",
                                gradeRange = "Grades 11-12",
                                phase = "DP HL Extension"
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun parseEconomics(jsonString: String, gradeLevel: Int, topics: MutableList<CurriculumTopic>) {
        val json = JSONObject(jsonString)

        when (gradeLevel) {
            0 -> addTopicsFromArray(json, "PYP", "Phase 1 (KG)", topics)
            1, 2 -> addTopicsFromArray(json, "PYP", "Phase 2 (Grades 1-2)", topics)
            3, 4 -> addTopicsFromArray(json, "PYP", "Phase 3 (Grades 3-4)", topics)
            5 -> addTopicsFromArray(json, "PYP", "Phase 4 (Grades 5-6)", topics)  // Only grade 5
            6 -> addTopicsFromArray(json, "MYP", "MYP 1 (Grade 6)", topics)      // Now reachable
            7 -> addTopicsFromArray(json, "MYP", "MYP 2 (Grade 7)", topics)
            8 -> addTopicsFromArray(json, "MYP", "MYP 3 (Grade 8)", topics)
            9, 10 -> {
                // Handle MYP 4-5 with nested Topics structure
                val myp = json.getJSONObject("MYP")
                if (myp.has("MYP 4–5 (Grades 9–10)")) {
                    val myp45 = myp.getJSONObject("MYP 4–5 (Grades 9–10)")
                    if (myp45.has("Topics")) {
                        val topicsArray = myp45.getJSONArray("Topics")
                        for (i in 0 until topicsArray.length()) {
                            topics.add(CurriculumTopic(
                                title = topicsArray.getString(i),
                                gradeRange = "Grades 9-10",
                                phase = "MYP 4-5"
                            ))
                        }
                    }
                }
            }
            11, 12 -> {
                // DP Economics units
                val dp = json.getJSONObject("DP")

                // Handle each unit as a separate section
                listOf(
                    "Unit 1: Introduction to Economics",
                    "Unit 2: Microeconomics",
                    "Unit 3: Macroeconomics",
                    "Unit 4: The Global Economy",
                    "Internal Assessment"
                ).forEach { unitKey ->
                    if (dp.has(unitKey)) {
                        val unitArray = dp.getJSONArray(unitKey)
                        for (i in 0 until unitArray.length()) {
                            topics.add(CurriculumTopic(
                                title = "${unitKey.substringBefore(":")} - ${unitArray.getString(i)}",
                                gradeRange = "Grades 11-12",
                                phase = "DP Economics"
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun parseComputerScience(jsonString: String, gradeLevel: Int, topics: MutableList<CurriculumTopic>) {
        val json = JSONObject(jsonString)

        when (gradeLevel) {
            0 -> addTopicsFromArray(json, "PYP", "Phase 1 (KG)", topics)
            1, 2 -> addTopicsFromArray(json, "PYP", "Phase 2 (Grades 1–2)", topics)
            3, 4 -> addTopicsFromArray(json, "PYP", "Phase 3 (Grades 3–4)", topics)
            5 -> addTopicsFromArray(json, "PYP", "Phase 4 (Grades 5–6)", topics)
            6 -> addTopicsFromArray(json, "MYP", "MYP 1 (Grade 6)", topics)
            7 -> addTopicsFromArray(json, "MYP", "MYP 2 (Grade 7)", topics)
            8 -> addTopicsFromArray(json, "MYP", "MYP 3 (Grade 8)", topics)
            9, 10 -> {
                // Handle MYP 4-5 with nested Topics structure
                val myp = json.getJSONObject("MYP")
                if (myp.has("MYP 4–5 (Grades 9–10)")) {
                    val myp45 = myp.getJSONObject("MYP 4–5 (Grades 9–10)")
                    if (myp45.has("Topics")) {
                        val topicsArray = myp45.getJSONArray("Topics")
                        for (i in 0 until topicsArray.length()) {
                            topics.add(CurriculumTopic(
                                title = topicsArray.getString(i),
                                gradeRange = "Grades 9-10",
                                phase = "MYP 4-5"
                            ))
                        }
                    }
                }
            }
            11, 12 -> {
                // DP Computer Science themes
                val dp = json.getJSONObject("DP")

                // Theme A - Concepts of computer science
                if (dp.has("Theme A – Concepts of computer science")) {
                    val themeA = dp.getJSONArray("Theme A – Concepts of computer science")
                    for (i in 0 until themeA.length()) {
                        topics.add(CurriculumTopic(
                            title = "Theme A - ${themeA.getString(i)}",
                            gradeRange = "Grades 11-12",
                            phase = "DP Computer Science"
                        ))
                    }
                }

                // Theme B - Computational thinking & problem-solving
                if (dp.has("Theme B – Computational thinking & problem-solving")) {
                    val themeB = dp.getJSONArray("Theme B – Computational thinking & problem-solving")
                    for (i in 0 until themeB.length()) {
                        topics.add(CurriculumTopic(
                            title = "Theme B - ${themeB.getString(i)}",
                            gradeRange = "Grades 11-12",
                            phase = "DP Computer Science"
                        ))
                    }
                }

                // Practical programme
                if (dp.has("Practical programme")) {
                    val practical = dp.getJSONArray("Practical programme")
                    for (i in 0 until practical.length()) {
                        topics.add(CurriculumTopic(
                            title = "Practical - ${practical.getString(i)}",
                            gradeRange = "Grades 11-12",
                            phase = "DP Computer Science"
                        ))
                    }
                }
            }
        }
    }

    private fun addTopicsFromArray(
        json: JSONObject,
        program: String,
        phase: String,
        topics: MutableList<CurriculumTopic>
    ) {
        try {
            val programObj = json.getJSONObject(program)
            if (programObj.has(phase)) {
                when (val phaseData = programObj.get(phase)) {
                    is JSONArray -> {
                        for (i in 0 until phaseData.length()) {
                            val topicText = phaseData.getString(i)
                            // Handle topics with subtopics (e.g., "Forces - Push")
                            if (topicText.contains(" - ")) {
                                val parts = topicText.split(" - ")
                                val mainTopic = parts[0]
                                val subtopic = parts[1]

                                // Find or create main topic
                                val existing = topics.find { it.title == mainTopic }
                                if (existing != null) {
                                    // Add subtopic to existing
                                    topics[topics.indexOf(existing)] = existing.copy(
                                        subtopics = existing.subtopics + subtopic
                                    )
                                } else {
                                    topics.add(CurriculumTopic(
                                        title = mainTopic,
                                        gradeRange = phase,
                                        phase = program,
                                        subtopics = listOf(subtopic)
                                    ))
                                }
                            } else {
                                topics.add(CurriculumTopic(
                                    title = topicText,
                                    gradeRange = phase,
                                    phase = program
                                ))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing topics for $program/$phase")
        }
    }

    /**
     * Generate a single curriculum-based question
     */
    private suspend fun generateSingleCurriculumQuestion(
        subject: Subject,
        topic: CurriculumTopic,
        gradeLevel: Int,
        questionType: QuestionType,
        difficulty: Difficulty,
        country: String? = null,
        previousQuestions: List<String>,
        attemptNumber: Int
    ): Question {
        
        // Try multiple times with similarity checking
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val prompt = createCurriculumPrompt(
                    subject = subject,
                    topic = topic,
                    gradeLevel = gradeLevel,
                    questionType = questionType,
                    difficulty = difficulty,
                    country = country,
                    previousQuestions = previousQuestions,
                    attemptNumber = attemptNumber + attempt
                )
                
                // Log prompt length to help debug truncation issues
                Timber.d("Generated prompt length: ${prompt.length} characters")

                val response = try {
                    gemmaService.generateTextAsync(
                        prompt,
                        UnifiedGemmaService.GenerationConfig(
                            maxTokens = 500,
                            temperature = 0.7f + ((attemptNumber + attempt) * 0.05f),
                            topK = 40 + (attempt * 10)
                        )
                    )
                } catch (e: Exception) {
                    // Handle MediaPipe session corruption
                    if (e.message?.contains("timestamp mismatch") == true || 
                        e.message?.contains("INVALID_ARGUMENT") == true) {
                        Timber.w("MediaPipe session corruption detected, using fallback")
                        throw Exception("Session corruption - will use fallback")
                    }
                    throw e
                }

                Timber.d("Received AI response length: ${response.length} characters")
                val question = parseQuestionResponse(response, questionType, difficulty, gradeLevel)
                Timber.d("Successfully parsed question: ${question.questionText.take(50)}...")
                
                // Check similarity against previous questions
                val similarities = previousQuestions.map { prev ->
                    calculateSimilarity(question.questionText.lowercase(), prev.lowercase())
                }
                val maxSimilarity = similarities.maxOfOrNull { it } ?: 0f
                // Use a more lenient threshold for curriculum questions since they're topic-specific
                // and allow more similarity, especially for foundational concepts
                val similarityThreshold = when (attempt) {
                    0 -> 0.85f  // First attempt: strict
                    1 -> 0.90f  // Second attempt: more lenient
                    else -> 0.95f  // Final attempt: very lenient
                }
                val isTooSimilar = maxSimilarity > similarityThreshold
                
                if (!isTooSimilar) {
                    Timber.d("Curriculum question accepted, max similarity: $maxSimilarity (threshold: $similarityThreshold)")
                    return question
                } else {
                    Timber.w("Curriculum question too similar ($maxSimilarity > $similarityThreshold), attempt ${attempt + 1}")
                    lastError = Exception("Question too similar: $maxSimilarity")
                }
                
            } catch (e: Exception) {
                lastError = e
                Timber.w("Curriculum question generation attempt ${attempt + 1} failed: ${e.message}")
            }
        }
        
        // If all attempts failed, use fallback
        Timber.e(lastError, "All curriculum question attempts failed, using fallback")
        return generateCurriculumFallback(subject, topic, gradeLevel, questionType, difficulty, country)
    }

    /**
     * Create a grade and curriculum aware prompt
     */
    private fun createCurriculumPrompt(
        subject: Subject,
        topic: CurriculumTopic,
        gradeLevel: Int,
        questionType: QuestionType,
        difficulty: Difficulty,
        country: String? = null,
        previousQuestions: List<String>,
        attemptNumber: Int
    ): String {

        val gradeContext = getGradeContext(gradeLevel)
        
        // Handle country-specific topic variations
        val topicDescription = if (country != null && topic.countrySpecific.containsKey(country)) {
            topic.countrySpecific[country] ?: topic.title
        } else if (topic.subtopics.isNotEmpty()) {
            "${topic.title} (specifically: ${topic.subtopics.random()})"
        } else {
            topic.title
        }
        
        // Add country context for relevant subjects
        val countryContext = if (country != null && (subject == Subject.HISTORY || subject == Subject.GEOGRAPHY)) {
            val context = getCountryContext(country, subject)
            Timber.d("Country context for $country in $subject: ${context.take(100)}...")
            context
        } else {
            Timber.d("No country context applied for country=$country, subject=$subject")
            null
        }

        val typeInstructions = getQuestionTypeInstructions(questionType, gradeLevel)
        val example = getGradeAppropriateExample(questionType, subject, gradeLevel)
        
        // Add variety based on attempt number to reduce similarity
        val approachVariations = listOf(
            "Focus on practical applications and real-world examples",
            "Emphasize conceptual understanding and 'why' questions",
            "Use creative scenarios and storytelling elements", 
            "Connect to current events or popular culture",
            "Focus on problem-solving and critical thinking",
            "Use analogies and comparisons to familiar concepts"
        )
        
        val questionStyles = listOf(
            "analytical and thoughtful",
            "creative and imaginative", 
            "practical and application-focused",
            "exploratory and discovery-based",
            "comparative and contrasting",
            "scenario-based and contextual"
        )
        
        val selectedApproach = approachVariations[attemptNumber % approachVariations.size]
        val selectedStyle = questionStyles[attemptNumber % questionStyles.size]

        val prompt = """
            Create a $selectedStyle $questionType question for a Grade $gradeLevel student.
            
            Subject: $subject
            Topic: $topicDescription
            Curriculum Phase: ${topic.phase}
            Difficulty: $difficulty
            Approach: $selectedApproach
            
            GRADE CONTEXT:
            $gradeContext
            ${countryContext?.let { "\n            COUNTRY CONTEXT:\n            $it" } ?: ""}
            
            INSTRUCTIONS:
            $typeInstructions
            
            EXAMPLE FORMAT:
            $example
            
            IMPORTANT:
            - Use age-appropriate vocabulary for grade $gradeLevel
            - Question must be about the specific topic: $topicDescription
            - Make it engaging and relevant to students of this age
            - Use a $selectedStyle approach with focus on: $selectedApproach
            - Create something completely different from these previous questions: ${previousQuestions.takeLast(3).joinToString("; ") { "\"${it.take(25)}...\"" }}
            
            Generate the question in valid JSON format:
        """.trimIndent()
        
        // Log prompt preview for debugging
        if (country != null) {
            Timber.d("Prompt preview (country=$country): ${prompt.take(300)}...")
        }
        
        return prompt
    }

    /**
     * Generate curriculum-aware fallback questions
     */
    fun generateCurriculumFallback(
        subject: Subject,
        topic: CurriculumTopic,
        gradeLevel: Int,
        questionType: QuestionType,
        difficulty: Difficulty,
        country: String? = null
    ): Question {

        // Create grade and subject appropriate fallbacks
        val fallbackPool = when (subject) {
            Subject.MATHEMATICS -> getMathFallbacks(gradeLevel, topic.title, country)
            Subject.SCIENCE -> getScienceFallbacks(gradeLevel, topic.title, country)
            Subject.ENGLISH, Subject.LANGUAGE_ARTS -> getEnglishFallbacks(gradeLevel, topic.title, country)
            Subject.HISTORY -> getHistoryFallbacks(gradeLevel, topic.title, country)
            Subject.GEOGRAPHY -> getGeographyFallbacks(gradeLevel, topic.title, country)
            Subject.ECONOMICS -> getEconomicsFallbacks(gradeLevel, topic.title, country)
            else -> getGeneralFallbacks(gradeLevel, country)
        }

        val selectedFallback = fallbackPool.filter { it.questionType == questionType }
            .randomOrNull() ?: fallbackPool.random()

        return selectedFallback.copy(
            difficulty = difficulty,
            conceptsCovered = listOf(topic.title.lowercase().replace(" ", "-"))
        )
    }

    // Helper functions

    private fun getGradeContext(gradeLevel: Int): String {
        return when (gradeLevel) {
            0, 1, 2 -> "Very young students (ages 5-7): Use simple words, concrete examples, and familiar objects."
            3, 4, 5 -> "Elementary students (ages 8-10): Can handle basic concepts but keep language clear."
            6, 7, 8 -> "Middle school (ages 11-13): Can understand more abstract concepts."
            9, 10 -> "High school (ages 14-15): Can handle complex ideas and technical vocabulary."
            11, 12 -> "Senior high school (ages 16-18): Preparing for college-level work."
            else -> "Adjust complexity appropriately."
        }
    }

    private fun selectQuestionTypesForGrade(gradeLevel: Int, count: Int): List<QuestionType> {
        val types = when (gradeLevel) {
            in 0..2 -> {
                // Young kids: mostly multiple choice and true/false
                listOf(
                    QuestionType.MULTIPLE_CHOICE,
                    QuestionType.MULTIPLE_CHOICE,
                    QuestionType.TRUE_FALSE,
                    QuestionType.TRUE_FALSE
                )
            }
            in 3..5 -> {
                // Elementary: add some fill in blank
                listOf(
                    QuestionType.MULTIPLE_CHOICE,
                    QuestionType.TRUE_FALSE,
                    QuestionType.FILL_IN_BLANK,
                    QuestionType.MULTIPLE_CHOICE
                )
            }
            in 6..8 -> {
                // Middle school: more variety
                listOf(
                    QuestionType.MULTIPLE_CHOICE,
                    QuestionType.TRUE_FALSE,
                    QuestionType.FILL_IN_BLANK,
                    QuestionType.SHORT_ANSWER
                )
            }
            else -> {
                // High school: all types
                QuestionType.values().toList()
            }
        }

        // Create a list of the requested count by cycling through types
        return List(count) { types[it % types.size] }
    }

    private fun adjustDifficultyForGrade(baseDifficulty: Difficulty, gradeLevel: Int): Difficulty {
        return when {
            gradeLevel <= 2 -> Difficulty.EASY // Always easy for very young
            gradeLevel <= 5 && baseDifficulty == Difficulty.HARD -> Difficulty.MEDIUM // Cap at medium
            gradeLevel >= 9 && baseDifficulty == Difficulty.EASY -> Difficulty.MEDIUM // Min medium for high school
            else -> baseDifficulty
        }
    }

    private fun getQuestionTypeInstructions(questionType: QuestionType, gradeLevel: Int): String {
        val baseInstructions = when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> "Create a multiple choice question with 4 options"
            QuestionType.TRUE_FALSE -> "Create a true/false statement"
            QuestionType.FILL_IN_BLANK -> "Create a sentence with one blank to fill"
            QuestionType.SHORT_ANSWER -> "Create an open-ended question requiring a brief answer"
            else -> "Create an appropriate question"
        }

        val gradeAddition = when {
            gradeLevel <= 2 -> "\nUse very simple language and familiar examples."
            gradeLevel <= 5 -> "\nUse clear, simple language."
            gradeLevel <= 8 -> "\nUse grade-appropriate vocabulary."
            else -> "\nCan use technical terms with explanation."
        }

        return baseInstructions + gradeAddition
    }

    private fun getGradeAppropriateExample(
        questionType: QuestionType,
        subject: Subject,
        gradeLevel: Int
    ): String {
        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> {
                when {
                    gradeLevel <= 2 -> """
                        {
                            "question": "How many legs does a cat have?",
                            "options": ["2 legs", "3 legs", "4 legs", "6 legs"],
                            "correctAnswer": "4 legs",
                            "explanation": "Cats have 4 legs to walk and run!"
                        }
                    """.trimIndent()
                    gradeLevel <= 5 -> """
                        {
                            "question": "What do plants need to grow?",
                            "options": ["Only water", "Only sunlight", "Water, sunlight, and air", "Only soil"],
                            "correctAnswer": "Water, sunlight, and air",
                            "explanation": "Plants need all three to make their food and grow."
                        }
                    """.trimIndent()
                    else -> """
                        {
                            "question": "Which process converts light energy into chemical energy?",
                            "options": ["Respiration", "Photosynthesis", "Fermentation", "Digestion"],
                            "correctAnswer": "Photosynthesis",
                            "explanation": "Photosynthesis uses sunlight to create glucose in plants."
                        }
                    """.trimIndent()
                }
            }
            QuestionType.TRUE_FALSE -> {
                when {
                    gradeLevel <= 2 -> """
                        {
                            "question": "The sun is hot.",
                            "options": ["True", "False"],
                            "correctAnswer": "True",
                            "explanation": "The sun is very hot and gives us light!"
                        }
                    """.trimIndent()
                    else -> """
                        {
                            "question": "All mammals lay eggs.",
                            "options": ["True", "False"],
                            "correctAnswer": "False",
                            "explanation": "Most mammals give birth to live young."
                        }
                    """.trimIndent()
                }
            }
            else -> """
                {
                    "question": "Sample question",
                    "options": [],
                    "correctAnswer": "Sample answer",
                    "explanation": "Sample explanation"
                }
            """.trimIndent()
        }
    }

    private fun parseQuestionResponse(
        response: String,
        questionType: QuestionType,
        difficulty: Difficulty,
        gradeLevel: Int
    ): Question {
        // Use your existing parsing logic but add grade validation
        try {
            val cleaned = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Fix common JSON issues
            val sanitized = sanitizeAndFixJson(cleaned)
            val json = JSONObject(sanitized)

            var questionText = json.getString("question")
            val correctAnswer = json.getString("correctAnswer")
            val explanation = json.optString("explanation", "")

            // Validate question is grade appropriate (basic check)
            if (gradeLevel <= 2 && questionText.length > 50) {
                questionText = questionText.take(50) + "?"
            }

            val options = when (questionType) {
                QuestionType.MULTIPLE_CHOICE -> {
                    val optArray = json.getJSONArray("options")
                    List(optArray.length()) { optArray.getString(it) }
                }
                QuestionType.TRUE_FALSE -> listOf("True", "False")
                else -> emptyList()
            }

            return Question(
                questionText = questionText,
                questionType = questionType,
                options = options,
                correctAnswer = correctAnswer,
                explanation = explanation,
                difficulty = difficulty,
                conceptsCovered = listOf("curriculum-topic")
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse curriculum question, response preview: ${response.take(200)}...")
            
            // Attempt to extract basic info from malformed response
            return tryParseFromMalformedResponse(response, questionType, difficulty).also { fallback ->
                if (fallback != null) {
                    Timber.i("Successfully recovered question using fallback parsing")
                } else {
                    Timber.e("Complete parsing failure - will throw exception")
                }
            } ?: throw Exception("Could not parse response after all attempts: ${e.message}")
        }
    }

    /**
     * Attempt to parse essential information from malformed AI responses
     */
    private fun tryParseFromMalformedResponse(
        response: String, 
        questionType: QuestionType, 
        difficulty: Difficulty
    ): Question? {
        return try {
            // Extract question text using regex patterns
            val questionPattern = "\"question\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val questionMatch = questionPattern.find(response)
            val questionText = questionMatch?.groupValues?.get(1) ?: return null
            
            // Extract correct answer
            val answerPattern = "\"correctAnswer\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val answerMatch = answerPattern.find(response)
            val correctAnswer = answerMatch?.groupValues?.get(1) ?: return null
            
            // Extract explanation (optional)
            val explanationPattern = "\"explanation\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val explanationMatch = explanationPattern.find(response)
            val explanation = explanationMatch?.groupValues?.get(1) ?: "Generated from partial response"
            
            // Extract options for multiple choice
            val options = when (questionType) {
                QuestionType.MULTIPLE_CHOICE -> {
                    val optionsPattern = "\"options\"\\s*:\\s*\\[([^\\]]+)\\]".toRegex()
                    val optionsMatch = optionsPattern.find(response)
                    optionsMatch?.groupValues?.get(1)?.let { optionsStr ->
                        optionsStr.split(",")
                            .map { it.trim().removePrefix("\"").removeSuffix("\"") }
                            .filter { it.isNotBlank() }
                    } ?: listOf(correctAnswer, "Option 1", "Option 2", "Option 3")
                }
                QuestionType.TRUE_FALSE -> listOf("True", "False")
                else -> emptyList()
            }
            
            Question(
                questionText = questionText,
                questionType = questionType,
                options = options,
                correctAnswer = correctAnswer,
                explanation = explanation,
                difficulty = difficulty,
                conceptsCovered = listOf("curriculum-topic-fallback")
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse from malformed response")
            null
        }
    }

    // Subject-specific fallback pools

    private fun getMathFallbacks(gradeLevel: Int, topic: String, country: String? = null): List<Question> {
        return when {
            gradeLevel <= 2 -> listOf(
                Question(
                    questionText = "What is 3 + 2?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("4", "5", "6", "7"),
                    correctAnswer = "5",
                    explanation = "When we add 3 and 2, we get 5!",
                    difficulty = Difficulty.EASY
                ),
                Question(
                    questionText = "A triangle has 3 sides.",
                    questionType = QuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "Triangles always have 3 sides!",
                    difficulty = Difficulty.EASY
                )
            )
            gradeLevel <= 5 -> listOf(
                Question(
                    questionText = "What is 7 × 8?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("54", "56", "58", "60"),
                    correctAnswer = "56",
                    explanation = "7 × 8 = 56",
                    difficulty = Difficulty.MEDIUM
                ),
                Question(
                    questionText = "12 ÷ 3 = _____",
                    questionType = QuestionType.FILL_IN_BLANK,
                    options = emptyList(),
                    correctAnswer = "4",
                    explanation = "12 divided by 3 equals 4",
                    difficulty = Difficulty.MEDIUM
                )
            )
            else -> listOf(
                Question(
                    questionText = "Solve for x: 2x + 5 = 13",
                    questionType = QuestionType.SHORT_ANSWER,
                    options = emptyList(),
                    correctAnswer = "x = 4",
                    explanation = "Subtract 5 from both sides, then divide by 2",
                    difficulty = Difficulty.MEDIUM
                )
            )
        }
    }

    private fun getScienceFallbacks(gradeLevel: Int, topic: String, country: String? = null): List<Question> {
        return when {
            gradeLevel <= 2 -> listOf(
                Question(
                    questionText = "What do we use to see things?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Our nose", "Our eyes", "Our ears", "Our mouth"),
                    correctAnswer = "Our eyes",
                    explanation = "We use our eyes to see!",
                    difficulty = Difficulty.EASY
                ),
                Question(
                    questionText = "Plants need water to grow.",
                    questionType = QuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "Plants need water, just like we do!",
                    difficulty = Difficulty.EASY
                )
            )
            gradeLevel <= 5 -> listOf(
                Question(
                    questionText = "What are the three states of matter?",
                    questionType = QuestionType.SHORT_ANSWER,
                    options = emptyList(),
                    correctAnswer = "Solid, liquid, and gas",
                    explanation = "Matter can be solid (like ice), liquid (like water), or gas (like steam)",
                    difficulty = Difficulty.MEDIUM
                )
            )
            else -> listOf(
                Question(
                    questionText = "What is the chemical formula for water?",
                    questionType = QuestionType.FILL_IN_BLANK,
                    options = emptyList(),
                    correctAnswer = "H2O",
                    explanation = "Water is made of 2 hydrogen atoms and 1 oxygen atom",
                    difficulty = Difficulty.EASY
                )
            )
        }
    }

    private fun getEnglishFallbacks(gradeLevel: Int, topic: String, country: String? = null): List<Question> {
        return when {
            gradeLevel <= 2 -> listOf(
                Question(
                    questionText = "Which word rhymes with 'cat'?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("dog", "hat", "bird", "fish"),
                    correctAnswer = "hat",
                    explanation = "Cat and hat both end with 'at' sound!",
                    difficulty = Difficulty.EASY
                )
            )
            gradeLevel <= 5 -> listOf(
                Question(
                    questionText = "A sentence always starts with a capital letter.",
                    questionType = QuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "Every sentence begins with a capital letter!",
                    difficulty = Difficulty.EASY
                )
            )
            else -> listOf(
                Question(
                    questionText = "What type of figurative language is: 'The stars danced in the sky'?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Simile", "Metaphor", "Personification", "Alliteration"),
                    correctAnswer = "Personification",
                    explanation = "Personification gives human qualities to non-human things",
                    difficulty = Difficulty.MEDIUM
                )
            )
        }
    }

    private fun getHistoryFallbacks(gradeLevel: Int, topic: String, country: String? = null): List<Question> {
        val countrySpecificQuestions = when (country) {
            "Canada" -> listOf(
                Question(
                    questionText = "When did Canada become a country?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("1867", "1876", "1901", "1931"),
                    correctAnswer = "1867",
                    explanation = "Canada became a country on July 1, 1867 through Confederation",
                    difficulty = Difficulty.MEDIUM
                ),
                Question(
                    questionText = "Who was Canada's first Prime Minister?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("John A. Macdonald", "Wilfrid Laurier", "Robert Borden", "Alexander Mackenzie"),
                    correctAnswer = "John A. Macdonald",
                    explanation = "Sir John A. Macdonald was Canada's first Prime Minister",
                    difficulty = Difficulty.MEDIUM
                )
            )
            "Australia" -> listOf(
                Question(
                    questionText = "When did Australia become a federation?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("1895", "1901", "1910", "1920"),
                    correctAnswer = "1901",
                    explanation = "Australia became a federation on January 1, 1901",
                    difficulty = Difficulty.MEDIUM
                )
            )
            "United Kingdom" -> listOf(
                Question(
                    questionText = "Who was the British queen during the Victorian era?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Elizabeth I", "Victoria", "Elizabeth II", "Mary"),
                    correctAnswer = "Victoria",
                    explanation = "Queen Victoria reigned during the Victorian era (1837-1901)",
                    difficulty = Difficulty.MEDIUM
                )
            )
            "Ghana" -> listOf(
                Question(
                    questionText = "Who was Ghana's first president after independence?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Kwame Nkrumah", "Jerry Rawlings", "John Kufuor", "Kofi Annan"),
                    correctAnswer = "Kwame Nkrumah",
                    explanation = "Kwame Nkrumah became Ghana's first president when it gained independence in 1957",
                    difficulty = Difficulty.MEDIUM
                ),
                Question(
                    questionText = "What was Ghana called before independence?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Gold Coast", "Ivory Coast", "Upper Volta", "British West Africa"),
                    correctAnswer = "Gold Coast",
                    explanation = "Ghana was known as the Gold Coast during British colonial rule",
                    difficulty = Difficulty.EASY
                )
            )
            else -> emptyList()
        }

        val generalQuestions = when {
            gradeLevel <= 5 -> listOf(
                Question(
                    questionText = "Who was the first president of the United States?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Abraham Lincoln", "George Washington", "Thomas Jefferson", "John Adams"),
                    correctAnswer = "George Washington",
                    explanation = "George Washington was the first U.S. president",
                    difficulty = Difficulty.EASY
                )
            )
            else -> listOf(
                Question(
                    questionText = "In which year did World War II end?",
                    questionType = QuestionType.FILL_IN_BLANK,
                    options = emptyList(),
                    correctAnswer = "1945",
                    explanation = "World War II ended in 1945",
                    difficulty = Difficulty.MEDIUM
                )
            )
        }

        return countrySpecificQuestions + generalQuestions
    }

    private fun getGeographyFallbacks(gradeLevel: Int, topic: String, country: String? = null): List<Question> {
        return when {
            gradeLevel <= 5 -> listOf(
                Question(
                    questionText = "How many continents are there?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("5", "6", "7", "8"),
                    correctAnswer = "7",
                    explanation = "There are 7 continents on Earth",
                    difficulty = Difficulty.EASY
                )
            )
            else -> listOf(
                Question(
                    questionText = "What is the capital of France?",
                    questionType = QuestionType.FILL_IN_BLANK,
                    options = emptyList(),
                    correctAnswer = "Paris",
                    explanation = "Paris is the capital city of France",
                    difficulty = Difficulty.EASY
                )
            )
        }
    }

    private fun getEconomicsFallbacks(gradeLevel: Int, topic: String, country: String? = null): List<Question> {
        return when {
            gradeLevel <= 2 -> listOf(
                Question(
                    questionText = "What do we call things we really need?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Wants", "Needs", "Toys", "Games"),
                    correctAnswer = "Needs",
                    explanation = "Needs are things we really need to live, like food and water!",
                    difficulty = Difficulty.EASY
                ),
                Question(
                    questionText = "We should share our toys with friends.",
                    questionType = QuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "Sharing is caring! When we share, everyone can have fun.",
                    difficulty = Difficulty.EASY
                )
            )
            gradeLevel <= 5 -> listOf(
                Question(
                    questionText = "What happens when lots of people want something but there isn't enough?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("The price goes down", "The price goes up", "Nothing happens", "It becomes free"),
                    correctAnswer = "The price goes up",
                    explanation = "When many people want something but there isn't enough, the price usually goes up",
                    difficulty = Difficulty.MEDIUM
                ),
                Question(
                    questionText = "Money helps us trade more easily than bartering.",
                    questionType = QuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "Money makes trading easier because everyone accepts it",
                    difficulty = Difficulty.EASY
                )
            )
            gradeLevel <= 8 -> listOf(
                Question(
                    questionText = "What is opportunity cost?",
                    questionType = QuestionType.SHORT_ANSWER,
                    options = emptyList(),
                    correctAnswer = "The next best alternative you give up when making a choice",
                    explanation = "Opportunity cost is what you miss out on when you choose one thing over another",
                    difficulty = Difficulty.MEDIUM
                ),
                Question(
                    questionText = "Supply and _____ work together to determine price.",
                    questionType = QuestionType.FILL_IN_BLANK,
                    options = emptyList(),
                    correctAnswer = "demand",
                    explanation = "Supply (how much is available) and demand (how much people want) determine price",
                    difficulty = Difficulty.MEDIUM
                )
            )
            else -> listOf(
                Question(
                    questionText = "What does GDP measure?",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Government debt", "Total economic output", "Unemployment rate", "Inflation rate"),
                    correctAnswer = "Total economic output",
                    explanation = "GDP (Gross Domestic Product) measures the total value of goods and services produced in a country",
                    difficulty = Difficulty.MEDIUM
                ),
                Question(
                    questionText = "Explain the difference between microeconomics and macroeconomics.",
                    questionType = QuestionType.SHORT_ANSWER,
                    options = emptyList(),
                    correctAnswer = "Microeconomics studies individual markets and consumers; macroeconomics studies the whole economy",
                    explanation = "Micro focuses on small parts, macro focuses on the big picture",
                    difficulty = Difficulty.HARD
                )
            )
        }
    }

    private fun getGeneralFallbacks(gradeLevel: Int, country: String? = null): List<Question> {
        return listOf(
            Question(
                questionText = "Which season comes after summer?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Winter", "Spring", "Fall/Autumn", "Summer"),
                correctAnswer = "Fall/Autumn",
                explanation = "The seasons go: Spring, Summer, Fall/Autumn, Winter",
                difficulty = Difficulty.EASY
            )
        )
    }
    
    /**
     * Calculate similarity between two question texts
     */
    private fun calculateSimilarity(text1: String, text2: String): Float {
        val clean1 = text1.lowercase().replace(Regex("[^a-z0-9\\s]"), "")
        val clean2 = text2.lowercase().replace(Regex("[^a-z0-9\\s]"), "")

        // Don't compare very short strings
        if (clean1.length < 20 || clean2.length < 20) {
            return 0f
        }

        // Check exact substring match (very similar)
        if (clean1.contains(clean2) || clean2.contains(clean1)) {
            return 0.9f
        }

        // Word-based similarity
        val words1 = clean1.split("\\s+".toRegex()).filter { it.length > 3 }.toSet()
        val words2 = clean2.split("\\s+".toRegex()).filter { it.length > 3 }.toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // Jaccard similarity
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        val jaccard = intersection.toFloat() / union

        // Trigram similarity
        val trigrams1 = getTrigrams(clean1)
        val trigrams2 = getTrigrams(clean2)

        val trigramIntersection = trigrams1.intersect(trigrams2).size
        val trigramUnion = trigrams1.union(trigrams2).size
        val trigramSimilarity = if (trigramUnion > 0) {
            trigramIntersection.toFloat() / trigramUnion
        } else 0f

        // Weighted combination
        return (jaccard * 0.6f + trigramSimilarity * 0.4f)
    }

    private fun getTrigrams(text: String): Set<String> {
        return if (text.length >= 3) {
            text.windowed(3, 1).toSet()
        } else {
            emptySet()
        }
    }

    /**
     * Sanitize and fix common JSON issues from AI responses
     */
    private fun sanitizeAndFixJson(rawJson: String): String {
        var json = rawJson.trim()
        
        // If JSON doesn't start with {, try to find it
        val startIndex = json.indexOf('{')
        if (startIndex > 0) {
            json = json.substring(startIndex)
        }
        
        // Handle unterminated strings by finding incomplete fields
        if (!json.endsWith("}")) {
            // Find the last complete field
            val lastCompleteField = findLastCompleteField(json)
            if (lastCompleteField != -1) {
                json = json.substring(0, lastCompleteField) + "}"
            } else {
                // Fallback: just add closing brace
                json += "}"
            }
        }
        
        // Fix common quote issues
        json = json.replace(""", "\"").replace(""", "\"")
        json = json.replace("'", "\"") // Replace single quotes with double quotes in keys
        
        // Ensure all string values are properly quoted
        json = fixUnquotedStrings(json)
        
        return json
    }
    
    private fun findLastCompleteField(json: String): Int {
        var lastGoodPosition = -1
        var inString = false
        var escaped = false
        var braceLevel = 0
        
        for (i in json.indices) {
            val char = json[i]
            
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString -> {
                    when (char) {
                        '{' -> braceLevel++
                        '}' -> braceLevel--
                        ',' -> if (braceLevel == 1) lastGoodPosition = i
                    }
                }
            }
        }
        
        return lastGoodPosition
    }
    
    private fun fixUnquotedStrings(json: String): String {
        // This is a simplified fix - in practice, you might need more robust parsing
        return json.replace(Regex("(\\w+)\\s*:"), "\"$1\":")
    }

    /**
     * Get country-specific context for questions
     */
    private fun getCountryContext(country: String, subject: Subject): String {
        return when (subject) {
            Subject.HISTORY -> getHistoryCountryContext(country)
            Subject.GEOGRAPHY -> getGeographyCountryContext(country)
            else -> ""
        }
    }

    private fun getHistoryCountryContext(country: String): String {
        return when (country) {
            "United States" -> "Focus on American history, including Colonial period, Revolutionary War, Civil War, and modern American events. Use examples from U.S. historical figures and events."
            "Canada" -> "Emphasize Canadian history including Indigenous peoples, French and British colonization, Confederation, and Canadian identity. Reference Canadian historical figures and events."
            "United Kingdom" -> "Draw from British history including monarchies, Industrial Revolution, British Empire, and modern UK. Use examples from British historical figures and events."
            "Australia" -> "Include Australian history such as Aboriginal culture, British colonization, gold rushes, and modern Australia. Reference Australian historical figures and events."
            "India" -> "Incorporate Indian history including ancient civilizations, Mughal Empire, British colonial period, independence movement, and modern India."
            "South Africa" -> "Include South African history covering indigenous peoples, Dutch and British colonization, apartheid, and post-apartheid era."
            "Germany" -> "Focus on German history including Holy Roman Empire, unification, World Wars, division and reunification."
            "France" -> "Emphasize French history including monarchy, French Revolution, Napoleon, and modern France."
            "China" -> "Include Chinese history covering ancient dynasties, imperial China, modern revolutions, and contemporary China."
            "Japan" -> "Focus on Japanese history including samurai period, Meiji Restoration, World War II, and modern Japan."
            "Ghana" -> "Focus on Ghanaian history including ancient kingdoms (like the Gold Coast), colonial period under British rule, independence movement led by Kwame Nkrumah, and modern Ghana. Reference Ghanaian historical figures and West African context."
            "Nigeria" -> "Include Nigerian history covering ancient kingdoms (Benin, Oyo), British colonial period, independence movement, civil war, and modern Nigeria. Reference Nigerian historical figures and West African context."
            "Kenya" -> "Focus on Kenyan history including indigenous peoples, British colonial period, Mau Mau uprising, independence movement, and modern Kenya. Reference Kenyan historical figures and East African context."
            "Egypt" -> "Include Egyptian history covering ancient civilizations, pharaohs, Islamic period, Ottoman rule, British influence, modern independence, and contemporary Egypt."
            else -> "Use examples and context relevant to the student's country and region when possible."
        }
    }

    private fun getGeographyCountryContext(country: String): String {
        return when (country) {
            "United States" -> "Use American geographic examples including states, major cities, rivers, mountain ranges, and climate zones. Reference the continental US, Alaska, and Hawaii."
            "Canada" -> "Include Canadian geographic features such as provinces, territories, major cities, the Canadian Shield, Rocky Mountains, and Arctic regions."
            "United Kingdom" -> "Focus on British Isles geography including England, Scotland, Wales, Northern Ireland, major cities, and surrounding seas."
            "Australia" -> "Emphasize Australian geography including states, territories, Outback, Great Barrier Reef, major cities, and unique climate zones."
            "India" -> "Include Indian subcontinent geography covering states, Himalayas, major rivers like Ganges, monsoons, and diverse climate zones."
            "South Africa" -> "Focus on South African geography including provinces, major cities, Table Mountain, Drakensberg Mountains, and different climate regions."
            "Germany" -> "Include German geography covering states (Länder), major cities, rivers like Rhine and Danube, and Central European features."
            "France" -> "Emphasize French geography including regions, major cities, Alps, Mediterranean coast, and European context."
            "China" -> "Focus on Chinese geography including provinces, major cities, rivers like Yangtze and Yellow River, and diverse landscapes."
            "Japan" -> "Include Japanese geography covering islands, major cities, mountains, earthquakes, and Pacific location."
            "Ghana" -> "Focus on Ghanaian geography including regions (Northern, Middle, Southern), major cities like Accra and Kumasi, Volta River, Lake Volta, Atlantic coastline, and West African climate zones."
            "Nigeria" -> "Include Nigerian geography covering states, major cities like Lagos and Abuja, Niger River, diverse climate zones from Sahel to coastal regions, and West African context."
            "Kenya" -> "Focus on Kenyan geography including counties, major cities like Nairobi and Mombasa, Great Rift Valley, Mount Kenya, Lake Victoria, and East African features."
            "Egypt" -> "Include Egyptian geography covering governorates, major cities like Cairo and Alexandria, Nile River, deserts, and North African/Middle Eastern context."
            else -> "Use geographic examples and context relevant to the student's country and region when possible."
        }
    }
}