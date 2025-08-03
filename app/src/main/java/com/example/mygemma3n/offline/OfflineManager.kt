package com.example.mygemma3n.offline

import android.content.Context
import androidx.room.withTransaction
import com.example.mygemma3n.data.AppDatabase
import com.example.mygemma3n.feature.tutor.TutorViewModel
import com.example.mygemma3n.shared_utilities.OfflineRAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineManager @Inject constructor(
    private val context: Context,
    private val database: AppDatabase
) {
    
    data class OfflineData(
        val conversations: List<OfflineConversation>,
        val curriculumData: Map<String, String>, // subject -> JSON content
        val userPreferences: Map<String, String>,
        val lastSyncTime: Long,
        val version: Int = 1
    )
    
    data class OfflineConversation(
        val id: String,
        val subject: String,
        val messages: List<OfflineMessage>,
        val timestamp: Long
    )
    
    data class OfflineMessage(
        val id: String,
        val content: String,
        val isUser: Boolean,
        val timestamp: Long,
        val metadata: Map<String, String> = emptyMap()
    )
    
    data class QueuedMessage(
        val id: String,
        val content: String,
        val subject: String,
        val gradeLevel: Int,
        val context: List<String>,
        val timestamp: Long,
        val retryCount: Int = 0
    )
    
    data class OfflineStatus(
        val isOnline: Boolean,
        val hasCachedData: Boolean,
        val cachedConversationsCount: Int,
        val queuedMessagesCount: Int,
        val lastSyncTime: Long?
    )
    
    private val _offlineStatus = MutableStateFlow(
        OfflineStatus(
            isOnline = true,
            hasCachedData = false,
            cachedConversationsCount = 0,
            queuedMessagesCount = 0,
            lastSyncTime = null
        )
    )
    val offlineStatus: Flow<OfflineStatus> = _offlineStatus.asStateFlow()
    
    private val messageQueue = mutableListOf<QueuedMessage>()
    private val offlineDataFile = File(context.filesDir, "offline_data.json")
    private val queueFile = File(context.filesDir, "message_queue.json")
    
    private val gson = Gson()
    
    suspend fun initializeOfflineMode() = withContext(Dispatchers.IO) {
        try {
            loadOfflineData()
            loadMessageQueue()
            updateOfflineStatus()
            Timber.d("Offline mode initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize offline mode")
        }
    }
    
    suspend fun cacheConversations(conversations: List<TutorViewModel.TutorMessage>) = withContext(Dispatchers.IO) {
        try {
            val offlineConversations = conversations.groupBy { it.metadata?.concept ?: "General" }
                .map { (subject, messages) ->
                    OfflineConversation(
                        id = java.util.UUID.randomUUID().toString(),
                        subject = subject,
                        messages = messages.map { msg ->
                            OfflineMessage(
                                id = msg.id,
                                content = msg.content,
                                isUser = msg.isUser,
                                timestamp = msg.timestamp,
                                metadata = mapOf(
                                    "status" to msg.status.name,
                                    "concept" to (msg.metadata?.concept ?: "")
                                )
                            )
                        },
                        timestamp = System.currentTimeMillis()
                    )
                }
            
            val currentData = loadOfflineDataFromFile()
            val updatedData = currentData.copy(
                conversations = (currentData.conversations + offlineConversations).takeLast(50), // Keep last 50 conversations
                lastSyncTime = System.currentTimeMillis()
            )
            
            saveOfflineDataToFile(updatedData)
            updateOfflineStatus()
            
            Timber.d("Cached ${offlineConversations.size} conversations offline")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache conversations")
        }
    }
    
    suspend fun cacheCurriculumData(subject: OfflineRAG.Subject, jsonContent: String) = withContext(Dispatchers.IO) {
        try {
            val currentData = loadOfflineDataFromFile()
            val updatedCurriculum = currentData.curriculumData.toMutableMap()
            updatedCurriculum[subject.name] = jsonContent
            
            val updatedData = currentData.copy(
                curriculumData = updatedCurriculum,
                lastSyncTime = System.currentTimeMillis()
            )
            
            saveOfflineDataToFile(updatedData)
            updateOfflineStatus()
            
            Timber.d("Cached curriculum data for ${subject.name}")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache curriculum data for ${subject.name}")
        }
    }
    
    suspend fun queueMessageForOnlineSync(
        content: String,
        subject: String,
        gradeLevel: Int,
        context: List<String>
    ): String = withContext(Dispatchers.IO) {
        val messageId = java.util.UUID.randomUUID().toString()
        
        val queuedMessage = QueuedMessage(
            id = messageId,
            content = content,
            subject = subject,
            gradeLevel = gradeLevel,
            context = context,
            timestamp = System.currentTimeMillis()
        )
        
        messageQueue.add(queuedMessage)
        saveMessageQueue()
        updateOfflineStatus()
        
        Timber.d("Queued message for online sync: $messageId")
        return@withContext messageId
    }
    
    suspend fun getOfflineConversations(): List<OfflineConversation> = withContext(Dispatchers.IO) {
        try {
            val data = loadOfflineDataFromFile()
            data.conversations.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load offline conversations")
            emptyList()
        }
    }
    
    suspend fun getCachedCurriculumData(subject: OfflineRAG.Subject): String? = withContext(Dispatchers.IO) {
        try {
            val data = loadOfflineDataFromFile()
            data.curriculumData[subject.name]
        } catch (e: Exception) {
            Timber.e(e, "Failed to load cached curriculum data for ${subject.name}")
            null
        }
    }
    
    suspend fun processPendingMessages(
        onlineProcessor: suspend (QueuedMessage) -> String
    ) = withContext(Dispatchers.IO) {
        if (messageQueue.isEmpty()) return@withContext
        
        val processedMessages = mutableListOf<QueuedMessage>()
        
        for (message in messageQueue.toList()) {
            try {
                val response = onlineProcessor(message)
                processedMessages.add(message)
                
                // Store the response for the user
                database.withTransaction {
                    // Add to chat history
                    Timber.d("Processed queued message ${message.id}: response length ${response.length}")
                }
                
            } catch (e: Exception) {
                Timber.w(e, "Failed to process queued message ${message.id}")
                
                // Increment retry count
                val retryMessage = message.copy(retryCount = message.retryCount + 1)
                if (retryMessage.retryCount < 3) {
                    messageQueue[messageQueue.indexOf(message)] = retryMessage
                } else {
                    // Max retries reached, remove from queue
                    processedMessages.add(message)
                    Timber.w("Max retries reached for message ${message.id}, removing from queue")
                }
            }
        }
        
        // Remove processed messages from queue
        messageQueue.removeAll(processedMessages)
        saveMessageQueue()
        updateOfflineStatus()
        
        Timber.d("Processed ${processedMessages.size} pending messages")
    }
    
    suspend fun clearOfflineData() = withContext(Dispatchers.IO) {
        try {
            offlineDataFile.delete()
            queueFile.delete()
            messageQueue.clear()
            updateOfflineStatus()
            Timber.d("Offline data cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear offline data")
        }
    }
    
    fun setOnlineStatus(isOnline: Boolean) {
        _offlineStatus.value = _offlineStatus.value.copy(isOnline = isOnline)
        Timber.d("Online status changed: $isOnline")
    }
    
    private suspend fun loadOfflineData() {
        try {
            val data = loadOfflineDataFromFile()
            Timber.d("Loaded offline data: ${data.conversations.size} conversations, ${data.curriculumData.size} curriculum files")
        } catch (e: Exception) {
            Timber.w(e, "No existing offline data found")
        }
    }
    
    private suspend fun loadMessageQueue() {
        try {
            if (queueFile.exists()) {
                val queueJson = queueFile.readText()
                val messages = gson.fromJson<List<QueuedMessage>>(queueJson, object : TypeToken<List<QueuedMessage>>() {}.type)
                messageQueue.clear()
                messageQueue.addAll(messages)
                Timber.d("Loaded ${messageQueue.size} queued messages")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load message queue")
        }
    }
    
    private suspend fun saveMessageQueue() {
        try {
            val queueJson = gson.toJson(messageQueue)
            queueFile.writeText(queueJson)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save message queue")
        }
    }
    
    private suspend fun loadOfflineDataFromFile(): OfflineData {
        return if (offlineDataFile.exists()) {
            try {
                val dataJson = offlineDataFile.readText()
                gson.fromJson(dataJson, OfflineData::class.java)
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse offline data, creating new")
                OfflineData(
                    conversations = emptyList(),
                    curriculumData = emptyMap(),
                    userPreferences = emptyMap(),
                    lastSyncTime = 0L
                )
            }
        } else {
            OfflineData(
                conversations = emptyList(),
                curriculumData = emptyMap(),
                userPreferences = emptyMap(),
                lastSyncTime = 0L
            )
        }
    }
    
    private suspend fun saveOfflineDataToFile(data: OfflineData) {
        try {
            val dataJson = gson.toJson(data)
            offlineDataFile.writeText(dataJson)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save offline data")
        }
    }
    
    private suspend fun updateOfflineStatus() {
        try {
            val data = loadOfflineDataFromFile()
            
            _offlineStatus.value = _offlineStatus.value.copy(
                hasCachedData = data.conversations.isNotEmpty() || data.curriculumData.isNotEmpty(),
                cachedConversationsCount = data.conversations.size,
                queuedMessagesCount = messageQueue.size,
                lastSyncTime = if (data.lastSyncTime > 0) data.lastSyncTime else null
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update offline status")
        }
    }
    
    suspend fun getOfflineStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        val data = loadOfflineDataFromFile()
        val dataSize = if (offlineDataFile.exists()) offlineDataFile.length() else 0L
        val queueSize = if (queueFile.exists()) queueFile.length() else 0L
        
        mapOf(
            "conversationsCount" to data.conversations.size,
            "curriculumFilesCount" to data.curriculumData.size,
            "queuedMessagesCount" to messageQueue.size,
            "totalDataSizeBytes" to (dataSize + queueSize),
            "lastSyncTime" to data.lastSyncTime,
            "isOnline" to _offlineStatus.value.isOnline
        )
    }
}

/**
 * Network connectivity monitor
 */
@Singleton
class NetworkMonitor @Inject constructor(
    private val context: Context,
    private val offlineManager: OfflineManager
) {
    
    private val _isConnected = MutableStateFlow(true)
    val isConnected: Flow<Boolean> = _isConnected.asStateFlow()
    
    fun startMonitoring() {
        // In a real implementation, you would use ConnectivityManager
        // and register a NetworkCallback to monitor network changes
        
        // Simulated network monitoring
        _isConnected.value = true
        offlineManager.setOnlineStatus(true)
        
        Timber.d("Network monitoring started")
    }
    
    fun stopMonitoring() {
        Timber.d("Network monitoring stopped")
    }
    
    fun simulateOfflineMode() {
        _isConnected.value = false
        offlineManager.setOnlineStatus(false)
        Timber.d("Simulating offline mode")
    }
    
    fun simulateOnlineMode() {
        _isConnected.value = true
        offlineManager.setOnlineStatus(true)
        Timber.d("Simulating online mode")
    }
}