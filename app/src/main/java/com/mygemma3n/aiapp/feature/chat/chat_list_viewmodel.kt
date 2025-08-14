package com.mygemma3n.aiapp.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygemma3n.aiapp.data.ChatRepository
import com.mygemma3n.aiapp.data.local.entities.ChatSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    val sessions: StateFlow<List<ChatSessionEntity>> = chatRepository
        .getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun createNewChat(): String {
        return chatRepository.createNewSession()
    }

    suspend fun deleteSession(session: ChatSessionEntity) {
        chatRepository.deleteSession(session)
    }
}