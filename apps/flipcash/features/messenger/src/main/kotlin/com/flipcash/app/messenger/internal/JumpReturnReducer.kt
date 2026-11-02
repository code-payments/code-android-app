package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.State

/** Jumping to a message and the stack of places to return to. Only the events [stateReducer] routes here arrive; any other is a routing bug. */
internal fun reduceJumpReturn(event: Event): (State) -> State =
    when (event) {
        // The request itself changes nothing: the target is only worth holding once the
        // walk's bound resolves, and that read is what decides whether it can be reached.
        // The return stack is the one thing that moves now: a quote pushes the message it
        // was tapped in, a message link has nowhere to return to.
        is Event.JumpToMessage -> { state ->
            val origin = event.origin
            state.copy(returnStack = origin?.let(state.returnStack::pushOrigin) ?: emptyList())
        }
        Event.ReturnToOrigin -> { state -> state }
        is Event.ReturnResolved -> { state ->
            val landing = event.landing
            val left = state.returnStack.dropLast(landing.popped)
            if (landing.origin == null) {
                state.copy(returnStack = left, newestRequests = state.newestRequests + 1)
            } else {
                state.copy(returnStack = left, jumpTarget = landing.origin, jumpBudget = landing.budget)
            }
        }
        is Event.ReturnOriginsReached -> { state ->
            state.copy(returnStack = state.returnStack.dropReached(event.origins))
        }
        Event.ReturnStackCleared -> { state -> state.copy(returnStack = emptyList()) }
        is Event.JumpResolved -> { state ->
            state.copy(jumpTarget = event.messageId, jumpBudget = event.budget)
        }
        Event.JumpConsumed -> { state ->
            state.copy(jumpTarget = null, jumpBudget = null, openingAtMessage = false)
        }
        Event.JumpUnreachable -> { state -> state.copy(openingAtMessage = false) }
        else -> error("reduceJumpReturn does not handle $event")
    }
