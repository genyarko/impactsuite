package com.example.mygemma3n.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.ChatRepository
import com.example.mygemma3n.data.local.entities.ChatSessionEntity
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