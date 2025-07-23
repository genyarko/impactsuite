package com.example.mygemma3n.feature.tutor

import com.example.mygemma3n.data.StudentProfileEntity

object TutorResponseFormatter {

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