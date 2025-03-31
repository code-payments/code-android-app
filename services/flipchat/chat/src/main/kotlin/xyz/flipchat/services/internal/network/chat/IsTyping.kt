package xyz.flipchat.services.internal.network.chat

import com.getcode.model.ID

data class IsTyping(
    val userId: ID,
    val state: TypingState
) {
    val currentlyTyping: Boolean
        get() = state is TypingState.Started || state is TypingState.Still
}

sealed interface TypingState {
    val ordinal: Int
    sealed interface ChangeEvent: TypingState
    sealed interface UserEvent: TypingState

    data object Unknown: TypingState {
        override val ordinal: Int = 0
    }
    data object Started: TypingState, ChangeEvent, UserEvent {
        override val ordinal: Int = 1
    }
    data object Still: TypingState, UserEvent {
        override val ordinal: Int = 2
    }
    data object Stopped: TypingState, ChangeEvent, UserEvent {
        override val ordinal: Int = 3
    }
    data object TimedOut: TypingState, ChangeEvent {
        override val ordinal: Int = 4
    }

    companion object {
        val entries: List<TypingState> = listOf(Unknown, Started, Still, Stopped, TimedOut)
    }
}