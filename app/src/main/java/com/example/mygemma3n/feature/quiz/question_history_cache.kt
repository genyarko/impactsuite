package com.example.mygemma3n.feature.quiz

import kotlin.random.Random

class QuestionHistoryCache {
    private val recentQuestions = mutableListOf<String>()
    private val maxSize = 50

    fun addQuestion(question: String) {
        recentQuestions.add(question)
        if (recentQuestions.size > maxSize) {
            recentQuestions.removeAt(0)
        }
    }

    fun isTooDimilar(newQuestion: String, threshold: Float = 0.6f): Boolean {
        return recentQuestions.any { existing ->
            calculateSimilarity(newQuestion, existing) > threshold
        }
    }

    private fun calculateSimilarity(a: String, b: String): Float {
        val aTokens = a.lowercase().split("\\s+".toRegex()).toSet()
        val bTokens = b.lowercase().split("\\s+".toRegex()).toSet()

        val intersection = aTokens.intersect(bTokens).size
        val union = aTokens.union(bTokens).size

        return if (union == 0) 0f else intersection.toFloat() / union
    }


    fun clear() {
        recentQuestions.clear()
    }
}

