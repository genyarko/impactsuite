package com.example.mygemma3n.domain.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.mygemma3n.models.EmbedderModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.mygemma3n.EMBEDDER_KEY
import com.example.mygemma3n.GEMINI_API_KEY
import com.example.mygemma3n.USE_ONLINE_SERVICE
import com.example.mygemma3n.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.dataStore

    val embedderFlow: Flow<EmbedderModel> = dataStore.data
        .map { prefs ->
            EmbedderModel.valueOf(
                prefs[EMBEDDER_KEY] ?: EmbedderModel.MOBILE_BERT.name
            )
        }

    val apiKeyFlow: Flow<String> = dataStore.data
        .map { prefs ->
            prefs[GEMINI_API_KEY] ?: ""
        }

    val useOnlineServiceFlow: Flow<Boolean> = dataStore.data
        .map { prefs ->
            prefs[USE_ONLINE_SERVICE] ?: true  // Default to online if network available
        }

    suspend fun save(model: EmbedderModel) =
        dataStore.edit { it[EMBEDDER_KEY] = model.name }

    suspend fun saveApiKey(apiKey: String) =
        dataStore.edit { it[GEMINI_API_KEY] = apiKey }

    suspend fun saveUseOnlineService(useOnline: Boolean) =
        dataStore.edit { it[USE_ONLINE_SERVICE] = useOnline }
}
