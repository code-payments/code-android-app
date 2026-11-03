package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.State

/** Pinned messages and the pin bar. Only the events [stateReducer] routes here arrive; any other is a routing bug. */
internal fun reducePins(event: Event): (State) -> State =
    when (event) {
        is Event.PinsRead -> { state ->
            val pins = state.pins.read(event.pins, event.version)
            // A pin that left can take the bar's place out from under it; start over at the newest.
            val index = state.pinBarIndex.takeIf { it < pins.visible.size } ?: 0
            state.copy(pins = pins, pinBarIndex = index)
        }
        is Event.PinnedCopiesStored -> { state ->
            val pins = state.pins.copy(stored = event.messages)
            val index = state.pinBarIndex.takeIf { it < pins.visible.size } ?: 0
            state.copy(pins = pins, pinBarIndex = index)
        }
        is Event.PinSummaryChanged -> { state ->
            state.copy(pins = state.pins.copy(summaryCount = event.count))
        }
        is Event.PinMessage -> { state -> state.copy(selection = null, confirmingDelete = false) }
        is Event.UnpinMessage -> { state -> state.copy(selection = null, confirmingDelete = false) }
        is Event.PinCapReached -> { state -> state.copy(waitingPin = event.waiting) }
        Event.PinBarTapped -> { state -> state.copy(pinBarIndex = state.pins.stepAfter(state.pinBarIndex)) }
        else -> error("reducePins does not handle $event")
    }
