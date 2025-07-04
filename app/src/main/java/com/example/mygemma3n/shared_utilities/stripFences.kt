// File: app/src/main/java/com/example/mygemma3n/shared_utilities/JsonUtils.kt
package com.example.mygemma3n.shared_utilities

/** Strip ```json``` fences from an LLM response. */
fun stripFences(s: String): String =
    s.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
