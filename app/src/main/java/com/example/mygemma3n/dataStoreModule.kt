package com.example.mygemma3n

import android.content.Context                    // gives you ‘Context’
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore


val Context.dataStore by preferencesDataStore(name = "settings")

private val SHOW_COMPLETED = booleanPreferencesKey("show_completed")
private val SORT_ORDER     = stringPreferencesKey("sort_order")
val EMBEDDER_KEY = stringPreferencesKey("embedder_model")

