package com.mygemma3n.aiapp.shared_utilities

/**
 * Simple singleton to share content between screens for quiz generation
 */
object QuizContentManager {
    
    private var quizContent: String? = null
    private var quizTitle: String? = null
    
    fun setContent(content: String, title: String = "Custom Topic") {
        quizContent = content
        quizTitle = title
    }
    
    fun getContent(): String? = quizContent
    
    fun getTitle(): String = quizTitle ?: "Custom Topic"
    
    fun hasContent(): Boolean = !quizContent.isNullOrBlank()
    
    fun clearContent() {
        quizContent = null
        quizTitle = null
    }
}