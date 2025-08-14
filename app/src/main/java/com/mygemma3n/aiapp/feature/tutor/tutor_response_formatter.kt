package com.mygemma3n.aiapp.feature.tutor

import com.mygemma3n.aiapp.data.StudentProfileEntity

object TutorResponseFormatter {

    // In TutorViewModel, add this method:
    private fun formatTutorResponse(
        rawResponse: String,
        student: StudentProfileEntity,
        approach: TutorViewModel.TeachingApproach
    ): String {
        // Detect if the response contains lists or steps
        val hasNumberedSteps = rawResponse.contains(Regex("\\d+\\.\\s"))
        val hasBulletPoints = rawResponse.contains("•") || rawResponse.contains("-")

        return when {
            // Format step-by-step instructions
            hasNumberedSteps && approach == TutorViewModel.TeachingApproach.PROBLEM_SOLVING -> {
                val steps = extractSteps(rawResponse)
                val intro = extractIntro(rawResponse)
                TutorResponseFormatter.formatStepsResponse(steps, intro, student)
            }

            // Format lists
            hasBulletPoints && approach == TutorViewModel.TeachingApproach.EXPLANATION -> {
                val items = extractListItems(rawResponse)
                val intro = extractIntro(rawResponse)
                TutorResponseFormatter.formatListResponse(items, intro, null, student)
            }

            // Default formatting
            else -> rawResponse.formatForTutor(student)
        }
    }

    // Helper methods to extract content
    private fun extractSteps(response: String): List<String> {
        return response.split(Regex("\\d+\\.\\s"))
            .drop(1) // Skip content before first number
            .map { it.trim().takeWhile { char -> char != '\n' } }
            .filter { it.isNotBlank() }
    }

    private fun extractListItems(response: String): List<String> {
        return response.split(Regex("[•\\-]\\s"))
            .drop(1)
            .map { it.trim().takeWhile { char -> char != '\n' } }
            .filter { it.isNotBlank() }
    }

    private fun extractIntro(response: String): String {
        val firstListMarker = response.indexOfAny(listOf("1.", "•", "-"))
        return if (firstListMarker > 0) {
            response.substring(0, firstListMarker).trim()
        } else {
            "Here's what you need to know:"
        }
    }

    /**
     * Formats and truncates responses to fit UI constraints
     */
    fun formatResponse(
        rawResponse: String,
        student: StudentProfileEntity,
        maxCharacters: Int = 400
    ): String {
        // Clean up the response
        var formatted = rawResponse
            .trim()
            .replace(Regex("\\s+"), " ") // Remove extra spaces
            .replace(Regex("\n{3,}"), "\n\n") // Limit line breaks

        // Apply grade-specific formatting
        formatted = when (student.gradeLevel) {
            in 1..3 -> {
                // Very young students: Ultra simple
                formatted
                    .replace(Regex("\\b(furthermore|additionally|moreover|consequently)\\b", RegexOption.IGNORE_CASE), "also")
                    .replace(Regex("\\b(utilize|employ)\\b", RegexOption.IGNORE_CASE), "use")
                    .take(200) // Hard limit for young kids
            }
            in 4..6 -> {
                // Elementary: Simple but can handle more
                formatted
                    .replace(Regex("\\b(utilize)\\b", RegexOption.IGNORE_CASE), "use")
                    .take(300)
            }
            in 7..9 -> {
                // Middle school: More complex OK
                formatted.take(400)
            }
            else -> {
                // High school: Full complexity
                formatted.take(maxCharacters)
            }
        }

        // Ensure we don't cut off mid-sentence
        if (formatted.length == maxCharacters) {
            val lastPeriod = formatted.lastIndexOf('.')
            val lastQuestion = formatted.lastIndexOf('?')
            val lastExclamation = formatted.lastIndexOf('!')

            val lastSentenceEnd = maxOf(lastPeriod, lastQuestion, lastExclamation)
            if (lastSentenceEnd > maxCharacters * 0.7) {
                formatted = formatted.substring(0, lastSentenceEnd + 1)
            }
        }

        return formatted
    }

    /**
     * Structures list-based responses properly
     */
    fun formatListResponse(
        items: List<String>,
        intro: String,
        explanations: Map<String, String>? = null,
        student: StudentProfileEntity
    ): String {
        val builder = StringBuilder()

        // Introduction
        builder.append(intro).append("\n\n")

        // List all items first
        items.forEach { item ->
            builder.append("• $item\n")
        }

        // Add brief explanations if provided and grade appropriate
        if (explanations != null && student.gradeLevel >= 4) {
            builder.append("\n")
            explanations.entries.take(3).forEach { (item, explanation) ->
                val briefExplanation = explanation.split(".").firstOrNull() ?: explanation
                builder.append("$item: $briefExplanation.\n")
            }
        }

        return formatResponse(builder.toString(), student)
    }

    /**
     * Formats step-by-step instructions
     */
    fun formatStepsResponse(
        steps: List<String>,
        intro: String,
        student: StudentProfileEntity
    ): String {
        val maxSteps = when (student.gradeLevel) {
            in 1..3 -> 3
            in 4..6 -> 4
            in 7..9 -> 5
            else -> 6
        }

        val builder = StringBuilder(intro).append("\n\n")

        steps.take(maxSteps).forEachIndexed { index, step ->
            builder.append("${index + 1}. $step\n")
        }

        return formatResponse(builder.toString(), student)
    }
}

// Extension function for easy use in ViewModel
fun String.formatForTutor(student: StudentProfileEntity): String {
    return TutorResponseFormatter.formatResponse(this, student)
}