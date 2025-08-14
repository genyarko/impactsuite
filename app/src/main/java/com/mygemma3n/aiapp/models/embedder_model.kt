package com.mygemma3n.aiapp.models

enum class EmbedderModel(val assetFile: String) {
    MOBILE_BERT("mobile_bert.tflite"),
    AVG_WORD("average_word_embedder.tflite")
}

