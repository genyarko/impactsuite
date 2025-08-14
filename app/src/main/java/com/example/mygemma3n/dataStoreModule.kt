package com.example.mygemma3n

import android.content.Context                    // gives you ‘Context’
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore


val Context.dataStore by preferencesDataStore(name = "settings")

private val SHOW_COMPLETED = booleanPreferencesKey("show_completed")
private val SORT_ORDER     = stringPreferencesKey("sort_order")
val EMBEDDER_KEY = stringPreferencesKey("embedder_model")

val SPEECH_API_KEY = stringPreferencesKey("google_speech_api_key")
val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
val USE_ONLINE_SERVICE = booleanPreferencesKey("use_online_service")
val ONLINE_MODEL_PROVIDER = stringPreferencesKey("online_model_provider")