package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.State

/** Who is typing, and the viewer's own typing state. Only the events [stateReducer] routes here arrive; any other is a routing bug. */
internal fun reduceTyping(event: Event): (State) -> State =
    when (event) {
        is Event.TypistsUpdated -> { state -> state.copy(typists = event.typists) }
        is Event.TypingAvatarsUpdated -> { state -> state.copy(typingAvatars = event.avatars) }
        Event.OnSelfTypingStarted -> { state -> state.copy(isSelfTyping = true) }
        Event.OnSelfTypingStill -> { state -> state }
        Event.OnSelfTypingStopped -> { state -> state.copy(isSelfTyping = false) }
        is Event.TypingEnabled -> { state ->
            state.copy(
                typingConstraints = state.typingConstraints.copy(
                    enabled = event.enabled,
                    resolved = true,
                )
            )
        }
        else -> error("reduceTyping does not handle $event")
    }
