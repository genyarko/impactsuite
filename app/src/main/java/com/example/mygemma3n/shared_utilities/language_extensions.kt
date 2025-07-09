package com.example.mygemma3n.shared_utilities


import com.example.mygemma3n.feature.caption.Language

/**
 * Extension utilities for working with [Language].
 */
fun Language.toGoogleLanguageCode(): String = when (this) {
    Language.AUTO -> "auto"
    Language.ENGLISH -> "en-US"
    Language.SPANISH -> "es-ES"
    Language.FRENCH -> "fr-FR"
    Language.GERMAN -> "de-DE"
    Language.CHINESE -> "zh-CN"
    Language.JAPANESE -> "ja-JP"
    Language.KOREAN -> "ko-KR"
    Language.HINDI -> "hi-IN"
    Language.ARABIC -> "ar-SA"
    Language.PORTUGUESE -> "pt-BR"
    Language.RUSSIAN -> "ru-RU"
    Language.ITALIAN -> "it-IT"
    Language.DUTCH -> "nl-NL"
    Language.SWEDISH -> "sv-SE"
    Language.POLISH -> "pl-PL"
    Language.TURKISH -> "tr-TR"
    Language.INDONESIAN -> "id-ID"
    Language.VIETNAMESE -> "vi-VN"
    Language.THAI -> "th-TH"
    Language.HEBREW -> "he-IL"
}