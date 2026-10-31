package com.flipcash.shared.chat.internal

import com.flipcash.shared.chat.ChatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    val current: ChatState get() = _state.value

    fun update(transform: (ChatState) -> ChatState) {
        _state.update(transform)
    }

    fun reset() {
        _state.value = ChatState()
    }
}
