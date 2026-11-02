package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.State
import com.flipcash.shared.chat.UnreadBoundary

/** The unread boundary, the badge and the arrival cutoff, plus the gated landing. Only the events [stateReducer] routes here arrive; any other is a routing bug. */
internal fun reduceUnread(event: Event): (State) -> State =
    when (event) {
        is Event.GatedLandingChanged -> { state -> state.copy(gatedLanding = event.item) }
        is Event.UnreadBoundaryResolved -> { state ->
            state.copy(
                unreadBoundary = event.boundary,
                unreadWalkBudget = event.walkBudget,
                unreadBadgeOpenCount = (event.boundary as? UnreadBoundary.At)?.count ?: 0,
            )
        }
        is Event.ArrivalCutoffRecorded -> { state -> state.copy(newestInboundIdAtOpen = event.cutoff) }
        Event.UserScrolledTranscript, Event.OwnMessageSent -> { state ->
            state.copy(unreadBadgeFrozen = false)
        }
        else -> error("reduceUnread does not handle $event")
    }
