package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.State

/** The quick reaction strip and toggling a reaction. Null when [event] belongs to another area. */
internal fun reduceReactions(event: Event): ((State) -> State)? =
    when (event) {
        // The toggle itself is handled in initMessageActionHandlers (it calls the
        // coordinator); the reducer's only job is to take the bubble out of selection mode
        // when the tap came from the quick strip.
        is Event.ToggleReaction -> { state ->
            if (event.clearsSelection) state.copy(selection = null) else state
        }
        is Event.QuickReactionInputsLoaded -> { state ->
            state.copy(quickReactionInputs = event.inputs).withQuickReactionStrip()
        }
        is Event.SelectionReactionsChanged -> { state ->
            val selection = state.selection
            if (selection?.messageId != event.messageId) {
                state
            } else {
                state.copy(selection = selection.copy(selfReactions = event.selfReactions))
                    .withQuickReactionStrip()
            }
        }
        is Event.RefreshReactionIds -> { state -> state }
        else -> null
    }
