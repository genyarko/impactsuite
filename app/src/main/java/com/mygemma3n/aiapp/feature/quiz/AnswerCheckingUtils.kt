package com.mygemma3n.aiapp.feature.quiz

// Extracted from QuizGeneratorViewModel for reuse and to reduce file size.
// Top-level functions so they can be used directly within the same package.


fun normalizeAnswer(answer: String): String {
        return answer
            .trim()
            .lowercase()
            // Remove common articles at the beginning
            .replace(Regex("^(the|a|an)\\s+"), "")
            // Remove punctuation
            .replace(Regex("[.,!?;:'\"-]"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

fun isFlexibleAnswer(expectedAnswer: String, userAnswer: String): Boolean {
        // Common patterns that indicate open-ended questions
        val flexibleAnswerPatterns = listOf(
            "answers will vary",
            "answers may vary",
            "various answers",
            "multiple answers",
            "depends on",
            "student answers",
            "open response",
            "personal opinion",
            "individual response",
            "varies",
            "different answers",
            "any reasonable",
            "sample answer",
            "example answer",
            "possible answer",
            "could include",
            "might include"
        )
        
        // Check if the expected answer contains any flexible patterns
        val isFlexibleExpected = flexibleAnswerPatterns.any { pattern ->
            expectedAnswer.contains(pattern, ignoreCase = true)
        }
        
        if (isFlexibleExpected) {
            // For flexible questions, accept any non-empty response that shows effort
            val trimmedUserAnswer = userAnswer.trim()
            
            // Reject obviously incomplete or non-effort responses
            val rejectedResponses = setOf(
                "", "i don't know", "dont know", "idk", "no idea", "nothing", 
                "not sure", "dunno", "?", "??", "???"
            )
            
            if (trimmedUserAnswer.lowercase() in rejectedResponses) {
                return false
            }
            
            // Accept if user provided any meaningful response (at least 2 characters)
            return trimmedUserAnswer.length >= 2
        }
        
        return false
    }

fun checkAnswerVariations(userAnswer: String, correctAnswer: String, question: Question): Boolean {
        // Check for compound answers with parenthetical explanations
        val compoundAnswerPattern = """(.+?)\s*\([^)]*\)\s*(or|and)\s*(.+?)\s*\([^)]*\)""".toRegex()
        val singleAnswerPattern = """(.+?)\s*\([^)]*\)""".toRegex()
        
        // Extract main terms from compound answers
        val correctAnswerParts = when {
            compoundAnswerPattern.containsMatchIn(correctAnswer) -> {
                // Handle "term1 (explanation) or term2 (explanation)" format
                compoundAnswerPattern.find(correctAnswer)?.let { match ->
                    listOf(
                        normalizeAnswer(match.groupValues[1].trim()),
                        normalizeAnswer(match.groupValues[3].trim())
                    )
                } ?: listOf(normalizeAnswer(correctAnswer))
            }
            singleAnswerPattern.containsMatchIn(correctAnswer) -> {
                // Handle "term (explanation)" format
                singleAnswerPattern.find(correctAnswer)?.let { match ->
                    listOf(normalizeAnswer(match.groupValues[1].trim()))
                } ?: listOf(normalizeAnswer(correctAnswer))
            }
            correctAnswer.contains(" or ") -> {
                // Handle simple "term1 or term2" format
                correctAnswer.split(" or ").map { normalizeAnswer(it.trim()) }
            }
            correctAnswer.contains(" and ") -> {
                // Handle simple "term1 and term2" format
                correctAnswer.split(" and ").map { normalizeAnswer(it.trim()) }
            }
            else -> listOf(normalizeAnswer(correctAnswer))
        }
        
        // Check if user answer matches any of the acceptable parts
        if (correctAnswerParts.any { it == userAnswer || it.contains(userAnswer) || userAnswer.contains(it) }) {
            return true
        }

        // Direct variations mapping
        val answerVariations = mapOf(
            "industrial revolution" to listOf(
                "industrial revolution",
                "the industrial revolution",
                "industrialization",
                "industrial age",
                "industrial era"
            ),
            "photosynthesis" to listOf(
                "photosynthesis",
                "photo synthesis",
                "photosynthetic process",
                "the process of photosynthesis"
            ),
            "evaporation" to listOf(
                "evaporation",
                "evaporating",
                "water evaporation",
                "the evaporation process"
            ),
            "mitochondria" to listOf(
                "mitochondria",
                "mitochondrion",
                "the mitochondria",
                "mitochondrial"
            ),
            "karma" to listOf(
                "karma",
                "good deeds and bad deeds",
                "actions and consequences",
                "law of karma"
            ),
            "dharma" to listOf(
                "dharma",
                "righteous duty",
                "moral law",
                "religious duty"
            )
        )

        // Check if the user answer or any correct answer part has known variations
        val allAnswerParts = correctAnswerParts + listOf(normalizeAnswer(correctAnswer))
        for (answerPart in allAnswerParts) {
            val variations = answerVariations[answerPart] ?: emptyList()
            if (variations.any { normalizeAnswer(it) == userAnswer }) {
                return true
            }
        }

        // Enhanced semantic matching for better coverage
        val userWords = userAnswer.split(" ").filter { it.length > 2 }
        val correctWords = correctAnswerParts.flatMap { it.split(" ").filter { word -> word.length > 2 } }

        if (userWords.isEmpty() || correctWords.isEmpty()) {
            return false
        }

        // Semantic word mappings for better matching
        val semanticMappings = mapOf(
            "water" to listOf("water", "drinking", "irrigation", "hydration"),
            "food" to listOf("food", "farming", "agriculture", "crops", "harvest"),
            "transport" to listOf("transport", "transportation", "trade", "travel", "movement"),
            "provided" to listOf("provided", "gave", "supplied", "offered", "made"),
            "easier" to listOf("easier", "better", "improved", "facilitated"),
            "climate" to listOf("climate", "weather", "environment", "conditions"),
            "egyptian" to listOf("egyptian", "egypt", "ancient"),
            "nile" to listOf("nile", "river")
        )

        val matchingWords = userWords.count { userWord ->
            correctWords.any { correctWord ->
                // Direct match
                userWord == correctWord ||
                // Substring matching
                (userWord.length > 3 && correctWord.contains(userWord)) ||
                (correctWord.length > 3 && userWord.contains(correctWord)) ||
                // Prefix matching
                (userWord.length > 4 && correctWord.startsWith(userWord)) ||
                (correctWord.length > 4 && userWord.startsWith(correctWord)) ||
                // Semantic mapping check
                semanticMappings[userWord]?.contains(correctWord) == true ||
                semanticMappings[correctWord]?.contains(userWord) == true
            }
        }

        // Expanded key concepts for better coverage (more lenient for 6th graders)
        val keyConceptsSet = setOf(
            "water", "food", "transport", "transportation", "trade", "farming", "agriculture",
            "leader", "leadership", "ruler", "king", "queen", "government", "rule", "control",
            "egypt", "egyptian", "nile", "river", "flood", "flooding", "harvest", "planting",
            "ancient", "civilization", "empire", "kingdom", "city", "culture", "religion",
            "democracy", "republic", "monarchy", "organize", "organization", "skill", "skills",
            "communication", "roads", "canals", "harbors", "travel", "commerce", "goods",
            "agreement", "exchange", "infrastructure", "customers", "business", "economy"
        )
        
        val keyConceptsInUser = userWords.intersect(keyConceptsSet)
        val keyConceptsInCorrect = correctWords.intersect(keyConceptsSet)
        val conceptCoverage = if (keyConceptsInCorrect.isNotEmpty()) {
            keyConceptsInUser.size.toFloat() / keyConceptsInCorrect.size
        } else 0f

        val similarity = matchingWords.toFloat() / maxOf(userWords.size, correctWords.size)
        
        // More lenient thresholds for 6th graders:
        // - Accept if user mentions ANY key concept (even just one)
        // - Lower word similarity requirement
        // - Accept if user answer contains at least one important word
        val hasKeyWords = keyConceptsInUser.isNotEmpty()
        val hasReasonableSimilarity = similarity >= 0.3f  // Lowered from 0.6f
        val hasGoodConceptCoverage = conceptCoverage >= 0.5f  // Lowered from 0.7f
        
        return hasKeyWords || hasReasonableSimilarity || hasGoodConceptCoverage
    }

fun checkSimpleKeywordMatch(userAnswer: String, correctAnswer: String): Boolean {
        val userWords = userAnswer.split(" ").filter { it.length > 2 }.map { it.lowercase() }
        val correctWords = correctAnswer.split(" ").filter { it.length > 2 }.map { it.lowercase() }
        
        // Expanded key educational concepts across all subjects
        val importantConcepts = setOf(
            // History & Government
            "trade", "trading", "leader", "leadership", "ruler", "rule", "government", 
            "skill", "skills", "organization", "communicate", "communication",
            "roads", "infrastructure", "agriculture", "farming", "water", "nile",
            "egypt", "egyptian", "ancient", "civilization", "democracy", "republic",
            
            // Geography & Demographics
            "population", "people", "density", "coastal", "coast", "cities", "city",
            "urban", "rural", "mountain", "mountains", "plains", "rivers", "river",
            "fertile", "land", "resources", "climate", "migrate", "move", "settlement",
            "region", "area", "location", "northern", "southern", "eastern", "western",
            
            // Science & Nature
            "climate", "weather", "temperature", "precipitation", "ecosystem", "habitat",
            "species", "adaptation", "environment", "natural", "resources", "energy",
            
            // Economics & Social
            "economy", "economic", "jobs", "employment", "industry", "services",
            "culture", "cultural", "society", "social", "community", "family"
        )
        
        // Check for semantic word overlap (more lenient)
        val userConcepts = userWords.intersect(importantConcepts)
        val correctConcepts = correctWords.intersect(importantConcepts)
        
        // Accept if user mentions relevant concepts
        if (userConcepts.isNotEmpty() && correctConcepts.isNotEmpty()) {
            val hasOverlap = userConcepts.intersect(correctConcepts).isNotEmpty()
            val hasRelevantConcept = userConcepts.size >= 1
            return hasOverlap || hasRelevantConcept
        }
        
        // Additional lenient check: partial word matching for key terms
        val keyTermsInCorrect = correctWords.filter { word ->
            importantConcepts.any { concept -> word.contains(concept) || concept.contains(word) }
        }
        val keyTermsInUser = userWords.filter { word ->
            importantConcepts.any { concept -> word.contains(concept) || concept.contains(word) }
        }
        
        // Accept if user mentions any key geographic/demographic terms for population questions
        val isPopulationQuestion = correctWords.any { it in setOf("population", "density", "people", "coastal", "cities") }
        val userMentionsPopulationConcepts = userWords.any { it in setOf("people", "population", "cities", "coastal", "move", "southern", "northern") }
        
        return (keyTermsInCorrect.isNotEmpty() && keyTermsInUser.isNotEmpty()) ||
               (isPopulationQuestion && userMentionsPopulationConcepts)
    }

fun checkGeographicConceptMatch(userAnswer: String, correctAnswer: String, question: Question): Boolean {
        val userWords = userAnswer.lowercase().split(" ").filter { it.length > 2 }
        val correctWords = correctAnswer.lowercase().split(" ").filter { it.length > 2 }
        
        // Check if this is a population/demographic question
        val isPopulationQuestion = correctWords.any { 
            it in setOf("population", "density", "people", "coastal", "cities", "plains", "rivers", "fertile", "mountainous") 
        }
        
        if (isPopulationQuestion) {
            // For population questions, accept if user shows understanding of:
            // 1. Where people live (coastal, cities, south, north, etc.)
            // 2. Why people live there (resources, fertile, etc.)
            val populationConcepts = setOf(
                "people", "population", "live", "move", "cities", "city", "urban",
                "coastal", "coast", "southern", "northern", "eastern", "western",
                "plains", "rivers", "fertile", "resources", "farming", "mountains",
                "density", "higher", "lower", "areas", "regions"
            )
            
            val userPopulationConcepts = userWords.intersect(populationConcepts)
            val correctPopulationConcepts = correctWords.intersect(populationConcepts)
            
            // Accept if user mentions relevant population concepts
            if (userPopulationConcepts.isNotEmpty() && correctPopulationConcepts.isNotEmpty()) {
                return true
            }
            
            // Specific pattern matching for population distribution answers
            val userMentionsLocation = userWords.any { it in setOf("southern", "coastal", "cities", "plains") }
            val correctMentionsLocation = correctWords.any { it in setOf("southern", "coastal", "cities", "plains") }
            
            if (userMentionsLocation && correctMentionsLocation) {
                return true
            }
        }
        
        // Check for climate/geography questions
        val isGeographyQuestion = correctWords.any {
            it in setOf("climate", "weather", "temperature", "rainfall", "desert", "forest", "mountain", "ocean")
        }
        
        if (isGeographyQuestion) {
            val geographyConcepts = setOf(
                "climate", "weather", "hot", "cold", "dry", "wet", "rain", "rainfall",
                "desert", "forest", "mountain", "ocean", "temperature", "season"
            )
            
            val userGeoConcepts = userWords.intersect(geographyConcepts)
            val correctGeoConcepts = correctWords.intersect(geographyConcepts)
            
            return userGeoConcepts.isNotEmpty() && correctGeoConcepts.isNotEmpty()
        }
        
        return false
    }

fun calculateEnhancedSimilarity(text1: String, text2: String): Float {
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
        val words1 = clean1.split("\\s+".toRegex()).filter { it.length > 3 }.toSet() // Changed from > 2 to > 3
        val words2 = clean2.split("\\s+".toRegex()).filter { it.length > 3 }.toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // Jaccard similarity
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        val jaccard = intersection.toFloat() / union

        // Also check for n-gram similarity (trigrams instead of bigrams)
        val trigrams1 = getTrigrams(clean1)
        val trigrams2 = getTrigrams(clean2)

        val trigramIntersection = trigrams1.intersect(trigrams2).size
        val trigramUnion = trigrams1.union(trigrams2).size
        val trigramSimilarity = if (trigramUnion > 0) {
            trigramIntersection.toFloat() / trigramUnion
        } else 0f

        // Weighted combination - slightly less aggressive
        return (jaccard * 0.5f + trigramSimilarity * 0.5f)
    }

fun getTrigrams(text: String): Set<String> {
        return if (text.length >= 3) {
            text.windowed(3, 1).toSet()
        } else {
            emptySet()
        }
    }

fun getBigrams(text: String): Set<String> {
        return text.windowed(2, 1).toSet()
    }