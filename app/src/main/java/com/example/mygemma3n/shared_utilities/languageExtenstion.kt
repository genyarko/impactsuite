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
    // Major World Languages
    Language.BENGALI -> "bn"
    
    // European Languages
    Language.GREEK -> "el"
    Language.CZECH -> "cs"
    Language.HUNGARIAN -> "hu"
    Language.ROMANIAN -> "ro"
    Language.BULGARIAN -> "bg"
    Language.CROATIAN -> "hr"
    Language.SERBIAN -> "sr"
    Language.SLOVAK -> "sk"
    Language.SLOVENIAN -> "sl"
    Language.LITHUANIAN -> "lt"
    Language.LATVIAN -> "lv"
    Language.ESTONIAN -> "et"
    Language.FINNISH -> "fi"
    Language.DANISH -> "da"
    Language.NORWEGIAN -> "no"
    Language.ICELANDIC -> "is"
    Language.IRISH -> "ga"
    Language.WELSH -> "cy"
    Language.BASQUE -> "eu"
    Language.CATALAN -> "ca"
    Language.GALICIAN -> "gl"
    Language.MALTESE -> "mt"
    
    // Asian Languages
    Language.MALAY -> "ms"
    Language.FILIPINO -> "tl"
    Language.BURMESE -> "my"
    Language.KHMER -> "km"
    Language.LAO -> "lo"
    Language.MONGOLIAN -> "mn"
    Language.NEPALI -> "ne"
    Language.SINHALA -> "si"
    Language.TAMIL -> "ta"
    Language.TELUGU -> "te"
    Language.KANNADA -> "kn"
    Language.MALAYALAM -> "ml"
    Language.MARATHI -> "mr"
    Language.GUJARATI -> "gu"
    Language.PUNJABI -> "pa"
    Language.URDU -> "ur"
    Language.PERSIAN -> "fa"
    Language.PASHTO -> "ps"
    Language.DARI -> "fa" // Dari uses Persian language code
    Language.KURDISH -> "ku"
    Language.ARMENIAN -> "hy"
    Language.GEORGIAN -> "ka"
    Language.AZERBAIJANI -> "az"
    Language.KAZAKH -> "kk"
    Language.KYRGYZ -> "ky"
    Language.TAJIK -> "tg"
    Language.TURKMEN -> "tk"
    Language.UZBEK -> "uz"
    
    // Middle Eastern & African Languages
    Language.AMHARIC -> "am"
    Language.HAUSA -> "ha"
    Language.YORUBA -> "yo"
    Language.IGBO -> "ig"
    Language.SWAHILI -> "sw"
    Language.SOMALI -> "so"
    Language.AFRIKAANS -> "af"
    Language.ZULU -> "zu"
    Language.XHOSA -> "xh"
    
    // Latin American Languages
    Language.QUECHUA -> "qu"
    Language.GUARANI -> "gn"
    
    // Pacific Languages
    Language.HAWAIIAN -> "haw"
    Language.MAORI -> "mi"
    Language.SAMOAN -> "sm"
    Language.TONGAN -> "to"
    Language.FIJIAN -> "fj"
    
    // Additional European Regional Languages
    Language.CORSICAN -> "co"
    Language.BRETON -> "br"
    Language.OCCITAN -> "oc"
    Language.SARDINIAN -> "sc"
    Language.LUXEMBOURGISH -> "lb"
    Language.FAROESE -> "fo"
    
    // Additional Asian Languages
    Language.TIBETAN -> "bo"
    Language.DZONGKHA -> "dz"
    Language.ASSAMESE -> "as"
    Language.ORIYA -> "or"
    Language.SANSKRIT -> "sa"
    
    // Sign Languages (fallback to English - limited support)
    Language.ASL -> "en"
    Language.BSL -> "en"
    
    // Constructed Languages
    Language.ESPERANTO -> "eo"
    Language.INTERLINGUA -> "ia"
    
    // Historical Languages
    Language.LATIN -> "la"
    Language.ANCIENT_GREEK -> "el" // Fallback to modern Greek
}