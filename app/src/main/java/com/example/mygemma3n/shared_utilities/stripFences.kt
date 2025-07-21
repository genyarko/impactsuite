// File: app/src/main/java/com/example/mygemma3n/shared_utilities/JsonUtils.kt
package com.example.mygemma3n.shared_utilities

/** Strip ```json``` fences from an LLM response. */
fun stripFences(s: String): String {
    return s.replace(Regex("^```(?:json)?\\s*|\\s*```$"), "").trim()
}
